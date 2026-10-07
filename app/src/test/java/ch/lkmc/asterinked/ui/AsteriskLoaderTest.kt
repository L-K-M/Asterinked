package ch.lkmc.asterinked.ui

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.view.View
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AsteriskLoaderTest {

    @Test
    fun theLoopTicksOnlyWhileOnScreen() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val loader = AsteriskLoader(activity)
        assertFalse(loader.animating)

        activity.setContentView(loader)
        assertTrue(loader.animating)

        loader.visibility = View.GONE
        assertFalse(loader.animating)
        loader.visibility = View.VISIBLE
        assertTrue(loader.animating)
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun theGhostKeepsTheMarkVisibleBeforeTheFirstFrame() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val loader = AsteriskLoader(activity)
        activity.setContentView(loader)
        loader.layout(0, 0, 300, 300)

        val frame = Bitmap.createBitmap(300, 300, Bitmap.Config.ARGB_8888)
        loader.draw(Canvas(frame))

        // Every arm passes through the middle, so even a frame captured before
        // the animator's first tick shows the ghost of the mark there.
        assertTrue(Color.alpha(frame.getPixel(150, 150)) > 0)
    }
}
