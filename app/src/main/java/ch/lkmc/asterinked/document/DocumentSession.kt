package ch.lkmc.asterinked.document

import android.content.Context
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/** Owns one service; production sessions borrow the process's serial worker. */
internal class DocumentSession private constructor(
    val operations: DocumentOperations,
    val worker: ExecutorService,
    private val ownership: WorkerOwnership,
) {
    fun close() {
        // Drain this editor's writes and release its renderer before a later
        // editor restores. Closing a session must not stop the process worker.
        worker.execute { operations.close() }
        if (ownership == WorkerOwnership.SESSION) worker.shutdown()
    }

    private enum class WorkerOwnership { PROCESS, SESSION }

    companion object {
        private val processWorker by lazy { Executors.newSingleThreadExecutor() }

        fun process(context: Context) = DocumentSession(
            DocumentService(context.applicationContext), processWorker, WorkerOwnership.PROCESS,
        )

        /** An injected worker belongs to this session and drains on close. */
        fun owned(operations: DocumentOperations, worker: ExecutorService) = DocumentSession(
            operations, worker, WorkerOwnership.SESSION,
        )
    }
}
