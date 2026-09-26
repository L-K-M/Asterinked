package ch.lkmc.asterinked.ink

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import kotlin.math.ceil
import kotlin.math.hypot
import kotlin.random.Random

class InkIncrementalTest {
    @Test fun batchGeometryMatchesTheOriginalAlgorithm() {
        for (seed in 0 until 40) {
            val stroke = randomStroke(Random(seed))
            assertEquals("seed $seed", reference(stroke), InkGeometry.segments(stroke))
        }
    }

    @Test fun everyLivePrefixMatchesBatchGeometry() {
        for (seed in 0 until 20) {
            val stroke = randomStroke(Random(seed))
            val builder = InkStrokeBuilder(stroke.width)
            stroke.points.forEachIndexed { index, point ->
                builder.add(point)
                val prefix = stroke.copy(points = stroke.points.subList(0, index + 1))
                assertEquals("seed $seed, prefix ${index + 1}", reference(prefix), builder.settled + builder.tail)
            }
        }
    }

    @Test fun invalidSamplesAreSkippedLikeTheBatchPath() {
        val points = listOf(InkPoint(Float.NaN, 1f, 1f), InkPoint(5f, 5f, 2f), InkPoint(6f, Float.POSITIVE_INFINITY, 1f), InkPoint(9f, 4f, Float.NaN))
        val builder = InkStrokeBuilder(Float.NaN)
        points.forEach(builder::add)
        assertEquals(reference(InkStroke(points, 0, Float.NaN)), builder.segments())
    }

    @Test fun cacheSmoothsOnlyNewStrokes() {
        val computed = mutableListOf<InkStroke>()
        val cache = InkGeometryCache { computed += it; InkGeometry.segments(it) }
        val first = randomStroke(Random(1))
        val second = randomStroke(Random(2))
        val third = randomStroke(Random(3))

        cache.update(listOf(first))
        val geometry = cache.update(listOf(first, second))
        assertEquals(listOf(first, second), computed)

        cache.seed(third, InkGeometry.segments(third))
        val withSeed = cache.update(listOf(first, second, third))
        assertEquals("Seeded and cached strokes are reused", 2, computed.size)
        assertSame(geometry[0], withSeed[0])

        cache.update(listOf(second))
        cache.update(listOf(first, second))
        assertEquals("Removed strokes are forgotten", listOf(first, second, first), computed)
    }

    @Test fun structurallyEqualStrokesKeepSeparateEntries() {
        val computed = mutableListOf<InkStroke>()
        val cache = InkGeometryCache { computed += it; InkGeometry.segments(it) }
        val stroke = randomStroke(Random(4))
        cache.update(listOf(stroke, stroke.copy()))
        assertEquals(2, computed.size)
    }

    private fun randomStroke(random: Random): InkStroke {
        val count = 1 + random.nextInt(40)
        var x = random.nextFloat() * 500f
        var y = random.nextFloat() * 700f
        val points = List(count) {
            x += random.nextFloat() * 30f - 15f
            y += random.nextFloat() * 30f - 15f
            InkPoint(x, y, random.nextFloat() * 1.2f - 0.1f)
        }
        return InkStroke(points, random.nextInt(), 0.5f + random.nextFloat() * 5f)
    }

    /** InkGeometry.segments as shipped in v0.1.0, kept verbatim as the export contract. */
    private fun reference(stroke: InkStroke): List<InkSegment> {
        val minWidth = 0.4f
        val segmentLength = 0.75f
        val maxSpanSteps = 64
        fun widthAt(width: Float, pressure: Float) = (width * pressure).coerceAtLeast(minWidth)
        fun distance(a: InkPoint, b: InkPoint) = hypot(a.x.toDouble() - b.x, a.y.toDouble() - b.y)
        fun midpoint(a: InkPoint, b: InkPoint) = InkPoint(a.x / 2f + b.x / 2f, a.y / 2f + b.y / 2f, (a.pressure + b.pressure) / 2f)
        fun quadratic(a: InkPoint, control: InkPoint, b: InkPoint, t: Float): InkPoint {
            val remaining = 1f - t
            val first = remaining * remaining
            val middle = 2f * remaining * t
            val last = t * t
            return InkPoint(first * a.x + middle * control.x + last * b.x,
                first * a.y + middle * control.y + last * b.y,
                first * a.pressure + middle * control.pressure + last * b.pressure)
        }

        val points = stroke.points.filter { it.x.isFinite() && it.y.isFinite() }.map {
            it.copy(pressure = if (it.pressure.isFinite()) it.pressure.coerceIn(0f, 1f) else 0f)
        }
        if (points.isEmpty()) return emptyList()
        val width = stroke.width.takeIf { it.isFinite() && it > 0f } ?: 2.2f
        if (points.size == 1) return listOf(InkSegment(points.first(), points.first(), widthAt(width, points.first().pressure)))
        val result = ArrayList<InkSegment>()
        var start = points.first()
        for (index in points.indices) {
            val control = points[index]
            val end = if (index == points.lastIndex) points.last() else midpoint(control, points[index + 1])
            val length = distance(start, control) + distance(control, end)
            val steps = ceil(length / segmentLength).toInt().coerceIn(1, maxSpanSteps)
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
}
