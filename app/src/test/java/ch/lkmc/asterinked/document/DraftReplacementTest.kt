package ch.lkmc.asterinked.document

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DraftReplacementTest {
    @Test fun committedReplacementRemovesUnreachableSource() {
        val app = RuntimeEnvironment.getApplication()
        val directory = File(app.filesDir, "documents").apply { deleteRecursively(); mkdirs() }
        val first = File(directory, "first.pdf").apply { writeText("first") }
        val store = DocumentStore(app)
        store.saveDraft(Draft(first, "first.pdf"), emptySet())
        val replacement = File(directory, "next.pdf").apply { writeText("next") }
        assertTrue(first.exists())

        store.saveDraft(Draft(replacement, "next.pdf"), emptySet())

        assertTrue(replacement.exists())
        assertEquals(replacement, store.restore()!!.source)
        assertFalse("The old PDF is no longer reachable", first.exists())
    }
}
