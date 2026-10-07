package ch.lkmc.asterinked.ui

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.graphics.Canvas
import android.hardware.input.InputManager
import android.os.Bundle
import android.os.Looper
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.lifecycle.ViewModelProvider
import ch.lkmc.asterinked.R
import ch.lkmc.asterinked.document.Draft
import ch.lkmc.asterinked.document.OpenDocument
import ch.lkmc.asterinked.ink.InkPoint
import ch.lkmc.asterinked.ink.InkStroke
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.InputDeviceBuilder
import org.robolectric.shadows.ShadowDialog
import java.io.File
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-mdpi")
class MainActivityChromeTest {
    private val app get() = RuntimeEnvironment.getApplication()

    @Before
    fun startClean() {
        File(app.filesDir, "documents").deleteRecursively()
        app.getSharedPreferences("pen", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test fun welcomeOffersOneWayInAndNoDeadControls() {
        Robolectric.buildActivity(MainActivity::class.java).setup().use { controller ->
            val root = settle(controller.get())
            val shown = descendants(root).filter { it.isShown }
            val open = app.getString(R.string.open_pdf)
            assertEquals("One Open PDF action", 1, shown.count { it is Button && it.text == open || it.contentDescription == open })
            for (label in listOf(R.string.undo, R.string.redo, R.string.save_copy, R.string.black, R.string.previous)) {
                val text = app.getString(label)
                assertFalse("$text is hidden without a document", shown.any { it.contentDescription == text || (it is Button && it.text == text) })
            }
        }
    }

    @Test fun penSettingsSurviveARestart() {
        Robolectric.buildActivity(MainActivity::class.java).setup().use { controller ->
            val root = settle(controller.get())
            dot(root, R.string.blue).performClick()
            dot(root, R.string.bold).performClick()
        }
        Robolectric.buildActivity(MainActivity::class.java).setup().use { controller ->
            val root = settle(controller.get())
            assertTrue(dot(root, R.string.blue).isSelected)
            assertFalse(dot(root, R.string.black).isSelected)
            assertTrue(dot(root, R.string.bold).isSelected)
        }
    }

    @Test fun penAndHighlighterKeepTheirOwnInk() {
        Robolectric.buildActivity(MainActivity::class.java).setup().use { controller ->
            val root = settle(controller.get())
            dot(root, R.string.red).performClick()
            control(root, R.string.highlighter).performClick()
            assertTrue("The highlighter starts yellow, not the pen's red slot", dot(root, R.string.yellow).isSelected)
            assertTrue(dot(root, R.string.medium).isSelected)

            dot(root, R.string.green).performClick()
            dot(root, R.string.bold).performClick()
            control(root, R.string.pen).performClick()
            assertTrue("The pen is still red", dot(root, R.string.red).isSelected)
            assertTrue("and medium", dot(root, R.string.medium).isSelected)
        }
        Robolectric.buildActivity(MainActivity::class.java).setup().use { controller ->
            val root = settle(controller.get())
            assertTrue(dot(root, R.string.red).isSelected)
            control(root, R.string.highlighter).performClick()
            assertTrue("Both survive a restart", dot(root, R.string.green).isSelected)
            assertTrue(dot(root, R.string.bold).isSelected)
        }
    }

    // Before each tool kept its own ink, both used the pen's keys.
    @Test fun anUpdateStartsTheHighlighterFromTheSharedChoice() {
        app.getSharedPreferences("pen", Context.MODE_PRIVATE).edit().putInt("penColor", 2).putInt("penWidth", 2).commit()
        Robolectric.buildActivity(MainActivity::class.java).setup().use { controller ->
            val root = settle(controller.get())
            assertTrue(dot(root, R.string.red).isSelected)
            control(root, R.string.highlighter).performClick()
            assertTrue("The shared slot, now pink", dot(root, R.string.pink).isSelected)
            assertTrue(dot(root, R.string.bold).isSelected)
            dot(root, R.string.yellow).performClick()
        }
        Robolectric.buildActivity(MainActivity::class.java).setup().use { controller ->
            val root = settle(controller.get())
            control(root, R.string.pen).performClick()
            assertTrue("The pen is untouched", dot(root, R.string.red).isSelected)
            control(root, R.string.highlighter).performClick()
            assertTrue(dot(root, R.string.yellow).isSelected)
        }
    }

    @Test fun aToolHintShowsTwiceThenStaysOutOfTheWay() {
        val hint = app.getString(R.string.highlight_hint)
        Robolectric.buildActivity(MainActivity::class.java).setup().use { controller ->
            val root = settle(controller.get())
            val shown = (1..3).map {
                control(root, R.string.highlighter).performClick()
                val text = notice(root).shown
                shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(HINT_GONE_S))
                control(root, R.string.pen).performClick()
                text == hint
            }
            assertEquals(listOf(true, true, false), shown)
        }
        Robolectric.buildActivity(MainActivity::class.java).setup().use { controller ->
            val root = settle(controller.get())
            control(root, R.string.highlighter).performClick()
            assertNull("Also after a restart", notice(root).shown)
        }
    }

    @Test fun pickingInkPutsTheEraserAway() {
        Robolectric.buildActivity(MainActivity::class.java).setup().use { controller ->
            val root = settle(controller.get())
            control(root, R.string.eraser).performClick()
            assertTrue(control(root, R.string.eraser).isSelected)
            dot(root, R.string.blue).performClick()
            assertTrue("Back to the pen", control(root, R.string.pen).isSelected)
            assertFalse(control(root, R.string.eraser).isSelected)
            control(root, R.string.eraser).performClick()
            dot(root, R.string.bold).performClick()
            assertTrue("A width also returns to the pen", control(root, R.string.pen).isSelected)
        }
    }

    // Welcome and loading sit on the bar surface; the empty page canvas would
    // show through the loading screen and reach TalkBack behind the welcome.
    @Test fun thePageStaysHiddenOutsideTheEditor() {
        Robolectric.buildActivity(MainActivity::class.java).setup().use { controller ->
            val activity = controller.get()
            val root = settle(activity)
            assertFalse("No page on the welcome screen", page(root).isShown)
            EditorScreens.publish(activity, EditorScreens.editing())
            assertTrue("The editor shows the page", page(root).isShown)
            // Last: publishing waits until the model is idle, which loading is not.
            EditorScreens.publish(activity, EditorState(busy = true))
            assertFalse("No page while loading", page(root).isShown)
        }
    }

    @Test fun oneWritingToolIsChosenAtATime() {
        Robolectric.buildActivity(MainActivity::class.java).setup().use { controller ->
            val root = settle(controller.get())
            control(root, R.string.highlighter).performClick()
            val chosen = listOf(R.string.pen, R.string.highlighter, R.string.eraser).filter { control(root, it).isSelected }
            assertEquals(listOf(R.string.highlighter), chosen)
            // Highlighter tints replace the pen colours on the same swatches.
            assertTrue(dot(root, R.string.yellow).isSelected)
        }
    }

    @Test fun firstLaunchWritesWithAFingerUnlessAStylusIsAttached() {
        assertEquals(InputMode.TOUCH, initialInputMode(saved = null, stylusAttached = false))
        assertEquals(InputMode.PEN, initialInputMode(saved = null, stylusAttached = true))
        assertEquals("A saved choice wins", InputMode.PEN, initialInputMode("PEN", stylusAttached = false))
        assertEquals("A saved choice wins", InputMode.TOUCH, initialInputMode("TOUCH", stylusAttached = true))
    }

    // Robolectric reports no input devices: a phone or Chromebook without a pen.
    @Test fun firstLaunchWithoutAStylusDrawsWithAFinger() {
        Robolectric.buildActivity(MainActivity::class.java).setup().use { controller ->
            assertTrue(control(settle(controller.get()), R.string.draw_with_finger).isSelected)
        }
    }

    // The stylus also shows that the saved choice outranks the first-launch default.
    @Test fun fingerDrawingSurvivesARestart() {
        attachStylus()
        Robolectric.buildActivity(MainActivity::class.java).setup().use { controller ->
            val root = settle(controller.get())
            assertFalse(control(root, R.string.draw_with_finger).isSelected)
            control(root, R.string.draw_with_finger).performClick()
        }
        Robolectric.buildActivity(MainActivity::class.java).setup().use { controller ->
            assertTrue(control(settle(controller.get()), R.string.draw_with_finger).isSelected)
        }
    }

    @Test fun aRememberedDestinationSkipsThePicker() {
        Robolectric.buildActivity(MainActivity::class.java).setup().use { controller ->
            val activity = controller.get()
            EditorScreens.publish(activity, EditorScreens.editing(destination = Uri.parse("content://test/review.pdf")))
            val root = settle(activity)
            val model = ViewModelProvider(activity)[EditorViewModel::class.java]
            val save = descendants(root).filterIsInstance<Button>().single { it.text == app.getString(R.string.save) }

            save.performClick()

            // The tap writes back to the remembered file instead of opening
            // the picker — export marks the model busy synchronously.
            assertTrue(model.state.value!!.busy)
        }
    }

    // A screen that reports a stylus with no pen in reach starts in pen mode,
    // where a finger only nudges the page.
    @Test fun aFingerDragInPenModeExplainsTheHandButtonOnce() {
        attachStylus()
        Robolectric.buildActivity(MainActivity::class.java).setup().use { controller ->
            val root = editor(controller.get())
            drag(page(root), MotionEvent.TOOL_TYPE_FINGER)
            assertEquals(app.getString(R.string.finger_drag_hint), notice(root).shown?.toString())
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis((Tone.INFO.millis + NOTICE_ANIMATION_MS).toLong()))
            assertNull(notice(root).shown)

            drag(page(root), MotionEvent.TOOL_TYPE_FINGER)
            assertNull("Once per session", notice(root).shown)
        }
    }

