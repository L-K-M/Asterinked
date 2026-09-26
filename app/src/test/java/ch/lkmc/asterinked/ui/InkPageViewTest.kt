package ch.lkmc.asterinked.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.view.InputDevice
import android.view.MotionEvent
import ch.lkmc.asterinked.document.Draft
import ch.lkmc.asterinked.document.PageSpec
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

    private val page: Bitmap = Bitmap.createBitmap(400, 600, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.WHITE) }

    private fun darkPixelsNear(view: InkPageView, x: Int, y: Int): Boolean {
        val image = Bitmap.createBitmap(600, 800, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(image))
        return (-3..3).any { dy -> Color.red(image.getPixel(x, y + dy)) < 128 }
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

    private fun send(view: InkPageView, action: Int, pointers: List<Pointer>) {
        val properties = pointers.map { pointer -> MotionEvent.PointerProperties().apply { id = pointer.id; toolType = pointer.tool } }.toTypedArray()
        val coordinates = pointers.map { pointer -> MotionEvent.PointerCoords().apply { x = pointer.x; y = pointer.y; pressure = 0.6f } }.toTypedArray()
        val event = MotionEvent.obtain(0, 10, action, pointers.size, properties, coordinates, 0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_STYLUS, 0)
        try { view.onTouchEvent(event) } finally { event.recycle() }
    }

    private data class Pointer(val id: Int, val tool: Int, val x: Float, val y: Float)
}
