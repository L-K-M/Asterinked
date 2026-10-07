package ch.lkmc.asterinked.document

import android.net.Uri
import android.provider.OpenableColumns
import ch.lkmc.asterinked.ink.InkPoint
import ch.lkmc.asterinked.ink.InkStroke
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    private fun savedDraft(store: DocumentStore): Draft {
        val stroke = InkStroke(listOf(InkPoint(10f, 20f, 0.5f), InkPoint(30f, 40f, 0.75f)), 1, 2f)
        val draft = Draft(file("current.pdf", CURRENT), "Report.pdf", page = 1, ink = mapOf(1 to listOf(stroke)))
        store.saveDraft(draft)
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
