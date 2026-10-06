package ch.lkmc.asterinked.ui

import android.graphics.Bitmap
import android.net.Uri
import android.os.Looper
import ch.lkmc.asterinked.R
import ch.lkmc.asterinked.document.DocumentException
import ch.lkmc.asterinked.document.DocumentOperations
import ch.lkmc.asterinked.document.DocumentProblem
import ch.lkmc.asterinked.document.Draft
import ch.lkmc.asterinked.document.OpenDocument
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File
import java.util.concurrent.AbstractExecutorService
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class EditorViewModelTest {
    @Test fun pickerResultDuringRestoreIsHandled() {
        val app = RuntimeEnvironment.getApplication()
        val worker = QueueExecutor()
        val model = EditorViewModel(app, UnreadableDocuments(), worker)
        assertTrue(model.state.value!!.busy)

        model.open(Uri.parse("content://missing-provider/document.pdf"))
        worker.runAll() // restore
        shadowOf(Looper.getMainLooper()).idle() // restore lands; the queued open runs
        worker.runAll() // open fails
        shadowOf(Looper.getMainLooper()).idle()

        val state = model.state.value!!
        assertEquals("Users see guidance, not the raw exception",
            EditorMessage(app.getString(R.string.error_source_unreadable), Tone.ERROR), state.message)
        assertFalse(state.busy)
    }

    private class QueueExecutor : AbstractExecutorService() {
        private val tasks = ArrayDeque<Runnable>()
        private var shutdown = false

        fun runAll() {
            while (tasks.isNotEmpty()) tasks.removeFirst().run()
        }

        override fun execute(command: Runnable) {
            tasks.addLast(command)
        }

        override fun shutdown() {
            shutdown = true
        }

        override fun shutdownNow(): MutableList<Runnable> = tasks.toMutableList().also { shutdown = true }
        override fun isShutdown() = shutdown
        override fun isTerminated() = shutdown && tasks.isEmpty()
        override fun awaitTermination(timeout: Long, unit: TimeUnit) = true
    }

    /** No draft to restore; every open fails as an unreadable source. */
    private class UnreadableDocuments : DocumentOperations {
        override fun restore(): OpenDocument? = null
        override fun open(uri: Uri): OpenDocument = throw DocumentException(DocumentProblem.SOURCE_UNREADABLE)
        override fun render(draft: Draft): Bitmap = throw UnsupportedOperationException()
        override fun cachedPreview(draft: Draft): Bitmap? = null
        override fun saveDraft(draft: Draft) = Unit
        override fun export(draft: Draft, destination: Uri) = throw UnsupportedOperationException()
        override fun share(draft: Draft): File = throw UnsupportedOperationException()
        override fun close() = Unit
    }
}
