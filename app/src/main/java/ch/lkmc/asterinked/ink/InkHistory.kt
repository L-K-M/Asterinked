package ch.lkmc.asterinked.ink

/**
 * Per-page undo and redo for one editing session. Each edit keeps the page's
 * stroke list before and after; lists are immutable and share their strokes,
 * so a snapshot costs one reference. Edits apply only to the exact list they
 * produced, which keeps a stale history from rewriting newer ink.
 */
internal class InkHistory {
    private class Edit(val before: List<InkStroke>, val after: List<InkStroke>)

    private val undo = mutableMapOf<Int, List<Edit>>()
    private val redo = mutableMapOf<Int, List<Edit>>()

    /** Records a change the user made; it clears that page's redo history. */
    fun record(page: Int, before: List<InkStroke>, after: List<InkStroke>) {
        undo[page] = undo[page].orEmpty() + Edit(before, after)
        redo.remove(page)
    }

    /**
     * Returns the strokes to show after undoing on [page], or null if there is
     * nothing to undo. Strokes restored from an earlier session have no recorded
     * edits; undo then removes the newest stroke, as it always has.
     */
    fun undo(page: Int, strokes: List<InkStroke>): List<InkStroke>? {
        val history = undo[page].orEmpty()
        val edit = if (history.lastOrNull()?.after === strokes) {
            undo[page] = history.dropLast(1)
            history.last()
        } else {
            undo.remove(page)
            if (strokes.isEmpty()) return null
            Edit(strokes.dropLast(1), strokes)
        }
        redo[page] = redo[page].orEmpty() + edit
        return edit.before
    }

    /** Returns the strokes to show after redoing on [page], or null if there is nothing to redo. */
    fun redo(page: Int, strokes: List<InkStroke>): List<InkStroke>? {
        val history = redo[page].orEmpty()
        val edit = history.lastOrNull() ?: return null
        if (edit.before !== strokes) {
            redo.remove(page)
            return null
        }
        redo[page] = history.dropLast(1)
        undo[page] = undo[page].orEmpty() + edit
        return edit.after
    }

    fun canUndo(page: Int, strokes: List<InkStroke>): Boolean = strokes.isNotEmpty() || !undo[page].isNullOrEmpty()

    fun canRedo(page: Int): Boolean = !redo[page].isNullOrEmpty()

    fun clear() {
        undo.clear()
        redo.clear()
    }
}
