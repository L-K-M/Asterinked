package ch.lkmc.asterinked.document

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import ch.lkmc.asterinked.ink.InkGeometry
import ch.lkmc.asterinked.ink.InkStroke
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.encryption.AccessPermission
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import com.tom_roush.pdfbox.pdmodel.encryption.StandardProtectionPolicy
import com.tom_roush.pdfbox.util.Matrix
import java.io.File
import java.util.UUID
import kotlin.math.max
import kotlin.math.roundToInt

internal data class PageSpec(val left: Float, val bottom: Float, val width: Float, val height: Float, val rotation: Int) {
    val displayWidth: Float get() = if (rotation == 90 || rotation == 270) height else width
    val displayHeight: Float get() = if (rotation == 90 || rotation == 270) width else height

    // Invert the cropped, rotated viewer coordinates into PDF user space.
    fun displayToPdf(): Matrix = when (rotation) {
        90 -> Matrix(0f, 1f, 1f, 0f, left, bottom)
        180 -> Matrix(-1f, 0f, 0f, 1f, left + width, bottom)
        270 -> Matrix(0f, -1f, -1f, 0f, left + width, bottom + height)
        else -> Matrix(1f, 0f, 0f, -1f, left, bottom + height)
    }
}

/** Not thread-safe: PdfRenderer and PDFBox handles are used from one worker only. */
internal class PdfEngine(private val scratchDirectory: File) {
    private var renderer: OpenRenderer? = null

    // Encrypted PDFs that open without a password (owner restrictions only) are
    // accepted; a PDF that needs a password fails in load().
    fun inspect(source: File): List<PageSpec> = load(source).use { document ->
        if (!document.currentAccessPermission.canModify()) throw DocumentException(DocumentProblem.EDITING_NOT_ALLOWED)
        if (document.numberOfPages == 0) throw DocumentException(DocumentProblem.NO_PAGES)
        document.pages.map { page ->
            val crop = page.cropBox
            if (!crop.width.isFinite() || !crop.height.isFinite() || crop.width <= 0 || crop.height <= 0) {
                throw DocumentException(DocumentProblem.NOT_A_PDF)
            }
            PageSpec(crop.lowerLeftX, crop.lowerLeftY, crop.width, crop.height, page.rotation)
        }
    }

    fun render(source: File, pageIndex: Int): Bitmap = rendererFor(source).openPage(pageIndex).use { page ->
        val scale = PREVIEW_LONG_EDGE.toFloat() / max(page.width, page.height)
        val bitmap = Bitmap.createBitmap(
            (page.width * scale).roundToInt().coerceAtLeast(1),
            (page.height * scale).roundToInt().coerceAtLeast(1),
            Bitmap.Config.ARGB_8888,
        )
        try {
            bitmap.eraseColor(Color.WHITE)
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            // Fit zoom on a phone shrinks the preview; mipmaps keep thin text from shimmering.
            bitmap.setHasMipMap(true)
            bitmap
        } catch (error: Exception) {
            bitmap.recycle()
            throw error
        }
    }

    /** Releases the open renderer; the next render reopens it. */
    fun close() {
        renderer?.close()
        renderer = null
    }

    // Opening a PdfRenderer parses the whole cross-reference table, so keep one
    // per document instead of reopening it for every page.
    private fun rendererFor(source: File): PdfRenderer {
        renderer?.let { if (it.source == source) return it.pdf }
        close()
        val descriptor = ParcelFileDescriptor.open(source, ParcelFileDescriptor.MODE_READ_ONLY)
        val pdf = try {
            PdfRenderer(descriptor)
        } catch (error: Exception) {
            descriptor.close()
            throw error
        }
        renderer = OpenRenderer(source, pdf)
        return pdf
    }

    private class OpenRenderer(val source: File, val pdf: PdfRenderer) {
        // PdfRenderer.close() also closes the descriptor it was given.
        fun close() = pdf.close()
    }

