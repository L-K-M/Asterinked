package ch.lkmc.asterinked.ui

import android.net.Uri
import android.os.Looper
import ch.lkmc.asterinked.R
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class EditorViewModelTest {
    @Test fun pickerResultDuringRestoreIsHandled() {
        val app = RuntimeEnvironment.getApplication()
        File(app.filesDir, "documents").deleteRecursively()
        val model = EditorViewModel(app)
        assertTrue(model.state.value!!.busy)
        // A failed import still proves the picker result was attempted after restoration.
        model.open(Uri.parse("content://missing-provider/document.pdf"))
        val deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(5)
        while (System.nanoTime() < deadline && model.state.value!!.message == null) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(10)
        }
        assertNotNull("Picker result was dropped during restoration", model.state.value!!.message)
        assertEquals("Users see guidance, not the raw exception", EditorMessage(app.getString(R.string.error_source_unreadable), Tone.ERROR), model.state.value!!.message)
        assertFalse(model.state.value!!.busy)
    }
}
