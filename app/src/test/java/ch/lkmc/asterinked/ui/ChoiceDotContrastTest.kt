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
        assertVisibleEdges(R.string.yellow to Color.rgb(255, 228, 92), R.string.pink to Color.rgb(255, 168, 207))
    }

    @Test @Config(qualifiers = "night-xhdpi")
    fun darkPenChoicesHaveVisibleUnselectedEdges() {
        assertVisibleEdges(R.string.black to Color.rgb(25, 38, 46), R.string.blue to Color.rgb(32, 85, 184), R.string.red to Color.rgb(179, 47, 61))
    }

    private fun assertVisibleEdges(vararg swatches: Pair<Int, Int>) {
        val context = RuntimeEnvironment.getApplication()
        val ui = Components(context)
        val backdrop = context.getColor(R.color.surface)
        val size = ui.dp(Size.TOUCH)
        val center = size / 2

        for ((label, color) in swatches) {
            val choice = ui.choice(label, 0) {}.apply {
                fill = color
                layout(0, 0, size, size)
            }
            val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888).apply { eraseColor(backdrop) }
            choice.draw(Canvas(bitmap))

            // Pixel centres are half-integral: mirror indices, not distances,
            // to sample the same fully-covered edge on all four sides.
            val near = center + ((choice.radius - EDGE_INSET_DP) * context.resources.displayMetrics.density).roundToInt()
            val far = size - 1 - near
            for ((x, y) in listOf(near to center, far to center, center to near, center to far)) {
                val contrast = ColorUtils.calculateContrast(bitmap.getPixel(x, y), backdrop)
                assertTrue("Unselected ${context.getString(label)} edge at ($x,$y) has $contrast:1 contrast", contrast >= MIN_CONTROL_CONTRAST)
            }
            assertEquals("The ink tint stays unchanged", color, bitmap.getPixel(center, center))
            bitmap.recycle()
        }
    }

    private companion object {
        const val EDGE_INSET_DP = 1f
        const val MIN_CONTROL_CONTRAST = 3.0
    }
}
