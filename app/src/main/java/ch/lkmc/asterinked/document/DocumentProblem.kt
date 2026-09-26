package ch.lkmc.asterinked.document

import android.system.ErrnoException
import android.system.OsConstants
import java.io.IOException

/** Why a document operation failed, in terms a user can act on. */
internal enum class DocumentProblem {
    SOURCE_UNREADABLE,
    NOT_A_PDF,
    PASSWORD_PROTECTED,
    EDITING_NOT_ALLOWED,
    NO_PAGES,
    DRAFT_UNREADABLE,
    DRAFT_NOT_SAVED,
    EXPORT_FAILED,
    DESTINATION_UNWRITABLE,
    OUT_OF_SPACE,
    OUT_OF_MEMORY,
    UNEXPECTED,
}

internal class DocumentException(val problem: DocumentProblem, cause: Throwable? = null) : IOException(problem.name, cause)

/**
 * Keeps a problem that was already identified, recognizes exhausted memory or
 * storage anywhere in the cause chain, and otherwise blames [stage]: the step
 * that was running when the failure happened.
 */
internal fun Throwable.toProblem(stage: DocumentProblem): DocumentProblem = when {
    this is DocumentException -> problem
    causes().any { it is OutOfMemoryError } -> DocumentProblem.OUT_OF_MEMORY
    causes().any(::isOutOfSpace) -> DocumentProblem.OUT_OF_SPACE
    else -> stage
}

private fun Throwable.causes(): Sequence<Throwable> = generateSequence(this) { it.cause }.take(MAX_CAUSES)

private fun isOutOfSpace(error: Throwable): Boolean =
    (error is ErrnoException && error.errno == OsConstants.ENOSPC) || error.message?.contains("ENOSPC") == true

private const val MAX_CAUSES = 16
