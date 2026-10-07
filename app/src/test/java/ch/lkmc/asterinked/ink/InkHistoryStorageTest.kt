package ch.lkmc.asterinked.ink

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.ref.Reference
import java.lang.reflect.Modifier
import java.util.Collections
import java.util.IdentityHashMap

class InkHistoryStorageTest {
    @Test fun retainedStrokeReferencesGrowLinearlyWithSingleStrokeEdits() {
        val history = InkHistory()
        var strokes = emptyList<InkStroke>()
        val retained = mutableMapOf<Int, Int>()

        for (count in 1..CHECKPOINTS.last()) {
            val next = strokes + InkStroke(listOf(InkPoint(count.toFloat(), 0f, 1f)), 0, 2f)
            history.record(0, strokes, next)
            strokes = next
            if (count in CHECKPOINTS) retained[count] = retainedStrokeReferences(history)
        }

        println("Retained stroke references by edit count: $retained")
        assertTrue("History retains full page prefixes: $retained", retained.all { (edits, references) ->
            references <= edits * MAX_REFERENCES_PER_EDIT
        })
    }

    @Test fun longAddEraseUndoRedoRebuildsCollectedSnapshotsInOrder() {
        val history = InkHistory()
        val original = List(LONG_SESSION_STROKES) { stroke(it.toFloat()) }
        var strokes = emptyList<InkStroke>()
        for (stroke in original) {
            val next = strokes + stroke
            history.record(0, strokes, next)
            strokes = next
        }

        val firstErase = strokes.filterIndexed { index, _ -> index % 3 != 1 }
        history.record(0, strokes, firstErase)
        val secondErase = firstErase.filterIndexed { index, _ -> index % 5 != 0 }
        history.record(0, firstErase, secondErase)
        strokes = emptyList()
        history.record(0, secondErase, strokes)

        val references = retainedStrokeReferences(history)
        assertEquals("Each stroke was added and eventually erased once", 2 * original.size, references)
        forgetOldSnapshots(history, strokes)

        strokes = history.undo(0, mapOf(0 to strokes))!!.strokes
        assertStrokeOrder(secondErase, strokes)
        strokes = history.undo(0, mapOf(0 to strokes))!!.strokes
        assertStrokeOrder(firstErase, strokes)
        strokes = history.undo(0, mapOf(0 to strokes))!!.strokes
        assertStrokeOrder(original, strokes)

        for (remaining in original.lastIndex downTo 0) {
            strokes = history.undo(0, mapOf(0 to strokes))!!.strokes
            assertEquals(remaining, strokes.size)
            if (strokes.isNotEmpty()) assertSame(original[remaining - 1], strokes.last())
        }
        assertFalse(history.canUndo(0, mapOf(0 to strokes)))
        assertTrue(history.canRedo())
        assertEquals("Moving edits to redo does not duplicate deltas", references, retainedStrokeReferences(history))
        forgetOldSnapshots(history, strokes)

        for (count in 1..original.size) {
            strokes = history.redo(mapOf(0 to strokes))!!.strokes
            assertEquals(count, strokes.size)
            assertSame(original[count - 1], strokes.last())
        }
        assertStrokeOrder(original, strokes)
        strokes = history.redo(mapOf(0 to strokes))!!.strokes
        assertStrokeOrder(firstErase, strokes)
        strokes = history.redo(mapOf(0 to strokes))!!.strokes
        assertStrokeOrder(secondErase, strokes)
        strokes = history.redo(mapOf(0 to strokes))!!.strokes
        assertTrue(strokes.isEmpty())
        assertFalse(history.canRedo())
        assertTrue(history.canUndo(0, mapOf(0 to strokes)))
        assertEquals(references, retainedStrokeReferences(history))

        history.clear()
        assertEquals(0, retainedStrokeReferences(history))
        assertFalse(history.canUndo(0, mapOf(0 to strokes)))
    }

    @Test fun restoredInkDoesNotAcquireRetroactiveEditRecords() {
        val history = InkHistory()
        val restored = List(LONG_SESSION_STROKES) { stroke(it.toFloat()) }
        assertTrue(history.canUndo(0, mapOf(0 to restored)))
        assertEquals(0, retainedStrokeReferences(history))

        val next = restored + stroke(-1f)
        history.record(0, restored, next)
        assertEquals("Only the new stroke is recorded", 1, retainedStrokeReferences(history))
        val undone = history.undo(0, mapOf(0 to next))!!.strokes
        assertSame(restored, undone)
        assertEquals(1, retainedStrokeReferences(history))

        val fallback = history.undo(0, mapOf(0 to undone))!!.strokes
        assertStrokeOrder(restored.dropLast(1), fallback)
        assertEquals("Explicit fallback undo records only the removed tail", 2, retainedStrokeReferences(history))
        assertSame(restored, history.redo(mapOf(0 to fallback))!!.strokes)
        assertSame(next, history.redo(mapOf(0 to restored))!!.strokes)
    }

