package ch.lkmc.asterinked.document

import ch.lkmc.asterinked.ink.InkPoint
import ch.lkmc.asterinked.ink.InkStroke
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

/** A draft that can never be restored must fail once, not on every launch. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DraftRecoveryTest {
    private val app get() = RuntimeEnvironment.getApplication()
    private val directory get() = File(app.filesDir, "documents")
    private val draftFile get() = File(directory, "draft.json")
    private val brokenFile get() = File(directory, "draft.broken.json")

    @Before
    fun startClean() {
        directory.deleteRecursively()
        directory.mkdirs()
    }

    @Test fun aCorruptDraftIsReportedOnceAndKeptAside() {
        draftFile.writeText("{\"source\": \"gone")

        assertUnreadable { DocumentStore(app).restore() }

        assertNull("The next launch starts clean", DocumentStore(app).restore())
        assertEquals("The broken draft is kept for recovery", "{\"source\": \"gone", brokenFile.readText())
    }

    @Test fun aDraftWhosePdfIsMissingIsReportedOnce() {
        val source = File(directory, "lost.pdf").apply { writeBytes(byteArrayOf(1)) }
        DocumentStore(app).saveDraft(Draft(source, "Lost.pdf", ink = mapOf(0 to listOf(stroke()))))
        source.delete()

        assertUnreadable { DocumentStore(app).restore() }

        assertNull(DocumentStore(app).restore())
        assertTrue(brokenFile.readText().contains("Lost.pdf"))
    }

    @Test fun onlyTheLatestBrokenDraftIsKept() {
        draftFile.writeText("first")
        assertUnreadable { DocumentStore(app).restore() }
        draftFile.writeText("second")
        assertUnreadable { DocumentStore(app).restore() }

        assertEquals("second", brokenFile.readText())
        assertEquals(listOf("draft.broken.json"), directory.list()!!.filter { it.endsWith(".json") })
    }

    private fun assertUnreadable(restore: () -> Unit) {
        try {
            restore()
            fail("A broken draft must be reported")
        } catch (error: DocumentException) {
            assertEquals(DocumentProblem.DRAFT_UNREADABLE, error.problem)
        }
    }

    private fun stroke() = InkStroke(listOf(InkPoint(10f, 10f, 0.5f), InkPoint(20f, 20f, 0.5f)), 0, 2f)
}
