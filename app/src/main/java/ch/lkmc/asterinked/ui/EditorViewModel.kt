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
import androidx.lifecycle.SavedStateHandle
import ch.lkmc.asterinked.R
import ch.lkmc.asterinked.document.DocumentProblem
import ch.lkmc.asterinked.document.DocumentOperations
import ch.lkmc.asterinked.document.DocumentSession
import ch.lkmc.asterinked.document.Draft
import ch.lkmc.asterinked.document.OpenDocument
import ch.lkmc.asterinked.document.OpenResult
import ch.lkmc.asterinked.document.PageSpec
import ch.lkmc.asterinked.document.toProblem
import ch.lkmc.asterinked.ink.InkHistory
import ch.lkmc.asterinked.ink.InkStroke
import ch.lkmc.asterinked.ink.PageInk
import java.io.File
import java.util.Collections
import java.util.IdentityHashMap
import java.util.concurrent.ExecutorService
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
    /** Where the last export landed; acknowledge once its notice is read. */
    val exported: Uri? = null,
    /** Another PDF opened over unexported notes, waiting for the user to replace or keep them. */
    val replacing: OpenDocument? = null,
)

/** What a notice's action button does when the message offers one. */
internal enum class MessageAction { SAVE_COPY }

/** Something to tell the user once; [tone] says whether it went well. */
internal data class EditorMessage(val text: String, val tone: Tone, val action: MessageAction? = null)

