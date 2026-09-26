package ch.lkmc.asterinked.ink

import org.junit.Assert.assertEquals
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
}
