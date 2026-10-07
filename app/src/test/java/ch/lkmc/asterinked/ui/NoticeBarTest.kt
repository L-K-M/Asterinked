package ch.lkmc.asterinked.ui

import android.app.Activity
import android.os.Looper
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class NoticeBarTest {
    // Attached to a window: a detached view never runs its posted timers and animations.
    private val notice = Robolectric.buildActivity(Activity::class.java).setup().get().let { activity ->
        NoticeBar(activity).also { activity.setContentView(FrameLayout(activity).apply { addView(it) }) }
    }

    @Test fun aNoticeHidesByItself() {
        notice.show("Saved", Tone.SUCCESS)
        assertEquals("Saved", notice.shown)
        advance(Tone.SUCCESS.millis + ANIMATION_MS)
        assertNull(notice.shown)
    }

    @Test fun aNewNoticeDuringDismissalStays() {
        notice.show("First", Tone.INFO)
        advance(ANIMATION_MS)
        notice.dismiss()
        notice.show("Second", Tone.ERROR)
        advance(ANIMATION_MS)
        assertEquals("Second", notice.shown)
    }

    // The fade-out is already running when the next message arrives.
    @Test fun aNewNoticeTakesOverAFadeOutHalfway() {
        notice.show("First", Tone.INFO)
        advance(ANIMATION_MS)
        notice.dismiss()
        advance(HALF_FADE_MS)
        notice.show("Second", Tone.SUCCESS)
        advance(ANIMATION_MS)
        assertEquals("Second", notice.shown)
        assertEquals(1f, notice.alpha)
    }

    @Test fun errorsStayLongerThanHints() {
        notice.show("Couldn’t save", Tone.ERROR)
        advance(Tone.INFO.millis + ANIMATION_MS)
        assertEquals("Couldn’t save", notice.shown)
    }

    @Test fun aHintDoesNotHideAnError() {
        notice.show("Couldn’t save", Tone.ERROR)
        notice.show("Drag across strokes to erase them", Tone.INFO)
        assertEquals("Couldn’t save", notice.shown)
        advance(Tone.ERROR.millis + ANIMATION_MS)
        notice.show("Drag across strokes to erase them", Tone.INFO)
        assertEquals("Drag across strokes to erase them", notice.shown)
    }

    @Test fun thePenWritesThroughANoticeAndPutsAHintAway() {
        notice.show("Drag across text to highlight it", Tone.INFO)
        advance(ANIMATION_MS)

        assertFalse("A pen touch is left to the page underneath", touch(MotionEvent.TOOL_TYPE_STYLUS))
        advance(ANIMATION_MS)
        assertNull("The hint gives way", notice.shown)
    }

    @Test fun thePenLeavesAnErrorToBeRead() {
        notice.show("Couldn’t save", Tone.ERROR)
        advance(ANIMATION_MS)

        assertFalse(touch(MotionEvent.TOOL_TYPE_STYLUS))
        advance(ANIMATION_MS)
        assertEquals("Couldn’t save", notice.shown)
    }

    @Test fun aFingerStillTapsTheNoticeAway() {
        notice.show("Drag across text to highlight it", Tone.INFO)
        advance(ANIMATION_MS)

        assertTrue(touch(MotionEvent.TOOL_TYPE_FINGER))
        assertTrue(touch(MotionEvent.TOOL_TYPE_FINGER, MotionEvent.ACTION_UP))
        advance(ANIMATION_MS)
        assertNull(notice.shown)
    }

    @Test fun thePenCanUseTheNoticeAction() {
        var acted = false
        notice.show("Your latest notes couldn’t be stored", Tone.ERROR, "Save copy") { acted = true }
        advance(ANIMATION_MS)
        val action = notice.findViewsWithText("Save copy")

        assertTrue("The action takes the pen", touch(MotionEvent.TOOL_TYPE_STYLUS, at = action))
        touch(MotionEvent.TOOL_TYPE_STYLUS, MotionEvent.ACTION_UP, at = action)
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(acted)
    }

    // Lays the notice out at a fixed size and touches its middle, or the middle of [at].
    private fun touch(tool: Int, action: Int = MotionEvent.ACTION_DOWN, at: View? = null): Boolean {
        notice.measure(View.MeasureSpec.makeMeasureSpec(NOTICE_WIDTH, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(NOTICE_HEIGHT, View.MeasureSpec.EXACTLY))
        notice.layout(0, 0, NOTICE_WIDTH, NOTICE_HEIGHT)
        val x = at?.let { it.left + it.width / 2f } ?: (NOTICE_WIDTH / 3f)
        val y = at?.let { it.top + it.height / 2f } ?: (NOTICE_HEIGHT / 2f)
        val properties = arrayOf(MotionEvent.PointerProperties().apply { id = 0; toolType = tool })
        val coordinates = arrayOf(MotionEvent.PointerCoords().apply { this.x = x; this.y = y })
        val event = MotionEvent.obtain(0, 10, action, 1, properties, coordinates, 0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_TOUCHSCREEN, 0)
        return try { notice.dispatchTouchEvent(event) } finally { event.recycle() }
    }

    private fun View.findViewsWithText(text: String): View =
        ArrayList<View>().also { findViewsWithText(it, text, View.FIND_VIEWS_WITH_TEXT) }.single()

    private fun advance(millis: Int) = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(millis.toLong()))

    private companion object {
        const val ANIMATION_MS = 500
        const val HALF_FADE_MS = 80
        const val NOTICE_WIDTH = 400
        const val NOTICE_HEIGHT = 120
    }
}
