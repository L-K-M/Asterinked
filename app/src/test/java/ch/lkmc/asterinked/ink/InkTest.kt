package ch.lkmc.asterinked.ink

import org.junit.Assert.*
import org.junit.Test

class InkTest {
    @Test fun tapHasVisibleGeometry() {
        val point = InkPoint(12f, 24f, 0f)
        val segment = InkGeometry.segments(InkStroke(listOf(point), 0, 2f)).single()
        assertEquals(point, segment.start)
        assertEquals(point, segment.end)
        assertTrue(segment.width > 0f)
    }

    @Test fun curvesRetainEndpointsAndConnectWithoutGaps() {
        val points = listOf(InkPoint(0f, 0f, .2f), InkPoint(30f, 0f, .5f), InkPoint(30f, 30f, 1f))
        val segments = InkGeometry.segments(InkStroke(points, 0, 4f))
        assertEquals(points.first(), segments.first().start)
        assertEquals(points.last(), segments.last().end)
        segments.zipWithNext().forEach { (a, b) -> assertEquals(a.end, b.start) }
        assertTrue(segments.any { it.end.x < 30f && it.end.y > 0f })
        assertTrue(segments.first().width < segments.last().width)
        assertTrue(segments.all { it.width > 0f && it.width <= 4f })
    }

    @Test fun invalidPressureAndHugeDistanceStayBounded() {
        val points = listOf(InkPoint(0f, 0f, Float.NaN), InkPoint(1e20f, 0f, 7f))
        val segments = InkGeometry.segments(InkStroke(points, 0, 3f))
        assertTrue(segments.size <= 128)
        assertTrue(segments.all { it.width.isFinite() && it.width > 0f && it.width <= 3f })
        assertEquals(points.last().x, segments.last().end.x)
    }
}
