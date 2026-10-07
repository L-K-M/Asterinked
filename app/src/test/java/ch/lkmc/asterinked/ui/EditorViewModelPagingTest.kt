package ch.lkmc.asterinked.ui

import android.os.Looper
import android.net.Uri
import androidx.lifecycle.ViewModelStore
import ch.lkmc.asterinked.ink.InkPoint
import ch.lkmc.asterinked.ink.InkStroke
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class EditorViewModelPagingTest {
    private val worker = QueueExecutor()
    private val documents = FakeDocuments(pageCount = 8)
    private val model = EditorViewModel(RuntimeEnvironment.getApplication(), documents, worker)
    private val state get() = model.state.value!!

    @Test fun neighboursArePrefetchedSoTheNextTurnIsImmediate() {
        settle()
        assertEquals(listOf(0, 1), documents.rendered)

        model.goToPage(1)

        assertEquals(1, state.draft!!.page)
        assertSame(documents.cachedPreview(state.draft!!), state.preview)
        assertFalse(state.busy)
    }

    @Test fun anUnrenderedPageShowsAtOnceAndKeepsTheEditorUsable() {
        settle()

        model.goToPage(5)

        assertEquals(5, state.draft!!.page)
        assertNull("The page shows blank while it renders", state.preview)
        assertFalse("Nothing is disabled while a page renders", state.busy)
        model.addStroke(stroke())
        assertEquals("Writing works before the render lands", 1, state.draft!!.ink[5]!!.size)

        settle()
        assertSame(documents.cachedPreview(state.draft!!), state.preview)
    }

    @Test fun pagesFlippedPastAreNeitherRenderedNorSaved() {
        settle()

        model.goToPage(5)
        model.goToPage(7)
        settle()

        assertFalse("Stale page render was skipped", 5 in documents.rendered)
        assertFalse("Stale neighbour prefetch was skipped", 4 in documents.rendered)
        assertTrue(7 in documents.rendered && 6 in documents.rendered)
        assertSame(documents.cachedPreview(state.draft!!), state.preview)
        assertEquals("Two quick turns cost one draft write", listOf(7), documents.saved.map { it.page })
    }

    @Test fun aRenderThatLandsAfterTheUserMovedOnIsDropped() {
        settle()
        model.goToPage(3)
        worker.runAll() // renders page 3 and prefetches 4; the result is posted, not yet delivered

        model.goToPage(4)
        idleMain()

        assertEquals(4, state.draft!!.page)
        assertSame(documents.cachedPreview(state.draft!!), state.preview)
    }

    @Test fun aBurstOfStrokesCostsOneDraftWriteWithTheNewestInk() {
        settle()
        documents.saved.clear()

        repeat(3) { model.addStroke(stroke()) }
        worker.runAll()

        assertEquals(1, documents.saved.size)
        assertEquals(3, documents.saved.single().ink[0]!!.size)
    }

    @Test fun clearingDrainsQueuedInkAndClosesTheServiceBeforeWorkerTermination() {
        settle()
        documents.saved.clear()
        model.goToPage(5)
        repeat(3) { model.addStroke(stroke()) }
        val clearedState = state

        clearModel()

        assertTrue("Injected executors remain session-owned", worker.isShutdown)
        assertFalse("Close must follow the queued writes", documents.closed)
        assertFalse(worker.isTerminated)
        worker.runAll()
        idleMain()

        assertEquals(3, documents.saved.single().ink.getValue(5).size)
        assertEquals("Cleared queued previews must be skipped", listOf(0, 1), documents.rendered)
        assertTrue(documents.closed)
        assertTrue(worker.isTerminated)
        assertSame("Cleared callbacks cannot update the editor", clearedState, state)
    }

    @Test fun postedRenderAndSaveErrorsAreSuppressedAfterClear() {
        settle()
        documents.failSaves = true
        documents.renderFailures += 5
        model.goToPage(5)
        worker.runAll() // The errors are posted to main, but have not landed yet.
        val clearedState = state

        clearModel()
        worker.runAll()
        idleMain()

        assertSame(clearedState, state)
        assertNull(state.message)
        assertTrue(documents.closed)
    }

    @Test fun aCompletedRestoreCannotPublishAfterClear() {
        worker.runAll() // Restore has completed, but its main-thread post is pending.
        val clearedState = state

        clearModel()
        worker.runAll()
        idleMain()

        assertSame(clearedState, state)
        assertTrue(documents.closed)
        assertEquals("Suppressed restore must not queue prefetch", listOf(0), documents.rendered)
    }

    @Test fun anExportRemembersItsDestination() {
        settle()
        model.addStroke(stroke())
        settle()
        val destination = Uri.parse("content://test/saved.pdf")

        model.export(destination)
        settle()

        assertEquals(destination, state.draft!!.destination)
        assertEquals(state.draft!!.ink, state.draft!!.savedInk)
        assertEquals(listOf(destination), documents.exportedTo)

        // The remembered place takes the next save without asking again.
        model.addStroke(stroke())
        model.export(destination)
        settle()
        assertEquals(listOf(destination, destination), documents.exportedTo)
    }

    @Test fun aDestinationThatStoppedTakingWritesIsForgotten() {
        settle()
        model.addStroke(stroke())
        settle()
        val destination = Uri.parse("content://test/saved.pdf")
        model.export(destination)
        settle()
        assertEquals(destination, state.draft!!.destination)

        // The provider revoked the grant or deleted the file: the next write
        // fails, the destination is dropped, and Save asks for a file again.
        documents.failExports = true
        model.export(destination)
        settle()

        assertNull(state.draft!!.destination)
        assertEquals(listOf(destination), documents.exportedTo)
        assertTrue(state.message != null)
        // The clearing is written back: a stale grant cannot resurrect the dead
        // target on the next restore.
        assertNull(documents.saved.last().destination)
    }
    private fun clearModel() = ViewModelStore().apply { put("editor", model) }.clear()

    private fun stroke() = InkStroke(listOf(InkPoint(10f, 10f, 0.5f), InkPoint(20f, 20f, 0.5f)), 0, 2f)

    private fun idleMain() = shadowOf(Looper.getMainLooper()).idle()

    private fun settle() {
        repeat(10) {
            worker.runAll()
            idleMain()
        }
    }
}
