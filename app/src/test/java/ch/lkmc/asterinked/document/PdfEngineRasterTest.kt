package ch.lkmc.asterinked.document

import android.graphics.Color
import ch.lkmc.asterinked.ink.InkPoint
import ch.lkmc.asterinked.ink.InkStroke
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.encryption.AccessPermission
import com.tom_roush.pdfbox.pdmodel.encryption.StandardProtectionPolicy
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.util.Matrix
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

/** Generate real exported PDFs for scripts/verify_pdf.py's independent PDFium check. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PdfEngineRasterTest {
    @Test fun exportCroppedRotatedPagesForIndependentRenderer() {
        val app = RuntimeEnvironment.getApplication()
        PDFBoxResourceLoader.init(app)
        val engine = PdfEngine(app.cacheDir)
        val output = File("build/test-output/raster-proof").apply { mkdirs() }
        // name to (rotation, owner-restricted encryption)
        val cases = listOf(0, 90, 180, 270).map { "$it" to (it to false) } + ("encrypted" to (0 to true))
        for ((name, options) in cases) {
            val (rotation, encrypted) = options
            val source = File(output, "source-$name.pdf")
            val exported = File(output, "export-$name.pdf")
            PDDocument().use { document ->
                val page = PDPage(PDRectangle.LETTER).apply {
                    this.rotation = rotation
                    cropBox = PDRectangle(36f, 48f, 540f, 696f)
                }
                document.addPage(page)
                PDPageContentStream(document, page).use { stream ->
                    stream.beginText()
                    stream.setFont(PDType1Font.HELVETICA, 12f)
                    stream.newLineAtOffset(72f, 700f)
                    stream.showText("Original text $rotation")
                    stream.endText()
                    // Leave a changed transform and clip to exercise resetContext.
                    stream.transform(Matrix(2f, 0f, 0f, 2f, 100f, 150f))
                    stream.addRect(0f, 0f, 5f, 5f)
                    stream.clip()
                }
                if (encrypted) {
                    // Opens without a password but forbids printing: the export must keep that.
                    val permissions = AccessPermission().apply { setCanPrint(false) }
                    document.protect(StandardProtectionPolicy("owner", "", permissions).apply { encryptionKeyLength = 128 })
                }
                document.save(source)
            }
            val spec = engine.inspect(source).single()
            val x = spec.displayWidth * 0.23f
            val y = spec.displayHeight * 0.31f
            val ink = listOf(
                InkStroke(listOf(InkPoint(x, y, 1f)), Color.RED, 10f),
                InkStroke(listOf(InkPoint(x - 20f, y, .2f), InkPoint(x + 20f, y, 1f)), Color.RED, 5f),
            )
            engine.export(source, exported, mapOf(0 to ink))
            assertTrue(exported.length() > source.length())
        }
    }
}
