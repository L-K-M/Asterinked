package ch.lkmc.asterinked.ui

import android.content.Context
import android.os.Looper
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import androidx.lifecycle.ViewModelProvider
import ch.lkmc.asterinked.R
import ch.lkmc.asterinked.ui.EditorScreens.descendants
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

/**
 * A hand resting on the bars while the pen writes must not press their
 * controls. Events go through the activity, which sees every pointer before
 * the framework splits them between the page and the bars. Orders in which a
 * finger is already down on a control when another pointer moves are covered
 * by PalmGuardTest: Robolectric's split events give that finger wrong
 * coordinates, which unpresses the control and would hide the bug.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w800dp-h1280dp-mdpi")
class MainActivityPalmTest {
    private val app get() = RuntimeEnvironment.getApplication()
    private var clock = 1_000L
    private var downTime = 0L

    @Before
    fun startClean() {
        File(app.filesDir, "documents").deleteRecursively()
        app.getSharedPreferences("pen", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test fun aPalmThatLandsOnTheToolBarWhileThePenWritesDoesNotClick() = editor { activity, root ->
        val pen = pen(centerOf(page(root)))
        val palm = finger(centerOf(dot(root, R.string.red)))

        dispatch(activity, MotionEvent.ACTION_DOWN, listOf(pen))
        dispatch(activity, MotionEvent.ACTION_POINTER_DOWN, listOf(pen, palm), actionIndex = 1)
        dispatch(activity, MotionEvent.ACTION_MOVE, listOf(pen.moved(20f), palm))
        dispatch(activity, MotionEvent.ACTION_POINTER_UP, listOf(pen.moved(20f), palm), actionIndex = 1)
        dispatch(activity, MotionEvent.ACTION_UP, listOf(pen.moved(20f)))
        idle()

        assertTrue("The ink colour stays", dot(root, R.string.black).isSelected)
        assertFalse(dot(root, R.string.red).isSelected)
    }

    @Test fun aHandThatLandsJustAfterThePenLiftsIsStillAPalm() = editor { activity, root ->
        val pen = pen(centerOf(page(root)))
        val palm = finger(centerOf(dot(root, R.string.red)))

        dispatch(activity, MotionEvent.ACTION_DOWN, listOf(pen))
        dispatch(activity, MotionEvent.ACTION_UP, listOf(pen))
        clock += 100
        tap(activity, palm)

        assertTrue(dot(root, R.string.black).isSelected)
    }

    @Test fun fingerAndPenTapsOnTheBarsStillWork() = editor { activity, root ->
        tap(activity, finger(centerOf(dot(root, R.string.red))))
        assertTrue("A finger tap without the pen picks a colour", dot(root, R.string.red).isSelected)
        tap(activity, finger(centerOf(control(root, R.string.next))))
        assertEquals("A finger tap turns the page", 3, page(activity))

        tap(activity, pen(centerOf(dot(root, R.string.blue))))
        assertTrue("The pen can tap the bars", dot(root, R.string.blue).isSelected)

        val pen = pen(centerOf(page(root)))
        dispatch(activity, MotionEvent.ACTION_DOWN, listOf(pen))
        dispatch(activity, MotionEvent.ACTION_UP, listOf(pen))
        clock += 1_000
        tap(activity, finger(centerOf(dot(root, R.string.green))))
        assertTrue("A deliberate tap a moment after writing works", dot(root, R.string.green).isSelected)
    }

    private fun editor(test: (MainActivity, View) -> Unit) {
        Robolectric.buildActivity(MainActivity::class.java).setup().use { controller ->
            val activity = controller.get()
            EditorScreens.publish(activity, EditorScreens.editing())
            test(activity, activity.window.decorView)
        }
    }

    private fun tap(activity: MainActivity, touch: Touch) {
        dispatch(activity, MotionEvent.ACTION_DOWN, listOf(touch))
        clock += 50
        dispatch(activity, MotionEvent.ACTION_UP, listOf(touch))
        idle()
    }

    private fun dispatch(activity: MainActivity, action: Int, touches: List<Touch>, actionIndex: Int = 0) {
        clock += 8
        if (action == MotionEvent.ACTION_DOWN) downTime = clock
        val properties = touches.map { touch -> MotionEvent.PointerProperties().apply { id = touch.id; toolType = touch.tool } }.toTypedArray()
        val coordinates = touches.map { touch -> MotionEvent.PointerCoords().apply { x = touch.x; y = touch.y; pressure = 0.6f } }.toTypedArray()
        val source = if (touches.any { it.tool == MotionEvent.TOOL_TYPE_STYLUS }) InputDevice.SOURCE_STYLUS else InputDevice.SOURCE_TOUCHSCREEN
        val masked = action or (actionIndex shl MotionEvent.ACTION_POINTER_INDEX_SHIFT)
        val event = MotionEvent.obtain(downTime, clock, masked, touches.size, properties, coordinates, 0, 0, 1f, 1f, 0, 0, source, 0)
        try { activity.dispatchTouchEvent(event) } finally { event.recycle() }
    }

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    private fun page(activity: MainActivity): Int = ViewModelProvider(activity)[EditorViewModel::class.java].state.value!!.draft!!.page

    private fun centerOf(view: View): Pair<Float, Float> {
        val location = IntArray(2).also(view::getLocationInWindow)
        return location[0] + view.width / 2f to location[1] + view.height / 2f
    }

    private fun page(root: View): InkPageView = descendants(root).filterIsInstance<InkPageView>().single()

    private fun control(root: View, label: Int): View = descendants(root).single { it.contentDescription == app.getString(label) }

    private fun dot(root: View, label: Int): ChoiceDot =
        descendants(root).filterIsInstance<ChoiceDot>().single { it.contentDescription == app.getString(label) }

    private fun pen(at: Pair<Float, Float>) = Touch(PEN_ID, MotionEvent.TOOL_TYPE_STYLUS, at.first, at.second)

    private fun finger(at: Pair<Float, Float>) = Touch(FINGER_ID, MotionEvent.TOOL_TYPE_FINGER, at.first, at.second)

    private data class Touch(val id: Int, val tool: Int, val x: Float, val y: Float) {
        fun moved(dx: Float) = copy(x = x + dx)
    }

    private companion object {
        const val PEN_ID = 3
        const val FINGER_ID = 5
    }
}
