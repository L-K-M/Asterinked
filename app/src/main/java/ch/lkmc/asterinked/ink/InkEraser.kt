package ch.lkmc.asterinked.ink

import java.util.IdentityHashMap
import kotlin.math.max
import kotlin.math.min

/**
 * Finds whole strokes touching a swept eraser disc, in page units. Cached
 * rendered segments preserve smoothing, pressure widths and round caps.
 */
internal class InkEraser {
    private class StrokeGeometry(
        val segments: List<InkSegment>,
        val left: Double, val top: Double, val right: Double, val bottom: Double,
    )

    private val geometry = IdentityHashMap<InkStroke, StrokeGeometry>()

    /** Releases a gesture's cache, including erased and off-page stroke instances. */
    fun reset() = geometry.clear()

    /**
     * Returns original stroke instances in input order. Null [from] tests only
     * the disc at [to]; otherwise the entire continuous sweep is tested.
     */
    fun hits(strokes: List<InkStroke>, from: InkPoint?, to: InkPoint, radius: Float): List<InkStroke> {
        val start = from ?: to
        if (!start.x.isFinite() || !start.y.isFinite() || !to.x.isFinite() || !to.y.isFinite() ||
            !radius.isFinite() || radius < 0f) return emptyList()

        val left = min(start.x.toDouble(), to.x.toDouble()) - radius
        val top = min(start.y.toDouble(), to.y.toDouble()) - radius
        val right = max(start.x.toDouble(), to.x.toDouble()) + radius
        val bottom = max(start.y.toDouble(), to.y.toDouble()) + radius
        val result = ArrayList<InkStroke>()
        for (stroke in strokes) {
            val cached = geometry.getOrPut(stroke) { geometryOf(stroke) }
            // One conservative rejection per stroke, including its rendered width.
            if (right < cached.left || left > cached.right || bottom < cached.top || top > cached.bottom) continue
            if (touches(cached.segments, start, to, radius.toDouble())) result.add(stroke)
        }
        return result
    }

    private fun touches(segments: List<InkSegment>, start: InkPoint, end: InkPoint, radius: Double): Boolean {
        for (index in segments.indices) {
            val segment = segments[index]
            val reach = radius + segment.width.toDouble() / 2
            if (segmentDistanceSquared(start, end, segment.start, segment.end) <= reach * reach) return true
        }
        return false
    }

    private fun geometryOf(stroke: InkStroke): StrokeGeometry {
        val smoothed = InkGeometry.segments(stroke)
        val segments = when (stroke.kind) {
            InkKind.PEN -> smoothed
            InkKind.HIGHLIGHTER -> {
                val width = InkGeometry.strokeWidth(stroke.width)
                smoothed.map { it.copy(width = width) }
            }
        }

        var left = Double.POSITIVE_INFINITY
        var top = Double.POSITIVE_INFINITY
        var right = Double.NEGATIVE_INFINITY
        var bottom = Double.NEGATIVE_INFINITY
        for (segment in segments) {
            val halfWidth = segment.width.toDouble() / 2
            left = min(left, min(segment.start.x.toDouble(), segment.end.x.toDouble()) - halfWidth)
            top = min(top, min(segment.start.y.toDouble(), segment.end.y.toDouble()) - halfWidth)
            right = max(right, max(segment.start.x.toDouble(), segment.end.x.toDouble()) + halfWidth)
            bottom = max(bottom, max(segment.start.y.toDouble(), segment.end.y.toDouble()) + halfWidth)
        }
        return StrokeGeometry(segments, left, top, right, bottom)
    }

    private fun segmentDistanceSquared(a: InkPoint, b: InkPoint, c: InkPoint, d: InkPoint): Double {
        // Crossing centerlines touch; otherwise a closest pair includes an endpoint.
        // Widen before arithmetic so long sweeps cannot overflow Float distances.
        val abX = b.x.toDouble() - a.x
        val abY = b.y.toDouble() - a.y
        val cdX = d.x.toDouble() - c.x
        val cdY = d.y.toDouble() - c.y
        val acX = c.x.toDouble() - a.x
        val acY = c.y.toDouble() - a.y
        val cross = abX * cdY - abY * cdX
        if (cross != 0.0) {
            val alongAB = (acX * cdY - acY * cdX) / cross
            val alongCD = (acX * abY - acY * abX) / cross
            if (alongAB >= 0.0 && alongAB <= 1.0 && alongCD >= 0.0 && alongCD <= 1.0) return 0.0
        }

        return min(
            min(distanceToSegmentSquared(a, c, d), distanceToSegmentSquared(b, c, d)),
            min(distanceToSegmentSquared(c, a, b), distanceToSegmentSquared(d, a, b)),
        )
    }

    private fun distanceToSegmentSquared(point: InkPoint, a: InkPoint, b: InkPoint): Double {
        val dx = b.x.toDouble() - a.x
        val dy = b.y.toDouble() - a.y
        val lengthSquared = dx * dx + dy * dy
        val t = if (lengthSquared == 0.0) 0.0 else
            (((point.x.toDouble() - a.x) * dx + (point.y.toDouble() - a.y) * dy) / lengthSquared).coerceIn(0.0, 1.0)
        val offsetX = a.x + t * dx - point.x
        val offsetY = a.y + t * dy - point.y
        return offsetX * offsetX + offsetY * offsetY
    }
}
