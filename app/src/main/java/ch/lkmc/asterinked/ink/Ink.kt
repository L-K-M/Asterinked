package ch.lkmc.asterinked.ink

import java.util.IdentityHashMap
import kotlin.math.ceil
import kotlin.math.hypot

internal data class InkPoint(val x: Float, val y: Float, val pressure: Float)
internal data class InkStroke(val points: List<InkPoint>, val color: Int, val width: Float)
internal data class InkSegment(val start: InkPoint, val end: InkPoint, val width: Float)

internal object InkGeometry {
    private const val MIN_WIDTH = 0.4f
    private const val DEFAULT_WIDTH = 2.2f
    private const val SEGMENT_LENGTH = 0.75f
    private const val MAX_SPAN_STEPS = 64

    fun segments(stroke: InkStroke): List<InkSegment> {
        val builder = InkStrokeBuilder(stroke.width)
        stroke.points.forEach(builder::add)
        return builder.segments()
    }

    internal fun sanitize(point: InkPoint): InkPoint? {
        if (!point.x.isFinite() || !point.y.isFinite()) return null
        return point.copy(pressure = if (point.pressure.isFinite()) point.pressure.coerceIn(0f, 1f) else 0f)
    }

    internal fun strokeWidth(width: Float): Float = width.takeIf { it.isFinite() && it > 0f } ?: DEFAULT_WIDTH

    internal fun dot(point: InkPoint, width: Float) = InkSegment(point, point, widthAt(width, point.pressure))

    // Midpoint quadratics round noisy corners while retaining the first and last sample.
    // Pressure follows the same curve, so both renderers use an identical taper.
    internal fun appendSpan(result: MutableList<InkSegment>, start: InkPoint, control: InkPoint, end: InkPoint, width: Float) {
        val length = distance(start, control) + distance(control, end)
        val steps = ceil(length / SEGMENT_LENGTH).toInt().coerceIn(1, MAX_SPAN_STEPS)
        var previous = start
        for (step in 1..steps) {
            val t = step.toFloat() / steps
            val point = if (step == steps) end else quadratic(start, control, end, t)
            result.add(InkSegment(previous, point, widthAt(width, (previous.pressure + point.pressure) / 2f)))
            previous = point
        }
    }

    internal fun midpoint(a: InkPoint, b: InkPoint) = InkPoint(
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

/**
 * Smooths a stroke one sample at a time and yields exactly the segments of
 * [InkGeometry.segments] for the samples added so far.
 *
 * Span i runs from the previous span's end, bends towards sample i and ends at
 * the midpoint between samples i and i+1. That end depends on nothing later, so
 * every span except the last is final once its successor sample arrives:
 *
 *     samples   p0 ---- p1 ---- p2 ---- p3
 *     settled   [p0..m01] [m01..m12] [m12..m23]
 *     tail                                  [m23..p3]   (redone on each add)
 *
 * A live stroke therefore costs O(new samples) per frame instead of
 * re-smoothing the whole stroke, while preview and export stay identical.
 */
internal class InkStrokeBuilder(width: Float) {
    private val width = InkGeometry.strokeWidth(width)
    private var previous: InkPoint? = null
    private val settledSegments = ArrayList<InkSegment>()
    private var tailSegments = emptyList<InkSegment>()
    private var spanStart: InkPoint? = null

    /** Segments that later samples can no longer change. */
    val settled: List<InkSegment> get() = settledSegments

    /** The open end of the stroke, recomputed whenever a sample arrives. */
    val tail: List<InkSegment> get() = tailSegments

    fun add(sample: InkPoint) {
        val point = InkGeometry.sanitize(sample) ?: return
        val control = previous
        previous = point
        if (control == null) {
            tailSegments = listOf(InkGeometry.dot(point, width))
            return
        }

        // Only the first span starts at a sample (the first one) rather than a midpoint.
        val end = InkGeometry.midpoint(control, point)
        InkGeometry.appendSpan(settledSegments, spanStart ?: control, control, end, width)
        spanStart = end
        tailSegments = ArrayList<InkSegment>().also { InkGeometry.appendSpan(it, end, point, point, width) }
    }

    fun segments(): List<InkSegment> = settledSegments + tailSegments
}

/**
 * Keeps smoothed geometry per stroke instance. Stroke lists are immutable and
 * share their unchanged strokes, so after an edit only new strokes are smoothed.
 * Keys are compared by identity: structural hashing would walk every point.
 */
internal class InkGeometryCache(private val compute: (InkStroke) -> List<InkSegment> = InkGeometry::segments) {
    private var entries = IdentityHashMap<InkStroke, List<InkSegment>>()

    /** Offers geometry that is already known, such as a live stroke's builder output. */
    fun seed(stroke: InkStroke, segments: List<InkSegment>) {
        entries[stroke] = segments
    }

    /** Returns geometry for [strokes] in order and forgets strokes no longer present. */
    fun update(strokes: List<InkStroke>): List<List<InkSegment>> {
        val next = IdentityHashMap<InkStroke, List<InkSegment>>(strokes.size)
        val result = strokes.map { stroke -> next.getOrPut(stroke) { entries[stroke] ?: compute(stroke) } }
        entries = next
        return result
    }
}
