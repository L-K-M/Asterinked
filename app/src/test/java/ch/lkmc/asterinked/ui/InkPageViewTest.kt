package ch.lkmc.asterinked.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.view.InputDevice
import android.view.MotionEvent
import ch.lkmc.asterinked.document.Draft
import ch.lkmc.asterinked.document.PageSpec
import ch.lkmc.asterinked.ink.InkKind
import ch.lkmc.asterinked.ink.InkPoint
import ch.lkmc.asterinked.ink.InkStroke
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class InkPageViewTest {
    @Test fun stylusRetainsStrokeWhenPalmArrivesInTouchMode() {
        val strokes = mutableListOf<InkStroke>()
        val view = pageView(InputMode.TOUCH, strokes)
        send(view, MotionEvent.ACTION_DOWN, listOf(Pointer(7, MotionEvent.TOOL_TYPE_STYLUS, 120f, 120f)))
        send(view, MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), listOf(
            Pointer(7, MotionEvent.TOOL_TYPE_STYLUS, 130f, 130f), Pointer(9, MotionEvent.TOOL_TYPE_FINGER, 250f, 300f),
        ))
        send(view, MotionEvent.ACTION_POINTER_UP, listOf(
            Pointer(7, MotionEvent.TOOL_TYPE_STYLUS, 160f, 160f), Pointer(9, MotionEvent.TOOL_TYPE_FINGER, 250f, 300f),
        ))
        assertEquals(1, strokes.size)
        assertTrue(strokes.single().points.last().x > strokes.single().points.first().x)
    }

    @Test fun fingerDoesNotDrawInPenMode() {
        val strokes = mutableListOf<InkStroke>()
        val view = pageView(InputMode.PEN, strokes)
        send(view, MotionEvent.ACTION_DOWN, listOf(Pointer(0, MotionEvent.TOOL_TYPE_FINGER, 120f, 120f)))
        send(view, MotionEvent.ACTION_UP, listOf(Pointer(0, MotionEvent.TOOL_TYPE_FINGER, 180f, 180f)))
        assertTrue(strokes.isEmpty())
    }

    @Test fun canceledStrokeIsNotCommitted() {
        val strokes = mutableListOf<InkStroke>()
        val view = pageView(InputMode.PEN, strokes)
        send(view, MotionEvent.ACTION_DOWN, listOf(Pointer(7, MotionEvent.TOOL_TYPE_STYLUS, 120f, 120f)))
        send(view, MotionEvent.ACTION_CANCEL, listOf(Pointer(7, MotionEvent.TOOL_TYPE_STYLUS, 180f, 180f)))
        assertTrue(strokes.isEmpty())
    }

    @Test fun pageStillRenderingTakesInkRightAway() {
        val strokes = mutableListOf<InkStroke>()
        val view = InkPageView(RuntimeEnvironment.getApplication()).apply {
            configure(InputMode.PEN, Color.BLACK, 2f) { strokes.add(it) }
            show(EditorState(Draft(File("test.pdf"), "test.pdf"), listOf(PageSpec(0f, 0f, 400f, 600f, 0)), preview = null, busy = false))
            layout(0, 0, 600, 800)
            draw(Canvas(Bitmap.createBitmap(600, 800, Bitmap.Config.ARGB_8888)))
        }

        send(view, MotionEvent.ACTION_DOWN, listOf(Pointer(7, MotionEvent.TOOL_TYPE_STYLUS, 150f, 300f)))
        send(view, MotionEvent.ACTION_UP, listOf(Pointer(7, MotionEvent.TOOL_TYPE_STYLUS, 250f, 300f)))

        assertEquals(1, strokes.size)
    }

    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Test fun committedAndLiveInkRenderInSoftware() {
        val strokes = mutableListOf<InkStroke>()
        val view = pageView(InputMode.PEN, strokes)
        send(view, MotionEvent.ACTION_DOWN, listOf(Pointer(7, MotionEvent.TOOL_TYPE_STYLUS, 150f, 300f)))
        send(view, MotionEvent.ACTION_MOVE, listOf(Pointer(7, MotionEvent.TOOL_TYPE_STYLUS, 250f, 300f)))
        assertTrue("Live stroke is drawn before it is committed", darkPixelsNear(view, 200, 300))
        send(view, MotionEvent.ACTION_UP, listOf(Pointer(7, MotionEvent.TOOL_TYPE_STYLUS, 350f, 300f)))
        val committed = strokes.single()
        view.show(EditorState(Draft(File("test.pdf"), "test.pdf", ink = mapOf(0 to listOf(committed))), listOf(PageSpec(0f, 0f, 400f, 600f, 0)), page, busy = false))
        assertTrue("Committed stroke is drawn", darkPixelsNear(view, 300, 300))
    }

    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Test fun batchedHistoricalSamplesShapeTheLiveStroke() {
        val view = pageView(InputMode.PEN, mutableListOf())
        send(view, MotionEvent.ACTION_DOWN, listOf(Pointer(7, MotionEvent.TOOL_TYPE_STYLUS, 150f, 300f)))
        // One MOVE carrying a historical sample at (250, 250) and the current one at (350, 300).
        val properties = arrayOf(MotionEvent.PointerProperties().apply { id = 7; toolType = MotionEvent.TOOL_TYPE_STYLUS })
        val coordinates = { x: Float, y: Float -> arrayOf(MotionEvent.PointerCoords().apply { this.x = x; this.y = y; pressure = 0.6f }) }
        val move = MotionEvent.obtain(0, 20, MotionEvent.ACTION_MOVE, 1, properties, coordinates(250f, 250f), 0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_STYLUS, 0)
        move.addBatch(30, coordinates(350f, 300f), 0)
        try { view.onTouchEvent(move) } finally { move.recycle() }

        // The smoothed curve peaks near y = 262 at x = 250; a stroke that skipped
        // the historical sample would be a straight line at y = 300.
        assertTrue("Live stroke bends through the historical sample", darkPixelsNear(view, 250, 262))
        assertFalse("Live stroke is not a straight line", darkPixelsNear(view, 250, 300))
    }

    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Test fun aHoveringStylusShowsWhereTheNibWillLand() {
        val blue = Color.rgb(32, 85, 184)
        val view = pageView(InputMode.PEN, mutableListOf()).apply { configure(InputMode.PEN, blue, 2f) {} }

        hover(view, MotionEvent.ACTION_HOVER_MOVE, 300f, 400f)
        assertTrue("A ring in the ink colour", pixelsNear(view, 300, 400) { Color.blue(it) > 150 && Color.red(it) < 100 })

        hover(view, MotionEvent.ACTION_HOVER_EXIT, 300f, 400f)
        assertFalse("Gone once the pen leaves", pixelsNear(view, 300, 400) { Color.blue(it) > 150 && Color.red(it) < 100 })
    }

    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Test fun hoveringWithTheEraserShowsTheEraserInstead() {
        val view = pageView(InputMode.PEN, mutableListOf()).apply { configure(InputMode.PEN, Color.rgb(32, 85, 184), 2f) {} }
        view.tool = InkTool.ERASER

        hover(view, MotionEvent.ACTION_HOVER_MOVE, 300f, 400f)

        assertFalse("No ink ring", pixelsNear(view, 300, 400) { Color.blue(it) > 150 && Color.red(it) < 100 })
        assertTrue("The eraser ring, 10dp out", pixelsNear(view, 310, 400, reach = 2) { Color.red(it) < 200 })
    }

    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Test fun noHoverRingWhileTheEditorIsBusyOrOffThePage() {
        val view = pageView(InputMode.PEN, mutableListOf()).apply { configure(InputMode.PEN, Color.rgb(32, 85, 184), 2f) {} }
        hover(view, MotionEvent.ACTION_HOVER_MOVE, 30f, 30f)
        assertFalse("Outside the page", pixelsNear(view, 30, 30) { Color.blue(it) > 150 && Color.red(it) < 100 })

        view.show(EditorState(Draft(File("test.pdf"), "test.pdf"), listOf(PageSpec(0f, 0f, 400f, 600f, 0)), page, busy = true))
        hover(view, MotionEvent.ACTION_HOVER_MOVE, 300f, 400f)
        assertFalse("While busy", pixelsNear(view, 300, 400) { Color.blue(it) > 150 && Color.red(it) < 100 })
    }

    private fun hover(view: InkPageView, action: Int, x: Float, y: Float) {
        val properties = arrayOf(MotionEvent.PointerProperties().apply { id = 0; toolType = MotionEvent.TOOL_TYPE_STYLUS })
        val coordinates = arrayOf(MotionEvent.PointerCoords().apply { this.x = x; this.y = y })
        val event = MotionEvent.obtain(0, 10, action, 1, properties, coordinates, 0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_STYLUS, 0)
        try { view.onHoverEvent(event) } finally { event.recycle() }
    }

    private fun pixelsNear(view: InkPageView, x: Int, y: Int, reach: Int = 6, matches: (Int) -> Boolean): Boolean {
        val image = Bitmap.createBitmap(600, 800, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(image))
        return (-reach..reach).any { dy -> (-reach..reach).any { dx -> matches(image.getPixel(x + dx, y + dy)) } }
    }

    private val page: Bitmap = Bitmap.createBitmap(400, 600, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.WHITE) }

    private fun darkPixelsNear(view: InkPageView, x: Int, y: Int): Boolean {
        val image = Bitmap.createBitmap(600, 800, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(image))
        return (-3..3).any { dy -> Color.red(image.getPixel(x, y + dy)) < 128 }
    }

    @Test fun eraserToolRemovesTouchedStrokesOnly() {
        val (view, drawn) = pageWithInk(InputMode.PEN)
        val erased = mutableListOf<InkStroke>()
        view.onErase = { erased.addAll(it) }
        view.tool = InkTool.ERASER

        drag(view, MotionEvent.TOOL_TYPE_STYLUS, fromY = 280f, toY = 320f, x = 200f)

        assertEquals(listOf(drawn[0]), erased)
        assertTrue("Erasing adds no ink", strokes.isEmpty())
    }

    @Test fun stylusEraserEndErasesWithThePenToolSelected() {
        val (view, drawn) = pageWithInk(InputMode.PEN)
        val erased = mutableListOf<InkStroke>()
        view.onErase = { erased.addAll(it) }

        drag(view, MotionEvent.TOOL_TYPE_ERASER, fromY = 280f, toY = 320f, x = 200f)

        assertEquals(listOf(drawn[0]), erased)
        assertTrue(strokes.isEmpty())
    }

    @Test fun stylusSideButtonErases() {
        val (view, drawn) = pageWithInk(InputMode.PEN)
        val erased = mutableListOf<InkStroke>()
        view.onErase = { erased.addAll(it) }

        drag(view, MotionEvent.TOOL_TYPE_STYLUS, fromY = 380f, toY = 420f, x = 200f, buttons = MotionEvent.BUTTON_STYLUS_PRIMARY)

        assertEquals(listOf(drawn[1]), erased)
        assertTrue(strokes.isEmpty())
    }

    @Test fun fingerErasesInTouchModeWithTheEraserTool() {
        val (view, drawn) = pageWithInk(InputMode.TOUCH)
        val erased = mutableListOf<InkStroke>()
        view.onErase = { erased.addAll(it) }
        view.tool = InkTool.ERASER

        drag(view, MotionEvent.TOOL_TYPE_FINGER, fromY = 280f, toY = 420f, x = 200f)

        assertEquals(drawn, erased)
        assertTrue("Erasing adds no ink", strokes.isEmpty())
    }

    @Test fun cancelledEraseRemovesNothing() {
        val (view, _) = pageWithInk(InputMode.PEN)
        val erased = mutableListOf<InkStroke>()
        view.onErase = { erased.addAll(it) }
        view.tool = InkTool.ERASER

        send(view, MotionEvent.ACTION_DOWN, listOf(Pointer(7, MotionEvent.TOOL_TYPE_STYLUS, 200f, 280f)))
        send(view, MotionEvent.ACTION_MOVE, listOf(Pointer(7, MotionEvent.TOOL_TYPE_STYLUS, 200f, 320f)))
        send(view, MotionEvent.ACTION_CANCEL, listOf(Pointer(7, MotionEvent.TOOL_TYPE_STYLUS, 200f, 320f)))

        assertTrue(erased.isEmpty())
    }

    private val strokes = mutableListOf<InkStroke>()

    /** Two horizontal strokes, drawn across screen y = 300 and y = 400. */
    private fun pageWithInk(mode: InputMode): Pair<InkPageView, List<InkStroke>> {
        val view = pageView(mode, strokes)
        listOf(300f, 400f).forEach { y ->
            send(view, MotionEvent.ACTION_DOWN, listOf(Pointer(7, MotionEvent.TOOL_TYPE_STYLUS, 150f, y)))
            send(view, MotionEvent.ACTION_UP, listOf(Pointer(7, MotionEvent.TOOL_TYPE_STYLUS, 250f, y)))
        }
        val drawn = strokes.toList()
        strokes.clear()
        view.show(EditorState(Draft(File("test.pdf"), "test.pdf", ink = mapOf(0 to drawn)), listOf(PageSpec(0f, 0f, 400f, 600f, 0)), Bitmap.createBitmap(400, 600, Bitmap.Config.ARGB_8888), busy = false))
        return view to drawn
    }

    private fun drag(view: InkPageView, tool: Int, fromY: Float, toY: Float, x: Float, buttons: Int = 0) {
        send(view, MotionEvent.ACTION_DOWN, listOf(Pointer(7, tool, x, fromY)), buttons)
        send(view, MotionEvent.ACTION_MOVE, listOf(Pointer(7, tool, x, (fromY + toY) / 2f)), buttons)
        send(view, MotionEvent.ACTION_UP, listOf(Pointer(7, tool, x, toY)), buttons)
    }

    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Test fun highlightsSitUnderPenInkWhateverTheirOrder() {
        val white = Bitmap.createBitmap(400, 600, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.WHITE) }
        val blue = Color.rgb(32, 85, 184)
        val pen = InkStroke(listOf(InkPoint(100f, 300f, 1f), InkPoint(300f, 300f, 1f)), blue, 6f)
        val marker = InkStroke(listOf(InkPoint(200f, 250f, 1f), InkPoint(200f, 350f, 1f)), Color.rgb(255, 228, 92), 12f, InkKind.HIGHLIGHTER)
        val view = InkPageView(RuntimeEnvironment.getApplication()).apply {
            configure(InputMode.PEN, Color.BLACK, 2f) {}
            show(EditorState(Draft(File("test.pdf"), "test.pdf", ink = mapOf(0 to listOf(pen, marker))), listOf(PageSpec(0f, 0f, 400f, 600f, 0)), white, busy = false))
            layout(0, 0, 600, 800)
        }
        val image = Bitmap.createBitmap(600, 800, Bitmap.Config.ARGB_8888).also { view.draw(Canvas(it)) }
        // Page (200, 300) lands at screen (300, 400): the pen crosses the highlight there.
        val crossing = image.getPixel(300, 400)
        assertTrue("Pen ink stays blue over a later highlight", Color.blue(crossing) > 150)
    }

    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Test fun aHighlightBeingDrawnAlsoSitsUnderPenInk() {
        val white = Bitmap.createBitmap(400, 600, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.WHITE) }
        val pen = InkStroke(listOf(InkPoint(100f, 300f, 1f), InkPoint(300f, 300f, 1f)), Color.rgb(32, 85, 184), 6f)
        val view = InkPageView(RuntimeEnvironment.getApplication()).apply {
            configure(InputMode.PEN, Color.rgb(255, 228, 92), 12f, InkKind.HIGHLIGHTER) {}
            show(EditorState(Draft(File("test.pdf"), "test.pdf", ink = mapOf(0 to listOf(pen))), listOf(PageSpec(0f, 0f, 400f, 600f, 0)), white, busy = false))
            layout(0, 0, 600, 800)
            draw(Canvas(Bitmap.createBitmap(600, 800, Bitmap.Config.ARGB_8888)))
        }
        // A live stroke down page x = 200 crosses the blue line at screen (300, 400).
        send(view, MotionEvent.ACTION_DOWN, listOf(Pointer(7, MotionEvent.TOOL_TYPE_STYLUS, 300f, 340f)))
        send(view, MotionEvent.ACTION_MOVE, listOf(Pointer(7, MotionEvent.TOOL_TYPE_STYLUS, 300f, 400f)))
        send(view, MotionEvent.ACTION_MOVE, listOf(Pointer(7, MotionEvent.TOOL_TYPE_STYLUS, 300f, 460f)))
        val image = Bitmap.createBitmap(600, 800, Bitmap.Config.ARGB_8888).also { view.draw(Canvas(it)) }
        assertTrue("The live highlight is visible", Color.blue(image.getPixel(300, 360)) < 150)
        assertTrue("Pen ink stays blue under the live highlight", Color.blue(image.getPixel(300, 400)) > 150)
    }

    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Test fun highlighterTintsThePageButKeepsDarkContentDark() {
        val strokes = mutableListOf<InkStroke>()
        // A white page with a vertical black bar at page x 150..170 (screen x ~237..262).
        val page = Bitmap.createBitmap(400, 600, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.WHITE)
            Canvas(this).drawRect(150f, 0f, 170f, 600f, android.graphics.Paint().apply { color = Color.BLACK })
        }
        val view = InkPageView(RuntimeEnvironment.getApplication()).apply {
            configure(InputMode.PEN, Color.rgb(255, 228, 92), 12f, InkKind.HIGHLIGHTER) { strokes.add(it) }
            show(EditorState(Draft(File("test.pdf"), "test.pdf"), listOf(PageSpec(0f, 0f, 400f, 600f, 0)), page, busy = false))
            layout(0, 0, 600, 800)
            draw(Canvas(Bitmap.createBitmap(600, 800, Bitmap.Config.ARGB_8888)))
        }
        send(view, MotionEvent.ACTION_DOWN, listOf(Pointer(7, MotionEvent.TOOL_TYPE_STYLUS, 150f, 300f)))
        send(view, MotionEvent.ACTION_MOVE, listOf(Pointer(7, MotionEvent.TOOL_TYPE_STYLUS, 250f, 302f)))
        send(view, MotionEvent.ACTION_UP, listOf(Pointer(7, MotionEvent.TOOL_TYPE_STYLUS, 350f, 304f)))
        val marker = strokes.single()
        assertEquals(InkKind.HIGHLIGHTER, marker.kind)

        view.show(EditorState(Draft(File("test.pdf"), "test.pdf", ink = mapOf(0 to listOf(marker))), listOf(PageSpec(0f, 0f, 400f, 600f, 0)), page, busy = false))
        val image = Bitmap.createBitmap(600, 800, Bitmap.Config.ARGB_8888).also { view.draw(Canvas(it)) }
        val tinted = image.getPixel(200, 302)
        assertTrue("White page turns yellow", Color.red(tinted) > 200 && Color.green(tinted) > 180 && Color.blue(tinted) < 150)
        val bar = image.getPixel(250, 302)
        assertTrue("Black content stays black under the highlight", Color.red(bar) < 60 && Color.green(bar) < 60)
    }

    private fun pageView(mode: InputMode, strokes: MutableList<InkStroke>): InkPageView {
        val bitmap = Bitmap.createBitmap(400, 600, Bitmap.Config.ARGB_8888)
        return InkPageView(RuntimeEnvironment.getApplication()).apply {
            configure(mode, Color.BLACK, 2f) { strokes.add(it) }
            show(EditorState(Draft(File("test.pdf"), "test.pdf"), listOf(PageSpec(0f, 0f, 400f, 600f, 0)), bitmap, busy = false))
            layout(0, 0, 600, 800)
            draw(Canvas(Bitmap.createBitmap(600, 800, Bitmap.Config.ARGB_8888)))
        }
    }

    private fun send(view: InkPageView, action: Int, pointers: List<Pointer>, buttons: Int = 0) {
        val properties = pointers.map { pointer -> MotionEvent.PointerProperties().apply { id = pointer.id; toolType = pointer.tool } }.toTypedArray()
        val coordinates = pointers.map { pointer -> MotionEvent.PointerCoords().apply { x = pointer.x; y = pointer.y; pressure = 0.6f } }.toTypedArray()
        val event = MotionEvent.obtain(0, 10, action, pointers.size, properties, coordinates, 0, buttons, 1f, 1f, 0, 0, InputDevice.SOURCE_STYLUS, 0)
        try { view.onTouchEvent(event) } finally { event.recycle() }
    }

    private data class Pointer(val id: Int, val tool: Int, val x: Float, val y: Float)
}
