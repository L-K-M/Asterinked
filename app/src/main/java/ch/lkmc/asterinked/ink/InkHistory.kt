package ch.lkmc.asterinked.ink

import java.lang.ref.WeakReference
import java.util.ArrayDeque
import java.util.IdentityHashMap

/** A page's strokes after an undo or redo. */
internal data class PageInk(val page: Int, val strokes: List<InkStroke>)

/**
 * Undo and redo for one editing session, in the order the edits happened
 * across the whole document: undo reaches the last edit even after the page
 * was turned.
 *
 *     record p1, record p3, record p1      undo -> p1, undo -> p3, undo -> p1
 *
 * An edit keeps only the strokes it added or removed and their positions,
 * never whole page lists, so history grows with the changes, not with the
 * page's size. Edits apply only to the exact list they produced, which keeps
 * a stale history from rewriting newer ink. Those lists are held weakly, as
 * boundaries shared by neighbouring edits on a page: a collected list is
 * rebuilt from the current one, and the next undo or redo still matches it.
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
        val page: Int,
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

    private val undo = ArrayDeque<Edit>()
    private val redo = ArrayDeque<Edit>()
    // The boundary that last described each page's list, so consecutive edits
    // on a page share it even with edits on other pages in between.
    private val latest = HashMap<Int, Boundary>()

    /** Records a change the user made on [page]; it clears the redo history. */
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
        val end = if (before === after) start else Boundary(after)
        undo.addLast(Edit(page, start, end, removed, added))
        latest[page] = end
        redo.clear()
    }

    /**
     * Undoes the newest edit, on whichever page it was made, given the
     * document's current [ink]. Strokes restored from an earlier session have
     * no recorded edits; once those run out, undo removes the newest stroke on
     * the visible [page], as it always has. Returns null if nothing is left.
     */
    fun undo(page: Int, ink: Map<Int, List<InkStroke>>): PageInk? {
        dropStale(ink)
        val edit = undo.pollLast() ?: newestStroke(page, ink) ?: return null
        val strokes = edit.undo(ink.strokesOn(edit.page))
        latest[edit.page] = edit.before
        redo.addLast(edit)
        return PageInk(edit.page, strokes)
    }

    /** Redoes the newest undone edit, or returns null if there is none or the ink has moved on. */
    fun redo(ink: Map<Int, List<InkStroke>>): PageInk? {
        val edit = redo.peekLast() ?: return null
        val current = ink.strokesOn(edit.page)
        if (!edit.before.matches(current)) {
            redo.clear()
            return null
        }
        redo.removeLast()
        val strokes = edit.redo(current)
        latest[edit.page] = edit.after
        undo.addLast(edit)
        return PageInk(edit.page, strokes)
    }

    // Matches undo(): an edit that still applies, or else the visible page's newest stroke.
    fun canUndo(page: Int, ink: Map<Int, List<InkStroke>>): Boolean =
        undo.any { it.applies(ink) } || ink.strokesOn(page).isNotEmpty()

    fun canRedo(): Boolean = redo.isNotEmpty()

    fun clear() {
        undo.clear()
        redo.clear()
        latest.clear()
    }

    // Edits on top that no longer match their page's ink are stale and go;
    // older edits that still match stay undoable.
    private fun dropStale(ink: Map<Int, List<InkStroke>>) {
        while (undo.isNotEmpty() && !undo.peekLast().applies(ink)) undo.removeLast()
    }

    // The fallback for ink without history: remove the newest stroke.
    private fun newestStroke(page: Int, ink: Map<Int, List<InkStroke>>): Edit? {
        val strokes = ink.strokesOn(page)
        if (strokes.isEmpty()) return null
        return Edit(page, Boundary(strokes.dropLast(1)), boundary(page, strokes), emptyList(),
            listOf(PositionedStroke(strokes.lastIndex, strokes.last())))
    }

    // Reuses the page's latest boundary when it describes [strokes], so an
    // earlier edit still matches a list rebuilt for a later one.
    private fun boundary(page: Int, strokes: List<InkStroke>): Boundary =
        latest[page]?.takeIf { it.matches(strokes) } ?: Boundary(strokes)

    private fun Edit.applies(ink: Map<Int, List<InkStroke>>) = after.matches(ink.strokesOn(page))

    private fun Map<Int, List<InkStroke>>.strokesOn(page: Int): List<InkStroke> = this[page].orEmpty()
}
