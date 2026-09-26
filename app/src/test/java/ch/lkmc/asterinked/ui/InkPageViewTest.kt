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
