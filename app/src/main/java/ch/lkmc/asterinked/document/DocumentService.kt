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
        val draft = during(DocumentProblem.SOURCE_UNREADABLE) { store.import(uri) }
        try {
            val document = during(DocumentProblem.NOT_A_PDF) {
                OpenDocument(draft, engine.inspect(draft.source), engine.render(draft.source, draft.page))
            }
            during(DocumentProblem.DRAFT_NOT_SAVED) { store.saveDraft(draft) }
            return document
        } catch (error: Exception) {
            draft.source.delete()
            throw error
        }
    }

    fun restore(): OpenDocument? = during(DocumentProblem.DRAFT_UNREADABLE) {
        val draft = store.restore() ?: return@during null
        val pages = engine.inspect(draft.source)
        require(draft.page in pages.indices) { "The saved page is invalid." }
        OpenDocument(draft, pages, engine.render(draft.source, draft.page))
    }

    fun render(draft: Draft): Bitmap = during(DocumentProblem.NOT_A_PDF) { engine.render(draft.source, draft.page) }

    fun saveDraft(draft: Draft) = store.saveDraft(draft)

    fun export(draft: Draft, destination: Uri) {
        val output = File.createTempFile("annotated-", ".pdf", cache)
        try {
            // Always derive from the imported PDF, so repeated saves never duplicate ink.
            during(DocumentProblem.EXPORT_FAILED) { engine.export(draft.source, output, draft.ink) }
            during(DocumentProblem.DESTINATION_UNWRITABLE) { store.writePdf(output, destination) }
        } finally {
            output.delete()
        }
    }

    // Tags failures with the step that was running; OutOfMemoryError is caught
    // too because a single oversized page or bitmap is recoverable here.
    private inline fun <T> during(stage: DocumentProblem, work: () -> T): T = try {
        work()
    } catch (error: Exception) {
        throw DocumentException(error.toProblem(stage), error)
    } catch (error: OutOfMemoryError) {
        throw DocumentException(DocumentProblem.OUT_OF_MEMORY, error)
    }
}
