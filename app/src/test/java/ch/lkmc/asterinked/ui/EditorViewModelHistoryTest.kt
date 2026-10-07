package ch.lkmc.asterinked.ui

import android.graphics.Bitmap
import android.net.Uri
import android.os.Looper
import ch.lkmc.asterinked.document.DocumentOperations
import ch.lkmc.asterinked.document.Draft
import ch.lkmc.asterinked.document.OpenDocument
import ch.lkmc.asterinked.document.OpenResult
import ch.lkmc.asterinked.document.PageSpec
import ch.lkmc.asterinked.ink.InkPoint
import ch.lkmc.asterinked.ink.InkStroke
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File
import java.util.concurrent.AbstractExecutorService
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class EditorViewModelHistoryTest {
    private val a = stroke(1f)
    private val equalA = a.copy()
    private val b = stroke(2f)
    private val otherPage = stroke(3f)
    private val restored = listOf(a, equalA, b)
    private val worker = QueueExecutor()
    private val documents = FakeDocuments(mapOf(0 to restored, 1 to listOf(otherPage)))
    private val model = EditorViewModel(RuntimeEnvironment.getApplication(), documents, worker)
    private val state get() = model.state.value!!
    private val strokes get() = state.draft!!.ink[state.draft!!.page].orEmpty()

    @Test fun longAddEraseUndoRedoKeepsOrderAndSavesOnlyTheNewestDraft() {
        settle()
        val additions = List(LONG_SESSION_STROKES) { stroke(it.toFloat()) }
        additions.forEach(model::addStroke)
        val all = restored + additions
        val erased = listOf(a) + additions.filterIndexed { index, _ -> index % 3 == 0 }
        val remaining = listOf(equalA, b) + additions.filterIndexed { index, _ -> index % 3 != 0 }

        model.eraseStrokes(erased)
        assertStrokeOrder(remaining, strokes)
        assertTrue(state.draft!!.dirty)
        assertTrue(state.canUndo)
        assertFalse(state.canRedo)

        model.undo()
        assertStrokeOrder(all, strokes)
        repeat(additions.size) { model.undo() }
        assertStrokeOrder(restored, strokes)
        assertFalse("Undoing session edits returns to the saved ink", state.draft!!.dirty)
        assertTrue("Restored ink retains explicit tail undo", state.canUndo)
        assertTrue(state.canRedo)

        repeat(additions.size) { model.redo() }
        assertStrokeOrder(all, strokes)
        model.redo()
        assertStrokeOrder(remaining, strokes)
        assertTrue(state.canUndo)
        assertFalse(state.canRedo)
        worker.runAll()

        assertEquals("A burst persists only its final state", 1, documents.writes)
        assertStrokeOrder(remaining, documents.saved!!.ink[0]!!)
        assertStrokeOrder(listOf(otherPage), documents.saved!!.ink[1]!!)
    }

    // Restored ink has no edits: undo removes the visible page's newest stroke.
    // A new edit anywhere clears redo for the whole document.
    @Test fun restoredTailUndoAndANewEditShareOneDocumentHistory() {
        settle()
        model.undo()
        assertStrokeOrder(listOf(a, equalA), strokes)
        assertTrue(state.canRedo)

        model.goToPage(1)
        assertTrue("Redo reaches the edit on page 0", state.canRedo)
        model.undo()
        assertTrue(strokes.isEmpty())
        assertFalse(state.canUndo)

        model.goToPage(0)
        val added = stroke(4f)
        model.addStroke(added)
        assertFalse("A new edit clears redo everywhere", state.canRedo)
        model.goToPage(1)
        model.redo()
        assertTrue("Nothing to redo on page 1 either", strokes.isEmpty())
        assertFalse(state.canRedo)

        model.undo()
        assertEquals("Undo turns back to the newest edit", 0, state.draft!!.page)
        assertStrokeOrder(listOf(a, equalA), strokes)
        model.undo()
        assertStrokeOrder(listOf(a), strokes)
        model.redo()
        model.redo()
        assertStrokeOrder(listOf(a, equalA, added), strokes)
    }

    @Test fun erasingAnEqualCopyCannotEraseTheDrawnInstanceOrCreateAnEdit() {
        settle()
        model.eraseStrokes(listOf(a.copy()))
        assertSame(restored, strokes)
        worker.runAll()
        assertEquals(0, documents.writes)

        model.eraseStrokes(listOf(a))
        assertStrokeOrder(listOf(equalA, b), strokes)
        model.undo()
        assertStrokeOrder(restored, strokes)
        model.redo()
        assertStrokeOrder(listOf(equalA, b), strokes)
    }

    @Test fun openingAnotherDocumentClearsUndoAndRedoOnEveryPage() {
        settle()
        model.addStroke(stroke(4f))
        model.undo()
        assertTrue(state.canRedo)
        model.goToPage(1)
        model.eraseStrokes(listOf(otherPage))
        assertTrue(state.canUndo)

        model.open(Uri.parse("content://test/replacement.pdf"))
        settle()
        // The edits above are unexported, so the replacement waits for a yes.
        model.replaceDraft()
        settle()
        assertEquals(File("replacement.pdf"), state.draft!!.source)
        assertTrue(strokes.isEmpty())
        assertFalse(state.canUndo)
        assertFalse(state.canRedo)
        model.goToPage(1)
        assertFalse(state.canUndo)
        assertFalse(state.canRedo)
        model.undo()
        model.redo()
        assertTrue(strokes.isEmpty())
    }

    private fun assertStrokeOrder(expected: List<InkStroke>, actual: List<InkStroke>) {
        assertEquals(expected.size, actual.size)
        expected.indices.forEach { assertSame("Stroke $it", expected[it], actual[it]) }
    }

    private fun stroke(x: Float) = InkStroke(listOf(InkPoint(x, 0f, 1f)), 0, 2f)

    private fun settle() {
        worker.runAll()
        shadowOf(Looper.getMainLooper()).idle()
        worker.runAll()
        shadowOf(Looper.getMainLooper()).idle()
    }

    private class QueueExecutor : AbstractExecutorService() {
        private val tasks = ArrayDeque<Runnable>()
        private var shutdown = false

        fun runAll() {
            while (tasks.isNotEmpty()) tasks.removeFirst().run()
        }

        override fun execute(command: Runnable) { tasks.addLast(command) }
        override fun shutdown() { shutdown = true }
        override fun shutdownNow(): MutableList<Runnable> = tasks.toMutableList().also { shutdown = true; tasks.clear() }
        override fun isShutdown() = shutdown
        override fun isTerminated() = shutdown && tasks.isEmpty()
        override fun awaitTermination(timeout: Long, unit: TimeUnit) = true
    }

    private class FakeDocuments(ink: Map<Int, List<InkStroke>>) : DocumentOperations {
        private val restored = Draft(File("restored.pdf"), "restored.pdf", ink = ink, savedInk = ink)
        private val pages = List(2) { PageSpec(0f, 0f, 400f, 600f, 0) }
        private val preview = Bitmap.createBitmap(4, 6, Bitmap.Config.ARGB_8888)
        var saved: Draft? = null
            private set
        var writes = 0
            private set

        override fun restore() = OpenDocument(restored, pages, preview)
        override fun open(uri: Uri, current: Draft?): OpenResult =
            OpenResult.Opened(OpenDocument(Draft(File("replacement.pdf"), "replacement.pdf"), pages, preview))
        override fun render(draft: Draft) = preview
        override fun cachedPreview(draft: Draft) = preview

        override fun saveDraft(draft: Draft) {
            saved = draft
            writes++
        }

        override fun export(draft: Draft, destination: Uri) = throw UnsupportedOperationException()
        override fun share(draft: Draft): File = throw UnsupportedOperationException()
        override fun adopt(document: OpenDocument) = Unit
        override fun discard(document: OpenDocument) = Unit
        override fun close() = Unit
    }

    private companion object {
        const val LONG_SESSION_STROKES = 2_000
    }
}
