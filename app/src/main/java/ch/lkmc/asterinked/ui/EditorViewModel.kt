package ch.lkmc.asterinked.ui

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import ch.lkmc.asterinked.R
import ch.lkmc.asterinked.document.DocumentProblem
import ch.lkmc.asterinked.document.DocumentService
import ch.lkmc.asterinked.document.Draft
import ch.lkmc.asterinked.document.OpenDocument
import ch.lkmc.asterinked.document.PageSpec
import ch.lkmc.asterinked.document.toProblem
import ch.lkmc.asterinked.ink.InkStroke
import java.util.concurrent.Executors

internal data class EditorState(
    val draft: Draft? = null,
    val pages: List<PageSpec> = emptyList(),
    val preview: Bitmap? = null,
    val busy: Boolean = true,
    val canRedo: Boolean = false,
    val message: String? = null,
)

internal class EditorViewModel(application: Application) : AndroidViewModel(application) {
    private val service = DocumentService(application)
    private val worker = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private val mutableState = MutableLiveData(EditorState())
    private val redo = mutableMapOf<Int, List<InkStroke>>()
    private var cleared = false
    private var restoring = true
    private var pendingPickerResult: (() -> Unit)? = null
    val state: LiveData<EditorState> = mutableState

    init {
        perform({ service.restore() }, completed = {
            restoring = false
            val pending = pendingPickerResult
            pendingPickerResult = null
            pending?.invoke()
        }) { document ->
            if (document == null) publish(EditorState(busy = false)) else show(document)
        }
    }

    fun open(uri: Uri) {
        if (restoring) {
            pendingPickerResult = { open(uri) }
            return
        }
        if (current.busy) return
        perform({ service.open(uri) }) {
            redo.clear()
            show(it)
        }
    }

    fun goToPage(page: Int) {
        val draft = current.draft ?: return
        if (current.busy || page !in current.pages.indices || page == draft.page) return
        val next = draft.copy(page = page)
        perform({ service.render(next).also { service.saveDraft(next) } }) {
            publish(current.copy(draft = next, preview = it, busy = false, canRedo = !redo[page].isNullOrEmpty()))
        }
    }

    fun addStroke(stroke: InkStroke) {
        val draft = current.draft ?: return
        if (current.busy || stroke.points.isEmpty()) return
        redo.remove(draft.page)
        changeInk(draft.copy(ink = draft.ink + (draft.page to (draft.ink[draft.page].orEmpty() + stroke))))
    }

    fun undo() {
        val draft = current.draft ?: return
        if (current.busy) return
        val strokes = draft.ink[draft.page].orEmpty()
        if (strokes.isEmpty()) return
        redo[draft.page] = redo[draft.page].orEmpty() + strokes.last()
        changeInk(draft.copy(ink = draft.ink + (draft.page to strokes.dropLast(1))))
    }

    fun redo() {
        val draft = current.draft ?: return
        if (current.busy) return
        val strokes = redo[draft.page].orEmpty()
        if (strokes.isEmpty()) return
        redo[draft.page] = strokes.dropLast(1)
        changeInk(draft.copy(ink = draft.ink + (draft.page to (draft.ink[draft.page].orEmpty() + strokes.last()))))
    }

    fun export(uri: Uri) {
        if (restoring) {
            pendingPickerResult = { export(uri) }
            return
        }
        val draft = current.draft ?: return
        if (current.busy) return
        val saved = draft.copy(savedInk = draft.ink)
        perform({ service.export(draft, uri); service.saveDraft(saved) }) {
            publish(current.copy(draft = saved, busy = false, message = text(R.string.pdf_saved)))
        }
    }

    fun acknowledgeMessage() {
        publish(current.copy(message = null))
    }

    private val current: EditorState get() = mutableState.value!!

    private fun show(document: OpenDocument) {
        publish(EditorState(document.draft, document.pages, document.preview, busy = false))
    }

    private fun changeInk(draft: Draft) {
        publish(current.copy(draft = draft, canRedo = !redo[draft.page].isNullOrEmpty()))
        // Snapshot immutable stroke lists before queuing durable, ordered draft writes.
        worker.execute {
            try {
                service.saveDraft(draft)
            } catch (error: Exception) {
                Log.w(TAG, "Draft could not be saved", error)
                main.post { if (!cleared) publish(current.copy(message = text(R.string.notes_not_saved))) }
            }
        }
    }

    private fun <T> perform(work: () -> T, completed: () -> Unit = {}, success: (T) -> Unit) {
        publish(current.copy(busy = true, message = null))
        worker.execute {
            val result = runCatching(work)
            main.post {
                if (cleared) return@post
                result.fold(success) { error ->
                    publish(current.copy(busy = false, message = messageFor(error)))
                }
                completed()
            }
        }
    }

    // Users see what went wrong and what to do; the raw exception goes to the log.
    private fun messageFor(error: Throwable): String {
        Log.w(TAG, "Document operation failed", error)
        return text(error.toProblem(DocumentProblem.UNEXPECTED).userMessage)
    }

    private fun text(id: Int): String = getApplication<Application>().getString(id)

    private fun publish(value: EditorState) {
        mutableState.value = value
    }

    override fun onCleared() {
        cleared = true
        worker.shutdown()
    }

    private companion object {
        const val TAG = "Asterinked"
    }
}

private val DocumentProblem.userMessage: Int get() = when (this) {
    DocumentProblem.SOURCE_UNREADABLE -> R.string.error_source_unreadable
    DocumentProblem.NOT_A_PDF -> R.string.error_not_a_pdf
    DocumentProblem.PASSWORD_PROTECTED -> R.string.error_password_protected
    DocumentProblem.EDITING_NOT_ALLOWED -> R.string.error_editing_not_allowed
    DocumentProblem.NO_PAGES -> R.string.error_no_pages
    DocumentProblem.DRAFT_UNREADABLE -> R.string.error_draft_unreadable
    DocumentProblem.DRAFT_NOT_SAVED -> R.string.error_draft_not_saved
    DocumentProblem.EXPORT_FAILED -> R.string.error_export_failed
    DocumentProblem.DESTINATION_UNWRITABLE -> R.string.error_destination_unwritable
    DocumentProblem.OUT_OF_SPACE -> R.string.error_out_of_space
    DocumentProblem.OUT_OF_MEMORY -> R.string.error_out_of_memory
    DocumentProblem.UNEXPECTED -> R.string.error_unexpected
}
