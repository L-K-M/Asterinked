package ch.lkmc.asterinked.document

import android.net.Uri
import java.io.ByteArrayOutputStream
import org.junit.Assert.assertSame
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDDocument
import android.util.LruCache
import android.graphics.Bitmap
import android.provider.OpenableColumns
import ch.lkmc.asterinked.ink.InkPoint
import ch.lkmc.asterinked.ink.InkStroke
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.fakes.RoboCursor
import java.io.ByteArrayInputStream
import java.io.File

/**
 * Opening a file again, say by tapping it once more in Files, must not replace
 * the draft that already holds it. PdfRenderer does not run here, so a match is
 * followed end to end and a mismatch up to the inspection that rejects it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ReopenSamePdfTest {
    private val app = RuntimeEnvironment.getApplication()
    private val directory = File(app.filesDir, "documents")
    private val uri = Uri.parse("content://downloads/report.pdf")

    @Before fun startEmpty() {
        directory.deleteRecursively()
    }

    @Test fun theSameBytesKeepTheDraftAndDropTheNewCopy() {
        val store = DocumentStore(app)
        val current = savedDraft(store)
        serve(CURRENT)

        val result = DocumentService(app).open(uri, current)

        // Robolectric's resolver knows no display name for the URI; a provider
        // that names nothing must not replace the name the draft already has.
        assertEquals(OpenResult.AlreadyOpen(current), result)
        assertEquals("Only the current PDF is left", listOf(current.source.name), pdfNames())
        assertEquals("The stored draft is untouched", current, store.restore())
    }

    // Some providers name a file with blanks; the draft keeps the name it has.
    @Test fun aBlankDisplayNameKeepsTheDraftsName() {
        val store = DocumentStore(app)
        val current = savedDraft(store)
        serve(CURRENT)
        shadowOf(app.contentResolver).setCursor(uri, RoboCursor().apply {
            setColumnNames(listOf(OpenableColumns.DISPLAY_NAME))
            setResults(arrayOf(arrayOf<Any>("  ")))
        })

        assertEquals(OpenResult.AlreadyOpen(current), DocumentService(app).open(uri, current))
    }

    // Opening another PDF keeps the current pages cached until it is adopted:
    // a "keep editing" answer must not cost re-rendering what was on screen.
    @Test fun openingAnotherPdfKeepsTheCurrentPreviewsCached() {
        val store = DocumentStore(app)
        val current = savedDraft(store)
        val service = DocumentService(app)
        val previews = cache(service)
        val preview = Bitmap.createBitmap(4, 6, Bitmap.Config.ARGB_8888)
        previews.put("${current.source.name}:${current.page}", preview)
        val other = ByteArrayOutputStream().also { bytes ->
            PDDocument().use { document -> document.addPage(PDPage()); document.save(bytes) }
        }.toByteArray()
        shadowOf(app.contentResolver).registerInputStream(uri, ByteArrayInputStream(other))

        // PdfRenderer does not run here, so the import stops at its preview.
        runCatching { service.open(uri, current) }

        assertSame(preview, service.cachedPreview(current))
    }

    // A PDF waiting for the replace question is not part of the draft, but a
    // draft write meanwhile (a save that lands) must not delete it.
    @Test fun aDraftWriteKeepsAPdfWaitingForTheQuestion() {
        val store = DocumentStore(app)
        val current = savedDraft(store)
        val service = DocumentService(app)
        val waiting = file("other.pdf", OTHER)
        waiting(service) += waiting

        service.saveDraft(current)

        assertTrue("The waiting PDF survives the write", waiting.exists())
    }

    @Test fun differentBytesAreOpenedAsAnotherPdf() {
        val store = DocumentStore(app)
        val current = savedDraft(store)
        serve(OTHER)

        // Rejected by the inspection only a new PDF goes through.
        val error = assertThrows(DocumentException::class.java) { DocumentService(app).open(uri, current) }

        assertEquals(DocumentProblem.NOT_A_PDF, error.problem)
        assertEquals("The rejected copy is gone", listOf(current.source.name), pdfNames())
        assertEquals("The stored draft is untouched", current, store.restore())
    }

    // Another PDF becomes the draft only when adopted; its old PDF goes with it.
    @Test fun adoptingAnOpenedPdfMakesItTheDraft() {
        val store = DocumentStore(app)
        savedDraft(store)
        val opened = OpenDocument(Draft(file("other.pdf", OTHER), "Other.pdf"), listOf(PageSpec(0f, 0f, 400f, 600f, 0)), null)

        DocumentService(app).adopt(opened)

        assertEquals(opened.draft, store.restore())
        assertEquals("The replaced PDF is pruned", listOf("other.pdf"), pdfNames())
    }

    // A failed write leaves the current draft in place and no stray copy behind.
    @Test fun aFailedAdoptionDropsTheNewCopyAndKeepsTheDraft() {
        val store = DocumentStore(app)
        val current = savedDraft(store)
        val opened = OpenDocument(Draft(file("other.pdf", OTHER), "Other.pdf"), listOf(PageSpec(0f, 0f, 400f, 600f, 0)), null)
        // The staging file for the next draft cannot be written.
        File(directory, "draft.json.new").mkdir()

        val error = assertThrows(DocumentException::class.java) { DocumentService(app).adopt(opened) }

        assertEquals(DocumentProblem.DRAFT_NOT_SAVED, error.problem)
        assertFalse("The new copy is gone", opened.draft.source.exists())
        assertEquals(current, store.restore())
    }

    // A dropped PDF leaves nothing behind, not even its preview, which would
    // otherwise push the current document's pages out of the cache.
    @Test fun discardingAnOpenedPdfDropsItsPreview() {
        val service = DocumentService(app)
        val opened = OpenDocument(Draft(file("other.pdf", OTHER), "Other.pdf"), listOf(PageSpec(0f, 0f, 400f, 600f, 0)), null)
        cache(service).put("${opened.draft.source.name}:0", Bitmap.createBitmap(4, 6, Bitmap.Config.ARGB_8888))

        service.discard(opened)

        assertNull(service.cachedPreview(opened.draft))
    }

    @Test fun aFailedAdoptionDropsItsPreview() {
        val service = DocumentService(app)
        savedDraft(DocumentStore(app))
        val opened = OpenDocument(Draft(file("other.pdf", OTHER), "Other.pdf"), listOf(PageSpec(0f, 0f, 400f, 600f, 0)), null)
        cache(service).put("${opened.draft.source.name}:0", Bitmap.createBitmap(4, 6, Bitmap.Config.ARGB_8888))
        File(directory, "draft.json.new").mkdir()

        runCatching { service.adopt(opened) }

        assertNull(service.cachedPreview(opened.draft))
    }

    @Test fun discardingAnOpenedPdfDropsOnlyItsCopy() {
        val store = DocumentStore(app)
        val current = savedDraft(store)
        val opened = OpenDocument(Draft(file("other.pdf", OTHER), "Other.pdf"), listOf(PageSpec(0f, 0f, 400f, 600f, 0)), null)

        DocumentService(app).discard(opened)

        assertEquals(listOf(current.source.name), pdfNames())
        assertEquals("The stored draft is untouched", current, store.restore())
    }

    @Test fun onlyIdenticalBytesMatch() {
        val store = DocumentStore(app)
        serve(CURRENT)
        val imported = store.import(uri)
        val changed = CURRENT.copyOf().also { it[it.lastIndex]++ }

        assertTrue(store.sameBytes(imported, file("same.pdf", CURRENT)))
        assertFalse("Same size, other bytes", store.sameBytes(imported, file("changed.pdf", changed)))
        assertFalse(store.sameBytes(imported, file("longer.pdf", CURRENT + 0)))
        assertFalse("A missing source matches nothing", store.sameBytes(imported, File(directory, "gone.pdf")))
    }

    // The service's preview cache, which only a device can fill by rendering.
    @Suppress("UNCHECKED_CAST")
    private fun cache(service: DocumentService) =
        DocumentService::class.java.getDeclaredField("previews").apply { isAccessible = true }.get(service) as LruCache<String, Bitmap>

    // The service's imported PDFs awaiting adoption, which only a device can
    // fill: opening another PDF renders it.
    @Suppress("UNCHECKED_CAST")
    private fun waiting(service: DocumentService) =
        DocumentService::class.java.getDeclaredField("waiting").apply { isAccessible = true }.get(service) as MutableSet<File>

    private fun savedDraft(store: DocumentStore): Draft {
        val stroke = InkStroke(listOf(InkPoint(10f, 20f, 0.5f), InkPoint(30f, 40f, 0.75f)), 1, 2f)
        val draft = Draft(file("current.pdf", CURRENT), "Report.pdf", page = 1, ink = mapOf(1 to listOf(stroke)))
        store.saveDraft(draft, emptySet())
        return draft
    }

    private fun file(name: String, bytes: ByteArray) = File(directory, name).apply { writeBytes(bytes) }

    private fun serve(bytes: ByteArray) = shadowOf(app.contentResolver).registerInputStream(uri, ByteArrayInputStream(bytes))

    private fun pdfNames() = directory.listFiles()!!.filter { it.extension == "pdf" }.map { it.name }

    private companion object {
        // Neither needs to be a valid PDF, as a match is never inspected. Both are
        // the same size, so only their digests tell them apart.
        val CURRENT = "%PDF-1.7 the current document".toByteArray()
        val OTHER = "%PDF-1.7 a different document".toByteArray()
    }
}
