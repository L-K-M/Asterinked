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

    private fun stroke(x: Float) = InkStroke(listOf(InkPoint(x, x, 1f)), 0, 2f)
}
