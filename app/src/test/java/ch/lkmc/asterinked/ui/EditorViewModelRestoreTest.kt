package ch.lkmc.asterinked.ui

import android.graphics.Bitmap
import android.net.Uri
import ch.lkmc.asterinked.R
import ch.lkmc.asterinked.document.DocumentException
import ch.lkmc.asterinked.document.DocumentOperations
import ch.lkmc.asterinked.document.DocumentProblem
import ch.lkmc.asterinked.document.Draft
import ch.lkmc.asterinked.document.OpenDocument
import ch.lkmc.asterinked.document.PageSpec
import ch.lkmc.asterinked.ink.InkPoint
import ch.lkmc.asterinked.ink.InkStroke
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import android.os.Looper
import java.io.File
import java.util.concurrent.AbstractExecutorService
import java.util.concurrent.TimeUnit

/** B8 at the screen level: the error is shown once, and a missing preview is retried. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class EditorViewModelRestoreTest {

    @Test
    fun aBrokenDraftIsReportedOnceNotOnEveryLaunch() {
        val app = RuntimeEnvironment.getApplication()
        val documents = FlakyRestore()
        val first = EditorViewModel(app, documents, InlineExecutor())
        settle()
        assertEquals(app.getString(R.string.error_draft_unreadable), first.state.value!!.message?.text)
        assertFalse(first.state.value!!.busy)

        // Next launch: the draft was set aside, so there is nothing to complain about.
        val second = EditorViewModel(app, documents, InlineExecutor())
        settle()
        assertNull(second.state.value!!.message)
        assertNull(second.state.value!!.draft)
        assertFalse(second.state.value!!.busy)
    }

    @Test
    fun aRestoreWithoutPreviewRendersLive() {
        val documents = UnrenderedRestore()
        val model = EditorViewModel(RuntimeEnvironment.getApplication(), documents, InlineExecutor())
        settle()

        val state = model.state.value!!
        assertNotNull(state.draft)
        assertEquals("The ink survived the failed restore render", 1, state.draft!!.ink.getValue(0).size)
        assertNotNull("The preview was retried after restoring", state.preview)
    }

    private fun settle() = shadowOf(Looper.getMainLooper()).idle()

    private class InlineExecutor : AbstractExecutorService() {
        private var shutdown = false
        override fun execute(command: Runnable) = command.run()
        override fun shutdown() { shutdown = true }
        override fun shutdownNow(): MutableList<Runnable> { shutdown = true; return mutableListOf() }
        override fun isShutdown() = shutdown
        override fun isTerminated() = shutdown
        override fun awaitTermination(timeout: Long, unit: TimeUnit) = true
    }

    /** Fails the first restore like a quarantined draft; the next one finds nothing. */
    private class FlakyRestore : DocumentOperations {
        private var failed = false
        override fun restore(): OpenDocument? {
            if (failed) return null
            failed = true
            throw DocumentException(DocumentProblem.DRAFT_UNREADABLE)
        }
        override fun open(uri: Uri): OpenDocument = throw UnsupportedOperationException()
        override fun render(draft: Draft): Bitmap = throw UnsupportedOperationException()
        override fun cachedPreview(draft: Draft): Bitmap? = null
        override fun saveDraft(draft: Draft) = Unit
        override fun export(draft: Draft, destination: Uri) = throw UnsupportedOperationException()
        override fun share(draft: Draft): File = throw UnsupportedOperationException()
        override fun close() = Unit
    }

    /** Restores a document whose preview failed, as a device OOM during restore would. */
    private class UnrenderedRestore : DocumentOperations {
        private val source = File("fake.pdf")
        private val pages = listOf(PageSpec(0f, 0f, 400f, 600f, 0))
        private val ink = mapOf(0 to listOf(InkStroke(listOf(InkPoint(1f, 1f, 1f), InkPoint(9f, 9f, 1f)), 1, 2f)))
        override fun restore(): OpenDocument = OpenDocument(Draft(source, "fake.pdf", ink = ink), pages, null)
        override fun render(draft: Draft): Bitmap = Bitmap.createBitmap(4, 6, Bitmap.Config.ARGB_8888)
        override fun cachedPreview(draft: Draft): Bitmap? = null
        override fun open(uri: Uri): OpenDocument = throw UnsupportedOperationException()
        override fun saveDraft(draft: Draft) = Unit
        override fun export(draft: Draft, destination: Uri) = throw UnsupportedOperationException()
        override fun share(draft: Draft): File = throw UnsupportedOperationException()
        override fun close() = Unit
    }
}
