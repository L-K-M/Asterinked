package ch.lkmc.asterinked.ui

import android.graphics.Bitmap
import android.net.Uri
import ch.lkmc.asterinked.document.DocumentException
import ch.lkmc.asterinked.document.DocumentOperations
import ch.lkmc.asterinked.document.DocumentProblem
import ch.lkmc.asterinked.document.Draft
import ch.lkmc.asterinked.document.OpenDocument
import ch.lkmc.asterinked.document.OpenResult
import ch.lkmc.asterinked.document.PageSpec
import java.io.File
import java.io.IOException
import java.util.concurrent.AbstractExecutorService
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.TimeUnit

/** Runs worker tasks only when a test says so, to decide exactly when they land. */
internal class QueueExecutor : AbstractExecutorService() {
    private val tasks = ArrayDeque<Runnable>()
    private var shutdown = false

    fun runAll() {
        while (tasks.isNotEmpty()) tasks.removeFirst().run()
    }

    override fun execute(command: Runnable) {
        if (shutdown) throw RejectedExecutionException("Worker is shut down")
        tasks.addLast(command)
    }

    override fun shutdown() {
        shutdown = true
    }

    override fun shutdownNow(): MutableList<Runnable> = tasks.toMutableList().also {
        shutdown = true
        tasks.clear()
    }
    override fun isShutdown() = shutdown
    override fun isTerminated() = shutdown && tasks.isEmpty()
    override fun awaitTermination(timeout: Long, unit: TimeUnit) = true
}

/** Restores a blank draft of [pageCount] pages and records renders, writes and opens. */
internal class FakeDocuments(pageCount: Int) : DocumentOperations {
    private val source = File("fake.pdf")
    private val cache = mutableMapOf<Int, Bitmap>()
    val pages = List(pageCount) { PageSpec(0f, 0f, 400f, 600f, 0) }
    val rendered = mutableListOf<Int>()
    val saved = mutableListOf<Draft>()
    val renderFailures = mutableSetOf<Int>()
    var failSaves = false
    val exportedTo = mutableListOf<Uri>()
    var failExports = false
    var closed = false
        private set

    /** What the next [open] finds; unset, opening fails. */
    var openResult: OpenResult? = null

    /** The current draft each [open] was handed. */
    val openedOver = mutableListOf<Draft?>()

    override fun restore(): OpenDocument {
        val draft = Draft(source, "fake.pdf")
        return OpenDocument(draft, pages, render(draft))
    }

    override fun render(draft: Draft): Bitmap {
        check(!closed)
        if (draft.page in renderFailures) throw IOException("Render failed")
        return cache.getOrPut(draft.page) {
            rendered += draft.page
            Bitmap.createBitmap(4, 6, Bitmap.Config.ARGB_8888)
        }
    }

    override fun cachedPreview(draft: Draft): Bitmap? = cache[draft.page]

    override fun saveDraft(draft: Draft) {
        check(!closed)
        if (failSaves) throw IOException("Save failed")
        saved += draft
    }

    override fun open(uri: Uri, current: Draft?): OpenResult {
        openedOver += current
        return openResult ?: throw UnsupportedOperationException()
    }

    override fun export(draft: Draft, destination: Uri) {
        if (failExports) throw DocumentException(DocumentProblem.DESTINATION_UNWRITABLE)
        exportedTo += destination
    }

    override fun share(draft: Draft): File = throw UnsupportedOperationException()
    override fun close() {
        closed = true
    }
}
