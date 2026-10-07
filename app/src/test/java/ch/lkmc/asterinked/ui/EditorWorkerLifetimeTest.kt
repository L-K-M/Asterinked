package ch.lkmc.asterinked.ui

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Looper
import androidx.lifecycle.ViewModelStore
import ch.lkmc.asterinked.document.DocumentService
import ch.lkmc.asterinked.document.DocumentStore
import ch.lkmc.asterinked.document.Draft
import ch.lkmc.asterinked.document.OpenDocument
import ch.lkmc.asterinked.document.PageSpec
import ch.lkmc.asterinked.ink.InkPoint
import ch.lkmc.asterinked.ink.InkStroke
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(
    sdk = [35],
    shadows = [EditorWorkerLifetimeTest.ShadowDocuments::class],
    instrumentedPackages = ["ch.lkmc.asterinked.document"],
)
class EditorWorkerLifetimeTest {
    private val app get() = RuntimeEnvironment.getApplication()
    private val events = CopyOnWriteArrayList<String>()
    private val sessions = mutableListOf<Session>()
    private lateinit var directory: File
    private lateinit var source: File

    @Before fun seedDraft() {
        ShadowDocuments.next.clear()
        directory = File(app.filesDir, "documents").apply { deleteRecursively(); mkdirs() }
        source = File(directory, "first.pdf").apply { writeText("original source") }
        DocumentStore(app).saveDraft(Draft(source, source.name))
    }

    @After fun closeSessions() {
        sessions.forEach { it.documents.releaseRender.countDown() }
        sessions.forEach { it.documents.releaseClose.countDown() }
        sessions.forEach { it.owner.clear() }
        sessions.forEach { await(it.documents.closed, "Session did not close") }
        idleMain()
    }

    @Test fun nextSessionKeepsBothEditorsInkAfterQueuedWritesAndClose() {
        val first = editor("A", Prefetch.BLOCKED)
        await(first.documents.restored, "A did not restore")
        awaitReady(first)
        await(first.documents.renderStarted, "A's prefetch did not start")

        val firstStroke = stroke(10f)
        val secondStroke = stroke(20f)
        first.model.goToPage(EDITED_PAGE)
        first.model.addStroke(firstStroke)
        first.model.addStroke(secondStroke)
        val publications = mutableListOf<EditorState>()
        first.model.state.observeForever { publications += it }
        val clearedState = first.model.state.value
        val publicationCount = publications.size
        first.owner.clear()

        val second = editor("B")
        restoreSecondWhileFirstIsBlocked(first, second)
        val laterStroke = stroke(30f)
        second.model.addStroke(laterStroke)
        await(second.documents.saved, "B did not save its ink")
        first.documents.releaseRender.countDown()
        await(first.documents.closed, "A did not finish its queued work")
        idleMain()

        assertTrue("The restored source must survive", source.isFile)
        val restored = DocumentStore(app).restore()!!
        assertEquals(source, restored.source)
        assertEquals(listOf(firstStroke, secondStroke, laterStroke), restored.ink[EDITED_PAGE])
        assertEquals("A's pending snapshots still coalesce", 1, first.documents.saveCount)
        assertTrue("B restores only after A releases its service", events.indexOf("A:close") < events.indexOf("B:restore"))
        assertSame("Cleared render callbacks must not publish", clearedState, first.model.state.value)
        assertEquals("A must publish no cleared callbacks", publicationCount, publications.size)
    }

    @Test fun queuedOldSaveCannotPruneTheNextSessionsReplacementSource() {
        val first = editor("A", Prefetch.BLOCKED)
        await(first.documents.restored, "A did not restore")
        awaitReady(first)
        await(first.documents.renderStarted, "A's prefetch did not start")
        first.model.addStroke(stroke(10f))
        first.owner.clear()

        val second = editor("B")
        restoreSecondWhileFirstIsBlocked(first, second)
        second.model.open(Uri.parse("content://test/replacement.pdf"))
        await(second.documents.opened, "B did not open its replacement")
        awaitReady(second)
        val replacement = second.model.state.value!!.draft!!.source
        val laterStroke = stroke(30f)
        second.model.addStroke(laterStroke)
        await(second.documents.saved, "B did not save its ink")
        first.documents.releaseRender.countDown()
        await(first.documents.closed, "A did not finish its queued work")
        idleMain()

        assertTrue("A's late snapshot must not delete B's source", replacement.isFile)
        assertEquals("replacement source", replacement.readText())
        val restored = DocumentStore(app).restore()!!
        assertEquals(replacement, restored.source)
        assertEquals(listOf(laterStroke), restored.ink[0])
    }

    @Test fun nextSessionWaitsForTheClearedServicesClose() {
        val first = editor("A", closing = Closing.BLOCKED)
        await(first.documents.restored, "A did not restore")
        awaitReady(first)
        first.owner.clear()
        await(first.documents.closeStarted, "A did not start closing")

        val second = editor("B")
        assertFalse("B must wait for A's service close", second.documents.restored.await(EARLY_RESTORE_SECONDS, TimeUnit.SECONDS))
        first.documents.releaseClose.countDown()
        await(second.documents.restored, "B did not restore after A closed")
        awaitReady(second)
        assertTrue(events.indexOf("A:close") < events.indexOf("B:restore"))
    }