internal class EditorViewModel private constructor(
    application: Application,
    private val session: DocumentSession,
    // Survives the process: the address of a PDF waiting for the replace question.
    private val saved: SavedStateHandle,
) : AndroidViewModel(application) {
    constructor(application: Application, saved: SavedStateHandle) : this(application, DocumentSession.process(application), saved)

    internal constructor(application: Application, service: DocumentOperations, worker: ExecutorService, saved: SavedStateHandle = SavedStateHandle()) :
        this(application, DocumentSession.owned(service, worker), saved)

    private val service = session.operations
    private val worker = session.worker
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
            // A PDF that waited for the replace question when the process ended
            // is opened again, unless the user has since picked something else.
            val waiting = saved.remove<Uri>(WAITING_KEY)
            (pending ?: waiting?.let { { open(it) } })?.invoke()
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
        // The newer request wins over a PDF still waiting for an answer.
        current.replacing?.let(::forget)
        // Read on the main thread; the worker only gets this snapshot.
        val shown = current.draft
        // Unexported notes give way only to a different PDF, and only once the
        // user agrees; asking before the import could not tell the two apart.
        val ask = shown?.dirty == true
        perform({
            service.open(uri, shown).also { if (it is OpenResult.Opened && !ask) service.adopt(it.document) }
        }) { result ->
            when (result) {
                is OpenResult.Opened -> if (ask) ask(uri, result.document) else replaceWith(result.document)
                is OpenResult.AlreadyOpen -> keep(result.draft)
            }
        }
    }

    /** Replaces the draft, and its unexported notes, with the PDF in [EditorState.replacing]. */
    fun replaceDraft() {
        val pending = current.replacing ?: return
        if (current.busy) return
        // Answered: perform() drops the question as the write starts, so a
        // failure reports its error without asking again.
        saved.remove<Uri>(WAITING_KEY)
        perform({ service.adopt(pending) }) { replaceWith(pending) }
    }

    /** Keeps the draft and drops the PDF that was waiting to replace it. */
    fun keepDraft() {
        val pending = current.replacing ?: return
        forget(pending)
        publish(current.copy(replacing = null))
    }

    private fun ask(uri: Uri, document: OpenDocument) {
        saved[WAITING_KEY] = uri
        publish(current.copy(busy = false, replacing = document))
    }

    private fun forget(pending: OpenDocument) {
        saved.remove<Uri>(WAITING_KEY)
        worker.execute { service.discard(pending) }
    }

    fun goToPage(page: Int) {
        val draft = current.draft ?: return
        if (current.busy || page !in current.pages.indices || page == draft.page) return
        showPage(draft.copy(page = page))
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

    /** Undoes the last edit in the document, turning to its page if needed. */
    fun undo() {
        val draft = current.draft ?: return
        if (current.busy) return
        history.undo(draft.page, draft.ink)?.let { applyHistory(draft, it) }
    }

    fun redo() {
        val draft = current.draft ?: return
        if (current.busy) return
        history.redo(draft.ink)?.let { applyHistory(draft, it) }
    }

    fun export(uri: Uri) {
        if (restoring) {
            pendingPickerResult = { export(uri) }
            return
        }
        val draft = current.draft ?: return
        if (current.busy) return
        val saved = draft.copy(savedInk = draft.ink, destination = uri)
        perform({ service.export(draft, uri) }, failed = { error ->
            // A retry of the remembered target that can no longer be written
            // is a dead end: forget it so the next Save asks for a new file.
            if (draft.destination == uri && error.toProblem(DocumentProblem.UNEXPECTED) == DocumentProblem.DESTINATION_UNWRITABLE) {
                val cleared = current.draft?.copy(destination = null)
                publish(current.copy(draft = cleared))
                // Persist the clearing too: a grant that outlives the file would
                // otherwise resurrect the dead target on the next restore.
                cleared?.let { saveDraft(it) }
            }
        }) {
            // The copy exists even if recording the draft fails below: mark it
            // exported anyway and let saveDraft report its own failure, rolling
            // the marker back so the notes stay flagged unbacked.
            publish(current.copy(draft = saved, busy = false, exported = uri))
            saveDraft(saved, draft.savedInk)
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

    fun acknowledgeExport() {
        publish(current.copy(exported = null))
    }

    fun acknowledgeMessage() {
        publish(current.copy(message = null))
    }

    private val current: EditorState get() = mutableState.value!!

    private fun show(document: OpenDocument) {
        visiblePage.set(document.draft.page)
        publish(withHistory(EditorState(document.draft, document.pages, document.preview, busy = false)))
        // A restored page that failed to render shows blank with its ink; try again.
        if (document.preview == null) renderVisible(document.draft)
        prefetchAround(document.draft)
    }

    private fun replaceWith(document: OpenDocument) {
        history.clear()
        show(document)
    }

    // The same PDF again, say tapped once more in Files: ink, page, preview and
    // undo history stay, and only the name follows the file.
    private fun keep(draft: Draft) {
        val renamed = draft.name != current.draft?.name
        val hasNotes = draft.ink.values.any { it.isNotEmpty() }
        val notice = EditorMessage(text(if (hasNotes) R.string.already_open_with_notes else R.string.already_open), Tone.INFO)
        publish(withHistory(current.copy(draft = draft, busy = false, message = notice)))
        if (renamed) saveDraft(draft)
    }

    // An edit on another page is shown on its page, so the user sees what changed.
    private fun applyHistory(draft: Draft, change: PageInk) {
        val edited = draft.copy(ink = draft.ink + (change.page to change.strokes))
        if (change.page == draft.page) changeInk(edited) else showPage(edited.copy(page = change.page))
    }

    // Turns instantly: a prefetched preview shows at once; otherwise the page is
    // drawn blank with its ink until the render lands, and writing can start
    // right away. Nothing is disabled while a page renders.
    private fun showPage(next: Draft) {
        visiblePage.set(next.page)
        val preview = service.cachedPreview(next)
        publish(withHistory(current.copy(draft = next, preview = preview)))
        saveDraft(next)
        if (preview == null) renderVisible(next)
        prefetchAround(next)
    }

    private fun edit(draft: Draft, before: List<InkStroke>, after: List<InkStroke>) {
        history.record(draft.page, before, after)
        changeInk(draft.copy(ink = draft.ink + (draft.page to after)))
    }

    private fun withHistory(state: EditorState): EditorState {
        val draft = state.draft ?: return state.copy(canUndo = false, canRedo = false)
        return state.copy(canUndo = history.canUndo(draft.page, draft.ink), canRedo = history.canRedo())
    }

    private fun changeInk(draft: Draft) {
        publish(withHistory(current.copy(draft = draft)))
        saveDraft(draft)
    }

    // Each write replaces the whole draft file, so only the newest state matters.
    // Every change queues a write; whichever runs first persists the newest draft
    // (immutable snapshots) and the rest find nothing left to do. A burst of
    // strokes or page turns therefore costs one write, not one per change.
    private fun saveDraft(draft: Draft, restoreSavedInk: Map<Int, List<InkStroke>>? = null) {
        unsavedDraft.set(draft)
        worker.execute {
            val latest = unsavedDraft.getAndSet(null) ?: return@execute
            try {
                service.saveDraft(latest)
            } catch (error: Exception) {
                Log.w(TAG, "Draft could not be saved", error)
                main.post {
                    if (cleared) return@post
                    // The file still holds the previous notes: put the marker back
                    // so the draft does not claim strokes that never reached it.
                    // A different open document is untouched — never blank it.
                    val restored = current.draft?.takeIf { it.source == latest.source }
                        ?.let { if (restoreSavedInk != null) it.copy(savedInk = restoreSavedInk) else it }
                    // Offer Save copy only when the failed draft is still on
                    // screen — otherwise the action would export the wrong file.
                    val action = if (restored != null) MessageAction.SAVE_COPY else null
                    publish(current.copy(draft = restored ?: current.draft,
                        message = EditorMessage(text(R.string.notes_not_saved), Tone.ERROR, action)))
                }
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
                val visible = visiblePage.get()
                if (visible == NO_PAGE || abs(page - visible) > 1) return@execute
                runCatching { service.render(draft.copy(page = page)) }
            }
        }
    }

    private fun <T> perform(work: () -> T, completed: () -> Unit = {}, failed: (Throwable) -> Unit = {}, success: (T) -> Unit) {
        // Work also ends a pending replace question, in the same step: idle
        // without a question is when the activity opens a PDF it holds.
        publish(current.copy(busy = true, message = null, replacing = null))
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
        val problem = error.toProblem(DocumentProblem.UNEXPECTED)
        // A draft-write failure leaves notes unbacked: offer the one action
        // that preserves them.
        val action = if (problem == DocumentProblem.DRAFT_NOT_SAVED) MessageAction.SAVE_COPY else null
        return EditorMessage(text(problem.userMessage), Tone.ERROR, action)
    }

    private fun text(id: Int): String = getApplication<Application>().getString(id)

    private fun publish(value: EditorState) {
        mutableState.value = value
    }

    override fun onCleared() {
        cleared = true
        visiblePage.set(NO_PAGE)
        // Leaving without an answer: the imported copy goes before the close.
        current.replacing?.let { pending -> worker.execute { service.discard(pending) } }
        session.close()
    }

    private companion object {
        const val TAG = "Asterinked"
        const val NO_PAGE = -1
        const val WAITING_KEY = "waitingPdf"
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
