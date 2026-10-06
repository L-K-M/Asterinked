package ch.lkmc.asterinked.ui

import android.content.Intent
import android.net.Uri
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MainActivityIntentTest {
    private val app get() = RuntimeEnvironment.getApplication()
    private val missing = Uri.parse("content://missing-provider/incoming.pdf")

    @Before
    fun clearDraft() {
        File(app.filesDir, "documents").deleteRecursively()
    }

    @Test fun otherAppsCanOpenAndSharePdfsWithTheEditor() {
        val view = Intent(Intent.ACTION_VIEW).setDataAndType(missing, "application/pdf").setPackage(app.packageName)
        val send = Intent(Intent.ACTION_SEND).setType("application/pdf").setPackage(app.packageName)
        for (intent in listOf(view, send)) {
            val target = app.packageManager.resolveActivity(intent, 0)
            assertEquals(intent.action, MainActivity::class.java.name, target?.activityInfo?.name)
        }
    }

    @Test fun anIncomingPdfIsOpenedOnceTheEditorIsIdle() {
        val intent = Intent(app, MainActivity::class.java).setAction(Intent.ACTION_VIEW).setDataAndType(missing, "application/pdf")
        Robolectric.buildActivity(MainActivity::class.java, intent).setup().use {
            // The missing provider makes the import fail, which proves an open was attempted.
            assertNotNull("The incoming PDF was not opened", awaitNotice(it.get()))
        }
    }

    @Test fun aRelaunchFromRecentsDoesNotReplayTheOldPdf() {
        val intent = Intent(app, MainActivity::class.java).setAction(Intent.ACTION_VIEW).setDataAndType(missing, "application/pdf")
            .addFlags(Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY)
        Robolectric.buildActivity(MainActivity::class.java, intent).setup().use {
            assertNull(awaitNotice(it.get(), timeoutMillis = 1_500))
        }
    }

    private fun awaitNotice(activity: MainActivity, timeoutMillis: Long = 5_000): CharSequence? {
        val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis)
        while (System.nanoTime() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            notice(activity.window.decorView)?.shown?.let { return it }
            Thread.sleep(10)
        }
        return null
    }

    private fun notice(view: View): NoticeBar? = when (view) {
        is NoticeBar -> view
        is ViewGroup -> (0 until view.childCount).firstNotNullOfOrNull { notice(view.getChildAt(it)) }
        else -> null
    }

}
