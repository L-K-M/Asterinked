package ch.lkmc.asterinked.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import androidx.core.graphics.ColorUtils
import ch.lkmc.asterinked.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.roundToInt

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ChoiceDotContrastTest {
    @Test @Config(qualifiers = "notnight-xhdpi")
    fun paleHighlightersHaveVisibleUnselectedEdges() {
        assertVisibleEdges(listOf(Color.rgb(255, 228, 92), Color.rgb(255, 168, 207)))
    }

    @Test @Config(qualifiers = "night-xhdpi")
    fun darkPenChoicesHaveVisibleUnselectedEdges() {
        assertVisibleEdges(listOf(Color.rgb(25, 38, 46), Color.rgb(32, 85, 184), Color.rgb(179, 47, 61)))
    }

    private fun assertVisibleEdges(colors: List<Int>) {
        val context = RuntimeEnvironment.getApplication()
        val ui = Components(context)
        val backdrop = context.getColor(R.color.surface)
        val size = ui.dp(Size.TOUCH)
        val center = size / 2

        for (color in colors) {
            val choice = ui.choice(R.string.blue, 0) {}.apply {
                fill = color
                layout(0, 0, size, size)
            }
            val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888).apply { eraseColor(backdrop) }
            choice.draw(Canvas(bitmap))

            // Sample inside the circumference, avoiding its anti-aliased outer pixel.
            val edgeX = center + ((choice.radius - EDGE_INSET_DP) * context.resources.displayMetrics.density).roundToInt()
            val contrast = ColorUtils.calculateContrast(bitmap.getPixel(edgeX, center), backdrop)
            assertTrue("Unselected ${Integer.toHexString(color)} edge has $contrast:1 contrast", contrast >= MIN_CONTROL_CONTRAST)
            assertEquals("The ink tint stays unchanged", color, bitmap.getPixel(center, center))
            bitmap.recycle()
        }
    }

    private companion object {
        const val EDGE_INSET_DP = 1f
        const val MIN_CONTROL_CONTRAST = 3.0
    }
}
