package ch.lkmc.asterinked.ui

import android.app.Activity
import android.os.Looper
import android.view.InputDevice
import android.view.MotionEvent
import android.view.ViewGroup
import android.widget.Button
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Feeds the guard the whole window's events and the protected control only
 * its own finger, as the framework does after splitting a touch.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PalmGuardTest {
    private val guard = PalmGuard()
    private var clicks = 0
    // Attached to a window: a detached view's posted click never runs.
    private val control = Robolectric.buildActivity(Activity::class.java).setup().get().let { activity ->
        Button(activity).apply {
            setOnClickListener { clicks++ }
            guard.protect(this)
            activity.setContentView(this, ViewGroup.LayoutParams(SIZE, SIZE))
            layout(0, 0, SIZE, SIZE)
        }
    }
    private var clock = 1_000L
    private var downTime = 0L

    @Test fun aHandRestingWhenThePenLandsDoesNotClick() {
        window(MotionEvent.ACTION_DOWN, listOf(FINGER))
        own(MotionEvent.ACTION_DOWN)
        window(MotionEvent.ACTION_POINTER_DOWN, listOf(FINGER, PEN), actionIndex = 1)
        own(MotionEvent.ACTION_MOVE)
        window(MotionEvent.ACTION_POINTER_UP, listOf(FINGER, PEN), actionIndex = 1)
        own(MotionEvent.ACTION_MOVE)
        window(MotionEvent.ACTION_UP, listOf(FINGER))
        own(MotionEvent.ACTION_UP)

        assertEquals(0, clicks)
        assertFalse("Not left pressed", control.isPressed)
    }

    @Test fun aHandThatLiftsAfterThePenDoesNotClick() {
        window(MotionEvent.ACTION_DOWN, listOf(PEN))
        window(MotionEvent.ACTION_POINTER_DOWN, listOf(PEN, FINGER), actionIndex = 1)
        own(MotionEvent.ACTION_DOWN)
        window(MotionEvent.ACTION_POINTER_UP, listOf(PEN, FINGER), actionIndex = 0)
        own(MotionEvent.ACTION_MOVE)
        clock += 400
        window(MotionEvent.ACTION_UP, listOf(FINGER))
        own(MotionEvent.ACTION_UP)

        assertEquals(0, clicks)
    }

    @Test fun aTapLongAfterThePenClicks() {
        window(MotionEvent.ACTION_DOWN, listOf(PEN))
        window(MotionEvent.ACTION_UP, listOf(PEN))
        clock += 400
        window(MotionEvent.ACTION_DOWN, listOf(FINGER))
        own(MotionEvent.ACTION_DOWN)
        window(MotionEvent.ACTION_UP, listOf(FINGER))
        own(MotionEvent.ACTION_UP)

        assertEquals(1, clicks)
    }

    @Test fun aNewGestureForgetsThePalm() {
        window(MotionEvent.ACTION_DOWN, listOf(PEN))
        window(MotionEvent.ACTION_POINTER_DOWN, listOf(PEN, FINGER), actionIndex = 1)
        window(MotionEvent.ACTION_POINTER_UP, listOf(PEN, FINGER), actionIndex = 1)
        window(MotionEvent.ACTION_UP, listOf(PEN))
        clock += 1_000

        window(MotionEvent.ACTION_DOWN, listOf(FINGER))
        own(MotionEvent.ACTION_DOWN)
        window(MotionEvent.ACTION_UP, listOf(FINGER))
        own(MotionEvent.ACTION_UP)

        assertEquals("The same pointer id taps deliberately later", 1, clicks)
    }

    // What the activity sees: every pointer.
    private fun window(action: Int, pointers: List<Int>, actionIndex: Int = 0) {
        clock += 8
        if (action == MotionEvent.ACTION_DOWN) downTime = clock
        val event = event(action or (actionIndex shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), pointers)
        try { guard.track(event) } finally { event.recycle() }
    }

    // What the protected control sees: its own finger, in its coordinates.
    private fun own(action: Int) {
        val event = event(action, listOf(FINGER))
        try { control.dispatchTouchEvent(event) } finally { event.recycle() }
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun event(action: Int, pointers: List<Int>): MotionEvent {
        val properties = pointers.map { pointer ->
            MotionEvent.PointerProperties().apply {
                id = pointer
                toolType = if (pointer == PEN) MotionEvent.TOOL_TYPE_STYLUS else MotionEvent.TOOL_TYPE_FINGER
            }
        }.toTypedArray()
        val coordinates = pointers.map { MotionEvent.PointerCoords().apply { x = SIZE / 2f; y = SIZE / 2f } }.toTypedArray()
        return MotionEvent.obtain(downTime, clock, action, pointers.size, properties, coordinates, 0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_TOUCHSCREEN, 0)
    }

    private companion object {
        const val PEN = 3
        const val FINGER = 5
        const val SIZE = 48
    }
}
