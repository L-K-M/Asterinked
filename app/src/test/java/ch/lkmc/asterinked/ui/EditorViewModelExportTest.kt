package ch.lkmc.asterinked.ui

import android.graphics.Bitmap
import android.net.Uri
import android.os.Looper
import ch.lkmc.asterinked.R
import ch.lkmc.asterinked.document.DocumentOperations
import ch.lkmc.asterinked.document.Draft
import ch.lkmc.asterinked.document.OpenDocument
import ch.lkmc.asterinked.document.PageSpec
import ch.lkmc.asterinked.ink.InkPoint
import ch.lkmc.asterinked.ink.InkStroke
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File
import java.io.IOException
import java.util.concurrent.AbstractExecutorService
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class EditorViewModelExportTest {
    private val worker = QueueExecutor()
    private val documents = FakeDocuments()
    private val app = RuntimeEnvironment.getApplication()
    private val model = EditorViewModel(app, documents, worker)
    private val state get() = model.state.value!!
    private val destination: Uri = Uri.parse("content://test/saved.pdf")

    @Test fun anExportMarksTheNotesAndOffersTheCopyBack() {
        settle()
        model.addStroke(stroke())
        settle()

        model.export(destination)
        settle()

        assertEquals(destination, state.exported)
        assertNull(state.message)
        assertFalse(state.busy)
        assertEquals("The draft knows the ink reached the file", state.draft!!.ink, state.draft!!.savedInk)

        model.acknowledgeExport()
        assertNull(state.exported)
    }

    @Test fun aFailedExportReportsAnErrorAndOffersNothing() {
        settle()
        documents.failExport = true

        model.export(destination)
        settle()

        assertNull(state.exported)
        assertEquals(Tone.ERROR, state.message!!.tone)
        assertFalse(state.busy)
    }

    // The copy was already written when the bookkeeping write fails: the save
    // must still read as a success, with the write error on top.
    @Test fun aFailedDraftWriteDoesNotUnsayTheExport() {
        settle()
        model.addStroke(stroke())
        settle()
        documents.failWrites = true

        model.export(destination)
        settle()

        assertEquals(destination, state.exported)
        assertEquals(app.getString(R.string.notes_not_saved), state.message!!.text)
        assertEquals(MessageAction.SAVE_COPY, state.message!!.action)
        // The file never got the strokes, so the marker rolls back: the draft
        // stays dirty and a later write or export still has them to send.
        assertTrue("The draft keeps flagging the unbacked notes", state.draft!!.dirty)
    }

    private fun stroke() = InkStroke(listOf(InkPoint(10f, 10f, 0.5f), InkPoint(20f, 20f, 0.5f)), 0, 2f)

    private fun idleMain() = shadowOf(Looper.getMainLooper()).idle()

    private fun settle() {
        repeat(10) {
            worker.runAll()
            idleMain()
        }
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

    private class FakeDocuments : DocumentOperations {
        private val source = File("fake.pdf")
        private val pages = listOf(PageSpec(0f, 0f, 400f, 600f, 0))
        private val cache = mutableMapOf<Int, Bitmap>()
        var failWrites = false
        var failExport = false

        override fun restore(): OpenDocument {
            val draft = Draft(source, "fake.pdf")
            return OpenDocument(draft, pages, render(draft))
        }

        override fun render(draft: Draft): Bitmap = cache.getOrPut(draft.page) {
            Bitmap.createBitmap(4, 6, Bitmap.Config.ARGB_8888)
        }

        override fun cachedPreview(draft: Draft): Bitmap? = cache[draft.page]

        override fun saveDraft(draft: Draft) {
            if (failWrites) throw IOException("disk full")
        }

        override fun open(uri: Uri): OpenDocument = throw UnsupportedOperationException()

        override fun export(draft: Draft, destination: Uri) {
            if (failExport) throw IOException("destination went away")
        }

        override fun share(draft: Draft): File = throw UnsupportedOperationException()
        override fun close() = Unit
    }
}
