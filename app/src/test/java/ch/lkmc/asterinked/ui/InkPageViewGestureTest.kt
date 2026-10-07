package ch.lkmc.asterinked.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.os.Looper
import android.view.InputDevice
import android.view.MotionEvent
import ch.lkmc.asterinked.document.Draft
import ch.lkmc.asterinked.document.PageSpec
import ch.lkmc.asterinked.ink.InkPoint
import ch.lkmc.asterinked.ink.InkStroke
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File
import java.time.Duration

/**
 * Gestures are asserted through where a stylus tap lands in page units, so
 * the tests observe zoom and pan the way a user would.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class InkPageViewGestureTest {
    private val strokes = mutableListOf<InkStroke>()
    private val turns = mutableListOf<Int>()
    private val historyGestures = mutableListOf<String>()
    private var clock = 1_000L

    @Test fun twoFingersPanInTouchMode() {
        val view = pageView(InputMode.PEN)
        pinch(view, 200f to 400f, 100f to 500f)
        view.configure(InputMode.TOUCH, Color.BLACK, 2f) { strokes.add(it) }
        val unitsPer100px = unitsPer100px(view)
        val before = pageAt(view, 300f, 400f)

        drag(view, listOf(260f to 400f, 340f to 400f), dx = 60f)

        val after = pageAt(view, 300f, 400f)
        assertEquals("Two-finger drag pans in touch mode", -0.6f * unitsPer100px, after.x - before.x, 0.5f)
        assertTrue("A pinch never inks in touch mode", strokes.isEmpty())
    }

    @Test fun oneFingerInksWithoutPanningInTouchMode() {
        val view = pageView(InputMode.PEN)
        pinch(view, 200f to 400f, 100f to 500f)
        view.configure(InputMode.TOUCH, Color.BLACK, 2f) { strokes.add(it) }
        val before = pageAt(view, 300f, 400f)

        send(view, MotionEvent.ACTION_DOWN, listOf(finger(0, 280f)))
        for (step in 1..4) send(view, MotionEvent.ACTION_MOVE, listOf(finger(0, 280f + 20f * step)))
        send(view, MotionEvent.ACTION_UP, listOf(finger(0, 360f)))
        assertEquals("One finger inks in touch mode", 1, strokes.size)
        strokes.clear()

        val after = pageAt(view, 300f, 400f)
        assertEquals("Inking must not pan the page", before.x, after.x, 0.01f)
        assertEquals(before.y, after.y, 0.01f)
    }

    @Test fun movingPalmDoesNotPanUnderTheStylus() {
        val view = pageView(InputMode.PEN)
        pinch(view, 200f to 400f, 100f to 500f)
        val before = pageAt(view, 300f, 400f)

        val pen = { x: Float -> Pointer(7, MotionEvent.TOOL_TYPE_STYLUS, x, 300f) }
        send(view, MotionEvent.ACTION_DOWN, listOf(pen(250f)))
        send(view, MotionEvent.ACTION_POINTER_DOWN, listOf(pen(255f), finger(1, 400f)), actionIndex = 1)
        for (step in 1..4) send(view, MotionEvent.ACTION_MOVE, listOf(pen(255f + 10f * step), finger(1, 400f + 30f * step)))
        send(view, MotionEvent.ACTION_POINTER_UP, listOf(pen(295f), finger(1, 520f)), actionIndex = 0)
        send(view, MotionEvent.ACTION_UP, listOf(finger(1, 520f)))
        assertEquals("The stylus stroke survives the palm", 1, strokes.size)
        strokes.clear()

        val after = pageAt(view, 300f, 400f)
        assertEquals("A resting palm must not pan the page", before.x, after.x, 0.01f)
        assertTrue("A moving palm must not turn the page", turns.isEmpty())
    }

    @Test fun palmSlidingAwayAfterWritingNeitherTurnsNorPans() {
        val view = pageView(InputMode.PEN)
        swipe(view, from = 500f, to = 510f) // an earlier finger gesture leaves detector state behind
        turns.clear()
        val before = pageAt(view, 300f, 400f)

        val pen = { x: Float -> Pointer(7, MotionEvent.TOOL_TYPE_STYLUS, x, 300f) }
        send(view, MotionEvent.ACTION_DOWN, listOf(pen(250f)))
        send(view, MotionEvent.ACTION_POINTER_DOWN, listOf(pen(255f), finger(1, 450f)), actionIndex = 1)
        send(view, MotionEvent.ACTION_MOVE, listOf(pen(270f), finger(1, 450f)))
        send(view, MotionEvent.ACTION_POINTER_UP, listOf(pen(280f), finger(1, 450f)), actionIndex = 0)
        // The stylus is up; the palm slides away fast and lifts.
        for (step in 1..4) {
            clock += 8
            send(view, MotionEvent.ACTION_MOVE, listOf(finger(1, 450f - 80f * step)))
        }
        send(view, MotionEvent.ACTION_UP, listOf(finger(1, 130f)))
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(600))

        assertEquals("The stroke is kept", 1, strokes.size)
        strokes.clear()
        assertTrue("A palm leaving the screen must not turn the page", turns.isEmpty())
        assertEquals(before.x, pageAt(view, 300f, 400f).x, 0.01f)
    }

    @Test fun pinchFollowsTheFingers() {
        val view = pageView(InputMode.PEN)
        pinch(view, 200f to 400f, 100f to 500f)
        val underFingers = pageAt(view, 300f, 400f)

        pinch(view, 200f to 400f, 230f to 470f)

        val stillUnderFingers = pageAt(view, 350f, 400f)
        assertEquals(underFingers.x, stillUnderFingers.x, 1f)
        assertEquals(underFingers.y, stillUnderFingers.y, 1f)
    }

    @Test fun liftingTheFirstFingerDoesNotJump() {
        val view = pageView(InputMode.PEN)
        pinch(view, 200f to 400f, 100f to 500f)
        val unitsPer100px = unitsPer100px(view)
        val before = pageAt(view, 300f, 400f)

        send(view, MotionEvent.ACTION_DOWN, listOf(finger(0, 100f)))
        send(view, MotionEvent.ACTION_POINTER_DOWN, listOf(finger(0, 100f), finger(1, 500f)), actionIndex = 1)
        send(view, MotionEvent.ACTION_POINTER_UP, listOf(finger(0, 100f), finger(1, 500f)), actionIndex = 0)
        send(view, MotionEvent.ACTION_MOVE, listOf(finger(1, 505f)))
        send(view, MotionEvent.ACTION_MOVE, listOf(finger(1, 510f)))
        send(view, MotionEvent.ACTION_UP, listOf(finger(1, 510f)))

        val after = pageAt(view, 300f, 400f)
        assertEquals("The page moves with the remaining finger only", -0.1f * unitsPer100px, after.x - before.x, 0.5f)
    }

    @Test fun doubleTapZoomsAroundTheTapAndBack() {
        val view = pageView(InputMode.PEN)
        val fit = unitsPer100px(view)
        val center = pageAt(view, 300f, 400f)

        doubleTap(view, 300f, 400f)
        assertEquals(fit / 2.5f, unitsPer100px(view), 0.3f)
        assertEquals(center.x, pageAt(view, 300f, 400f).x, 1f)

        doubleTap(view, 300f, 400f)
        assertEquals(fit, unitsPer100px(view), 0.3f)
    }

    @Test fun doubleTapDoesNotZoomInTouchMode() {
        val view = pageView(InputMode.TOUCH)
        val fit = unitsPer100px(view)
        doubleTap(view, 300f, 400f)
        assertEquals(fit, unitsPer100px(view), 0.01f)
    }

    @Test fun swipeTurnsPagesAtFitZoom() {
        val view = pageView(InputMode.PEN)
        swipe(view, from = 450f, to = 150f)
        swipe(view, from = 150f, to = 450f)
        assertEquals(listOf(1, -1), turns)
    }

    @Test fun swipeWhileZoomedPansInstead() {
        val view = pageView(InputMode.PEN)
        pinch(view, 200f to 400f, 100f to 500f)
        swipe(view, from = 450f, to = 150f)
        assertTrue(turns.isEmpty())
    }

    @Test fun zoomSurvivesPageTurnButNotANewDocument() {
        val view = pageView(InputMode.PEN)
        val fit = unitsPer100px(view)
        pinch(view, 200f to 400f, 100f to 500f)
        val zoomed = unitsPer100px(view)

        view.show(state(page = 1))
        assertEquals(zoomed, unitsPer100px(view), 0.01f)

        view.show(state(page = 0, source = "other.pdf"))
        assertEquals(fit, unitsPer100px(view), 0.01f)
    }

    @Test fun twoFingerTapUndoesAndThreeFingerTapRedoes() {
        val view = pageView(InputMode.PEN)
        multiTap(view, fingers = 2)
        multiTap(view, fingers = 3)
        assertEquals(listOf("undo", "redo"), historyGestures)
    }

    @Test fun multiFingerTapsCancelFingerInkInTouchMode() {
        val view = pageView(InputMode.TOUCH)
        multiTap(view, fingers = 2)
        multiTap(view, fingers = 3)
        assertEquals(listOf("undo", "redo"), historyGestures)
        assertTrue("The first finger's unfinished stroke was cancelled", strokes.isEmpty())
    }

    @Test fun aTapSurvivesJitterAfterOneFingerLifts() {
        val view = pageView(InputMode.PEN)
        send(view, MotionEvent.ACTION_DOWN, listOf(finger(0, 260f)))
        send(view, MotionEvent.ACTION_POINTER_DOWN, listOf(finger(0, 260f), finger(1, 340f)), actionIndex = 1)
        send(view, MotionEvent.ACTION_POINTER_UP, listOf(finger(0, 260f), finger(1, 340f)), actionIndex = 1)
        send(view, MotionEvent.ACTION_MOVE, listOf(finger(0, 261f)))
        send(view, MotionEvent.ACTION_UP, listOf(finger(0, 261f)))
        assertEquals(listOf("undo"), historyGestures)
    }

    @Test fun movementReportedOnlyOnReleaseIsNotATap() {
        val view = pageView(InputMode.PEN)
        send(view, MotionEvent.ACTION_DOWN, listOf(finger(0, 260f)))
        send(view, MotionEvent.ACTION_POINTER_DOWN, listOf(finger(0, 260f), finger(1, 340f)), actionIndex = 1)
        send(view, MotionEvent.ACTION_POINTER_UP, listOf(finger(0, 260f), finger(1, 420f)), actionIndex = 1)
        send(view, MotionEvent.ACTION_UP, listOf(finger(0, 260f)))
        assertTrue(historyGestures.isEmpty())
    }

    @Test fun oppositeMotionWithAnUnchangedCentroidAndSpanIsNotATap() {
        val view = pageView(InputMode.PEN)
        send(view, MotionEvent.ACTION_DOWN, listOf(finger(0, 260f)))
        send(view, MotionEvent.ACTION_POINTER_DOWN, listOf(finger(0, 260f), finger(1, 340f)), actionIndex = 1)
        send(view, MotionEvent.ACTION_MOVE, listOf(finger(0, 340f), finger(1, 260f)))
        send(view, MotionEvent.ACTION_POINTER_UP, listOf(finger(0, 340f), finger(1, 260f)), actionIndex = 1)
        send(view, MotionEvent.ACTION_UP, listOf(finger(0, 340f)))
        assertTrue(historyGestures.isEmpty())
    }

    @Test fun aCancelledFingerDoesNotTriggerUndo() {
        val view = pageView(InputMode.PEN)
        send(view, MotionEvent.ACTION_DOWN, listOf(finger(0, 260f)))
        send(view, MotionEvent.ACTION_POINTER_DOWN, listOf(finger(0, 260f), finger(1, 340f)), actionIndex = 1)
        send(view, MotionEvent.ACTION_POINTER_UP, listOf(finger(0, 260f), finger(1, 340f)),
            actionIndex = 1, flags = MotionEvent.FLAG_CANCELED)
        send(view, MotionEvent.ACTION_UP, listOf(finger(0, 260f)))
        assertTrue(historyGestures.isEmpty())
    }

    @Test fun aTwoFingerDragIsNotAnUndo() {
        val view = pageView(InputMode.PEN)
        pinch(view, 200f to 400f, 100f to 500f)
        drag(view, listOf(260f to 400f, 340f to 400f), dx = 60f)
        assertTrue("A two-finger drag pans, it does not undo: $historyGestures", historyGestures.isEmpty())
    }

    @Test fun aSlowTwoFingerPressIsNotAnUndo() {
        val view = pageView(InputMode.PEN)
        send(view, MotionEvent.ACTION_DOWN, listOf(finger(0, 260f)))
        send(view, MotionEvent.ACTION_POINTER_DOWN, listOf(finger(0, 260f), finger(1, 340f)), actionIndex = 1)
        clock += 500
        send(view, MotionEvent.ACTION_POINTER_UP, listOf(finger(0, 260f), finger(1, 340f)), actionIndex = 1)
        send(view, MotionEvent.ACTION_UP, listOf(finger(0, 260f)))
        assertTrue(historyGestures.isEmpty())
    }

    @Test fun aStylusAndFingerComboIsNotAnUndo() {
        val view = pageView(InputMode.PEN)
        send(view, MotionEvent.ACTION_DOWN, listOf(Pointer(7, MotionEvent.TOOL_TYPE_STYLUS, 260f, 400f)))
        send(view, MotionEvent.ACTION_POINTER_DOWN, listOf(Pointer(7, MotionEvent.TOOL_TYPE_STYLUS, 260f, 400f), finger(1, 340f)), actionIndex = 1)
        send(view, MotionEvent.ACTION_POINTER_UP, listOf(Pointer(7, MotionEvent.TOOL_TYPE_STYLUS, 260f, 400f), finger(1, 340f)), actionIndex = 1)
        send(view, MotionEvent.ACTION_UP, listOf(Pointer(7, MotionEvent.TOOL_TYPE_STYLUS, 260f, 400f)))
        assertTrue(historyGestures.isEmpty())
    }

    private fun multiTap(view: InkPageView, fingers: Int) {
        val pointers = { offset: Float -> (0 until fingers).map { Pointer(it, MotionEvent.TOOL_TYPE_FINGER, 300f + it * 60f + offset, 400f) } }
        send(view, MotionEvent.ACTION_DOWN, pointers(0f).take(1))
        for (id in 1 until fingers) send(view, MotionEvent.ACTION_POINTER_DOWN, pointers(0f).take(id + 1), actionIndex = id)
        clock += 20
        for (id in (1 until fingers).reversed()) send(view, MotionEvent.ACTION_POINTER_UP, pointers(0f).take(id + 1), actionIndex = id)
        send(view, MotionEvent.ACTION_UP, pointers(0f).take(1))
        redraw(view)
    }

    private fun pageView(mode: InputMode) = InkPageView(RuntimeEnvironment.getApplication()).apply {
        configure(mode, Color.BLACK, 2f) { strokes.add(it) }
        onTurnPage = { turns.add(it) }
        onUndo = { historyGestures.add("undo") }
        onRedo = { historyGestures.add("redo") }
        show(state(page = 0))
        layout(0, 0, 600, 800)
        redraw(this)
    }

    private fun state(page: Int, source: String = "test.pdf") = EditorState(
        Draft(File(source), source, page = page),
        listOf(PageSpec(0f, 0f, 400f, 600f, 0), PageSpec(0f, 0f, 400f, 600f, 0)),
        Bitmap.createBitmap(40, 60, Bitmap.Config.ARGB_8888),
        busy = false,
    )

    private fun redraw(view: InkPageView) = view.draw(Canvas(Bitmap.createBitmap(600, 800, Bitmap.Config.ARGB_8888)))

    /** Page units covered by 100 screen pixels: smaller means more zoom. */
    private fun unitsPer100px(view: InkPageView): Float = pageAt(view, 400f, 400f).x - pageAt(view, 300f, 400f).x

    private fun pageAt(view: InkPageView, x: Float, y: Float): InkPoint {
        redraw(view)
        send(view, MotionEvent.ACTION_DOWN, listOf(Pointer(9, MotionEvent.TOOL_TYPE_STYLUS, x, y)))
        send(view, MotionEvent.ACTION_UP, listOf(Pointer(9, MotionEvent.TOOL_TYPE_STYLUS, x, y)))
        return strokes.removeAt(strokes.lastIndex).points.first()
    }

    private fun pinch(view: InkPageView, from: Pair<Float, Float>, to: Pair<Float, Float>) {
        send(view, MotionEvent.ACTION_DOWN, listOf(finger(0, from.first)))
        send(view, MotionEvent.ACTION_POINTER_DOWN, listOf(finger(0, from.first), finger(1, from.second)), actionIndex = 1)
        for (step in 1..STEPS) {
            val t = step.toFloat() / STEPS
            send(view, MotionEvent.ACTION_MOVE, listOf(
                finger(0, from.first + (to.first - from.first) * t),
                finger(1, from.second + (to.second - from.second) * t),
            ))
        }
        send(view, MotionEvent.ACTION_POINTER_UP, listOf(finger(0, to.first), finger(1, to.second)), actionIndex = 1)
        send(view, MotionEvent.ACTION_UP, listOf(finger(0, to.first)))
        redraw(view)
    }

    private fun drag(view: InkPageView, start: List<Pair<Float, Float>>, dx: Float) {
        val fingers = { offset: Float -> start.mapIndexed { id, (x, y) -> Pointer(id, MotionEvent.TOOL_TYPE_FINGER, x + offset, y) } }
        send(view, MotionEvent.ACTION_DOWN, fingers(0f).take(1))
        send(view, MotionEvent.ACTION_POINTER_DOWN, fingers(0f), actionIndex = 1)
        for (step in 1..STEPS) send(view, MotionEvent.ACTION_MOVE, fingers(dx * step / STEPS))
        send(view, MotionEvent.ACTION_POINTER_UP, fingers(dx), actionIndex = 1)
        send(view, MotionEvent.ACTION_UP, fingers(dx).take(1))
        redraw(view)
    }

    private fun doubleTap(view: InkPageView, x: Float, y: Float) {
        repeat(2) {
            send(view, MotionEvent.ACTION_DOWN, listOf(Pointer(0, MotionEvent.TOOL_TYPE_FINGER, x, y)))
            clock += 50
            send(view, MotionEvent.ACTION_UP, listOf(Pointer(0, MotionEvent.TOOL_TYPE_FINGER, x, y)))
            clock += 100
        }
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(600))
        redraw(view)
    }

    private fun swipe(view: InkPageView, from: Float, to: Float) {
        send(view, MotionEvent.ACTION_DOWN, listOf(finger(0, from)))
        for (step in 1..4) {
            clock += 8
            send(view, MotionEvent.ACTION_MOVE, listOf(finger(0, from + (to - from) * step / 4)))
        }
        send(view, MotionEvent.ACTION_UP, listOf(finger(0, to)))
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(600))
        redraw(view)
    }

    private fun finger(id: Int, x: Float) = Pointer(id, MotionEvent.TOOL_TYPE_FINGER, x, 400f)

    private var downTime = 0L

    private fun send(view: InkPageView, action: Int, pointers: List<Pointer>, actionIndex: Int = 0, flags: Int = 0) {
        if (action == MotionEvent.ACTION_DOWN) downTime = clock
        clock += 4
        val properties = pointers.map { MotionEvent.PointerProperties().apply { id = it.id; toolType = it.tool } }.toTypedArray()
        val coordinates = pointers.map { MotionEvent.PointerCoords().apply { x = it.x; y = it.y; pressure = 0.6f } }.toTypedArray()
        val source = if (pointers.any { it.tool == MotionEvent.TOOL_TYPE_STYLUS }) InputDevice.SOURCE_STYLUS else InputDevice.SOURCE_TOUCHSCREEN
        val masked = action or (actionIndex shl MotionEvent.ACTION_POINTER_INDEX_SHIFT)
        val event = MotionEvent.obtain(downTime, clock, masked, pointers.size, properties, coordinates, 0, 0, 1f, 1f, 0, 0, source, flags)
        try { view.onTouchEvent(event) } finally { event.recycle() }
    }

    private data class Pointer(val id: Int, val tool: Int, val x: Float, val y: Float)

    private companion object {
        const val STEPS = 6

    }
}
