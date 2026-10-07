package ch.lkmc.asterinked.document

import ch.lkmc.asterinked.ink.InkPoint
import ch.lkmc.asterinked.ink.InkStroke
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

/**
 * B8 through the service: a draft that cannot be decoded fails once, while
 * read and PDF inspection failures keep the draft for the next launch.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DocumentServiceRestoreTest {
    private val app get() = RuntimeEnvironment.getApplication()
    private val directory get() = File(app.filesDir, "documents").apply { mkdirs() }
    private val brokenFile get() = File(directory, "draft.broken.json")

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
        assertTrue(brokenFile.isFile)
    }

    @Test
    fun aFailedPdfReadDoesNotSetTheDraftAside() {
        val source = File(directory, "temporarily-unreadable.pdf")
        PDDocument().use { document ->
            document.addPage(PDPage(PDRectangle.LETTER))
            document.save(source)
        }
        val ink = mapOf(0 to listOf(InkStroke(listOf(InkPoint(1f, 1f, 1f)), 1, 2f)))
        DocumentStore(app).saveDraft(Draft(source, "Unreadable.pdf", ink = ink))
        assertTrue(source.setReadable(false, false))

        try {
            assertFalse(source.canRead())
            assertEquals(DocumentProblem.DRAFT_UNREADABLE, restoreProblem())
            assertTrue("A read failure must retain the draft", File(directory, "draft.json").isFile)
            assertFalse(brokenFile.exists())
        } finally {
            source.setReadable(true, true)
        }

        assertEquals(ink, DocumentStore(app).restore()!!.ink)
    }

    @Test
    fun aFailedDraftReadDoesNotSetTheDraftAside() {
        val draft = File(directory, "draft.json").apply { mkdir() }

        assertEquals(DocumentProblem.DRAFT_UNREADABLE, restoreProblem())
        assertTrue("An I/O failure leaves the draft path in place", draft.isDirectory)
        assertFalse(brokenFile.exists())
    }

    private fun restoreProblem(): DocumentProblem = try {
        DocumentService(app).restore()
        throw AssertionError("restore should have failed")
    } catch (error: DocumentException) {
        error.problem
    }
}
