package ch.lkmc.asterinked.document

import ch.lkmc.asterinked.ink.InkPoint
import ch.lkmc.asterinked.ink.InkStroke
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DraftDirtyTest {

    private fun stroke(x: Float = 10f, y: Float = 20f) =
        InkStroke(listOf(InkPoint(x, y, 0.8f), InkPoint(x + 5f, y + 5f, 0.9f)), -0x1000000, 2.2f)

    @Test
    fun emptyDraft_isClean() {
        assertFalse(Draft(File("a.pdf"), "a").dirty)
    }

    @Test
    fun addedStroke_isDirty() {
        val draft = Draft(File("a.pdf"), "a", ink = mapOf(0 to listOf(stroke())))
        assertTrue(draft.dirty)
    }

    @Test
    fun undoToSavedEquality_isClean() {
        val saved = mapOf(0 to listOf(stroke()))
        val dirty = Draft(File("a.pdf"), "a", ink = mapOf(0 to listOf(stroke(), stroke(30f, 40f))), savedInk = saved)
        assertTrue(dirty.dirty)
        // Undo = drop last stroke -> back to saved content.
        val undone = dirty.copy(ink = mapOf(0 to listOf(stroke())))
        assertFalse(undone.dirty)
        assertEquals(saved, undone.ink)
    }

    @Test
    fun emptyStrokeLists_doNotMarkDirty() {
        val draft = Draft(File("a.pdf"), "a", ink = mapOf(0 to emptyList()), savedInk = emptyMap())
        assertFalse(draft.dirty)
    }

    @Test
    fun otherPageInk_isDirty() {
        val draft = Draft(File("a.pdf"), "a", ink = mapOf(2 to listOf(stroke())), savedInk = emptyMap())
        assertTrue(draft.dirty)
    }
}
