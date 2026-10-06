package ch.lkmc.asterinked.document

import android.system.ErrnoException
import android.system.OsConstants
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DocumentProblemTest {
    @Test fun identifiedProblemWinsOverTheStage() {
        val error = DocumentException(DocumentProblem.PASSWORD_PROTECTED)
        assertEquals(DocumentProblem.PASSWORD_PROTECTED, error.toProblem(DocumentProblem.NOT_A_PDF))
    }

    @Test fun exhaustedMemoryIsRecognizedAnywhereInTheCauseChain() {
        assertEquals(DocumentProblem.OUT_OF_MEMORY, OutOfMemoryError().toProblem(DocumentProblem.NOT_A_PDF))
        assertEquals(DocumentProblem.OUT_OF_MEMORY, IllegalStateException(OutOfMemoryError()).toProblem(DocumentProblem.NOT_A_PDF))
    }

    @Test fun fullStorageIsRecognizedAnywhereInTheCauseChain() {
        val full = IOException("write failed", ErrnoException("write", OsConstants.ENOSPC))
        assertEquals(DocumentProblem.OUT_OF_SPACE, full.toProblem(DocumentProblem.DESTINATION_UNWRITABLE))
        assertEquals(DocumentProblem.OUT_OF_SPACE, IOException("write failed: ENOSPC (No space left on device)").toProblem(DocumentProblem.DRAFT_NOT_SAVED))
    }

    @Test fun exhaustionWinsOverAnEarlierLabel() {
        val labelled = DocumentException(DocumentProblem.EXPORT_FAILED, IOException("write failed", ErrnoException("write", OsConstants.ENOSPC)))
        assertEquals(DocumentProblem.OUT_OF_SPACE, labelled.toProblem(DocumentProblem.UNEXPECTED))
    }

    @Test fun anythingElseBlamesTheStage() {
        assertEquals(DocumentProblem.DESTINATION_UNWRITABLE, IOException("EACCES").toProblem(DocumentProblem.DESTINATION_UNWRITABLE))
        assertEquals(DocumentProblem.NOT_A_PDF, IOException("Error: End-of-File, expected line").toProblem(DocumentProblem.NOT_A_PDF))
    }
}
