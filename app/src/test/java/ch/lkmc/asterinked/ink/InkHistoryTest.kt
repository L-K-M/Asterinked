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

        val undone = history.undo(0, mapOf(0 to two))!!
        assertEquals(0, undone.page)
        assertSame(one, undone.strokes)
        assertSame(two, history.redo(mapOf(0 to undone.strokes))!!.strokes)
    }

    @Test fun undoReachesTheLastEditOnAnotherPage() {
        val history = InkHistory()
        val first = listOf(a)
        val third = listOf(b)
        history.record(0, emptyList(), first)
        history.record(2, emptyList(), third)
        var ink = mapOf(0 to first, 2 to third)

        // The user turned to page 5, which has no ink: undo still works.
        assertTrue(history.canUndo(5, ink))
        val newest = history.undo(5, ink)!!
        assertEquals(PageInk(2, emptyList()), newest)
        ink = ink + (newest.page to newest.strokes)

        assertEquals(PageInk(0, emptyList()), history.undo(5, ink))
    }

    @Test fun redoReplaysEditsAcrossPagesInOrder() {
        val history = InkHistory()
        val first = listOf(a)
        val second = listOf(b)
        history.record(0, emptyList(), first)
        history.record(1, emptyList(), second)
        var ink = mapOf(0 to first, 1 to second)
        repeat(2) {
            val undone = history.undo(1, ink)!!
            ink = ink + (undone.page to undone.strokes)
        }

        val redone = history.redo(ink)!!
        assertEquals(0, redone.page)
        assertSame(first, redone.strokes)
        ink = ink + (redone.page to redone.strokes)
        assertSame(second, history.redo(ink)!!.strokes)
        assertFalse(history.canRedo())
    }

    @Test fun undoingAnEraseRestoresStrokesWhereTheyWere() {
        val history = InkHistory()
        val before = listOf(a, b, c)
        val after = listOf(a, c)
        history.record(0, before, after)

        assertEquals(listOf(a, b, c), history.undo(0, mapOf(0 to after))!!.strokes)
    }

    @Test fun erasingEverythingStillLeavesSomethingToUndo() {
        val history = InkHistory()
        val erased = listOf(a).filterNot { it === a }
        history.record(0, listOf(a), erased)
        assertTrue(history.canUndo(0, mapOf(0 to erased)))
        assertEquals(listOf(a), history.undo(0, mapOf(0 to erased))!!.strokes)
        assertFalse(history.canUndo(0, emptyMap()))
    }

    @Test fun strokesFromAnEarlierSessionUndoNewestFirstOnTheVisiblePage() {
        val history = InkHistory()
        val restored = mapOf(0 to listOf(c), 1 to listOf(a, b))

        val undone = history.undo(1, restored)!!
        assertEquals(PageInk(1, listOf(a)), undone)
        assertEquals(PageInk(1, listOf(a, b)), history.redo(restored + (1 to undone.strokes)))
    }

    @Test fun aNewEditClearsRedo() {
        val history = InkHistory()
        val first = listOf(a)
        val second = listOf(b)
        history.record(0, emptyList(), first)
        history.record(1, emptyList(), second)
        history.undo(1, mapOf(0 to first, 1 to second))
        assertTrue(history.canRedo())

        history.record(0, first, first + c)

        assertFalse(history.canRedo())
    }

    @Test fun historyNeverRewritesStrokesItDidNotProduce() {
        val history = InkHistory()
        history.record(0, emptyList(), listOf(a))
        val unrelated = mapOf(0 to listOf(b, c))

        assertEquals("Falls back to removing the newest stroke", PageInk(0, listOf(b)), history.undo(0, unrelated))
        assertNull("Redo does not apply to a different list", history.redo(mapOf(0 to listOf(c))))
        assertFalse(history.canRedo())
    }

    private fun stroke(x: Float) = InkStroke(listOf(InkPoint(x, x, 1f)), 0, 2f)
}
