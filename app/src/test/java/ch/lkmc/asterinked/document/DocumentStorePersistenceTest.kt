package ch.lkmc.asterinked.document

import ch.lkmc.asterinked.ink.InkKind
import ch.lkmc.asterinked.ink.InkPoint
import ch.lkmc.asterinked.ink.InkStroke
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DocumentStorePersistenceTest {

    private val app get() = RuntimeEnvironment.getApplication()

    @Before
    fun clearDraft() {
        File(app.filesDir, "documents/draft.json").delete()
        File(app.filesDir, "documents/draft.json.bak").delete()
    }

    @Test
    fun restoreWithoutDraft_returnsNull() {
        assertNull(DocumentStore(app).restore())
    }

    @Test
    fun saveAndRestore_roundTripsInk() {
        val store = DocumentStore(app)
        val dir = File(app.filesDir, "documents").apply { mkdirs() }
        val source = File(dir, "persist.pdf").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        val ink = mapOf(
            0 to listOf(
                InkStroke(
                    listOf(InkPoint(1.5f, 2.5f, 0.5f), InkPoint(10f, 20f, 1f)),
                    0xFF19262E.toInt(),
                    2.2f,
                ),
            ),
            2 to listOf(
                InkStroke(listOf(InkPoint(7f, 8f, 0.3f)), -0x1000000, 3f),
            ),
        )
        val savedInk = mapOf(0 to listOf(ink.getValue(0).first()))
        store.saveDraft(Draft(source, "Persist.pdf", page = 2, ink = ink, savedInk = savedInk))

        val restored = store.restore()
        assertNotNull(restored)
        assertEquals("Persist.pdf", restored!!.name)
        assertEquals(2, restored.page)
        assertEquals(source.canonicalFile, restored.source.canonicalFile)
        assertEquals(ink.size, restored.ink.size)
        val points = restored.ink.getValue(0).first().points
        assertEquals(1.5f, points[0].x, 0.001f)
        assertEquals(2.5f, points[0].y, 0.001f)
        assertEquals(0.5f, points[0].pressure, 0.001f)
        assertEquals(2.2f, restored.ink.getValue(0).first().width, 0.001f)
        assertEquals(savedInk.getValue(0).size, restored.savedInk.getValue(0).size)
        // Dirty state survives the round trip (page 2 tap not yet saved).
        assertEquals(true, restored.dirty)
    }

    @Test
    fun saveAndRestore_keepsStrokeKindAndReadsOldDrafts() {
        val store = DocumentStore(app)
        val dir = File(app.filesDir, "documents").apply { mkdirs() }
        val source = File(dir, "kinds.pdf").apply { writeBytes(byteArrayOf(9)) }
        val pen = InkStroke(listOf(InkPoint(1f, 1f, 1f)), 1, 1f)
        val marker = InkStroke(listOf(InkPoint(2f, 2f, 1f)), 2, 12f, InkKind.HIGHLIGHTER)
        store.saveDraft(Draft(source, "k.pdf", ink = mapOf(0 to listOf(pen, marker))))

        val json = File(dir, "draft.json").readText()
        assertEquals("Pen strokes omit the key", 1, Regex("\"kind\"").findAll(json).count())
        assertEquals(listOf(InkKind.PEN, InkKind.HIGHLIGHTER), store.restore()!!.ink.getValue(0).map { it.kind })

        // A draft from a newer version with an unknown kind still restores its strokes.
        File(dir, "draft.json").writeText(json.replace("\"highlighter\"", "\"calligraphy\""))
        assertEquals(listOf(InkKind.PEN, InkKind.PEN), DocumentStore(app).restore()!!.ink.getValue(0).map { it.kind })
    }

    @Test
    fun saveAndRestore_cleanDraftStaysClean() {
        val store = DocumentStore(app)
        val dir = File(app.filesDir, "documents").apply { mkdirs() }
        val source = File(dir, "clean.pdf").apply { writeBytes(byteArrayOf(9)) }
        val ink = mapOf(0 to listOf(InkStroke(listOf(InkPoint(1f, 1f, 1f)), 1, 1f)))
        store.saveDraft(Draft(source, "c.pdf", ink = ink, savedInk = ink))
        assertEquals(false, store.restore()!!.dirty)
    }

    @Test
    fun restore_sanitizesCorruptStrokeWidths() {
        val store = DocumentStore(app)
        val dir = File(app.filesDir, "documents").apply { mkdirs() }
        val source = File(dir, "widths.pdf").apply { writeBytes(byteArrayOf(3)) }
        val stroke = InkStroke(listOf(InkPoint(1f, 1f, 1f)), 1, 2f)
        store.saveDraft(Draft(source, "w.pdf", ink = mapOf(0 to listOf(stroke, stroke))))

        // org.json refuses NaN/Infinity at parse time (such drafts take the
        // broken-draft path), but zero and negative widths parse fine.
        val points = """[[1,1,1]]"""
        File(dir, "draft.json").writeText(
            """{"source":"${source.name}","name":"w.pdf","page":0,""" +
                """"ink":{"0":[{"color":1,"width":0,"points":$points},""" +
                """{"color":1,"width":-3,"points":$points},{"color":1,"width":5,"points":$points}]},"savedInk":{}}""",
        )
        val widths = DocumentStore(app).restore()!!.ink.getValue(0).map { it.width }
        assertEquals(listOf(2.2f, 2.2f, 5f), widths)
    }
}
