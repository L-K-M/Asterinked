package ch.lkmc.asterinked.ink

/** A page's strokes after an undo or redo. */
internal data class PageInk(val page: Int, val strokes: List<InkStroke>)

/**
 * Undo and redo for one editing session, in the order the edits happened
 * across the whole document: undo reaches the last edit even after the page
 * was turned. Each edit keeps its page's stroke list before and after; lists
 * are immutable and share their strokes, so a snapshot costs one reference.
 * Edits apply only to the exact list they produced, which keeps a stale
 * history from rewriting newer ink.
 *
 *     record p1, record p3, record p1      undo -> p1, undo -> p3, undo -> p1
 */
internal class InkHistory {
    private class Edit(val page: Int, val before: List<InkStroke>, val after: List<InkStroke>)

    private val undo = ArrayList<Edit>()
    private val redo = ArrayList<Edit>()

    /** Records a change the user made on [page]; it clears the redo history. */
    fun record(page: Int, before: List<InkStroke>, after: List<InkStroke>) {
        undo += Edit(page, before, after)
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
        val edit = undo.removeLastOrNull() ?: newestStroke(page, ink) ?: return null
        redo += edit
        return PageInk(edit.page, edit.before)
    }

    /** Redoes the newest undone edit, or returns null if there is none or the ink has moved on. */
    fun redo(ink: Map<Int, List<InkStroke>>): PageInk? {
        val edit = redo.lastOrNull() ?: return null
        if (edit.before !== ink.strokesOn(edit.page)) {
            redo.clear()
            return null
        }
        redo.removeAt(redo.lastIndex)
        undo += edit
        return PageInk(edit.page, edit.after)
    }

    // Matches undo(): an edit that still applies, or else the visible page's newest stroke.
    fun canUndo(page: Int, ink: Map<Int, List<InkStroke>>): Boolean =
        undo.any { it.applies(ink) } || ink.strokesOn(page).isNotEmpty()

    fun canRedo(): Boolean = redo.isNotEmpty()

    fun clear() {
        undo.clear()
        redo.clear()
    }

    // Edits on top that no longer match their page's ink are stale and go;
    // older edits that still match stay undoable.
    private fun dropStale(ink: Map<Int, List<InkStroke>>) {
        while (undo.isNotEmpty() && !undo.last().applies(ink)) undo.removeAt(undo.lastIndex)
    }

    // The fallback for ink without history: remove the newest stroke.
    private fun newestStroke(page: Int, ink: Map<Int, List<InkStroke>>): Edit? {
        val strokes = ink.strokesOn(page)
        if (strokes.isEmpty()) return null
        return Edit(page, strokes.dropLast(1), strokes)
    }

    private fun Edit.applies(ink: Map<Int, List<InkStroke>>) = after === ink.strokesOn(page)

    private fun Map<Int, List<InkStroke>>.strokesOn(page: Int): List<InkStroke> = this[page].orEmpty()
}
