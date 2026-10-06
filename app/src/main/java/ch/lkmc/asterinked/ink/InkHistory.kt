package ch.lkmc.asterinked.ink

import java.lang.ref.WeakReference
import java.util.ArrayDeque
import java.util.IdentityHashMap

/**
 * Per-page, session-only history retains changed strokes and their positions,
 * never full page prefixes. Weak boundaries preserve exact-list stale checks
 * and return the original lists while callers still hold them.
 */
internal class InkHistory {
    private class Boundary(strokes: List<InkStroke>) {
        private var snapshot = WeakReference(strokes)

        fun matches(strokes: List<InkStroke>): Boolean = snapshot.get() === strokes

        fun resolve(rebuild: () -> List<InkStroke>): List<InkStroke> {
            val existing = snapshot.get()
            if (existing != null) return existing

            return rebuild().also { snapshot = WeakReference(it) }
        }
    }

    private class PositionedStroke(val index: Int, val stroke: InkStroke)

    private class Edit(
        val before: Boundary,
        val after: Boundary,
        private val removed: List<PositionedStroke>,
        private val added: List<PositionedStroke>,
    ) {
        fun undo(strokes: List<InkStroke>): List<InkStroke> = before.resolve { apply(strokes, added, removed) }

        fun redo(strokes: List<InkStroke>): List<InkStroke> = after.resolve { apply(strokes, removed, added) }

        // Merge in order once; repeated indexed insertion would be quadratic for a large erase.
        private fun apply(
            strokes: List<InkStroke>,
            removals: List<PositionedStroke>,
            additions: List<PositionedStroke>,
        ): List<InkStroke> {
            val size = strokes.size - removals.size + additions.size
            val result = ArrayList<InkStroke>(size)
            var source = 0
            var removal = 0
            var addition = 0

            for (destination in 0 until size) {
                while (removal < removals.size && removals[removal].index == source) {
                    source++
                    removal++
                }

                if (addition < additions.size && additions[addition].index == destination) {
                    result.add(additions[addition++].stroke)
                } else {
                    result.add(strokes[source++])
                }
            }
            return result
        }
    }

    private val undo = mutableMapOf<Int, ArrayDeque<Edit>>()
    private val redo = mutableMapOf<Int, ArrayDeque<Edit>>()

    /** Records a change the user made; it clears that page's redo history. */
    fun record(page: Int, before: List<InkStroke>, after: List<InkStroke>) {
        val removed = ArrayList<PositionedStroke>()
        val added = ArrayList<PositionedStroke>()
        var source = 0
        while (source < before.size && source < after.size && before[source] === after[source]) source++

        // Identity queues also distinguish repeated occurrences of the same instance.
        // A shared prefix avoids indexing unchanged strokes.
        val positions = IdentityHashMap<InkStroke, ArrayDeque<Int>>()
        if (source < after.size) {
            for (index in source until before.size) {
                positions.getOrPut(before[index]) { ArrayDeque() }.addLast(index)
            }
        }

        for (destination in source until after.size) {
            val candidates = positions[after[destination]]
            while (candidates?.peekFirst()?.let { it < source } == true) candidates.removeFirst()
            val position = candidates?.pollFirst()
            if (position == null) {
                added.add(PositionedStroke(destination, after[destination]))
                continue
            }

            while (source < position) {
                removed.add(PositionedStroke(source, before[source]))
                source++
            }
            source++
        }

        for (index in source until before.size) removed.add(PositionedStroke(index, before[index]))

        val start = boundary(page, before)
        val end = if (before === after) start else boundary(page, after)
        undo.getOrPut(page) { ArrayDeque() }.addLast(Edit(start, end, removed, added))
        redo.remove(page)
    }

    /**
     * Returns the strokes to show after undoing on [page], or null if there is
     * nothing to undo. Strokes restored from an earlier session have no recorded
     * edits; undo then removes the newest stroke, as it always has.
     */
    fun undo(page: Int, strokes: List<InkStroke>): List<InkStroke>? {
        val history = undo[page]
        val edit = if (history?.peekLast()?.after?.matches(strokes) == true) {
            history.removeLast()
        } else {
            undo.remove(page)
            if (strokes.isEmpty()) return null

            Edit(
                Boundary(strokes.dropLast(1)), boundary(page, strokes), emptyList(),
                listOf(PositionedStroke(strokes.lastIndex, strokes.last())),
            )
        }
        val result = edit.undo(strokes)
        redo.getOrPut(page) { ArrayDeque() }.addLast(edit)
        return result
    }

    /** Returns the strokes to show after redoing on [page], or null if there is nothing to redo. */
    fun redo(page: Int, strokes: List<InkStroke>): List<InkStroke>? {
        val history = redo[page]
        val edit = history?.peekLast() ?: return null
        if (!edit.before.matches(strokes)) {
            redo.remove(page)
            return null
        }
        history.removeLast()
        val result = edit.redo(strokes)
        undo.getOrPut(page) { ArrayDeque() }.addLast(edit)
        return result
    }

    // Adjacent edits share a boundary, so rebuilding a collected list keeps the
    // next undo/redo valid without accepting an equal, unrelated list.
    private fun boundary(page: Int, strokes: List<InkStroke>): Boundary {
        val previous = undo[page]?.peekLast()?.after
        if (previous?.matches(strokes) == true) return previous

        val next = redo[page]?.peekLast()?.before
        if (next?.matches(strokes) == true) return next

        return Boundary(strokes)
    }

    fun canUndo(page: Int, strokes: List<InkStroke>): Boolean = strokes.isNotEmpty() || !undo[page].isNullOrEmpty()

    fun canRedo(page: Int): Boolean = !redo[page].isNullOrEmpty()

    fun clear() {
        undo.clear()
        redo.clear()
    }
}
