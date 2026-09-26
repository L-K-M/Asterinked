package ch.lkmc.asterinked.document

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import java.io.File

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
}
