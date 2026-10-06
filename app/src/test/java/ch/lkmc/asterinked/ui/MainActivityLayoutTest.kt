package ch.lkmc.asterinked.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import ch.lkmc.asterinked.R
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

    @Test @Config(qualifiers = "w320dp-h640dp-port-mdpi")
    fun everyControlFitsANarrowPhone() = assertControlsFit()

    @Test @Config(qualifiers = "ar-ldrtl-w320dp-h640dp-port-mdpi")
    fun everyControlFitsANarrowPhoneRightToLeft() = assertControlsFit()

    @Test @Config(qualifiers = "w680dp-h360dp-land-mdpi")
    fun everyControlFitsAtTheFormerOneRowBreakpoint() = assertControlsFit()

    @Test @Config(qualifiers = "w720dp-h360dp-land-mdpi")
    fun theToolBarFitsAfterSideInsetsChange() = editor { root ->
        val blue = descendants(root).single { it.contentDescription == app.getString(R.string.blue) }
        blue.performClick()
        val content = root.findViewById<ViewGroup>(android.R.id.content).getChildAt(0)
        ViewCompat.dispatchApplyWindowInsets(content, WindowInsetsCompat.Builder()
            .setInsets(WindowInsetsCompat.Type.navigationBars(), Insets.of(0, 0, SIDE_INSET_DP, 0))
            .build())
        measure(root)
        assertControlsFit(root)
        assertTrue("Ink selection survives reflow", blue.isSelected)

        val bold = descendants(root).single { it.contentDescription == app.getString(R.string.bold) }
        val position = IntArray(2).also(bold::getLocationInWindow)
        assertTrue("Bold stays clear of the side navigation bar", position[0] + bold.width <= root.width - SIDE_INSET_DP)

        ViewCompat.dispatchApplyWindowInsets(content, WindowInsetsCompat.Builder()
            .setInsets(WindowInsetsCompat.Type.navigationBars(), Insets.NONE)
            .build())
        measure(root)
        assertControlsFit(root)
        assertTrue("Ink selection survives returning to the wide layout", blue.isSelected)
        for (dot in descendants(root).filterIsInstance<ChoiceDot>()) assertChoiceAppearance(dot)
    }

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

    @Test @Config(qualifiers = "ar-ldrtl-w411dp-h891dp-port-mdpi")
    fun theStatusDotStaysWithItsTextRightToLeft() = editor { root ->
        val status = descendants(root).filterIsInstance<TextView>().single { it.text == app.getString(R.string.unsaved) }
        val dot = status.compoundDrawablesRelative[0]!!
        // Right to left the dot sits at the right edge; the text must end beside it.
        val textRight = status.totalPaddingLeft + status.layout.getLineRight(0)
        val dotLeft = status.width - status.paddingRight - dot.bounds.width()
        assertTrue("Text ends at $textRight, dot starts at $dotLeft", dotLeft - textRight <= status.compoundDrawablePadding + 1)
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
            val root = activity.window.decorView
            measure(root)
            check(root)
        }
    }

    private fun measure(root: View) {
        val metrics = root.resources.displayMetrics
        root.measure(View.MeasureSpec.makeMeasureSpec(metrics.widthPixels, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(metrics.heightPixels, View.MeasureSpec.EXACTLY))
        root.layout(0, 0, metrics.widthPixels, metrics.heightPixels)
    }

    // A reflow may detach a dot mid-animation. Its indicator must match a
    // freshly laid-out control with the same selection, not a cancelled frame.
    private fun assertChoiceAppearance(dot: ChoiceDot) {
        val reference = Components(dot.context).choice(R.string.blue, 0) {}.apply {
            fill = dot.fill
            radius = dot.radius
            ringColor = dot.ringColor
            outlineColor = dot.outlineColor
            backdrop = dot.backdrop
            isSelected = dot.isSelected
        }
        reference.measure(View.MeasureSpec.makeMeasureSpec(dot.width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(dot.height, View.MeasureSpec.EXACTLY))
        reference.layout(0, 0, dot.width, dot.height)
        val expected = Bitmap.createBitmap(dot.width, dot.height, Bitmap.Config.ARGB_8888)
        val actual = Bitmap.createBitmap(dot.width, dot.height, Bitmap.Config.ARGB_8888)
        reference.draw(Canvas(expected))
        dot.draw(Canvas(actual))
        assertTrue("${dot.contentDescription} selection indicator survives reflow", actual.sameAs(expected))
    }

    private companion object {
        // At mdpi one dp is one pixel.
        const val TOUCH_DP = 48
        const val MIN_TITLE_DP = 96
        const val MIN_CONTROLS = 15
        const val SIDE_INSET_DP = 48
    }
}
