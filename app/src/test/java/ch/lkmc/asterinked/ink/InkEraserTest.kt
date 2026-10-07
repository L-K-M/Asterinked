package ch.lkmc.asterinked.ink

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class InkEraserTest {
    private val eraser = InkEraser()
    private val line = InkStroke(listOf(InkPoint(0f, 0f, 1f), InkPoint(100f, 0f, 1f)), 0, 4f)

    @Test fun touchingTheLineOrItsWidthHits() {
        assertEquals(listOf(line), eraser.hits(listOf(line), null, InkPoint(50f, 6f, 1f), radius = 5f))
        assertTrue("Radius 5 + half width 2 = 7 reach", eraser.hits(listOf(line), null, InkPoint(50f, 7.5f, 1f), radius = 5f).isEmpty())
        assertTrue("Past the end cap", eraser.hits(listOf(line), null, InkPoint(110f, 0f, 1f), radius = 5f).isEmpty())
    }

    @Test fun aFastDragDoesNotSkipAThinStrokeBetweenSamples() {
        val thin = InkStroke(listOf(InkPoint(50f, -20f, 1f), InkPoint(50f, 20f, 1f)), 0, 0.5f)
        assertEquals(listOf(thin), eraser.hits(listOf(thin), InkPoint(0f, 0f, 1f), InkPoint(100f, 0f, 1f), radius = 2f))
    }

    @Test fun resetForgetsStrokesButKeepsHitTestingCorrect() {
        val eraser = InkEraser()
        eraser.hits(listOf(line), null, InkPoint(50f, 0f, 1f), radius = 5f)
        eraser.reset()
        assertEquals(listOf(line), eraser.hits(listOf(line), null, InkPoint(50f, 6f, 1f), radius = 5f))
    }

    @Test fun dotsAreErasableAndOnlyTouchedStrokesAreReturned() {
        val dot = InkStroke(listOf(InkPoint(200f, 200f, 1f)), 0, 2f)
        val far = InkStroke(listOf(InkPoint(400f, 400f, 1f), InkPoint(420f, 420f, 1f)), 0, 2f)
        assertEquals(listOf(dot), eraser.hits(listOf(line, dot, far), null, InkPoint(203f, 200f, 1f), radius = 3f))
    }

    @Test fun theVisibleSmoothedCornerIsErasable() {
        val stroke = corner()
        assertSame(stroke, eraser.hits(listOf(stroke), null, InkPoint(87.5f, 12.5f, 1f), radius = 5f).single())
    }

    @Test fun theEmptyRawCornerIsNotErasable() {
        assertTrue(eraser.hits(listOf(corner()), null, InkPoint(100f, 0f, 1f), radius = 5f).isEmpty())
    }

    @Test fun lightPressureUsesTheRenderedWidthRatherThanNominalWidth() {
        val stroke = InkStroke(listOf(InkPoint(0f, 0f, 0.1f), InkPoint(100f, 0f, 0.1f)), 0, 3.6f)
        assertTrue("Rendered width is 0.4, so reach is 5.2", eraser.hits(listOf(stroke), null, InkPoint(50f, 6.2f, 1f), radius = 5f).isEmpty())
        assertSame(stroke, eraser.hits(listOf(stroke), null, InkPoint(50f, 5.1f, 1f), radius = 5f).single())
    }

    @Test fun eachSegmentUsesItsLocalPressureWidth() {
        val stroke = InkStroke(listOf(InkPoint(0f, 0f, 0.1f), InkPoint(100f, 0f, 1f)), 0, 3.6f)
        assertTrue(eraser.hits(listOf(stroke), null, InkPoint(0f, 6.2f, 1f), radius = 5f).isEmpty())
        assertSame(stroke, eraser.hits(listOf(stroke), null, InkPoint(100f, 6.2f, 1f), radius = 5f).single())
    }

    @Test fun dotsUsePressureWidthAndIncludeTangency() {
        val dot = InkStroke(listOf(InkPoint(0f, 0f, 0.25f)), 0, 4f)
        assertSame(dot, eraser.hits(listOf(dot), null, InkPoint(5.5f, 0f, 1f), radius = 5f).single())
        assertTrue(eraser.hits(listOf(dot), null, InkPoint(5.501f, 0f, 1f), radius = 5f).isEmpty())

        val minimumWidthDot = dot.copy(points = listOf(InkPoint(0f, 0f, 0f)))
        assertSame(minimumWidthDot, eraser.hits(listOf(minimumWidthDot), null, InkPoint(5.1f, 0f, 1f), radius = 5f).single())
        assertTrue(eraser.hits(listOf(minimumWidthDot), null, InkPoint(5.21f, 0f, 1f), radius = 5f).isEmpty())
    }

    @Test fun aSweptDiscHitsAGrazingDotBetweenTheOldProbes() {
        val dot = InkStroke(listOf(InkPoint(5f, 10.9f, 1f)), 0, 2f)
        val start = InkPoint(0f, 0f, 1f)
        val end = InkPoint(20f, 0f, 1f)
        assertSame(dot, eraser.hits(listOf(dot), start, end, radius = 10f).single())
        assertSame(dot, eraser.hits(listOf(dot), end, start, radius = 10f).single())
    }

    @Test fun aLongSweepHasNoStepCapGaps() {
        val thin = InkStroke(listOf(InkPoint(20f, -20f, 1f), InkPoint(20f, 20f, 1f)), 0, 0.5f)
        assertSame(thin, eraser.hits(listOf(thin), InkPoint(0f, 0f, 1f), InkPoint(10_000f, 0f, 1f), radius = 2f).single())
    }

    @Test fun bothSweepEndpointCapsIncludeContactButExcludeOutsideDots() {
        val before = InkStroke(listOf(InkPoint(-6f, 0f, 1f)), 0, 2f)
        val after = before.copy(points = listOf(InkPoint(26f, 0f, 1f)))
        val outsideBefore = before.copy(points = listOf(InkPoint(-6.001f, 0f, 1f)))
        val outsideAfter = after.copy(points = listOf(InkPoint(26.001f, 0f, 1f)))
        val strokes = listOf(outsideBefore, after, before, outsideAfter)
        assertEquals(listOf(after, before), eraser.hits(strokes, InkPoint(0f, 0f, 1f), InkPoint(20f, 0f, 1f), radius = 5f))
    }

    @Test fun parallelSweepTangencyHitsButJustOutsideMisses() {
        val touching = InkStroke(listOf(InkPoint(5f, 11f, 1f), InkPoint(15f, 11f, 1f)), 0, 2f)
        val outside = touching.copy(points = listOf(InkPoint(5f, 11.001f, 1f), InkPoint(15f, 11.001f, 1f)))
        assertEquals(listOf(touching), eraser.hits(listOf(outside, touching), InkPoint(0f, 0f, 1f), InkPoint(20f, 0f, 1f), radius = 10f))
    }

    @Test fun diagonalInteriorCrossingHitsWithAllEndpointsFarAway() {
        val crossing = InkStroke(listOf(InkPoint(2f, 18f, 1f), InkPoint(18f, 2f, 1f)), 0, 0.4f)
        assertSame(crossing, eraser.hits(listOf(crossing), InkPoint(0f, 0f, 1f), InkPoint(20f, 20f, 1f), radius = 0f).single())
    }

    @Test fun collinearSweepsHandleOverlapAndRoundCaps() {
        val stroke = InkStroke(listOf(InkPoint(15f, 0f, 1f), InkPoint(30f, 0f, 1f)), 0, 2f)
        assertSame(stroke, eraser.hits(listOf(stroke), InkPoint(0f, 0f, 1f), InkPoint(20f, 0f, 1f), radius = 0f).single())
        assertSame(stroke, eraser.hits(listOf(stroke), InkPoint(0f, 0f, 1f), InkPoint(14f, 0f, 1f), radius = 0f).single())
        assertTrue(eraser.hits(listOf(stroke), InkPoint(0f, 0f, 1f), InkPoint(13.999f, 0f, 1f), radius = 0f).isEmpty())
    }

    @Test fun overlappingBoundsAloneDoNotEraseEnclosingInk() {
        val enclosing = InkStroke(listOf(InkPoint(-100f, 0f, 1f), InkPoint(-100f, 100f, 1f),
            InkPoint(100f, 100f, 1f), InkPoint(100f, 0f, 1f)), 0, 2f)
        assertTrue(eraser.hits(listOf(enclosing), InkPoint(-20f, 20f, 1f), InkPoint(20f, 20f, 1f), radius = 5f).isEmpty())
    }

    @Test fun highlightersUseConstantWidthEvenAtZeroPressure() {
        val highlight = InkStroke(listOf(InkPoint(0f, 0f, 0f), InkPoint(100f, 0f, 0f)), 0, 12f, InkKind.HIGHLIGHTER)
        assertSame(highlight, eraser.hits(listOf(highlight), null, InkPoint(50f, 11f, 1f), radius = 5f).single())
        assertTrue(eraser.hits(listOf(highlight), null, InkPoint(50f, 11.001f, 1f), radius = 5f).isEmpty())

        val dot = highlight.copy(points = listOf(InkPoint(0f, 0f, 0f)))
        assertSame(dot, eraser.hits(listOf(dot), null, InkPoint(11f, 0f, 1f), radius = 5f).single())
        assertTrue(eraser.hits(listOf(dot), null, InkPoint(11.001f, 0f, 1f), radius = 5f).isEmpty())
    }

    @Test fun highlightersFollowTheSameSmoothedCenterline() {
        val highlight = corner().copy(kind = InkKind.HIGHLIGHTER)
        assertSame(highlight, eraser.hits(listOf(highlight), null, InkPoint(87.5f, 12.5f, 1f), radius = 5f).single())
        assertTrue(eraser.hits(listOf(highlight), null, InkPoint(100f, 0f, 1f), radius = 5f).isEmpty())
    }

    @Test fun invalidHighlighterWidthsUseTheFinitePositiveGeometryFallback() {
        for (width in listOf(0f, -2f, Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY)) {
            val highlight = InkStroke(listOf(InkPoint(0f, 0f, 0f), InkPoint(100f, 0f, 0f)), 0, width, InkKind.HIGHLIGHTER)
            assertEquals("width $width", listOf(highlight), eraser.hits(listOf(highlight), null, InkPoint(50f, 3f, 1f), radius = 2f))
            assertTrue("width $width", eraser.hits(listOf(highlight), null, InkPoint(50f, 3.2f, 1f), radius = 2f).isEmpty())
        }
    }

    @Test fun invalidSamplesAreSkippedAndEmptyGeometryCannotHit() {
        val empty = InkStroke(emptyList(), 0, 2f)
        val invalid = empty.copy(points = listOf(InkPoint(Float.NaN, 0f, 1f), InkPoint(0f, Float.POSITIVE_INFINITY, 1f)))
        val dot = invalid.copy(points = invalid.points + InkPoint(5f, 0f, Float.NaN) + invalid.points)
        assertSame(dot, eraser.hits(listOf(empty, invalid, dot), null, InkPoint(5f, 0f, 1f), radius = 1f).single())
    }

    @Test fun equalButDistinctStrokesRetainIdentityAndInputOrder() {
        val first = corner()
        val second = first.copy()
        val location = InkPoint(50f, 0f, 1f)
        val hits = eraser.hits(listOf(second, first), null, location, radius = 1f)
        assertEquals(2, hits.size)
        assertSame(second, hits[0])
        assertSame(first, hits[1])
        assertSame(first, eraser.hits(listOf(first), null, location, radius = 1f).single())
        assertSame(second, hits[0])
    }

    @Test fun repeatedSweepsReuseGeometryUntilReset() {
        val samples = listOf(InkPoint(0f, 0f, 1f), InkPoint(100f, 0f, 1f))
        var reads = 0
        val counted = object : AbstractList<InkPoint>() {
            override val size get() = samples.size
            override fun get(index: Int): InkPoint { reads++; return samples[index] }
        }
        val stroke = InkStroke(counted, 0, 2f)
        val start = InkPoint(50f, -10f, 1f)
        val end = InkPoint(50f, 10f, 1f)
        eraser.hits(listOf(stroke), null, start, radius = 2f)
        reads = 0

        repeat(10) { assertSame(stroke, eraser.hits(listOf(stroke), start, end, radius = 2f).single()) }
        assertEquals("Historical samples must not reread or resmooth cached strokes", 0, reads)

        eraser.reset()
        assertSame(stroke, eraser.hits(listOf(stroke), start, end, radius = 2f).single())
        assertTrue("Reset releases cached geometry", reads > 0)
    }

    @Test fun sharedAndSeededGeometrySurvivesResetWithoutChangingHighlightWidths() {
        val computed = mutableListOf<InkStroke>()
        val cache = InkGeometryCache { computed.add(it); InkGeometry.segments(it) }
        val penSegments = cache.update(listOf(line)).single()
        val highlight = line.copy(points = line.points.map { it.copy(pressure = 0f) }, width = 12f, kind = InkKind.HIGHLIGHTER)
        val highlightSegments = InkGeometry.segments(highlight)
        cache.seed(highlight, highlightSegments)
        val eraser = InkEraser(cache::cachedSegments)

        repeat(2) {
            assertEquals(listOf(highlight), eraser.hits(listOf(line, highlight), null, InkPoint(50f, 6f, 1f), radius = 1f))
            assertSame(penSegments, cache.cachedSegments(line))
            assertSame(highlightSegments, cache.cachedSegments(highlight))
            assertTrue("Constant marker width must not overwrite shared pressure widths", highlightSegments.all { it.width == 0.4f })
            eraser.reset()
        }
        assertEquals(listOf(line), computed)
    }

    @Test fun aProviderMissUsesStandaloneGeometryWithoutRepopulatingTheRendererCache() {
        val cache = InkGeometryCache { error("A read-only lookup must not compute") }
        val eraser = InkEraser(cache::cachedSegments)
        assertSame(line, eraser.hits(listOf(line), null, InkPoint(50f, 0f, 1f), radius = 1f).single())
        assertNull(cache.cachedSegments(line))
    }

    private fun corner() = InkStroke(listOf(InkPoint(0f, 0f, 1f), InkPoint(100f, 0f, 1f), InkPoint(100f, 100f, 1f)), 0, 2f)
}
