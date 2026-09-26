package ch.lkmc.asterinked.document

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import java.io.File
import java.util.UUID

internal data class OpenDocument(val draft: Draft, val pages: List<PageSpec>, val preview: Bitmap)

/** Called on one worker: PDF handles and disk writes never race one another. */
internal class DocumentService(context: Context) {
    private val store = DocumentStore(context)
    private val engine = PdfEngine(context.cacheDir)
    private val cache = context.cacheDir

    init {
        PDFBoxResourceLoader.init(context.applicationContext)
    }

    fun open(uri: Uri): OpenDocument {
        val draft = store.import(uri)
        try {
            val pages = engine.inspect(draft.source)
            val preview = engine.render(draft.source, draft.page)
            store.saveDraft(draft)
            return OpenDocument(draft, pages, preview)
        } catch (error: Exception) {
            draft.source.delete()
            throw error
        }
    }

    fun restore(): OpenDocument? {
        val draft = store.restore() ?: return null
        val pages = engine.inspect(draft.source)
        require(draft.page in pages.indices) { "The saved page is invalid." }
        return OpenDocument(draft, pages, engine.render(draft.source, draft.page))
    }

    fun render(draft: Draft): Bitmap = engine.render(draft.source, draft.page)

    fun saveDraft(draft: Draft) = store.saveDraft(draft)

    /**
     * Builds an annotated copy for the share sheet under cache/shared/, in its own
     * folder so it keeps the document's name. Starting a new share deletes the
     * previous copies; a recipient that has not read its copy by then loses it.
     */
    fun share(draft: Draft): File {
        val shared = File(cache, SHARED_DIRECTORY)
        shared.deleteRecursively()
        val folder = File(shared, UUID.randomUUID().toString()).apply { mkdirs() }
        val output = File(folder, draft.exportName)
        try {
            engine.export(draft.source, output, draft.ink)
            return output
        } catch (error: Exception) {
            folder.deleteRecursively()
            throw error
        }
    }

    fun export(draft: Draft, destination: Uri) {
        val output = File.createTempFile("annotated-", ".pdf", cache)
        try {
            // Always derive from the imported PDF, so repeated saves never duplicate ink.
            engine.export(draft.source, output, draft.ink)
            store.writePdf(output, destination)
        } finally {
            output.delete()
        }
    }

    companion object {
        /** Matches the cache-path in res/xml/shared_files.xml. */
        const val SHARED_DIRECTORY = "shared"
    }
}