    fun export(source: File, destination: File, ink: Map<Int, List<InkStroke>>) {
        load(source).use { document ->
            for ((index, strokes) in ink) {
                if (strokes.isEmpty()) continue
                val page = document.getPage(index)
                val crop = page.cropBox
                val spec = PageSpec(crop.lowerLeftX, crop.lowerLeftY, crop.width, crop.height, page.rotation)
                // Reset inherited graphics state; append keeps original text and artwork intact.
                PDPageContentStream(document, page, PDPageContentStream.AppendMode.APPEND, true, true).use { stream ->
                    stream.saveGraphicsState()
                    stream.transform(spec.displayToPdf())
                    stream.addRect(0f, 0f, spec.displayWidth, spec.displayHeight)
                    stream.clip()
                    stream.setLineCapStyle(ROUND_CAP)
                    stream.setLineJoinStyle(ROUND_CAP)
                    for (stroke in strokes) {
                        stream.setStrokingColor(Color.red(stroke.color), Color.green(stroke.color), Color.blue(stroke.color))
                        stream.setNonStrokingColor(Color.red(stroke.color), Color.green(stroke.color), Color.blue(stroke.color))
                        for (segment in InkGeometry.segments(stroke)) {
                            val start = segment.start
                            val end = segment.end
                            stream.setLineWidth(segment.width)
                            if (start.x == end.x && start.y == end.y) {
                                drawDot(stream, start.x, start.y, segment.width / 2f)
                                continue
                            }
                            stream.moveTo(start.x, start.y)
                            stream.lineTo(end.x, end.y)
                            stream.stroke()
                        }
                    }
                    stream.restoreGraphicsState()
                }
            }
            if (document.isEncrypted) keepProtection(document)
            document.save(destination)
        }
    }

    private fun load(source: File): PDDocument = try {
        PDDocument.load(source, MemoryUsageSetting.setupMixed(PDF_MEMORY_BYTES).setTempDir(scratchDirectory))
    } catch (error: InvalidPasswordException) {
        throw DocumentException(DocumentProblem.PASSWORD_PROTECTED, error)
    }

    // PDFBox refuses to save a decrypted document under its old encryption
    // dictionary. Re-encrypt the copy with the source's permissions and an empty
    // user password (the source opened without one), so owner restrictions such
    // as "no printing" carry over instead of being silently stripped. The owner
    // password is random: the original one is unknown to the app. 128-bit copies
    // use AES (PDFBox 2 would pick RC4), so an AES source is never downgraded.
    private fun keepProtection(document: PDDocument) {
        val permissions = AccessPermission(document.currentAccessPermission.permissionBytes)
        val policy = StandardProtectionPolicy(UUID.randomUUID().toString(), "", permissions)
        policy.encryptionKeyLength = supportedKeyLength(document.encryption.length)
        policy.isPreferAES = true
        document.protect(policy)
    }

    private fun supportedKeyLength(bits: Int): Int = SUPPORTED_KEY_LENGTHS.firstOrNull { bits <= it } ?: SUPPORTED_KEY_LENGTHS.last()

    private fun drawDot(stream: PDPageContentStream, x: Float, y: Float, radius: Float) {
        val handle = radius * CIRCLE_BEZIER
        stream.moveTo(x + radius, y)
        stream.curveTo(x + radius, y + handle, x + handle, y + radius, x, y + radius)
        stream.curveTo(x - handle, y + radius, x - radius, y + handle, x - radius, y)
        stream.curveTo(x - radius, y - handle, x - handle, y - radius, x, y - radius)
        stream.curveTo(x + handle, y - radius, x + radius, y - handle, x + radius, y)
        stream.fill()
    }

    private companion object {
        const val PREVIEW_LONG_EDGE = 2048
        const val PDF_MEMORY_BYTES = 32L * 1024 * 1024
        const val ROUND_CAP = 1
        const val CIRCLE_BEZIER = 0.55228475f
        val SUPPORTED_KEY_LENGTHS = intArrayOf(40, 128, 256)
    }
}
