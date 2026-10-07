package ch.lkmc.asterinked.ui

import android.view.MotionEvent
import android.view.View

/**
 * Keeps a hand resting on the bars from pressing their controls while the pen
 * writes. The framework splits a touch on the tool bar or the page pill off to
 * that control, so the page's own palm handling never sees it, and lifting the
 * hand would change the colour, redo or turn the page:
 *
 *     pen writes on the page ... hand lands on Red ... hand lifts   -> no click
 *     hand rests on Next ....... pen lands ........... hand lifts   -> no click
 *     finger taps Red, no pen down or just lifted                   -> click
 *
 * A finger is a palm when it lands while a pen is down or within
 * [PEN_LIFT_GRACE_MS] of the pen lifting, or when a pen lands while it is
 * down. Pen taps on the bars, keyboard shortcuts and TalkBack never count.
 */
internal class PalmGuard {
    private val pens = HashSet<Int>()
    private val fingers = HashSet<Int>()
    private val palms = HashSet<Int>()
    private var penLiftedAt = NEVER

    /** Follows every pointer in the window; call before the event is dispatched. */
    fun track(event: MotionEvent) {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                pens.clear()
                fingers.clear()
                palms.clear()
                land(event, 0)
            }
            MotionEvent.ACTION_POINTER_DOWN -> land(event, event.actionIndex)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> lift(event, event.actionIndex)
            MotionEvent.ACTION_CANCEL -> {
                pens.clear()
                fingers.clear()
            }
        }
    }

    /** Makes a palm's touch on [control] press and click nothing. */
    fun protect(control: View) = control.setOnTouchListener(::filter)

    private fun land(event: MotionEvent, index: Int) {
        val id = event.getPointerId(index)
        if (isPen(event.getToolType(index))) {
            pens += id
            palms += fingers
            return
        }

        fingers += id
        palms -= id
        val penNearby = pens.isNotEmpty() || event.eventTime - penLiftedAt < PEN_LIFT_GRACE_MS
        if (penNearby) palms += id
    }

    // A lifted palm stays marked: its control receives the UP only after this.
    private fun lift(event: MotionEvent, index: Int) {
        val id = event.getPointerId(index)
        fingers -= id
        if (pens.remove(id) && pens.isEmpty()) penLiftedAt = event.eventTime
    }

    // A control that already saw the finger land is cancelled, so it neither
    // clicks nor stays pressed when the palm lifts.
    private fun filter(control: View, event: MotionEvent): Boolean {
        if (event.getPointerId(event.actionIndex) !in palms) return false
        if (event.actionMasked == MotionEvent.ACTION_DOWN) return true

        val cancel = MotionEvent.obtain(event).apply { action = MotionEvent.ACTION_CANCEL }
        try { control.onTouchEvent(cancel) } finally { cancel.recycle() }
        return true
    }

    private fun isPen(tool: Int) = tool == MotionEvent.TOOL_TYPE_STYLUS || tool == MotionEvent.TOOL_TYPE_ERASER

    private companion object {
        // A writing hand often comes down a moment after the pen lifts.
        const val PEN_LIFT_GRACE_MS = 250L
        const val NEVER = Long.MIN_VALUE / 2
    }
}
