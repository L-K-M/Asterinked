package ch.lkmc.asterinked.document

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.LruCache
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import org.json.JSONException
import java.io.File
import java.io.IOException
import java.util.UUID

internal data class OpenDocument(val draft: Draft, val pages: List<PageSpec>, val preview: Bitmap?)

/**
 * What the editor needs from storage and rendering. Every call except
 * [cachedPreview] runs on one worker thread, so PDF handles and disk writes
 * never race one another.
 */
internal interface DocumentOperations {
    fun open(uri: Uri): OpenDocument
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

    init {
        PDFBoxResourceLoader.init(context.applicationContext)
    }

    override fun open(uri: Uri): OpenDocument {
        val draft = during(DocumentProblem.SOURCE_UNREADABLE) { store.import(uri) }
        try {
            val document = during(DocumentProblem.NOT_A_PDF) {
                val pages = engine.inspect(draft.source)
                previews.evictAll()
                OpenDocument(draft, pages, renderPage(draft))
            }
            during(DocumentProblem.DRAFT_NOT_SAVED) { store.saveDraft(draft) }
            return document
        } catch (error: Exception) {
            draft.source.delete()
            throw error
        }
    }

    /**
     * Invalid draft metadata is set aside once. Read and PDF inspection
     * failures retain the draft; a failed preview is retried in the editor.
     */
    override fun restore(): OpenDocument? {
        val draft = try {
            store.restore()
        } catch (error: IOException) {
            // A failed read is usually environmental (storage briefly not
            // ready); keep the draft and retry next launch.
            throw DocumentException(error.toProblem(DocumentProblem.DRAFT_UNREADABLE), error)
        } catch (error: OutOfMemoryError) {
            throw DocumentException(DocumentProblem.OUT_OF_MEMORY, error)
        } catch (error: JSONException) {
            throw brokenDraft(error)
        } catch (error: IllegalArgumentException) {
            // Invalid page keys or a missing source cannot restore.
            throw brokenDraft(error)
        } ?: return null

        // Inspection can fail because the PDF is temporarily unreadable.
        // Keep its ink rather than classifying every parser failure as corruption.
        val pages = during(DocumentProblem.DRAFT_UNREADABLE) { engine.inspect(draft.source) }
        if (draft.page !in pages.indices) {
            throw brokenDraft(IllegalStateException("Saved page ${draft.page} is outside 0..${pages.lastIndex}"))
        }
        // Any render failure (OOM on a huge page, a renderer that will not
        // open) keeps the draft: the editor retries the render live and
        // reports it there if it fails again.
        val preview = runCatching { renderPage(draft) }.getOrNull()
        return OpenDocument(draft, pages, preview)
    }

    private fun brokenDraft(cause: Exception): DocumentException {
        store.quarantineBrokenDraft()
        return DocumentException(DocumentProblem.DRAFT_UNREADABLE, cause)
    }

    override fun render(draft: Draft): Bitmap = during(DocumentProblem.NOT_A_PDF) { renderPage(draft) }

    override fun cachedPreview(draft: Draft): Bitmap? = previews.get(key(draft))

    override fun saveDraft(draft: Draft) = during(DocumentProblem.DRAFT_NOT_SAVED) { store.saveDraft(draft) }

    override fun close() {
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

    private fun renderPage(draft: Draft): Bitmap =
        cachedPreview(draft) ?: engine.render(draft.source, draft.page).also { previews.put(key(draft), it) }

    private fun key(draft: Draft) = "${draft.source.name}:${draft.page}"

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
    }
}
