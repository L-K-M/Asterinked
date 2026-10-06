package ch.lkmc.asterinked.ui

import android.content.Context
import android.graphics.Rect
import android.view.View
import android.widget.TextView
import ch.lkmc.asterinked.ui.EditorScreens.descendants
import ch.lkmc.asterinked.ui.EditorScreens.editing
import ch.lkmc.asterinked.ui.EditorScreens.publish
import ch.lkmc.asterinked.ui.EditorScreens.settle
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** The editor chrome on the screen sizes people actually hold. Text needs native graphics to have a width. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MainActivityLayoutTest {
    private val app get() = RuntimeEnvironment.getApplication()

    @Before
    fun startClean() {
        File(app.filesDir, "documents").deleteRecursively()
        app.getSharedPreferences("pen", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test @Config(qualifiers = "w360dp-h640dp-port-mdpi")
    fun everyControlFitsASmallPhone() = assertControlsFit()

    @Test @Config(qualifiers = "w411dp-h891dp-port-mdpi")
    fun everyControlFitsAPhone() = assertControlsFit()

    @Test @Config(qualifiers = "w891dp-h411dp-land-mdpi")
    fun everyControlFitsAPhoneInLandscape() = assertControlsFit()

    @Test @Config(qualifiers = "w800dp-h1280dp-port-mdpi")
    fun everyControlFitsATablet() = assertControlsFit()

    @Test @Config(qualifiers = "w360dp-h640dp-port-mdpi")
    fun theFileNameStaysReadableWithTheLargestText() {
        RuntimeEnvironment.setFontScale(2f)
        editor { root ->
            val title = descendants(root).filterIsInstance<TextView>().single { it.text == "Quarterly review.pdf" }
            assertTrue("Title is ${title.width}px wide", title.width >= MIN_TITLE_DP)
            assertControlsFit(root)
        }
    }

    private fun assertControlsFit() = editor(::assertControlsFit)

    // On screen, at least 48dp square, and never on top of another control.
    private fun assertControlsFit(root: View) {
        val screen = Rect(0, 0, root.width, root.height)
        val controls = descendants(root).filter { it.isShown && it.isClickable && it !is InkPageView }.toList()
        assertTrue("Editor controls are shown", controls.size > MIN_CONTROLS)
        val bounds = controls.associateWith { view ->
            IntArray(2).let { view.getLocationInWindow(it); Rect(it[0], it[1], it[0] + view.width, it[1] + view.height) }
        }
        for ((view, rect) in bounds) {
            val name = view.contentDescription ?: (view as? TextView)?.text
            assertTrue("$name lies inside the screen: $rect", screen.contains(rect))
            assertTrue("$name is at least 48dp: $rect", rect.width() >= TOUCH_DP && rect.height() >= TOUCH_DP)
            for ((other, otherRect) in bounds) {
                if (other === view || other.parent === view || view.parent === other) continue
                assertFalse("$name overlaps ${other.contentDescription}", Rect.intersects(rect, otherRect))
            }
        }
    }

    private fun editor(check: (View) -> Unit) {
        Robolectric.buildActivity(MainActivity::class.java).setup().use { controller ->
            val activity = controller.get()
            settle()
            publish(activity, editing())
            val metrics = activity.resources.displayMetrics
            val root = activity.window.decorView
            root.measure(View.MeasureSpec.makeMeasureSpec(metrics.widthPixels, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(metrics.heightPixels, View.MeasureSpec.EXACTLY))
            root.layout(0, 0, metrics.widthPixels, metrics.heightPixels)
            check(root)
        }
    }

    private companion object {
        // At mdpi one dp is one pixel.
        const val TOUCH_DP = 48
        const val MIN_TITLE_DP = 96
        const val MIN_CONTROLS = 15
    }
}
