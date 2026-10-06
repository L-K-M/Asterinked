package ch.lkmc.asterinked.ink

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class InkHistoryTest {
    private val a = stroke(1f)
    private val b = stroke(2f)
    private val c = stroke(3f)

    @Test fun addedStrokesUndoAndRedoInOrder() {
        val history = InkHistory()
        val one = listOf(a)
        val two = one + b
        history.record(0, emptyList(), one)
        history.record(0, one, two)

        val undone = history.undo(0, two)!!
        assertSame(one, undone)
        assertSame(two, history.redo(0, undone))
    }

    @Test fun undoingAnEraseRestoresStrokesWhereTheyWere() {
        val history = InkHistory()
        val before = listOf(a, b, c)
        val after = listOf(a, c)
        history.record(0, before, after)

        assertEquals(listOf(a, b, c), history.undo(0, after))
    }

    @Test fun erasingEverythingStillLeavesSomethingToUndo() {
        val history = InkHistory()
        history.record(0, listOf(a), emptyList())
        assertTrue(history.canUndo(0, emptyList()))
        assertEquals(listOf(a), history.undo(0, emptyList()))
        assertFalse(history.canUndo(0, emptyList()))
    }

    @Test fun strokesFromAnEarlierSessionUndoNewestFirst() {
        val history = InkHistory()
        val restored = listOf(a, b)

        val undone = history.undo(0, restored)!!
        assertEquals(listOf(a), undone)
        assertEquals(listOf(a, b), history.redo(0, undone))
    }

    @Test fun aNewEditClearsThatPagesRedoOnly() {
        val history = InkHistory()
        history.record(0, emptyList(), listOf(a))
        history.record(1, emptyList(), listOf(b))
        history.undo(0, listOf(a))
        history.undo(1, listOf(b))

        history.record(0, emptyList(), listOf(c))

        assertFalse(history.canRedo(0))
        assertTrue(history.canRedo(1))
    }

    @Test fun historyNeverRewritesStrokesItDidNotProduce() {
        val history = InkHistory()
        history.record(0, emptyList(), listOf(a))
        val unrelated = listOf(b, c)

        assertEquals("Falls back to removing the newest stroke", listOf(b), history.undo(0, unrelated))
        assertNull("Redo does not apply to a different list", history.redo(0, listOf(c)))
    }

    @Test fun anEqualListCopyIsStillStaleForUndoAndRedo() {
        val history = InkHistory()
        val before = listOf(a, b)
        val after = listOf(a, c)
        history.record(0, before, after)

        val unrelated = after.toMutableList()
        val undone = history.undo(0, unrelated)!!
        assertEquals("A copied list uses tail removal, not the recorded erase", listOf(a), undone)
        assertSame(unrelated, history.redo(0, undone))
        val again = history.undo(0, unrelated)!!

        assertNull(history.redo(0, again.toMutableList()))
        assertFalse(history.canRedo(0))
        assertNull("Rejected redo is discarded even for the original list", history.redo(0, again))
    }

    @Test fun anUnrelatedEmptyListCannotRestoreErasedInk() {
        val history = InkHistory()
        val erased = ArrayList<InkStroke>()
        history.record(0, listOf(a, b), erased)

        val unrelated = ArrayList<InkStroke>()
        assertNull(history.undo(0, unrelated))
        assertFalse(history.canUndo(0, unrelated))
        assertFalse(history.canRedo(0))
    }

    @Test fun aDisconnectedRecordDiscardsOlderUndoOnMismatch() {
        val history = InkHistory()
        val older = listOf(a)
        history.record(0, emptyList(), older)
        val before = listOf(b)
        val after = before + c
        history.record(0, before, after)

        val undone = history.undo(0, after)!!
        assertSame(before, undone)
        val fallback = history.undo(0, undone)!!
        assertTrue("The old stroke cannot be restored across the mismatch", fallback.isEmpty())
        assertFalse(history.canUndo(0, fallback))
        val redone = history.redo(0, fallback)!!
        assertSame(before, redone)
        assertSame(after, history.redo(0, redone))
    }

    @Test fun noOpRecordsRemainUndoableAndRedoable() {
        val history = InkHistory()
        val strokes = listOf(a)
        history.record(0, strokes, strokes)

        assertSame(strokes, history.undo(0, strokes))
        assertSame(strokes, history.redo(0, strokes))
    }

    @Test fun clearDiscardsEveryPagesSessionHistory() {
        val history = InkHistory()
        val first = listOf(a)
        val second = listOf(b)
        history.record(0, emptyList(), first)
        history.record(1, emptyList(), second)
        val undone = history.undo(0, first)!!

        history.clear()

        assertFalse(history.canRedo(0))
        assertFalse(history.canUndo(0, undone))
        assertEquals("Remaining ink keeps the explicit tail-removal fallback", emptyList<InkStroke>(), history.undo(1, second))
    }

    private fun stroke(x: Float) = InkStroke(listOf(InkPoint(x, x, 1f)), 0, 2f)
}
