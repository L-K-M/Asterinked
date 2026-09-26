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
import com.tom_roush.pdfbox.util.Matrix
import java.io.File
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

internal class PdfEngine(private val scratchDirectory: File) {
    fun inspect(source: File): List<PageSpec> = load(source).use { document ->
        require(!document.isEncrypted) { "Password-protected PDFs are not supported yet." }
        require(document.currentAccessPermission.canModify()) { "This PDF does not allow changes." }
        require(document.numberOfPages > 0) { "This PDF has no pages." }
        document.pages.map { page ->
            val crop = page.cropBox
            require(crop.width.isFinite() && crop.height.isFinite() && crop.width > 0 && crop.height > 0) {
                "This PDF has an invalid page size."
            }
            PageSpec(crop.lowerLeftX, crop.lowerLeftY, crop.width, crop.height, page.rotation)
        }
    }

    fun render(source: File, pageIndex: Int): Bitmap {
        val descriptor = ParcelFileDescriptor.open(source, ParcelFileDescriptor.MODE_READ_ONLY)
        val renderer = try {
            PdfRenderer(descriptor)
        } catch (error: Exception) {
            descriptor.close()
            throw error
        }
        return renderer.use {
            it.openPage(pageIndex).use { page ->
                val scale = PREVIEW_LONG_EDGE.toFloat() / max(page.width, page.height)
                val bitmap = Bitmap.createBitmap(
                    (page.width * scale).roundToInt().coerceAtLeast(1),
                    (page.height * scale).roundToInt().coerceAtLeast(1),
                    Bitmap.Config.ARGB_8888,
                )
                try {
                    bitmap.eraseColor(Color.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    bitmap
                } catch (error: Exception) {
                    bitmap.recycle()
                    throw error
                }
            }
        }
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
            document.save(destination)
        }
    }

    private fun load(source: File): PDDocument = PDDocument.load(
        source, MemoryUsageSetting.setupMixed(PDF_MEMORY_BYTES).setTempDir(scratchDirectory),
    )

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
    }
}
