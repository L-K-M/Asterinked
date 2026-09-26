package ch.lkmc.asterinked.document

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.LruCache
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import java.io.File

internal data class OpenDocument(val draft: Draft, val pages: List<PageSpec>, val preview: Bitmap)

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
        val draft = store.import(uri)
        try {
            val pages = engine.inspect(draft.source)
            previews.evictAll()
            val preview = render(draft)
            store.saveDraft(draft)
            return OpenDocument(draft, pages, preview)
        } catch (error: Exception) {
            draft.source.delete()
            throw error
        }
    }

    override fun restore(): OpenDocument? {
        val draft = store.restore() ?: return null
        val pages = engine.inspect(draft.source)
        require(draft.page in pages.indices) { "The saved page is invalid." }
        return OpenDocument(draft, pages, render(draft))
    }

    override fun render(draft: Draft): Bitmap =
        cachedPreview(draft) ?: engine.render(draft.source, draft.page).also { previews.put(key(draft), it) }

    override fun cachedPreview(draft: Draft): Bitmap? = previews.get(key(draft))

    override fun saveDraft(draft: Draft) = store.saveDraft(draft)

    override fun close() {
        previews.evictAll()
        engine.close()
    }

    override fun export(draft: Draft, destination: Uri) {
        val output = File.createTempFile("annotated-", ".pdf", cache)
        try {
            // Always derive from the imported PDF, so repeated saves never duplicate ink.
            engine.export(draft.source, output, draft.ink)
            store.writePdf(output, destination)
        } finally {
            output.delete()
        }
    }

    private fun key(draft: Draft) = "${draft.source.name}:${draft.page}"

    private companion object {
        const val PREVIEW_HEAP_SHARE = 6
    }
}