    // A message on the first editor must not consume the one-time gesture hint.
    @Test fun theGestureHintWaitsForAMessageToClear() {
        Robolectric.buildActivity(MainActivity::class.java).setup().use { controller ->
            val activity = controller.get()
            settle(activity)
            val error = app.getString(R.string.error_out_of_memory)
            EditorScreens.publish(activity, EditorScreens.editing().copy(message = EditorMessage(error, Tone.ERROR)))
            val root = activity.window.decorView
            assertEquals(error, notice(root).shown?.toString())

            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis((Tone.ERROR.millis + NOTICE_ANIMATION_MS).toLong()))

            assertEquals(app.getString(R.string.touch_hint), notice(root).shown?.toString())
        }
    }

    // A finger hint an error held back is offered again on the next drag.
    @Test fun aFingerHintAnErrorHeldBackComesBack() {
        attachStylus()
        Robolectric.buildActivity(MainActivity::class.java).setup().use { controller ->
            val root = editor(controller.get())
            notice(root).show("Couldn’t save", Tone.ERROR)
            drag(page(root), MotionEvent.TOOL_TYPE_FINGER)
            assertEquals("Couldn’t save", notice(root).shown?.toString())

            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis((Tone.ERROR.millis + NOTICE_ANIMATION_MS).toLong()))
            drag(page(root), MotionEvent.TOOL_TYPE_FINGER)

            assertEquals(fingerHint, notice(root).shown?.toString())
        }
    }

    @Test fun noFingerHintForSomeoneWhoUsedTheHandButton() {
        attachStylus()
        Robolectric.buildActivity(MainActivity::class.java).setup().use { controller ->
            val root = settle(controller.get())
            repeat(2) { control(root, R.string.draw_with_finger).performClick() }
        }
        Robolectric.buildActivity(MainActivity::class.java).setup().use { controller ->
            val root = editor(controller.get())
            drag(page(root), MotionEvent.TOOL_TYPE_FINGER)
            assertNotEquals("They know the button, also after a restart", fingerHint, notice(root).shown?.toString())
        }
    }

    @Test fun noFingerHintOnceAPenTouchedThePage() {
        attachStylus()
        Robolectric.buildActivity(MainActivity::class.java).setup().use { controller ->
            val root = editor(controller.get())
            // Beside the page, so the pen leaves no stroke for the stand-in draft to store.
            drag(page(root), MotionEvent.TOOL_TYPE_STYLUS, x = 1f, y = 1f, distance = 0f)
            drag(page(root), MotionEvent.TOOL_TYPE_FINGER)
            assertNotEquals(fingerHint, notice(root).shown?.toString())
        }
        Robolectric.buildActivity(MainActivity::class.java).setup().use { controller ->
            val root = editor(controller.get())
            drag(page(root), MotionEvent.TOOL_TYPE_FINGER)
            assertNotEquals("The pen is remembered across launches", fingerHint, notice(root).shown?.toString())
        }
    }

    @Test fun statusDistinguishesNoNotesFromExportedNotes() {
        val stroke = InkStroke(listOf(InkPoint(1f, 1f, 1f)), 0, 2f)
        val bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        fun status(draft: Draft?, busy: Boolean = false) = EditorState(draft, emptyList(), bitmap, busy = busy).statusText()
        val blank = Draft(File("a.pdf"), "a.pdf")
        val exported = blank.copy(ink = mapOf(0 to listOf(stroke)), savedInk = mapOf(0 to listOf(stroke)))

        assertNull(status(null))
        assertEquals(R.string.no_notes, status(blank))
        assertEquals(R.string.saved, status(exported))
        assertEquals(R.string.unsaved, status(blank.copy(ink = mapOf(0 to listOf(stroke)))))
        assertEquals(R.string.working, status(exported, busy = true))
    }

    @Test fun thePagePillStepsAsideWhileWriting() {
        Robolectric.buildActivity(MainActivity::class.java).setup().use { controller ->
            val activity = controller.get()
            val root = settle(activity)
            EditorScreens.publish(activity, EditorScreens.editing())
            val page = page(root)
            page.draw(android.graphics.Canvas(Bitmap.createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888)))
            val pill = control(root, R.string.previous).parent as View
            assertTrue("The pill starts visible", pill.isShown)

            stylus(page, android.view.MotionEvent.ACTION_DOWN, 300f, 500f)
            stylus(page, android.view.MotionEvent.ACTION_MOVE, 320f, 500f)
            shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(400))
            assertFalse("The pill steps aside while writing", pill.isShown)

            // Cancel avoids committing the stand-in PDF used by this UI test.
            stylus(page, android.view.MotionEvent.ACTION_CANCEL, 320f, 500f)
            shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(400))
            assertFalse("The pill stays away right after the stroke", pill.isShown)
            shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(1500))
            assertTrue("The pill returns once the pen rests", pill.isShown)
        }
    }

    // Fading out takes a moment; a tap meanwhile must not reach the pill's
    // buttons, as it would not once the pill is gone.
    // A save picked just before a rotation that then fails must still give
    // back the grant taken for the picked file; grants are finite.
    // A message and a landed export arriving together are both read, one after
    // the other, rather than the second hiding the first unseen.
    // Whether notes would be lost depends on the file picked, so the question
    // comes after the picker, not before it.
    @Test fun unexportedNotesGoStraightToThePicker() {
        Robolectric.buildActivity(MainActivity::class.java).setup().use { controller ->
            val activity = controller.get()
            val root = settle(activity)
            EditorScreens.publish(activity, EditorScreens.editing())

            control(root, R.string.open_pdf).performClick()

            assertEquals(Intent.ACTION_OPEN_DOCUMENT, shadowOf(activity).nextStartedActivityForResult?.intent?.action)
            assertNull("No question yet", ShadowDialog.getLatestDialog()?.takeIf { it.isShowing })
        }
    }

    @Test fun aPdfFromAnotherAppOpensBeforeAnyQuestion() {
        Robolectric.buildActivity(MainActivity::class.java).setup().use { controller ->
            val activity = controller.get()
            settle(activity)
            EditorScreens.publish(activity, EditorScreens.editing())

            controller.newIntent(Intent(Intent.ACTION_VIEW, Uri.parse("content://downloads/report.pdf")))
            settle(activity)

            assertNull("Only a different PDF asks, once it is known", ShadowDialog.getLatestDialog()?.takeIf { it.isShowing })
            // No provider serves the address here, so the attempted open reports it.
            assertEquals(app.getString(R.string.error_source_unreadable), notice(activity.window.decorView).shown?.toString())
        }
    }

    @Test fun aWaitingReplacementAsksAndKeepingDropsIt() {
        Robolectric.buildActivity(MainActivity::class.java).setup().use { controller ->
            val activity = controller.get()
            settle(activity)
            val other = OpenDocument(Draft(File("other.pdf"), "Other.pdf"), emptyList(), null)
            EditorScreens.publish(activity, EditorScreens.editing().copy(replacing = other))

            val dialog = ShadowDialog.getLatestDialog() as AlertDialog
            assertTrue(dialog.isShowing)
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).performClick()
            settle(activity)

            assertNull(ViewModelProvider(activity)[EditorViewModel::class.java].state.value!!.replacing)
        }
    }

    // The one-time gesture hint is not spent under the question's dialog.
    @Test fun theGestureHintWaitsForTheReplaceQuestion() {
        Robolectric.buildActivity(MainActivity::class.java).setup().use { controller ->
            val activity = controller.get()
            val root = settle(activity)
            val other = OpenDocument(Draft(File("other.pdf"), "Other.pdf"), emptyList(), null)
            EditorScreens.publish(activity, EditorScreens.editing().copy(replacing = other))
            assertNull(notice(root).shown)

            (ShadowDialog.getLatestDialog() as AlertDialog).getButton(AlertDialog.BUTTON_NEGATIVE).performClick()
            settle(activity)

            assertEquals(app.getString(R.string.touch_hint), notice(root).shown?.toString())
        }
    }

    // A question about a PDF that is no longer waiting must not stay up.
    @Test fun aNewReplacementReplacesTheQuestion() {
        Robolectric.buildActivity(MainActivity::class.java).setup().use { controller ->
            val activity = controller.get()
            settle(activity)
            EditorScreens.publish(activity, EditorScreens.editing().copy(replacing = OpenDocument(Draft(File("a.pdf"), "A.pdf"), emptyList(), null)))
            val first = ShadowDialog.getLatestDialog()

            EditorScreens.publish(activity, EditorScreens.editing().copy(replacing = OpenDocument(Draft(File("b.pdf"), "B.pdf"), emptyList(), null)))

            assertFalse(first.isShowing)
            assertTrue(ShadowDialog.getLatestDialog().isShowing)
        }
    }

    @Test fun twoReportsTakeTurns() {
        Robolectric.buildActivity(MainActivity::class.java).setup().use { controller ->
            val activity = controller.get()
            val root = settle(activity)
            val already = app.getString(R.string.already_open)
            EditorScreens.publish(activity, EditorScreens.editing().copy(
                message = EditorMessage(already, Tone.INFO), exported = Uri.parse("content://test/saved.pdf")))
            assertEquals(app.getString(R.string.pdf_saved), notice(root).shown?.toString())

            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ACTION_NOTICE_MS + NOTICE_ANIMATION_MS))

            assertEquals("The message waited its turn", already, notice(root).shown?.toString())
        }
    }

    // A failed draft write right after a save interrupts "PDF saved", which
    // comes back once the error has been read.
    @Test fun anErrorInterruptingASavedNoticeLetsItReturn() {
        Robolectric.buildActivity(MainActivity::class.java).setup().use { controller ->
            val activity = controller.get()
            val root = settle(activity)
            val model = ViewModelProvider(activity)[EditorViewModel::class.java]
            EditorScreens.publish(activity, EditorScreens.editing().copy(exported = Uri.parse("content://test/saved.pdf")))
            val error = app.getString(R.string.notes_not_saved)
            EditorScreens.publish(activity, model.state.value!!.copy(message = EditorMessage(error, Tone.ERROR)))
            assertEquals(error, notice(root).shown?.toString())

            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis((Tone.ERROR.millis + NOTICE_ANIMATION_MS).toLong()))

            assertEquals(app.getString(R.string.pdf_saved), notice(root).shown?.toString())
        }
    }

    // A tool hint answers what the user just did; the report it covers returns after it.
    @Test fun aSavedNoticeReturnsAfterAToolHint() {
        Robolectric.buildActivity(MainActivity::class.java).setup().use { controller ->
            val activity = controller.get()
            val root = settle(activity)
            EditorScreens.publish(activity, EditorScreens.editing().copy(exported = Uri.parse("content://test/saved.pdf")))
            control(root, R.string.highlighter).performClick()
            assertEquals(app.getString(R.string.highlight_hint), notice(root).shown?.toString())

            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis((Tone.INFO.millis + NOTICE_ANIMATION_MS).toLong()))

            assertEquals(app.getString(R.string.pdf_saved), notice(root).shown?.toString())
        }
    }

    // Dismissing reads a report at once, so a second save to the same file that
    // lands during the fade-out gets its own notice instead of being swallowed.
    @Test fun aSecondSaveDuringTheFadeOutIsShownToo() {
        Robolectric.buildActivity(MainActivity::class.java).setup().use { controller ->
            val activity = controller.get()
            val root = settle(activity)
            val model = ViewModelProvider(activity)[EditorViewModel::class.java]
            val saved = Uri.parse("content://test/saved.pdf")
            EditorScreens.publish(activity, EditorScreens.editing().copy(exported = saved))

            notice(root).dismiss()
            EditorScreens.publish(activity, model.state.value!!.copy(exported = saved))
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(NOTICE_ANIMATION_MS.toLong()))

            assertEquals(app.getString(R.string.pdf_saved), notice(root).shown?.toString())
        }
    }

    // A report already dismissed must not come back after a notice covers its fade-out.
    @Test fun aDismissedReportStaysReadWhenCoveredWhileFading() {
        Robolectric.buildActivity(MainActivity::class.java).setup().use { controller ->
            val activity = controller.get()
            val root = settle(activity)
            val model = ViewModelProvider(activity)[EditorViewModel::class.java]
            EditorScreens.publish(activity, EditorScreens.editing().copy(exported = Uri.parse("content://test/saved.pdf")))

            notice(root).dismiss()
            val error = app.getString(R.string.error_out_of_memory)
            EditorScreens.publish(activity, model.state.value!!.copy(message = EditorMessage(error, Tone.ERROR)))
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis((Tone.ERROR.millis + NOTICE_ANIMATION_MS).toLong()))

            assertNotEquals(app.getString(R.string.pdf_saved), notice(root).shown?.toString())
        }
    }

    @Test fun aGrantPickedBeforeARotationIsGivenBackWhenTheSaveFails() {
        val picked = Uri.parse("content://test/picked.pdf")
        val state = Bundle()
        Robolectric.buildActivity(MainActivity::class.java).setup().use { controller ->
            val activity = controller.get()
            val root = settle(activity)
            EditorScreens.publish(activity, EditorScreens.editing())
            descendants(root).filterIsInstance<Button>().single { it.text == app.getString(R.string.save_copy) }.performClick()
            val request = shadowOf(activity).nextStartedActivityForResult
            // The save is still running when the activity is recreated.
            EditorScreens.publish(activity, EditorScreens.editing().copy(busy = true))
            shadowOf(activity).receiveResult(request.intent, Activity.RESULT_OK, Intent().setData(picked))
            assertTrue(app.contentResolver.persistedUriPermissions.any { it.uri == picked })
            controller.saveInstanceState(state)
        }

        Robolectric.buildActivity(MainActivity::class.java).setup(state).use { controller ->
            // The save failed: the draft has no destination.
            EditorScreens.publish(controller.get(), EditorScreens.editing())

            assertTrue("The grant is given back", app.contentResolver.persistedUriPermissions.none { it.uri == picked })
        }
    }

    @Test fun aFadingPillLetsANewTouchThrough() {
        Robolectric.buildActivity(MainActivity::class.java).setup().use { controller ->
            val activity = controller.get()
            val root = settle(activity)
            EditorScreens.publish(activity, EditorScreens.editing())
            val next = control(root, R.string.next)
            val pill = next.parent as View

            page(root).onWritingChanged(WritingState.ACTIVE)
            val x = next.left + next.width / 2f
            val y = next.top + next.height / 2f
            val down = android.view.MotionEvent.obtain(0L, 0L, android.view.MotionEvent.ACTION_DOWN, x, y, 0)
            val taken = try { pill.dispatchTouchEvent(down) } finally { down.recycle() }

            assertFalse("The fading pill refuses the touch", taken)
            assertFalse(next.isPressed)
        }
    }

    @Test fun aPendingPillReturnDoesNotRevealControlsOnWelcome() {
        Robolectric.buildActivity(MainActivity::class.java).setup().use { controller ->
            val activity = controller.get()
            val root = settle(activity)
            EditorScreens.publish(activity, EditorScreens.editing())
            val page = page(root)
            page.draw(android.graphics.Canvas(Bitmap.createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888)))
            val pill = control(root, R.string.previous).parent as View

            stylus(page, android.view.MotionEvent.ACTION_DOWN, 300f, 500f)
            shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(400))
            stylus(page, android.view.MotionEvent.ACTION_CANCEL, 300f, 500f)
            EditorScreens.publish(activity, EditorState(busy = false))
            shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(2000))

            assertEquals("Navigation stays gone outside the editor", View.GONE, pill.visibility)
        }
    }

    @Test fun leavingTheEditorDuringAStrokeDoesNotRescheduleThePill() {
        Robolectric.buildActivity(MainActivity::class.java).setup().use { controller ->
            val activity = controller.get()
            val root = settle(activity)
            EditorScreens.publish(activity, EditorScreens.editing())
            val page = page(root)
            page.draw(android.graphics.Canvas(Bitmap.createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888)))
            val pill = control(root, R.string.previous).parent as View

            stylus(page, android.view.MotionEvent.ACTION_DOWN, 300f, 500f)
            EditorScreens.publish(activity, EditorState(busy = false))
            shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(2000))

            assertEquals(View.GONE, pill.visibility)
        }
    }

    private fun stylus(view: View, action: Int, x: Float, y: Float) {
        val properties = arrayOf(android.view.MotionEvent.PointerProperties().apply {
            id = 7
            toolType = android.view.MotionEvent.TOOL_TYPE_STYLUS
        })
        val coordinates = arrayOf(android.view.MotionEvent.PointerCoords().apply { this.x = x; this.y = y; pressure = 0.6f })
        val event = android.view.MotionEvent.obtain(1000L, 1004L, action, 1, properties, coordinates,
            0, 0, 1f, 1f, 0, 0, android.view.InputDevice.SOURCE_STYLUS, 0)
        try { view.onTouchEvent(event) } finally { event.recycle() }
    }

    private fun settle(activity: MainActivity): View {
        repeat(50) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(10)
        }
        return activity.window.decorView
    }

    private fun attachStylus() {
        val stylus = InputDeviceBuilder.newBuilder().setId(STYLUS_DEVICE)
            .setSources(InputDevice.SOURCE_TOUCHSCREEN or InputDevice.SOURCE_STYLUS).build()
        shadowOf(app.getSystemService(InputManager::class.java)).addInputDevice(stylus)
    }

    // The editor with its page drawn once, which places the page inside the view.
    private fun editor(activity: MainActivity): View {
        val root = settle(activity)
        EditorScreens.publish(activity, EditorScreens.editing())
        val page = page(root)
        assertTrue("The page is laid out", page.width > 0 && page.height > 0)
        page.draw(Canvas(Bitmap.createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888)))
        return root
    }

    // Slow and vertical, so it neither swipes to another page nor zooms.
    private fun drag(page: InkPageView, tool: Int, x: Float = page.width / 2f, y: Float = page.height / 2f, distance: Float = DRAG_PX) {
        val start = SystemClock.uptimeMillis()
        val source = if (tool == MotionEvent.TOOL_TYPE_STYLUS) InputDevice.SOURCE_STYLUS else InputDevice.SOURCE_TOUCHSCREEN
        fun send(action: Int, step: Int) {
            val properties = arrayOf(MotionEvent.PointerProperties().apply { id = 0; toolType = tool })
            val coordinates = arrayOf(MotionEvent.PointerCoords().also { it.x = x; it.y = y + distance * step / DRAG_STEPS; it.pressure = 0.6f })
            val event = MotionEvent.obtain(start, start + step * DRAG_STEP_MS, action, 1, properties, coordinates, 0, 0, 1f, 1f, 0, 0, source, 0)
            try { page.dispatchTouchEvent(event) } finally { event.recycle() }
        }
        send(MotionEvent.ACTION_DOWN, 0)
        for (step in 1..DRAG_STEPS) send(MotionEvent.ACTION_MOVE, step)
        send(MotionEvent.ACTION_UP, DRAG_STEPS)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(DRAG_STEPS * DRAG_STEP_MS))
    }

    private fun notice(root: View): NoticeBar = descendants(root).filterIsInstance<NoticeBar>().single()

    // The editor's one-time gesture hint may be on screen; these tests are about this one.
    private val fingerHint get() = app.getString(R.string.finger_drag_hint)

    private fun control(root: View, label: Int): View =
        descendants(root).single { it.contentDescription == app.getString(label) }

    private fun page(root: View): InkPageView = descendants(root).filterIsInstance<InkPageView>().single()

    private fun dot(root: View, label: Int): ChoiceDot =
        descendants(root).filterIsInstance<ChoiceDot>().single { it.contentDescription == app.getString(label) }

    private fun descendants(view: View): Sequence<View> = sequence {
        yield(view)
        if (view is ViewGroup) for (index in 0 until view.childCount) yieldAll(descendants(view.getChildAt(index)))
    }

    private companion object {
        // Longer than an info notice stays, so the next one starts from hidden.
        const val HINT_GONE_S = 4L
        const val STYLUS_DEVICE = 7
        const val DRAG_PX = 120f
        const val DRAG_STEPS = 6
        const val DRAG_STEP_MS = 50L
        const val NOTICE_ANIMATION_MS = 500
        // How long NoticeBar keeps a notice that offers an action.
        const val ACTION_NOTICE_MS = 10_000L
    }
}
