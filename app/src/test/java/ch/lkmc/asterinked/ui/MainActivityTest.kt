package ch.lkmc.asterinked.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.widget.TextView
import ch.lkmc.asterinked.R
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MainActivityTest {
    @Test fun launchesWithDocumentPickerAction() {
        File(RuntimeEnvironment.getApplication().filesDir, "documents").deleteRecursively()
        Robolectric.buildActivity(MainActivity::class.java).setup().use { controller ->
            val activity = controller.get()
            val root = activity.window.decorView
            root.measure(View.MeasureSpec.makeMeasureSpec(411, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(891, View.MeasureSpec.EXACTLY))
            root.layout(0, 0, 411, 891)
            assertTrue(descendants(root).filterIsInstance<TextView>().any { it.text.toString() == activity.getString(R.string.open_pdf) })
            val image = Bitmap.createBitmap(411, 891, Bitmap.Config.ARGB_8888)
            root.draw(Canvas(image))
            val output = File("build/reports/launch.png").apply { parentFile!!.mkdirs() }
            output.outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }

    private fun descendants(view: View): Sequence<View> = sequence {
        yield(view)
        if (view is android.view.ViewGroup) for (index in 0 until view.childCount) yieldAll(descendants(view.getChildAt(index)))
    }
}
