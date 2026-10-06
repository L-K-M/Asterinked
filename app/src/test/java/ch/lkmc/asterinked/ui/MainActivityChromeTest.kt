package ch.lkmc.asterinked.ui

import android.content.Context
import android.graphics.Bitmap
import android.os.Looper
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
import java.io.File

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

    @Test fun fingerDrawingSurvivesARestart() {
        Robolectric.buildActivity(MainActivity::class.java).setup().use { controller ->
            val root = settle(controller.get())
            assertFalse(control(root, R.string.draw_with_finger).isSelected)
            control(root, R.string.draw_with_finger).performClick()
        }
        Robolectric.buildActivity(MainActivity::class.java).setup().use { controller ->
            assertTrue(control(settle(controller.get()), R.string.draw_with_finger).isSelected)
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

            stylus(page, android.view.MotionEvent.ACTION_UP, 320f, 500f)
            shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(400))
            assertFalse("The pill stays away right after the stroke", pill.isShown)
            shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(1500))
            assertTrue("The pill returns once the pen rests", pill.isShown)
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

    private fun control(root: View, label: Int): View =
        descendants(root).single { it.contentDescription == app.getString(label) }

    private fun page(root: View): InkPageView = descendants(root).filterIsInstance<InkPageView>().single()

    private fun dot(root: View, label: Int): ChoiceDot =
        descendants(root).filterIsInstance<ChoiceDot>().single { it.contentDescription == app.getString(label) }

    private fun descendants(view: View): Sequence<View> = sequence {
        yield(view)
        if (view is ViewGroup) for (index in 0 until view.childCount) yieldAll(descendants(view.getChildAt(index)))
    }
}
