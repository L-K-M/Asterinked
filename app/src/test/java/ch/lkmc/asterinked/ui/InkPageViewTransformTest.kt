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
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.Duration

/** Input after a transform change must not need a frame to catch up. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "mdpi")
class InkPageViewTransformTest {
    private val strokes = mutableListOf<InkStroke>()
    private val erased = mutableListOf<InkStroke>()
    private var clock = 1_000L
    private var downTime = 0L
    private val portrait = PageSpec(0f, 0f, 400f, 600f, 0)
    private val landscapePages = listOf(
        PageSpec(0f, 0f, 600f, 400f, 0),
        PageSpec(20f, 30f, 400f, 600f, 90),
        PageSpec(20f, 30f, 600f, 400f, 180),
        PageSpec(20f, 30f, 400f, 600f, 270),
    )

    @Test fun firstShowAndLayoutAcceptInkBeforeDraw() {
        val view = newView()
        view.show(state(listOf(portrait)))
        view.layout(0, 0, 600, 800)

        assertPoint(200f, 300f, inkAt(view, 300f, 400f))
    }

    @Test fun pageChangeMapsInkBeforeDrawAtEveryRotation() {
        for (page in landscapePages) {
            val view = pageView()
            view.show(state(listOf(portrait, page), page = 1))

            // The new page occupies (12, 208)..(588, 592), at scale 0.96.
            assertPoint(91.66667f, 95.83333f, inkAt(view, 100f, 300f))
        }
    }

    @Test fun pageChangeMapsEraseBeforeDrawAtEveryRotation() {
        for (page in landscapePages) {
            val view = pageView()
            val target = dot(91.66667f, 95.83333f)
            val staleTarget = dot(68.04124f, 148.45361f)
            view.show(state(listOf(portrait, page), page = 1, ink = listOf(target, staleTarget)))

            assertEquals(listOf(target), eraseAt(view, 100f, 300f))
        }
    }

    @Test fun replacingDimensionsOnTheSamePageRefreshesInputBeforeDraw() {
        val view = pageView()
        view.show(state(listOf(landscapePages.first())))

        assertPoint(91.66667f, 95.83333f, inkAt(view, 100f, 300f))
    }

    @Test fun pageChangeRejectsInkOutsideTheNewPageBeforeDraw() {
        val view = pageView()
        view.show(state(listOf(portrait, PageSpec(0f, 0f, 200f, 1000f, 0)), page = 1))

        tap(view, 100f, 300f)

        assertTrue("The tap is outside the new, narrow page", strokes.isEmpty())
    }

    @Test fun resizeMapsInkBeforeDraw() {
        val view = pageView()
        view.layout(0, 0, 800, 600)

        assertPoint(116.66667f, 154.16667f, inkAt(view, 320f, 160f))
    }

    @Test fun resizeMapsEraseBeforeDraw() {
        val target = dot(116.66667f, 154.16667f)
        val view = pageView(listOf(target))
        view.layout(0, 0, 800, 600)

        assertEquals(listOf(target), eraseAt(view, 320f, 160f))
    }

    @Test fun resizeAcceptsInkInTheNewHitAreaBeforeDraw() {
        val view = pageView()
        view.layout(0, 0, 800, 600)

        assertPoint(366.66667f, 39.58333f, inkAt(view, 560f, 50f))
    }

    @Test fun panMapsInkBeforeDraw() {
        val view = pageView()
        pinch(view)
        redraw(view)
        pan(view, 60f, 40f)

        assertPoint(200f, 300f, inkAt(view, 360f, 440f))
    }

    @Test fun panMapsEraseBeforeDraw() {
        val target = dot(200f, 300f)
        val view = pageView(listOf(target))
        pinch(view)
        redraw(view)
        pan(view, 60f, 40f)

        assertEquals(listOf(target), eraseAt(view, 360f, 440f))
    }

    @Test fun pinchMapsInkBeforeDraw() {
        val view = pageView()
        pinch(view)

        val immediate = inkAt(view, 400f, 400f)
        redraw(view)
        val drawn = inkAt(view, 400f, 400f)

        assertTrue("The pinch zooms in", drawn.x < 250f)
        assertPoint(drawn.x, drawn.y, immediate)
    }

    @Test fun pinchMapsEraseBeforeDraw() {
        // A drawn reference supplies the detector's final scale without reading
        // private zoom state or depending on its platform-specific span slop.
        val reference = pageView()
        pinch(reference)
        redraw(reference)
        val point = inkAt(reference, 400f, 400f)
        val target = dot(point.x, point.y)
        val view = pageView(listOf(target))
        pinch(view)

        assertEquals(listOf(target), eraseAt(view, 400f, 400f))
    }

    @Test fun animatedZoomMapsInkBeforeDraw() {
        val view = pageView()
        doubleTap(view)

        assertPoint(230.92783f, 300f, inkAt(view, 400f, 400f))
    }

    @Test fun animatedZoomMapsEraseBeforeDraw() {
        val target = dot(230.92783f, 300f)
        val view = pageView(listOf(target))
        doubleTap(view)

        assertEquals(listOf(target), eraseAt(view, 400f, 400f))
    }

    @Test fun resetZoomMapsInkBeforeDraw() {
        val view = pageView()
        doubleTap(view)
        redraw(view)
        view.resetZoom()
        settleAnimation()

        assertPoint(277.31958f, 300f, inkAt(view, 400f, 400f))
    }

    @Test fun resetZoomMapsEraseBeforeDraw() {
        val target = dot(277.31958f, 300f)
        val view = pageView(listOf(target))
        doubleTap(view)
        redraw(view)
        view.resetZoom()
        settleAnimation()

        assertEquals(listOf(target), eraseAt(view, 400f, 400f))
    }

    @Test fun zoomedPageChangeRetainsColumnAndAlignsTopBeforeInk() {
        val view = zoomedAndPannedView()
        view.show(state(listOf(portrait, landscapePages.first()), page = 1))

        // Retained 2.5x zoom and panX = 60 put the new top at the 12px margin.
        assertPoint(191.66667f, 120f, inkAt(view, 100f, 300f))
    }

    @Test fun zoomedPageChangeRetainsColumnAndAlignsTopBeforeErase() {
        val view = zoomedAndPannedView()
        val target = dot(191.66667f, 120f)
        view.show(state(listOf(portrait, landscapePages.first()), page = 1, ink = listOf(target)))

        assertEquals(listOf(target), eraseAt(view, 100f, 300f))
    }

    @Test fun newDocumentResetsTransformBeforeInk() {
        val view = zoomedAndPannedView()
        view.show(state(listOf(portrait), source = "other.pdf"))

        assertPoint(200f, 300f, inkAt(view, 300f, 400f))
    }

    @Test fun newDocumentResetsTransformBeforeErase() {
        val view = zoomedAndPannedView()
        val target = dot(200f, 300f)
        view.show(state(listOf(portrait), ink = listOf(target), source = "other.pdf"))

        assertEquals(listOf(target), eraseAt(view, 300f, 400f))
    }

    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Test fun immediatePageChangeInkRendersUnderTheTap() {
        val view = pageView()
        val pages = listOf(portrait, landscapePages.first())
        view.show(state(pages, page = 1))
        tap(view, 100f, 300f)
        view.show(state(pages, page = 1, ink = strokes.toList()))

        val image = screenshot(view, "page-transform-ink")

        assertTrue("Immediate ink stays under the tap", Color.red(image.getPixel(100, 300)) < 128)
        assertEquals("No ink at the stale mapping", Color.WHITE, image.getPixel(77, 350))
    }

    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Test fun immediatePageChangeEraseRendersTheCorrectRemoval() {
        val view = pageView()
        val pages = listOf(portrait, landscapePages.first())
        val target = dot(91.66667f, 95.83333f)
        val staleTarget = dot(68.04124f, 148.45361f)
        val ink = listOf(target, staleTarget)
        view.show(state(pages, page = 1, ink = ink))
        val removed = eraseAt(view, 100f, 300f)
        view.show(state(pages, page = 1, ink = ink.filter { it !in removed }))

        val image = screenshot(view, "page-transform-erase")

        assertEquals("Ink under the eraser is gone", Color.WHITE, image.getPixel(100, 300))
        assertTrue("Untouched ink remains", Color.red(image.getPixel(77, 350)) < 128)
    }

    private fun newView() = InkPageView(RuntimeEnvironment.getApplication()).apply {
        configure(InputMode.PEN, Color.BLACK, 6f) { strokes.add(it) }
        onErase = { erased.addAll(it) }
    }

    private fun pageView(ink: List<InkStroke> = emptyList()) = newView().apply {
        show(state(listOf(portrait), ink = ink))
        layout(0, 0, 600, 800)
        redraw(this)
    }

    private fun zoomedAndPannedView() = pageView().apply {
        doubleTap(this)
        pan(this, 60f, 40f)
        redraw(this)
    }

    private fun state(pages: List<PageSpec>, page: Int = 0, ink: List<InkStroke> = emptyList(), source: String = "test.pdf") =
        EditorState(Draft(File(source), source, page = page, ink = mapOf(page to ink)), pages, busy = false)

    private fun dot(x: Float, y: Float) = InkStroke(listOf(InkPoint(x, y, 1f)), Color.BLACK, 6f)

    private fun inkAt(view: InkPageView, x: Float, y: Float): InkPoint {
        tap(view, x, y)
        assertEquals("The tap commits one stroke", 1, strokes.size)
        return strokes.removeAt(0).points.first()
    }

    private fun eraseAt(view: InkPageView, x: Float, y: Float): List<InkStroke> {
        erased.clear()
        view.tool = InkTool.ERASER
        tap(view, x, y)
        assertTrue("Erasing adds no ink", strokes.isEmpty())
        return erased.toList()
    }

    private fun assertPoint(x: Float, y: Float, actual: InkPoint) {
        assertEquals("Page x", x, actual.x, 0.01f)
        assertEquals("Page y", y, actual.y, 0.01f)
    }

    private fun tap(view: InkPageView, x: Float, y: Float) {
        send(view, MotionEvent.ACTION_DOWN, listOf(Pointer(9, MotionEvent.TOOL_TYPE_STYLUS, x, y)))
        send(view, MotionEvent.ACTION_UP, listOf(Pointer(9, MotionEvent.TOOL_TYPE_STYLUS, x, y)))
    }

    private fun pinch(view: InkPageView) {
        val fingers = { spread: Float -> listOf(finger(0, 300f - spread, 400f), finger(1, 300f + spread, 400f)) }
        send(view, MotionEvent.ACTION_DOWN, fingers(100f).take(1))
        send(view, MotionEvent.ACTION_POINTER_DOWN, fingers(100f), actionIndex = 1)
        for (step in 1..GESTURE_STEPS) send(view, MotionEvent.ACTION_MOVE, fingers(100f + 100f * step / GESTURE_STEPS))
        send(view, MotionEvent.ACTION_POINTER_UP, fingers(200f), actionIndex = 1)
        send(view, MotionEvent.ACTION_UP, fingers(200f).take(1))
    }

    private fun pan(view: InkPageView, dx: Float, dy: Float) {
        send(view, MotionEvent.ACTION_DOWN, listOf(finger(0, 300f, 400f)))
        for (step in 1..GESTURE_STEPS) {
            send(view, MotionEvent.ACTION_MOVE, listOf(finger(0, 300f + dx * step / GESTURE_STEPS, 400f + dy * step / GESTURE_STEPS)))
        }
        send(view, MotionEvent.ACTION_UP, listOf(finger(0, 300f + dx, 400f + dy)))
    }

    private fun doubleTap(view: InkPageView) {
        repeat(2) {
            send(view, MotionEvent.ACTION_DOWN, listOf(finger(0, 300f, 400f)))
            clock += 50
            send(view, MotionEvent.ACTION_UP, listOf(finger(0, 300f, 400f)))
            clock += 100
        }
        settleAnimation()
    }

    private fun settleAnimation() = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(600))

    private fun finger(id: Int, x: Float, y: Float) = Pointer(id, MotionEvent.TOOL_TYPE_FINGER, x, y)

    private fun send(view: InkPageView, action: Int, pointers: List<Pointer>, actionIndex: Int = 0) {
        if (action == MotionEvent.ACTION_DOWN) downTime = clock
        clock += 4
        val properties = pointers.map { MotionEvent.PointerProperties().apply { id = it.id; toolType = it.tool } }.toTypedArray()
        val coordinates = pointers.map { MotionEvent.PointerCoords().apply { x = it.x; y = it.y; pressure = 0.6f } }.toTypedArray()
        val source = if (pointers.any { it.tool == MotionEvent.TOOL_TYPE_STYLUS }) InputDevice.SOURCE_STYLUS else InputDevice.SOURCE_TOUCHSCREEN
        val event = MotionEvent.obtain(downTime, clock, action or (actionIndex shl MotionEvent.ACTION_POINTER_INDEX_SHIFT),
            pointers.size, properties, coordinates, 0, 0, 1f, 1f, 0, 0, source, 0)
        try { view.onTouchEvent(event) } finally { event.recycle() }
    }

    private fun redraw(view: InkPageView): Bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888).also {
        view.draw(Canvas(it))
    }

    private fun screenshot(view: InkPageView, name: String): Bitmap {
        val image = redraw(view)
        val output = File("build/reports/screens/$name.png").apply { parentFile!!.mkdirs() }
        output.outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return image
    }

    private data class Pointer(val id: Int, val tool: Int, val x: Float, val y: Float)

    private companion object {
        const val GESTURE_STEPS = 6
    }
}