    private fun editor(name: String, prefetch: Prefetch = Prefetch.NORMAL, closing: Closing = Closing.NORMAL): Session {
        val documents = StoredDocuments(app, directory, name, events, prefetch, closing)
        ShadowDocuments.next.add(documents)
        val model = EditorViewModel(app)
        val owner = ViewModelStore().apply { put("editor", model) }
        return Session(model, owner, documents).also { sessions += it }
    }

    private fun restoreSecondWhileFirstIsBlocked(first: Session, second: Session) {
        // Independent workers let B edit first. A shared queue must finish A
        // before restoring B. Both schedules end with an on-disk assertion.
        if (!second.documents.restored.await(EARLY_RESTORE_SECONDS, TimeUnit.SECONDS)) {
            first.documents.releaseRender.countDown()
            await(second.documents.restored, "B did not restore after A finished")
        }
        awaitReady(second)
    }

    private fun idleMain() = shadowOf(Looper.getMainLooper()).idle()

    private fun awaitReady(session: Session) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(TIMEOUT_SECONDS)
        while (System.nanoTime() < deadline) {
            idleMain()
            if (!session.model.state.value!!.busy) return
            Thread.sleep(1)
        }
        throw AssertionError("Editor did not publish its completed operation")
    }

    private fun await(latch: CountDownLatch, message: String) {
        assertTrue(message, latch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS))
    }

    private fun stroke(x: Float) = InkStroke(listOf(InkPoint(x, 10f, 0.5f)), 0, 2f)

    private data class Session(val model: EditorViewModel, val owner: ViewModelStore, val documents: StoredDocuments)

    internal enum class Prefetch { NORMAL, BLOCKED }
    internal enum class Closing { NORMAL, BLOCKED }

    // Use the production constructor and worker lifetime, but stand in for the
    // device-only renderer. Persistence and source pruning use the real store.
    @Implements(value = DocumentService::class, isInAndroidSdk = false)
    internal class ShadowDocuments {
        private lateinit var documents: StoredDocuments

        @Implementation fun __constructor__(context: Context) {
            documents = next.remove()
        }

        @Implementation fun restore() = documents.restore()
        @Implementation fun open(uri: Uri) = documents.open()
        @Implementation fun render(draft: Draft) = documents.render(draft)
        @Implementation fun cachedPreview(draft: Draft) = documents.cachedPreview(draft)
        @Implementation fun saveDraft(draft: Draft) = documents.saveDraft(draft)
        @Implementation fun close() = documents.close()

        companion object {
            internal val next = LinkedBlockingQueue<StoredDocuments>()
        }
    }

    internal class StoredDocuments(
        context: Context,
        private val directory: File,
        private val name: String,
        private val events: MutableList<String>,
        private val prefetch: Prefetch,
        closing: Closing,
    ) {
        private val store = DocumentStore(context)
        private val pages = List(6) { PageSpec(0f, 0f, 400f, 600f, 0) }
        private val previews = ConcurrentHashMap<Int, Bitmap>()
        val restored = CountDownLatch(1)
        val opened = CountDownLatch(1)
        val saved = CountDownLatch(1)
        val renderStarted = CountDownLatch(1)
        val releaseRender = CountDownLatch(1)
        val closeStarted = CountDownLatch(1)
        val releaseClose = CountDownLatch(if (closing == Closing.BLOCKED) 1 else 0)
        val closed = CountDownLatch(1)
        var saveCount = 0
            private set

        fun restore(): OpenDocument {
            check(closed.count != 0L)
            events += "$name:restore"
            val draft = store.restore()!!
            return OpenDocument(draft, pages, render(draft)).also { restored.countDown() }
        }

        fun open(): OpenDocument {
            check(closed.count != 0L)
            val source = File(directory, "replacement.pdf").apply { writeText("replacement source") }
            val draft = Draft(source, source.name)
            store.saveDraft(draft)
            previews.clear()
            return OpenDocument(draft, pages, render(draft)).also { opened.countDown() }
        }

        fun render(draft: Draft): Bitmap {
            check(closed.count != 0L)
            if (prefetch == Prefetch.BLOCKED && draft.page == PREFETCH_PAGE) {
                renderStarted.countDown()
                check(releaseRender.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)) { "Blocked render was not released" }
            }
            return previews.getOrPut(draft.page) { Bitmap.createBitmap(4, 6, Bitmap.Config.ARGB_8888) }
        }

        fun cachedPreview(draft: Draft): Bitmap? = previews[draft.page]

        fun saveDraft(draft: Draft) {
            check(closed.count != 0L)
            store.saveDraft(draft)
            saveCount++
            events += "$name:save"
            saved.countDown()
        }

        fun close() {
            closeStarted.countDown()
            check(releaseClose.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)) { "Blocked close was not released" }
            events += "$name:close"
            previews.clear()
            closed.countDown()
        }
    }

    private companion object {
        const val PREFETCH_PAGE = 1
        const val EDITED_PAGE = 4
        const val EARLY_RESTORE_SECONDS = 1L
        const val TIMEOUT_SECONDS = 10L
    }
}
