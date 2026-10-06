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
import ch.lkmc.asterinked.document.DocumentOperations
import ch.lkmc.asterinked.document.DocumentService
import ch.lkmc.asterinked.document.Draft
import ch.lkmc.asterinked.document.OpenDocument
import ch.lkmc.asterinked.document.PageSpec
import ch.lkmc.asterinked.document.toProblem
import ch.lkmc.asterinked.ink.InkHistory
import ch.lkmc.asterinked.ink.InkStroke
import java.io.File
import java.util.Collections
import java.util.IdentityHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.abs

internal data class EditorState(
    val draft: Draft? = null,
    val pages: List<PageSpec> = emptyList(),
    /** The visible page's render; null while it is still being rendered. */
    val preview: Bitmap? = null,
    val busy: Boolean = true,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val message: EditorMessage? = null,
    /** An annotated copy ready for the share sheet; acknowledge once handed over. */
    val shared: File? = null,
)

/** Something to tell the user once; [tone] says whether it went well. */
internal data class EditorMessage(val text: String, val tone: Tone)

internal class EditorViewModel internal constructor(
    application: Application,
    private val service: DocumentOperations,
    private val worker: ExecutorService,
) : AndroidViewModel(application) {
    constructor(application: Application) : this(application, DocumentService(application), Executors.newSingleThreadExecutor())

    private val main = Handler(Looper.getMainLooper())
    // Page the user is on, readable from the worker so queued renders of pages
    // already flipped past are skipped.
    private val visiblePage = AtomicInteger(NO_PAGE)
    private val unsavedDraft = AtomicReference<Draft?>()
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

    // Turns instantly: a prefetched preview shows at once; otherwise the page is
    // drawn blank with its ink until the render lands, and writing can start
    // right away. Nothing is disabled while a page renders.
    fun goToPage(page: Int) {
        val draft = current.draft ?: return
        if (current.busy || page !in current.pages.indices || page == draft.page) return
        val next = draft.copy(page = page)
        visiblePage.set(page)
        val preview = service.cachedPreview(next)
        publish(withHistory(current.copy(draft = next, preview = preview)))
        saveDraft(next)
        if (preview == null) renderVisible(next)
        prefetchAround(next)
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
        val saved = draft.copy(savedInk = draft.ink, destination = uri)
        perform({ service.export(draft, uri); service.saveDraft(saved) }, failed = { error ->
            // A retry of the remembered target that can no longer be written
            // is a dead end: forget it so the next Save asks for a new file.
            if (draft.destination == uri && error.toProblem(DocumentProblem.UNEXPECTED) == DocumentProblem.DESTINATION_UNWRITABLE) {
                publish(current.copy(draft = current.draft?.copy(destination = null)))
            }
        }) {
            publish(current.copy(draft = saved, busy = false, message = EditorMessage(text(R.string.pdf_saved), Tone.SUCCESS)))
        }
    }

    /**
     * Builds an annotated copy to share. Sharing does not count as exporting:
     * the notes stay marked unexported until they are saved to a file.
     */
    fun share() {
        val draft = current.draft ?: return
        if (current.busy) return
        perform({ service.share(draft) }) { publish(current.copy(busy = false, shared = it)) }
    }

    fun acknowledgeShare() {
        publish(current.copy(shared = null))
    }

    fun acknowledgeMessage() {
        publish(current.copy(message = null))
    }

    private val current: EditorState get() = mutableState.value!!

    private fun show(document: OpenDocument) {
        visiblePage.set(document.draft.page)
        publish(withHistory(EditorState(document.draft, document.pages, document.preview, busy = false)))
        prefetchAround(document.draft)
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
        saveDraft(draft)
    }

    // Each write replaces the whole draft file, so only the newest state matters.
    // Every change queues a write; whichever runs first persists the newest draft
    // (immutable snapshots) and the rest find nothing left to do. A burst of
    // strokes or page turns therefore costs one write, not one per change.
    private fun saveDraft(draft: Draft) {
        unsavedDraft.set(draft)
        worker.execute {
            val latest = unsavedDraft.getAndSet(null) ?: return@execute
            try {
                service.saveDraft(latest)
            } catch (error: Exception) {
                Log.w(TAG, "Draft could not be saved", error)
                main.post { if (!cleared) publish(current.copy(message = EditorMessage(text(R.string.notes_not_saved), Tone.ERROR))) }
            }
        }
    }

    private fun renderVisible(draft: Draft) {
        worker.execute {
            if (visiblePage.get() != draft.page) return@execute
            val result = runCatching { service.render(draft) }
            main.post {
                val shown = current.draft
                if (cleared || shown?.source != draft.source || shown.page != draft.page) return@post
                result.fold({ publish(current.copy(preview = it)) }) { publish(current.copy(message = messageFor(it))) }
            }
        }
    }

    // Renders the neighbours into the service's cache so the next turn is instant.
    // The next page goes last: in a cache with room for one preview it is the one
    // that survives, and turning forward is the common case. Failures stay silent
    // here; they surface if the user actually visits the page.
    private fun prefetchAround(draft: Draft) {
        for (page in listOf(draft.page - 1, draft.page + 1)) {
            if (page !in current.pages.indices) continue
            worker.execute {
                if (abs(page - visiblePage.get()) <= 1) runCatching { service.render(draft.copy(page = page)) }
            }
        }
    }

    private fun <T> perform(work: () -> T, completed: () -> Unit = {}, failed: (Throwable) -> Unit = {}, success: (T) -> Unit) {
        publish(current.copy(busy = true, message = null))
        worker.execute {
            val result = runCatching(work)
            main.post {
                if (cleared) return@post
                result.fold(success) { error ->
                    publish(current.copy(busy = false, message = messageFor(error)))
                    failed(error)
                }
                completed()
            }
        }
    }

    // Users see what went wrong and what to do; the raw exception goes to the log.
    private fun messageFor(error: Throwable): EditorMessage {
        Log.w(TAG, "Document operation failed", error)
        return EditorMessage(text(error.toProblem(DocumentProblem.UNEXPECTED).userMessage), Tone.ERROR)
    }

    private fun text(id: Int): String = getApplication<Application>().getString(id)

    private fun publish(value: EditorState) {
        mutableState.value = value
    }

    override fun onCleared() {
        cleared = true
        // Queued draft writes still run before the renderer is released.
        worker.execute { service.close() }
        worker.shutdown()
    }

    private companion object {
        const val TAG = "Asterinked"
        const val NO_PAGE = -1
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
