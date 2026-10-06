package ch.lkmc.asterinked.ui

import android.app.Activity
import android.os.Looper
import android.widget.Button
import android.widget.FrameLayout
import org.junit.Assert.assertEquals
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

    @Test fun anActionRunsItsCallbackAndHidesTheNotice() {
        var ran = false
        notice.show("PDF saved.", Tone.SUCCESS, NoticeAction("Open") { ran = true })
        assertEquals("Open", notice.actionLabel)

        actionButton()!!.performClick()

        assertTrue(ran)
        advance(ANIMATION_MS)
        assertNull(notice.shown)
    }

    @Test fun anActionNoticeStaysLongEnoughToReachIt() {
        notice.show("PDF saved.", Tone.SUCCESS, NoticeAction("Open") {})
        advance(Tone.SUCCESS.millis + ANIMATION_MS)
        assertEquals("PDF saved.", notice.shown)
    }

    @Test fun aNoticeWithoutAnActionDropsTheOldOne() {
        notice.show("PDF saved.", Tone.SUCCESS, NoticeAction("Open") {})
        notice.show("Drag across strokes to erase them", Tone.INFO)
        assertEquals("Drag across strokes to erase them", notice.shown)
        assertNull(notice.actionLabel)
    }

    private fun actionButton(): Button? = (0 until notice.childCount)
        .map { notice.getChildAt(it) }
        .filterIsInstance<Button>()
        .singleOrNull()

    private fun advance(millis: Int) = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(millis.toLong()))

    private companion object {
        const val ANIMATION_MS = 500
        const val HALF_FADE_MS = 80
    }
}
