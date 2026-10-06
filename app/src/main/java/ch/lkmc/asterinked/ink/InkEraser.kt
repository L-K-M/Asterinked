package ch.lkmc.asterinked.ink

import java.util.IdentityHashMap
import kotlin.math.ceil
import kotlin.math.hypot
import kotlin.math.max

/**
 * Finds strokes under a moving eraser, in page units. A stroke is hit when
 * the eraser disc touches its centerline widened by half the stroke's full
 * width. Within one gesture, bounds are cached per stroke instance so most
 * strokes are rejected without walking their points.
 */
internal class InkEraser {
    private class Bounds(val left: Float, val top: Float, val right: Float, val bottom: Float)

    private val bounds = IdentityHashMap<InkStroke, Bounds>()

    /** Ends a gesture: forgets cached bounds so erased or off-page strokes can be collected. */
    fun reset() = bounds.clear()

    /**
     * Returns strokes touched while the eraser moved from [from] (null at the
     * start of a gesture) to [to]. A fast drag is walked in steps no longer than
     * the radius, so a thin stroke between two samples is not skipped.
     */
    fun hits(strokes: List<InkStroke>, from: InkPoint?, to: InkPoint, radius: Float): List<InkStroke> {
        val start = from ?: to
        val distance = hypot(to.x - start.x, to.y - start.y)
        val steps = ceil(distance / max(radius, MIN_STEP)).toInt().coerceIn(1, MAX_STEPS)
        val probes = List(steps) { step ->
            val t = (step + 1).toFloat() / steps
            start.x + (to.x - start.x) * t to start.y + (to.y - start.y) * t
        } + listOf(start.x to start.y)
        return strokes.filter { stroke -> probes.any { (x, y) -> touches(stroke, x, y, radius) } }
    }

    private fun touches(stroke: InkStroke, x: Float, y: Float, radius: Float): Boolean {
        val reach = radius + stroke.width / 2f
        val box = bounds.getOrPut(stroke) { boundsOf(stroke) }
        if (x < box.left - reach || x > box.right + reach || y < box.top - reach || y > box.bottom + reach) return false

        val points = stroke.points
        if (points.size == 1) return hypot(points[0].x - x, points[0].y - y) <= reach
        for (index in 1 until points.size) {
            if (distanceToSegment(x, y, points[index - 1], points[index]) <= reach) return true
        }
        return false
    }

    private fun boundsOf(stroke: InkStroke): Bounds {
        val finite = stroke.points.filter { it.x.isFinite() && it.y.isFinite() }
        if (finite.isEmpty()) return Bounds(Float.NaN, Float.NaN, Float.NaN, Float.NaN)
        return Bounds(finite.minOf { it.x }, finite.minOf { it.y }, finite.maxOf { it.x }, finite.maxOf { it.y })
    }

    private fun distanceToSegment(x: Float, y: Float, a: InkPoint, b: InkPoint): Float {
        val dx = b.x - a.x
        val dy = b.y - a.y
        val lengthSquared = dx * dx + dy * dy
        val t = if (lengthSquared == 0f) 0f else (((x - a.x) * dx + (y - a.y) * dy) / lengthSquared).coerceIn(0f, 1f)
        return hypot(a.x + t * dx - x, a.y + t * dy - y)
    }

    private companion object {
        const val MIN_STEP = 0.5f
        const val MAX_STEPS = 256
    }
}