    @Test fun rebuiltEditsKeepEqualDistinctAndRepeatedStrokeInstances() {
        val history = InkHistory()
        val a = stroke(1f)
        val equalA = a.copy()
        val b = stroke(2f)
        val c = stroke(3f)
        val inserted = stroke(4f)
        val before = listOf(a, b, a, equalA, c)
        val after = listOf(b, inserted, a, c, a, equalA)
        history.record(0, before, after)
        forgetOldSnapshots(history, after)

        val undone = history.undo(0, mapOf(0 to after))!!.strokes
        assertStrokeOrder(before, undone)
        forgetOldSnapshots(history, undone)
        assertStrokeOrder(after, history.redo(mapOf(0 to undone))!!.strokes)
    }

    // Edits on another page sit between two edits on page 0. After collection,
    // each undo rebuilds its page's list, and the older edit still matches it.
    @Test fun interleavedPagesRebuildCollectedSnapshotsInOrder() {
        val history = InkHistory()
        val (a, b, c, d) = List(4) { stroke(it.toFloat()) }
        var ink = mapOf(0 to emptyList<InkStroke>(), 1 to emptyList())
        for ((page, stroke) in listOf(0 to a, 1 to c, 0 to b, 1 to d)) {
            val next = ink.getValue(page) + stroke
            history.record(page, ink.getValue(page), next)
            ink = ink + (page to next)
        }

        repeat(4) {
            forgetOldSnapshots(history, ink.values)
            val undone = history.undo(0, ink)!!
            ink = ink + (undone.page to undone.strokes)
        }
        assertTrue(ink.values.all { it.isEmpty() })
        assertFalse(history.canUndo(0, ink))

        repeat(4) {
            forgetOldSnapshots(history, ink.values)
            val redone = history.redo(ink)!!
            ink = ink + (redone.page to redone.strokes)
        }
        assertStrokeOrder(listOf(a, b), ink.getValue(0))
        assertStrokeOrder(listOf(c, d), ink.getValue(1))
    }

    private fun assertStrokeOrder(expected: List<InkStroke>, actual: List<InkStroke>) {
        assertEquals(expected.size, actual.size)
        expected.indices.forEach { assertSame("Stroke $it", expected[it], actual[it]) }
    }

    private fun stroke(x: Float) = InkStroke(listOf(InkPoint(x, 0f, 1f)), 0, 2f)

    // Simulate collection deterministically; forced GC and heap thresholds are flaky.
    // The currently supplied list stays reachable, as it does in the editor.
    private fun forgetOldSnapshots(history: InkHistory, current: List<InkStroke>) = forgetOldSnapshots(history, listOf(current))

    private fun forgetOldSnapshots(history: InkHistory, current: Collection<List<InkStroke>>) {
        visitStrongReferences(history) { value ->
            if (value is Reference<*> && current.none { it === value.get() }) value.clear()
        }
    }

    // Count strong stroke slots, not unique strokes or weak snapshot referents.
    // This catches shared full-list prefixes without depending on stack layout.
    private fun retainedStrokeReferences(history: InkHistory): Int {
        var references = 0
        visitStrongReferences(history) { if (it is InkStroke) references++ }
        return references
    }

    private fun visitStrongReferences(history: InkHistory, visit: (Any) -> Unit) {
        val seen = Collections.newSetFromMap(IdentityHashMap<Any, Boolean>())
        val pending = ArrayDeque<Any>()
        pending.addLast(history)

        fun enqueue(value: Any?) {
            if (value != null) pending.addLast(value)
        }

        while (pending.isNotEmpty()) {
            val value = pending.removeLast()
            visit(value)
            if (value is InkStroke) continue
            if (!seen.add(value)) continue

            when (value) {
                is Reference<*> -> Unit
                is Map<*, *> -> value.forEach { (key, entry) -> enqueue(key); enqueue(entry) }
                is Iterable<*> -> value.forEach(::enqueue)
                is Array<*> -> value.forEach(::enqueue)
                else -> {
                    if (!value.javaClass.name.startsWith(InkHistory::class.java.name)) continue
                    for (field in value.javaClass.declaredFields) {
                        if (Modifier.isStatic(field.modifiers) || field.type.isPrimitive) continue
                        field.isAccessible = true
                        enqueue(field.get(value))
                    }
                }
            }
        }
    }

    private companion object {
        val CHECKPOINTS = listOf(1_000, 2_000, 4_000, 5_000)
        const val MAX_REFERENCES_PER_EDIT = 2
        const val LONG_SESSION_STROKES = 2_000
    }
}
