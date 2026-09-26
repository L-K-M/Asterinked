package ch.lkmc.asterinked.document

import ch.lkmc.asterinked.ink.InkPoint
import ch.lkmc.asterinked.ink.InkStroke
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.text.PDFTextStripper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

/**
 * PdfEngine integration: source PDFs are generated with PDFBox text, then
 * [PdfEngine.export] appends pressure-sensitive vector ink. Reopening must
 * show original text/content plus stroke operators, with no raster fallback.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PdfEngineExportTest {

    private val app get() = RuntimeEnvironment.getApplication()
    private lateinit var engine: PdfEngine

    @Before
    fun setUp() {
        PDFBoxResourceLoader.init(RuntimeEnvironment.getApplication())
        engine = PdfEngine(app.cacheDir)
    }

    // ---------- helpers ----------

    private fun tmp(name: String) = File(app.cacheDir, "$name-${System.nanoTime()}.pdf")

    private fun writeTextPage(doc: PDDocument, page: PDPage, text: String) {
        PDPageContentStream(doc, page).use { stream ->
            stream.beginText()
            stream.setFont(PDType1Font.HELVETICA, 12f)
            stream.newLineAtOffset(72f, 700f)
            stream.showText(text)
            stream.endText()
        }
    }

    /** Single Letter page with [text]. Rotation/crop optional. */
    private fun sourcePdf(
        text: String = "Asterinked original",
        rotation: Int = 0,
        cropInset: Float = 0f,
    ): File {
        val out = tmp("src")
        PDDocument().use { doc ->
            val page = PDPage(PDRectangle.LETTER)
            if (rotation != 0) page.rotation = rotation
            if (cropInset != 0f) {
                page.cropBox = PDRectangle(
                    cropInset, cropInset,
                    PDRectangle.LETTER.width - 2 * cropInset,
                    PDRectangle.LETTER.height - 2 * cropInset,
                )
            }
            doc.addPage(page)
            writeTextPage(doc, page, text)
            doc.save(out)
        }
        return out
    }

    private fun twoPagePdf(): File {
        val out = tmp("twopage")
        PDDocument().use { doc ->
            val first = PDPage(PDRectangle.LETTER)
            val second = PDPage(PDRectangle.LETTER)
            doc.addPage(first)
            doc.addPage(second)
            writeTextPage(doc, first, "First page sentinel")
            writeTextPage(doc, second, "Second page sentinel")
            doc.save(out)
        }
        return out
    }

    private fun stroke(
        vararg xy: Float,
        pressure: Float = 0.8f,
        color: Int = -0x1000000,
        width: Float = 2.2f,
    ): InkStroke {
        require(xy.size % 2 == 0 && xy.isNotEmpty())
        val points = mutableListOf<InkPoint>()
        var i = 0
        while (i < xy.size) {
            points += InkPoint(xy[i], xy[i + 1], pressure)
            i += 2
        }
        return InkStroke(points, color, width)
    }

    private fun extractText(file: File): String {
        PDDocument.load(file).use { doc ->
            return PDFTextStripper().getText(doc)
        }
    }

    private fun contentsOf(file: File, pageIndex: Int): String {
        PDDocument.load(file).use { doc ->
            return doc.getPage(pageIndex).contents.readBytes().toString(Charsets.ISO_8859_1)
        }
    }

    private fun countOf(haystack: String, needle: String): Int =
        haystack.split(needle, ignoreCase = false, limit = 0).size - 1

    private fun strokeOps(contents: String): Int =
        // Each exported segment ends with stroke ("S") or dot fill ("f").
        countOf(contents, "\nS") + countOf(contents, " S\n") + countOf(contents, "\nf")

    // ---------- tests ----------

    @Test
    fun export_preservesTextAndAddsVectorStroke() {
        val source = sourcePdf()
        val dest = tmp("out")
        engine.export(source, dest, mapOf(0 to listOf(stroke(10f, 10f, 60f, 40f, 100f, 20f))))

        assertTrue(extractText(dest).contains("Asterinked original"))
        val contents = contentsOf(dest, 0)
        // Original text show operator survives the append.
        assertTrue(contents.contains("Tj") || contents.contains("TJ"))
        // Vector ink: line segments stroked, no image XObject fallback.
        assertTrue(contents.contains(" m") || contents.contains("\nm"))
        assertTrue(contents.contains(" S") || contents.contains("\nS") || contents.contains(" f"))
        assertTrue(!contents.contains(" Do"))
        PDDocument.load(dest).use { doc ->
            assertEquals(1, doc.numberOfPages)
            assertEquals(false, doc.getPage(0).resources.xObjectNames.iterator().hasNext())
        }
    }

    @Test
    fun export_tapDotAddsFillOps() {
        val source = sourcePdf()
        val dest = tmp("tap")
        engine.export(source, dest, mapOf(0 to listOf(stroke(50f, 50f))))
        assertTrue(extractText(dest).contains("Asterinked original"))
        val contents = contentsOf(dest, 0)
        // Dot path uses bezier curves + fill.
        assertTrue(contents.contains(" c") || contents.contains("\nc"))
        assertTrue(contents.contains(" f") || contents.contains("\nf"))
        assertTrue(!contents.contains(" Do"))
    }

    @Test
    fun repeatedExportFromOriginal_doesNotDuplicateStrokes() {
        val source = sourcePdf()
        val ink = mapOf(0 to listOf(stroke(10f, 10f, 60f, 40f)))
        val first = tmp("first")
        val second = tmp("second")
        engine.export(source, first, ink)
        engine.export(source, second, ink)
        assertEquals(strokeOps(contentsOf(first, 0)), strokeOps(contentsOf(second, 0)))
        // Export contains exactly one pass of ink, not accumulated layers.
        val single = strokeOps(contentsOf(first, 0))
        val base = strokeOps(contentsOf(source, 0))
        assertTrue(single > base)
    }

    @Test
    fun export_onlyInksRequestedPage() {
        val source = twoPagePdf()
        val dest = tmp("paged")
        engine.export(source, dest, mapOf(1 to listOf(stroke(10f, 10f, 80f, 80f))))
        assertTrue(extractText(dest).contains("First page sentinel"))
        assertTrue(extractText(dest).contains("Second page sentinel"))
        assertEquals(strokeOps(contentsOf(source, 0)), strokeOps(contentsOf(dest, 0)))
        assertTrue(strokeOps(contentsOf(dest, 1)) > strokeOps(contentsOf(source, 1)))
    }

    @Test
    fun export_preservesRotationAndCrop() {
        for (rotation in listOf(0, 90, 180, 270)) {
            val source = sourcePdf(text = "Rotate $rotation", rotation = rotation, cropInset = 36f)
            val dest = tmp("rot$rotation")
            engine.export(source, dest, mapOf(0 to listOf(stroke(10f, 10f, 50f, 40f))))
            // Text extraction order/spacing depends on rotation; export must preserve it exactly.
            val before = extractText(source)
            assertTrue("rot=$rotation source text", before.isNotBlank())
            assertEquals("rot=$rotation text", before, extractText(dest))
            PDDocument.load(dest).use { doc ->
                assertEquals(rotation, doc.getPage(0).rotation)
                assertEquals(36f, doc.getPage(0).cropBox.lowerLeftX, 0.01f)
                assertEquals(36f, doc.getPage(0).cropBox.lowerLeftY, 0.01f)
            }
            val contents = contentsOf(dest, 0)
            assertTrue("rot=$rotation ink", strokeOps(contents) > 0)
            // inspect() agrees with stored boxes for the viewer mapping.
            val specs = engine.inspect(dest)
            assertEquals(rotation, specs[0].rotation)
        }
    }

    @Test
    fun inspect_reportsCropAndRotation() {
        val source = sourcePdf(rotation = 90, cropInset = 36f)
        val specs = engine.inspect(source)
        assertEquals(1, specs.size)
        assertEquals(90, specs[0].rotation)
        // LETTER 612x792 minus 36pt insets on each side.
        assertEquals(540f, specs[0].width, 0.5f)
        assertEquals(720f, specs[0].height, 0.5f)
        // Display swaps w/h for 90deg.
        assertEquals(specs[0].height, specs[0].displayWidth, 0.001f)
        assertEquals(specs[0].width, specs[0].displayHeight, 0.001f)
    }
}
