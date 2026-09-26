package ch.lkmc.asterinked.document

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Display (viewer) coordinates: origin top-left of the rotated, cropped
 * page, y-down, extents [0, displayWidth] x [0, displayHeight].
 * PdfEngine concatenates [PageSpec.displayToPdf] onto the CTM so appended
 * ink lands in PDF default user space.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PageSpecMappingTest {

    private fun pdfPoint(spec: PageSpec, x: Float, y: Float): Pair<Double, Double> {
        val at = spec.displayToPdf().createAffineTransform()
        val src = doubleArrayOf(x.toDouble(), y.toDouble())
        val dst = DoubleArray(2)
        at.transform(src, 0, dst, 0, 1)
        return dst[0] to dst[1]
    }

    private fun assertPdfPoint(spec: PageSpec, x: Float, y: Float, ex: Float, ey: Float) {
        val (ax, ay) = pdfPoint(spec, x, y)
        assertEquals(ex.toDouble(), ax, 0.001)
        assertEquals(ey.toDouble(), ay, 0.001)
    }

    @Test
    fun displayDimensionsSwapOn90And270() {
        val base = PageSpec(0f, 0f, 100f, 200f, 0)
        assertEquals(100f, base.displayWidth)
        assertEquals(200f, base.displayHeight)
        assertEquals(200f, base.copy(rotation = 90).displayWidth)
        assertEquals(100f, base.copy(rotation = 90).displayHeight)
        assertEquals(100f, base.copy(rotation = 180).displayWidth)
        assertEquals(200f, base.copy(rotation = 180).displayHeight)
        assertEquals(200f, base.copy(rotation = 270).displayWidth)
        assertEquals(100f, base.copy(rotation = 270).displayHeight)
    }

    @Test
    fun rotation0_mapsViewerTopLeftToCropTopLeft() {
        // CropBox lower-left (10, 20), w=100 h=200.
        val spec = PageSpec(10f, 20f, 100f, 200f, 0)
        assertPdfPoint(spec, 0f, 0f, 10f, 220f)
        assertPdfPoint(spec, 100f, 200f, 110f, 20f)
        assertPdfPoint(spec, 50f, 100f, 60f, 120f)
    }

    @Test
    fun rotation90_mapsCorners() {
        // displayWidth=200 (height), displayHeight=100 (width).
        val spec = PageSpec(10f, 20f, 100f, 200f, 90)
        assertPdfPoint(spec, 0f, 0f, 10f, 20f)
        assertPdfPoint(spec, 200f, 100f, 110f, 220f)
        assertPdfPoint(spec, 0f, 100f, 110f, 20f)
    }

    @Test
    fun rotation180_mapsCorners() {
        val spec = PageSpec(10f, 20f, 100f, 200f, 180)
        assertPdfPoint(spec, 0f, 0f, 110f, 20f)
        assertPdfPoint(spec, 100f, 200f, 10f, 220f)
        assertPdfPoint(spec, 100f, 0f, 10f, 20f)
    }

    @Test
    fun rotation270_mapsCorners() {
        // displayWidth=200, displayHeight=100.
        val spec = PageSpec(10f, 20f, 100f, 200f, 270)
        assertPdfPoint(spec, 0f, 0f, 110f, 220f)
        assertPdfPoint(spec, 200f, 100f, 10f, 20f)
        assertPdfPoint(spec, 200f, 0f, 110f, 20f)
    }

    @Test
    fun cropOffsetIsIncluded_notMediaOrigin() {
        // Same size, shifted crop: mapping must follow left/bottom, not (0,0).
        val a = PageSpec(0f, 0f, 100f, 100f, 0)
        val b = PageSpec(36f, 48f, 100f, 100f, 0)
        val (ax, ay) = pdfPoint(a, 10f, 10f)
        val (bx, by) = pdfPoint(b, 10f, 10f)
        assertEquals(36.0, bx - ax, 0.001)
        assertEquals(48.0, by - ay, 0.001)
    }

    @Test
    fun mappedCornersStayInsideCropBox_rot0() {
        val spec = PageSpec(36f, 48f, 100f, 200f, 0)
        for ((x, y) in listOf(0f to 0f, spec.displayWidth to 0f, 0f to spec.displayHeight, spec.displayWidth to spec.displayHeight)) {
            val (px, py) = pdfPoint(spec, x, y)
            assertEquals(true, px >= 36.0 - 0.01 && px <= 136.0 + 0.01)
            assertEquals(true, py >= 48.0 - 0.01 && py <= 248.0 + 0.01)
        }
    }
}
