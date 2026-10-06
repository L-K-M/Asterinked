package ch.lkmc.asterinked.document

import ch.lkmc.asterinked.ink.InkPoint
import ch.lkmc.asterinked.ink.InkStroke
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

/** B8: a broken draft must fail once, not on every launch, and must not take the ink with it. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DocumentServiceRestoreTest {

    private val app get() = RuntimeEnvironment.getApplication()
    private val directory get() = File(app.filesDir, "documents").apply { mkdirs() }

    @Before
    fun clearDrafts() {
        directory.deleteRecursively()
        directory.mkdirs()
    }

    @Test
    fun restoreWithoutDraft_returnsNull() {
        assertNull(DocumentService(app).restore())
    }

    @Test
    fun corruptDraftFailsOnceThenStartsClean() {
        File(directory, "draft.json").writeText("{ not json")

        assertEquals(DocumentProblem.DRAFT_UNREADABLE, restoreProblem())
        assertNull("The broken draft is set aside, not read again", DocumentService(app).restore())
        val broken = directory.listFiles().orEmpty().filter { it.name.startsWith("draft.broken-") }
        assertEquals("One broken draft is kept for forensics", 1, broken.size)
    }

    @Test
    fun missingSourceFailsOnceThenStartsClean() {
        val store = DocumentStore(app)
        val source = File(directory, "gone.pdf").apply { writeBytes(byteArrayOf(1)) }
        store.saveDraft(Draft(source, "Gone.pdf"))
        source.delete()

        assertEquals(DocumentProblem.DRAFT_UNREADABLE, restoreProblem())
        assertNull(DocumentService(app).restore())
    }

    @Test
    fun aDraftWhoseRenderFailsKeepsItsInk() {
        val store = DocumentStore(app)
        val source = File(directory, "real.pdf")
        PDDocument().use { document ->
            document.addPage(PDPage(PDRectangle.LETTER))
            document.save(source)
        }
        val ink = mapOf(0 to listOf(InkStroke(listOf(InkPoint(1f, 1f, 1f), InkPoint(9f, 9f, 1f)), 1, 2f)))
        store.saveDraft(Draft(source, "Real.pdf", ink = ink))

        // Robolectric cannot run PdfRenderer, so the preview render fails here
        // exactly like a device render that runs out of memory.
        val restored = DocumentService(app).restore()
        assertNotNull(restored)
        assertNull("The page renders later, over a blank page", restored!!.preview)
        assertEquals("The ink survives", 1, restored.draft.ink.getValue(0).size)
        assertEquals("Nothing was set aside", 0, directory.listFiles().orEmpty().count { it.name.startsWith("draft.broken-") })
    }

    private fun restoreProblem(): DocumentProblem = try {
        DocumentService(app).restore()
        throw AssertionError("restore should have failed")
    } catch (error: DocumentException) {
        error.problem
    }
}
