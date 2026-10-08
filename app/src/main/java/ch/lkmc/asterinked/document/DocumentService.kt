package ch.lkmc.asterinked.document

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import android.util.LruCache
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import java.io.File
import java.util.UUID

internal data class OpenDocument(val draft: Draft, val pages: List<PageSpec>, val preview: Bitmap?)

/** What [DocumentOperations.open] found behind a URI. */
internal sealed interface OpenResult {
    /**
     * A different PDF, inspected and rendered but not yet the draft:
     * [DocumentOperations.adopt] makes it the draft, [DocumentOperations.discard] drops it.
     */
    data class Opened(val document: OpenDocument) : OpenResult

    /**
     * The same bytes as the current draft's PDF, which stays open as [draft]:
     * unchanged except for the name the file arrived with this time.
     */
    data class AlreadyOpen(val draft: Draft) : OpenResult
}

/**
 * What the editor needs from storage and rendering. Every call except
 * [cachedPreview] runs on the process worker in production, including close.
 * A later editor restores only after the cleared editor's writes and close.
 */
internal interface DocumentOperations {
    /**
     * Imports [uri] to replace [current]. A file with the same bytes as
     * [current]'s PDF keeps that draft instead, and its new copy is dropped;
     * the caller then saves the kept draft if its name changed. Any other PDF
     * waits to be adopted or discarded, so unexported notes are replaced only
     * once the user agrees.
     */
    fun open(uri: Uri, current: Draft?): OpenResult

    /** Makes an opened document the draft, replacing the current one and its PDF. */
    fun adopt(document: OpenDocument)

    /** Drops an opened document the user decided not to keep. */
    fun discard(document: OpenDocument)
    fun restore(): OpenDocument?
    fun render(draft: Draft): Bitmap

    /** A preview [render] already produced, if still cached. Safe on any thread. */
    fun cachedPreview(draft: Draft): Bitmap?
    fun saveDraft(draft: Draft)
    fun export(draft: Draft, destination: Uri)

    /** Writes an annotated copy for the share sheet and returns it. */
    fun share(draft: Draft): File
    fun close()
}

internal class DocumentService(context: Context) : DocumentOperations {
    private val store = DocumentStore(context)
    private val engine = PdfEngine(context.cacheDir)
    private val cache = context.cacheDir
    // A preview is ~13 MB. A sixth of the heap holds the visible page and both
    // neighbours with a 256 MB heap, and only the most recent preview with 128 MB.
    // Below about 80 MB nothing fits: pages still render, but prefetch is wasted.
    private val previews = object : LruCache<String, Bitmap>((Runtime.getRuntime().maxMemory() / PREVIEW_HEAP_SHARE).toInt()) {
        override fun sizeOf(key: String, value: Bitmap) = value.allocationByteCount
    }

    // Imported PDFs waiting to be adopted or discarded. They are not the
    // draft's, but a draft write meanwhile must not prune them. Only the
    // worker touches this set.
    private val waiting = mutableSetOf<File>()

    init {
        PDFBoxResourceLoader.init(context.applicationContext)
    }

    override fun open(uri: Uri, current: Draft?): OpenResult {
        val imported = during(DocumentProblem.SOURCE_UNREADABLE) { store.import(uri) }
        val draft = imported.draft
        try {
            // The draft already holds this file, so nothing needs inspecting or rendering.
            if (current != null && store.sameBytes(imported, current.source)) {
                draft.source.delete()
                // A provider that names nothing keeps the name the draft has.
                return OpenResult.AlreadyOpen(current.copy(name = imported.displayName ?: current.name))
            }
            val document = during(DocumentProblem.NOT_A_PDF) {
                val pages = engine.inspect(draft.source)
                OpenDocument(draft, pages, renderPage(draft))
            }
            // Not yet the draft: adopt() makes it so, once nothing would be lost.
            waiting += draft.source
            return OpenResult.Opened(document)
        } catch (error: Exception) {
            draft.source.delete()
            throw error
        }
    }

    // A failed write leaves the current draft in place and drops the new copy.
    override fun adopt(document: OpenDocument) {
        try {
            during(DocumentProblem.DRAFT_NOT_SAVED) { store.saveDraft(document.draft, waiting - document.draft.source) }
            waiting -= document.draft.source
        } catch (error: Exception) {
            discard(document)
            throw error
        }
        // The replaced document's pages are no longer needed; its own stay.
        dropPreviews { !it.startsWith(previewPrefix(document.draft)) }
    }

