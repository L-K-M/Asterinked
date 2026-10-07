package ch.lkmc.asterinked.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.hardware.input.InputManager
import android.os.Looper
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import ch.lkmc.asterinked.R
import ch.lkmc.asterinked.document.Draft
import ch.lkmc.asterinked.ink.InkPoint
import ch.lkmc.asterinked.ink.InkStroke
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    @Test fun noFingerHintOnceAPenTouchedThePage() {
        attachStylus()
        Robolectric.buildActivity(MainActivity::class.java).setup().use { controller ->
            val root = editor(controller.get())
            // Beside the page, so the pen leaves no stroke for the stand-in draft to store.
            drag(page(root), MotionEvent.TOOL_TYPE_STYLUS, x = 1f, y = 1f, distance = 0f)
            drag(page(root), MotionEvent.TOOL_TYPE_FINGER)
            assertNull(notice(root).shown)
        }
        Robolectric.buildActivity(MainActivity::class.java).setup().use { controller ->
            val root = editor(controller.get())
            drag(page(root), MotionEvent.TOOL_TYPE_FINGER)
            assertNull("The pen is remembered across launches", notice(root).shown)
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
        const val STYLUS_DEVICE = 7
        const val DRAG_PX = 120f
        const val DRAG_STEPS = 6
        const val DRAG_STEP_MS = 50L
        const val NOTICE_ANIMATION_MS = 500
    }
}
