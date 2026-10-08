package ch.lkmc.asterinked.document

import android.util.AtomicFile
import ch.lkmc.asterinked.ink.InkPoint
import ch.lkmc.asterinked.ink.InkStroke
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29, 35])
class DocumentStoreCommitTest {
    private val app get() = RuntimeEnvironment.getApplication()
    private val directory get() = File(app.filesDir, "documents")

    @Before
    fun clearDraft() {
        directory.deleteRecursively()
        directory.mkdirs()
    }

    @Test
    fun partialWriteFailure_reportsFailureAndRestoresPreviousDraft() = failedReplacement(Fault.WRITE)

    @Test
    fun syncFailure_reportsFailureAndRestoresPreviousDraft() = failedReplacement(Fault.SYNC)

    @Test
    fun closeFailure_reportsFailureAndRestoresPreviousDraft() = failedReplacement(Fault.CLOSE)

    @Test
    fun renameFailure_reportsFailureAndRestoresPreviousDraft() = failedReplacement(Fault.RENAME)

    @Test
    fun syncFailureAfterBackupRecovery_restoresPreviousDraft() = failedReplacement(Fault.SYNC) {
        interruptLegacyWrite()
    }

    @Test
    fun backupRecoveryRenameFailure_reportsFailureAndRestoresPreviousDraft() = failedReplacement(Fault.RENAME) {
        interruptLegacyWrite()
    }

    @Test
    fun successfulReplacement_restoresNewDraftAndPrunesPreviousSource() {
        val previous = savePrevious()
        val source = File(directory, "replacement.pdf").apply { writeBytes(byteArrayOf(4, 5, 6)) }
        val replacement = Draft(source, "Replacement.pdf", ink = previous.ink, savedInk = previous.ink)

        DocumentStore(app).saveDraft(replacement, emptySet())

        assertEquals(replacement, DocumentStore(app).restore())
        assertArrayEquals(byteArrayOf(4, 5, 6), source.readBytes())
        assertFalse(previous.source.exists())
        assertFalse(File(directory, "draft.json.new").exists())
        assertFalse(File(directory, "draft.json.bak").exists())
    }

    @Test
    fun interruptedLegacyWrite_restoresBackupInsteadOfPartialBaseOrPendingFile() {
        val previous = savePrevious()
        interruptLegacyWrite()
        File(directory, "draft.json.new").writeText("partial pending write")

        assertEquals(previous, DocumentStore(app).restore())
    }

    @Test
    fun backupWithoutBase_restoresPreviousDraft() {
        val previous = savePrevious()
        val base = File(directory, "draft.json")
        assertTrue(base.renameTo(File("$base.bak")))

        assertEquals(previous, DocumentStore(app).restore())
    }

    @Test
    fun interruptedStagedWrite_restoresBaseInsteadOfPendingFile() {
        val previous = savePrevious()
        File(directory, "draft.json.new").writeText("partial pending write")

        assertEquals(previous, DocumentStore(app).restore())
    }

    private fun savePrevious(): Draft {
        val source = File(directory, "previous.pdf").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        val ink = mapOf(2 to listOf(InkStroke(listOf(InkPoint(10f, 20f, 0.5f)), 1, 2f)))
        val previous = Draft(source, "Previous.pdf", page = 2, ink = ink)
        DocumentStore(app).saveDraft(previous, emptySet())
        return previous
    }

    private fun interruptLegacyWrite() {
        val base = File(directory, "draft.json")
        assertTrue(base.renameTo(File("$base.bak")))
        base.writeText("partial base write")
    }

    private fun failedReplacement(injected: Fault, beforeSave: () -> Unit = {}) {
        val previous = savePrevious()
        val source = previous.source
        val previousJson = File(directory, "draft.json").readBytes()
        val previousSource = source.readBytes()
        val replacement = File(directory, "replacement.pdf").apply { writeBytes(byteArrayOf(4, 5, 6)) }
        beforeSave()

        val store = DocumentStore(app, FaultyDraftFileIo(injected))
        val failure = runCatching { store.saveDraft(Draft(replacement, "Replacement.pdf"), emptySet()) }.exceptionOrNull()
        // DocumentService removes the newly imported source when saving fails.
        replacement.delete()

        assertTrue("$injected must retain the previous PDF", source.isFile)
        assertArrayEquals(previousSource, source.readBytes())
        assertArrayEquals(previousJson, AtomicFile(File(directory, "draft.json")).openRead().use { it.readBytes() })
        assertEquals(previous, DocumentStore(app).restore())
        assertTrue("$injected must report an IOException, got $failure", failure is IOException)
    }

    private enum class Fault { WRITE, SYNC, CLOSE, RENAME }

    private class FaultyDraftFileIo(private val fault: Fault) : DraftFileIo() {
        override fun open(file: File): FileOutputStream =
            object : FileOutputStream(file) {
                override fun write(bytes: ByteArray) {
                    if (fault == Fault.WRITE) {
                        super.write(bytes, 0, bytes.size / 2)
                        throw IOException("Injected partial write failure")
                    }
                    super.write(bytes)
                }

                override fun close() {
                    super.close()
                    if (fault == Fault.CLOSE) throw IOException("Injected close failure")
                }
            }

        override fun sync(output: FileOutputStream) {
            if (fault == Fault.SYNC) throw IOException("Injected sync failure")
            super.sync(output)
        }

        override fun rename(source: File, target: File): Boolean =
            if (fault == Fault.RENAME) false else super.rename(source, target)
    }
}
