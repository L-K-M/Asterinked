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
import ch.lkmc.asterinked.document.OpenResult
import ch.lkmc.asterinked.document.PageSpec
import ch.lkmc.asterinked.ink.InkPoint
import ch.lkmc.asterinked.ink.InkStroke
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
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
class EditorViewModelRestoreTest {
    private val app = RuntimeEnvironment.getApplication()
    private val worker = QueueExecutor()

    @Test fun aDraftWhosePageFailsToRenderStillRestoresItsInk() {
        val documents = UnrenderableDocuments(failures = 1)
        val model = EditorViewModel(app, documents, worker)
        settle()

        val state = model.state.value!!
        assertEquals("The ink is restored", documents.ink, state.draft!!.ink)
        assertFalse(state.busy)
        assertNull("The page shows blank", state.preview)
        assertEquals(EditorMessage(app.getString(R.string.error_out_of_memory), Tone.ERROR), state.message)
    }

    @Test fun aPageThatRendersOnRetryReplacesTheBlankPage() {
        val documents = UnrenderableDocuments(failures = 0)
        val model = EditorViewModel(app, documents, worker)
        settle()

        val state = model.state.value!!
        assertNotNull(state.preview)
        assertSame(documents.cachedPreview(state.draft!!), state.preview)
        assertNull(state.message)
    }

    @Test fun aBrokenDraftIsReportedOnceNotOnEveryLaunch() {
        val documents = BrokenOnce()
        val first = EditorViewModel(app, documents, worker)
        settle()
        assertEquals(app.getString(R.string.error_draft_unreadable), first.state.value!!.message?.text)
        assertFalse(first.state.value!!.busy)

        // Next launch: the draft was set aside, so there is nothing to report.
        val second = EditorViewModel(app, documents, worker)
        settle()
        val state = second.state.value!!
        assertNull(state.message)
        assertNull(state.draft)
        assertFalse(state.busy)
    }

    private fun settle() {
        repeat(10) {
            worker.runAll()
            shadowOf(Looper.getMainLooper()).idle()
        }
    }

    /** Fails the first restore as a set-aside draft would; the next finds nothing. */
    private class BrokenOnce : DocumentOperations {
        private var failed = false

        override fun restore(): OpenDocument? {
            if (failed) return null
            failed = true
            throw DocumentException(DocumentProblem.DRAFT_UNREADABLE)
        }

        override fun open(uri: Uri, current: Draft?): OpenResult = throw UnsupportedOperationException()
        override fun render(draft: Draft): Bitmap = throw UnsupportedOperationException()
        override fun cachedPreview(draft: Draft): Bitmap? = null
        override fun saveDraft(draft: Draft) = Unit
        override fun export(draft: Draft, destination: Uri) = throw UnsupportedOperationException()
        override fun share(draft: Draft): File = throw UnsupportedOperationException()
        override fun adopt(document: OpenDocument) = throw UnsupportedOperationException()
        override fun discard(document: OpenDocument) = Unit
        override fun close() = Unit
    }

    /** Restores a draft without its preview; the first [failures] renders fail. */
    private class UnrenderableDocuments(private var failures: Int) : DocumentOperations {
        private val draft = Draft(File("big.pdf"), "big.pdf", ink = mapOf(0 to listOf(
            InkStroke(listOf(InkPoint(10f, 10f, 0.5f), InkPoint(20f, 20f, 0.5f)), 0, 2f),
        )))
        private val preview = Bitmap.createBitmap(4, 6, Bitmap.Config.ARGB_8888)
        private var rendered = false
        val ink get() = draft.ink

        override fun restore() = OpenDocument(draft, listOf(PageSpec(0f, 0f, 400f, 600f, 0)), preview = null)

        override fun render(draft: Draft): Bitmap {
            if (failures > 0) {
                failures--
                throw DocumentException(DocumentProblem.OUT_OF_MEMORY)
            }
            rendered = true
            return preview
        }

        override fun cachedPreview(draft: Draft): Bitmap? = preview.takeIf { rendered }
        override fun saveDraft(draft: Draft) = Unit
        override fun open(uri: Uri, current: Draft?): OpenResult = throw UnsupportedOperationException()
        override fun export(draft: Draft, destination: Uri) = throw UnsupportedOperationException()
        override fun share(draft: Draft): File = throw UnsupportedOperationException()
        override fun adopt(document: OpenDocument) = throw UnsupportedOperationException()
        override fun discard(document: OpenDocument) = Unit
        override fun close() = Unit
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
}