    // Nothing of a dropped PDF stays behind: a stale preview would push the
    // current document's pages out of the cache.
    override fun discard(document: OpenDocument) {
        waiting -= document.draft.source
        document.draft.source.delete()
        dropPreviews { it.startsWith(previewPrefix(document.draft)) }
    }

    private fun dropPreviews(matching: (String) -> Boolean) {
        previews.snapshot().keys.filter(matching).forEach(previews::remove)
    }

    private fun previewPrefix(draft: Draft) = "${draft.source.name}:"

    override fun restore(): OpenDocument? = during(DocumentProblem.DRAFT_UNREADABLE) {
        val saved = store.restore() ?: return@during null
        val pages = engine.inspect(saved.source)
        // A page beyond the document (a damaged draft) opens its last page instead.
        val draft = saved.copy(page = saved.page.coerceIn(pages.indices))
        OpenDocument(draft, pages, previewOrNull(draft))
    }

    override fun render(draft: Draft): Bitmap = during(DocumentProblem.NOT_A_PDF) { renderPage(draft) }

    override fun cachedPreview(draft: Draft): Bitmap? = previews.get(key(draft))

    override fun saveDraft(draft: Draft) = during(DocumentProblem.DRAFT_NOT_SAVED) { store.saveDraft(draft, waiting) }

    override fun close() {
        // Nothing adopts a waiting copy after this, so none may outlive the
        // service, even one the caller did not discard.
        waiting.forEach(File::delete)
        waiting.clear()
        previews.evictAll()
        engine.close()
    }

    /**
     * Builds an annotated copy for the share sheet under cache/shared/, in its own
     * folder so it keeps the document's name. Starting a new share deletes the
     * previous copies; a recipient that has not read its copy by then loses it.
     */
    override fun share(draft: Draft): File = during(DocumentProblem.EXPORT_FAILED) {
        val shared = File(cache, SHARED_DIRECTORY)
        shared.deleteRecursively()
        val folder = File(shared, UUID.randomUUID().toString()).apply { mkdirs() }
        val output = File(folder, draft.exportName)
        try {
            engine.export(draft.source, output, draft.ink)
            output
        } catch (error: Exception) {
            folder.deleteRecursively()
            throw error
        }
    }

    override fun export(draft: Draft, destination: Uri) {
        val output = File.createTempFile("annotated-", ".pdf", cache)
        try {
            // Always derive from the imported PDF, so repeated saves never duplicate ink.
            during(DocumentProblem.EXPORT_FAILED) { engine.export(draft.source, output, draft.ink) }
            during(DocumentProblem.DESTINATION_UNWRITABLE) { store.writePdf(output, destination) }
        } finally {
            output.delete()
        }
    }

    // The ink is restored even when its page fails to render (a huge page on a
    // small heap): the editor shows the page blank, retries and reports there.
    private fun previewOrNull(draft: Draft): Bitmap? = try {
        renderPage(draft)
    } catch (error: Exception) {
        Log.w(TAG, "The restored page did not render; the editor retries", error)
        null
    } catch (error: OutOfMemoryError) {
        Log.w(TAG, "The restored page did not render; the editor retries", error)
        null
    }

    private fun renderPage(draft: Draft): Bitmap =
        cachedPreview(draft) ?: engine.render(draft.source, draft.page).also { previews.put(key(draft), it) }

    private fun key(draft: Draft) = "${previewPrefix(draft)}${draft.page}"

    // Tags failures with the step that was running. Two errors are recoverable
    // here too: OutOfMemoryError from a single oversized page or bitmap, and
    // StackOverflowError from PDFBox's recursive parser on a deeply nested file.
    private inline fun <T> during(stage: DocumentProblem, work: () -> T): T = try {
        work()
    } catch (error: Exception) {
        throw DocumentException(error.toProblem(stage), error)
    } catch (error: OutOfMemoryError) {
        throw DocumentException(DocumentProblem.OUT_OF_MEMORY, error)
    } catch (error: StackOverflowError) {
        throw DocumentException(stage, error)
    }

    companion object {
        /** Matches the cache-path in res/xml/shared_files.xml. */
        const val SHARED_DIRECTORY = "shared"
        private const val PREVIEW_HEAP_SHARE = 6
        private const val TAG = "Asterinked"
    }
}
