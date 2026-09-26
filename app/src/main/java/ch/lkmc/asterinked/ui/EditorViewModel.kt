package ch.lkmc.asterinked.ui

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import ch.lkmc.asterinked.document.DocumentService
import ch.lkmc.asterinked.document.Draft
import ch.lkmc.asterinked.document.OpenDocument
import ch.lkmc.asterinked.document.PageSpec
import ch.lkmc.asterinked.ink.InkHistory
import ch.lkmc.asterinked.ink.InkStroke
import java.util.Collections
import java.util.IdentityHashMap
import java.util.concurrent.Executors

internal data class EditorState(
    val draft: Draft? = null,
    val pages: List<PageSpec> = emptyList(),
    val preview: Bitmap? = null,
    val busy: Boolean = true,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val message: String? = null,
)

internal class EditorViewModel(application: Application) : AndroidViewModel(application) {
    private val service = DocumentService(application)
    private val worker = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private val mutableState = MutableLiveData(EditorState())
    private val history = InkHistory()
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
            history.clear()
            show(it)
        }
    }

    fun goToPage(page: Int) {
        val draft = current.draft ?: return
        if (current.busy || page !in current.pages.indices || page == draft.page) return
        val next = draft.copy(page = page)
        perform({ service.render(next).also { service.saveDraft(next) } }) {
            publish(withHistory(current.copy(draft = next, preview = it, busy = false)))
        }
    }

    fun addStroke(stroke: InkStroke) {
        val draft = current.draft ?: return
        if (current.busy || stroke.points.isEmpty()) return
        val strokes = draft.ink[draft.page].orEmpty()
        edit(draft, strokes, strokes + stroke)
    }

    /** Removes whole strokes from the visible page as one undoable edit. */
    fun eraseStrokes(erased: Collection<InkStroke>) {
        val draft = current.draft ?: return
        if (current.busy || erased.isEmpty()) return
        // The view hands back the instances it drew; identity keeps an equal copy of a stroke.
        val gone = Collections.newSetFromMap(IdentityHashMap<InkStroke, Boolean>()).apply { addAll(erased) }
        val strokes = draft.ink[draft.page].orEmpty()
        val remaining = strokes.filterNot { it in gone }
        if (remaining.size != strokes.size) edit(draft, strokes, remaining)
    }

    fun undo() {
        val draft = current.draft ?: return
        if (current.busy) return
        val strokes = history.undo(draft.page, draft.ink[draft.page].orEmpty()) ?: return
        changeInk(draft.copy(ink = draft.ink + (draft.page to strokes)))
    }

    fun redo() {
        val draft = current.draft ?: return
        if (current.busy) return
        val strokes = history.redo(draft.page, draft.ink[draft.page].orEmpty()) ?: return
        changeInk(draft.copy(ink = draft.ink + (draft.page to strokes)))
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
            publish(current.copy(draft = saved, busy = false, message = "PDF saved."))
        }
    }

    fun acknowledgeMessage() {
        publish(current.copy(message = null))
    }

    private val current: EditorState get() = mutableState.value!!

    private fun show(document: OpenDocument) {
        publish(withHistory(EditorState(document.draft, document.pages, document.preview, busy = false)))
    }

    private fun edit(draft: Draft, before: List<InkStroke>, after: List<InkStroke>) {
        history.record(draft.page, before, after)
        changeInk(draft.copy(ink = draft.ink + (draft.page to after)))
    }

    private fun withHistory(state: EditorState): EditorState {
        val draft = state.draft ?: return state.copy(canUndo = false, canRedo = false)
        return state.copy(canUndo = history.canUndo(draft.page, draft.ink[draft.page].orEmpty()), canRedo = history.canRedo(draft.page))
    }

    private fun changeInk(draft: Draft) {
        publish(withHistory(current.copy(draft = draft)))
        // Snapshot immutable stroke lists before queuing durable, ordered draft writes.
        worker.execute {
            try {
                service.saveDraft(draft)
            } catch (error: Exception) {
                main.post { if (!cleared) publish(current.copy(message = "Draft could not be saved. Save a PDF copy now.")) }
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
                    publish(current.copy(busy = false, message = error.message ?: "Could not complete this PDF operation."))
                }
                completed()
            }
        }
    }

    private fun publish(value: EditorState) {
        mutableState.value = value
    }

    override fun onCleared() {
        cleared = true
        worker.shutdown()
    }
}
