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

    @Test fun aStaleEditDoesNotCostTheOlderOnes() {
        val history = InkHistory()
        val first = listOf(a)
        history.record(0, emptyList(), first)
        history.record(1, emptyList(), listOf(b))
        // Page 1's ink was replaced outside the history; page 0 still matches.
        val ink = mapOf(0 to first, 1 to listOf(c))

        assertTrue(history.canUndo(5, ink))
        assertEquals(PageInk(0, emptyList()), history.undo(5, ink))
    }

    @Test fun undoIsOfferedOnlyWhenItWouldDoSomething() {
        val history = InkHistory()
        history.record(1, emptyList(), listOf(b))
        val stale = mapOf(1 to listOf(c))

        assertFalse("The only edit is stale and the visible page is empty", history.canUndo(5, stale))
        assertNull(history.undo(5, stale))
    }

    @Test fun anEqualListCopyIsStillStaleForUndoAndRedo() {
        val history = InkHistory()
        val before = listOf(a, b)
        val after = listOf(a, c)
        history.record(0, before, after)

        val unrelated = after.toMutableList()
        val undone = history.undo(0, mapOf(0 to unrelated))!!.strokes
        assertEquals("A copied list uses tail removal, not the recorded erase", listOf(a), undone)
        assertSame(unrelated, history.redo(mapOf(0 to undone))!!.strokes)
        val again = history.undo(0, mapOf(0 to unrelated))!!.strokes

        assertNull(history.redo(mapOf(0 to again.toMutableList())))
        assertFalse(history.canRedo())
        assertNull("Rejected redo is discarded even for the original list", history.redo(mapOf(0 to again)))
    }

    @Test fun anUnrelatedEmptyListCannotRestoreErasedInk() {
        val history = InkHistory()
        val erased = ArrayList<InkStroke>()
        history.record(0, listOf(a, b), erased)

        val unrelated = mapOf(0 to ArrayList<InkStroke>())
        assertNull(history.undo(0, unrelated))
        assertFalse(history.canUndo(0, unrelated))
        assertFalse(history.canRedo())
    }

    @Test fun aDisconnectedRecordDiscardsOlderUndoOnMismatch() {
        val history = InkHistory()
        val older = listOf(a)
        history.record(0, emptyList(), older)
        val before = listOf(b)
        val after = before + c
        history.record(0, before, after)

        val undone = history.undo(0, mapOf(0 to after))!!.strokes
        assertSame(before, undone)
        val fallback = history.undo(0, mapOf(0 to undone))!!.strokes
        assertTrue("The old stroke cannot be restored across the mismatch", fallback.isEmpty())
        assertFalse(history.canUndo(0, mapOf(0 to fallback)))
        val redone = history.redo(mapOf(0 to fallback))!!.strokes
        assertSame(before, redone)
        assertSame(after, history.redo(mapOf(0 to redone))!!.strokes)
    }

    @Test fun noOpRecordsRemainUndoableAndRedoable() {
        val history = InkHistory()
        val strokes = listOf(a)
        history.record(0, strokes, strokes)

        assertSame(strokes, history.undo(0, mapOf(0 to strokes))!!.strokes)
        assertSame(strokes, history.redo(mapOf(0 to strokes))!!.strokes)
    }

    @Test fun clearDiscardsTheWholeSessionHistory() {
        val history = InkHistory()
        val first = listOf(a)
        val second = listOf(b)
        history.record(0, emptyList(), first)
        history.record(1, emptyList(), second)
        val undone = history.undo(0, mapOf(0 to first, 1 to second))!!
        val ink = mapOf(0 to first, undone.page to undone.strokes)

        history.clear()

        assertFalse(history.canRedo())
        assertFalse(history.canUndo(1, ink))
        assertEquals("Remaining ink keeps the explicit tail-removal fallback", PageInk(0, emptyList()), history.undo(0, ink))
    }

    private fun stroke(x: Float) = InkStroke(listOf(InkPoint(x, x, 1f)), 0, 2f)
}
