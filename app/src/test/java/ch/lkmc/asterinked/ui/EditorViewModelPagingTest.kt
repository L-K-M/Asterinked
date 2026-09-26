package ch.lkmc.asterinked.ui

import android.graphics.Bitmap
import android.net.Uri
import android.os.Looper
import ch.lkmc.asterinked.document.DocumentOperations
import ch.lkmc.asterinked.document.Draft
import ch.lkmc.asterinked.document.OpenDocument
import ch.lkmc.asterinked.document.PageSpec
import ch.lkmc.asterinked.ink.InkPoint
import ch.lkmc.asterinked.ink.InkStroke
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
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
class EditorViewModelPagingTest {
    private val worker = QueueExecutor()
    private val documents = FakeDocuments(pageCount = 8)
    private val model = EditorViewModel(RuntimeEnvironment.getApplication(), documents, worker)
    private val state get() = model.state.value!!

    @Test fun neighboursArePrefetchedSoTheNextTurnIsImmediate() {
        settle()
        assertEquals(listOf(0, 1), documents.rendered)

        model.goToPage(1)

        assertEquals(1, state.draft!!.page)
        assertSame(documents.cachedPreview(state.draft!!), state.preview)
        assertFalse(state.busy)
    }

    @Test fun anUnrenderedPageShowsAtOnceAndKeepsTheEditorUsable() {
        settle()

        model.goToPage(5)

        assertEquals(5, state.draft!!.page)
        assertNull("The page shows blank while it renders", state.preview)
        assertFalse("Nothing is disabled while a page renders", state.busy)
        model.addStroke(stroke())
        assertEquals("Writing works before the render lands", 1, state.draft!!.ink[5]!!.size)

        settle()
        assertSame(documents.cachedPreview(state.draft!!), state.preview)
    }

    @Test fun pagesFlippedPastAreNeitherRenderedNorSaved() {
        settle()

        model.goToPage(5)
        model.goToPage(7)
        settle()

        assertFalse("Stale page render was skipped", 5 in documents.rendered)
        assertFalse("Stale neighbour prefetch was skipped", 4 in documents.rendered)
        assertTrue(7 in documents.rendered && 6 in documents.rendered)
        assertSame(documents.cachedPreview(state.draft!!), state.preview)
        assertEquals("Two quick turns cost one draft write", listOf(7), documents.saved.map { it.page })
    }

    @Test fun aRenderThatLandsAfterTheUserMovedOnIsDropped() {
        settle()
        model.goToPage(3)
        worker.runAll() // renders page 3 and prefetches 4; the result is posted, not yet delivered

        model.goToPage(4)
        idleMain()

        assertEquals(4, state.draft!!.page)
        assertSame(documents.cachedPreview(state.draft!!), state.preview)
    }

    @Test fun aBurstOfStrokesCostsOneDraftWriteWithTheNewestInk() {
        settle()
        documents.saved.clear()

        repeat(3) { model.addStroke(stroke()) }
        worker.runAll()

        assertEquals(1, documents.saved.size)
        assertEquals(3, documents.saved.single().ink[0]!!.size)
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

    private class FakeDocuments(pageCount: Int) : DocumentOperations {
        private val source = File("fake.pdf")
        private val pages = List(pageCount) { PageSpec(0f, 0f, 400f, 600f, 0) }
        private val cache = mutableMapOf<Int, Bitmap>()
        val rendered = mutableListOf<Int>()
        val saved = mutableListOf<Draft>()

        override fun restore(): OpenDocument {
            val draft = Draft(source, "fake.pdf")
            return OpenDocument(draft, pages, render(draft))
        }

        override fun render(draft: Draft): Bitmap = cache.getOrPut(draft.page) {
            rendered += draft.page
            Bitmap.createBitmap(4, 6, Bitmap.Config.ARGB_8888)
        }

        override fun cachedPreview(draft: Draft): Bitmap? = cache[draft.page]

        override fun saveDraft(draft: Draft) {
            saved += draft
        }

        override fun open(uri: Uri): OpenDocument = throw UnsupportedOperationException()
        override fun export(draft: Draft, destination: Uri) = throw UnsupportedOperationException()
        override fun share(draft: Draft): File = throw UnsupportedOperationException()
        override fun close() = Unit
    }
}
