package ch.lkmc.asterinked.ink

import kotlin.math.ceil
import kotlin.math.hypot

internal data class InkPoint(val x: Float, val y: Float, val pressure: Float)

/**
 * How a stroke is drawn. A pen tapers with pressure. A highlighter keeps its full
 * width and multiplies its colour with the page, so text underneath stays readable.
 */
internal enum class InkKind { PEN, HIGHLIGHTER }

internal data class InkStroke(val points: List<InkPoint>, val color: Int, val width: Float, val kind: InkKind = InkKind.PEN)
internal data class InkSegment(val start: InkPoint, val end: InkPoint, val width: Float)

internal object InkGeometry {
    private const val MIN_WIDTH = 0.4f
    private const val DEFAULT_WIDTH = 2.2f
    private const val SEGMENT_LENGTH = 0.75f
    private const val MAX_SPAN_STEPS = 64

    fun segments(stroke: InkStroke): List<InkSegment> {
        val points = stroke.points.filter { it.x.isFinite() && it.y.isFinite() }.map {
            it.copy(pressure = if (it.pressure.isFinite()) it.pressure.coerceIn(0f, 1f) else 0f)
        }
        if (points.isEmpty()) return emptyList()
        val width = stroke.width.takeIf { it.isFinite() && it > 0f } ?: DEFAULT_WIDTH
        if (points.size == 1) return listOf(InkSegment(points.first(), points.first(), widthAt(width, points.first().pressure)))

        val result = ArrayList<InkSegment>()
        var start = points.first()
        // Midpoint quadratics round noisy corners while retaining the first and last sample.
        // Pressure follows the same curve, so both renderers use an identical taper.
        for (index in points.indices) {
            val control = points[index]
            val end = if (index == points.lastIndex) points.last() else midpoint(control, points[index + 1])
            val length = distance(start, control) + distance(control, end)
            val steps = ceil(length / SEGMENT_LENGTH).toInt().coerceIn(1, MAX_SPAN_STEPS)
            var previous = start
            for (step in 1..steps) {
                val t = step.toFloat() / steps
                val point = if (step == steps) end else quadratic(start, control, end, t)
                result.add(InkSegment(previous, point, widthAt(width, (previous.pressure + point.pressure) / 2f)))
                previous = point
            }
            start = end
        }
        return result
    }

    /**
     * The smoothed centerline of a stroke: the same curve as [segments], as one
     * polyline. Highlighter strokes are drawn along it at constant width.
     */
    fun centerline(stroke: InkStroke): List<InkPoint> {
        val segments = segments(stroke)
        if (segments.isEmpty()) return emptyList()
        return listOf(segments.first().start) + segments.map { it.end }
    }

    private fun midpoint(a: InkPoint, b: InkPoint) = InkPoint(
        a.x / 2f + b.x / 2f, a.y / 2f + b.y / 2f, (a.pressure + b.pressure) / 2f,
    )

    private fun quadratic(a: InkPoint, control: InkPoint, b: InkPoint, t: Float): InkPoint {
        val remaining = 1f - t
        val first = remaining * remaining
        val middle = 2f * remaining * t
        val last = t * t
        return InkPoint(first * a.x + middle * control.x + last * b.x,
            first * a.y + middle * control.y + last * b.y,
            first * a.pressure + middle * control.pressure + last * b.pressure)
    }

    private fun distance(a: InkPoint, b: InkPoint): Double = hypot(a.x.toDouble() - b.x, a.y.toDouble() - b.y)
    private fun widthAt(width: Float, pressure: Float): Float = (width * pressure).coerceAtLeast(MIN_WIDTH)
}
