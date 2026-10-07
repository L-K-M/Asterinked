package ch.lkmc.asterinked.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.View
import android.view.animation.LinearInterpolator
import ch.lkmc.asterinked.R
import kotlin.math.min

/**
 * The brand asterisk drawing itself stroke by stroke, like someone jotting a
 * mark — the spinner of the loading screen. A faint ghost of the finished mark
 * sits underneath, so a paused or captured frame is never blank. The loop runs
 * only while the view is actually on screen.
 */
internal class AsteriskLoader(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        color = context.getColor(R.color.accent)
    }
    private var cycle = 0f
    private val animator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = CYCLE_MS
        interpolator = LinearInterpolator()
        repeatCount = ValueAnimator.INFINITE
        repeatMode = ValueAnimator.RESTART
        addUpdateListener {
            cycle = it.animatedValue as Float
            invalidate()
        }
    }

    /** For tests: whether the draw loop is ticking. */
    val animating: Boolean get() = animator.isStarted

    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        sync()
    }

    override fun onDetachedFromWindow() {
        animator.cancel()
        super.onDetachedFromWindow()
    }

    // Both callbacks feed the same gate: aggregated visibility is the reliable
    // signal on devices, plain visibility changes fire under Robolectric too.
    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        sync()
    }

    override fun onVisibilityAggregated(isVisible: Boolean) {
        super.onVisibilityAggregated(isVisible)
        sync()
    }

    private fun sync() {
        // start() on a running animator rewinds it; keep the loop idempotent.
        if (isAttachedToWindow && isShown) {
            if (!animator.isStarted) animator.start()
        } else {
            animator.cancel()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val arm = min(width, height) * ARM_REACH
        val cx = width / 2f
        val cy = height / 2f
        val strokeWidth = min(width, height) * STROKE_FRACTION

        // Ghost: where the strokes will land, faintly.
        paint.alpha = GHOST_ALPHA
        paint.strokeWidth = strokeWidth
        for ((sx, sy, ex, ey) in ARMS) {
            canvas.drawLine(cx + sx * arm, cy + sy * arm, cx + ex * arm, cy + ey * arm, paint)
        }

        val alpha = (255 * fade()).toInt()
        for ((index, armPoints) in ARMS.withIndex()) {
            val t = ((cycle - index * STAGGER) / DRAW).coerceIn(0f, 1f)
            if (t <= 0f) continue
            val eased = Motion.EASING.getInterpolation(t)
            paint.alpha = alpha
            // A stroke starts thin and settles to full width, like ink.
            paint.strokeWidth = strokeWidth * (THIN_START + (1f - THIN_START) * eased)
            val (sx, sy, ex, ey) = armPoints
            canvas.drawLine(cx + sx * arm, cy + sy * arm,
                cx + (sx + (ex - sx) * eased) * arm, cy + (sy + (ey - sy) * eased) * arm, paint)
        }
    }

    // Hold the finished mark, then let it fade back to the ghost and restart.
    private fun fade(): Float = ((FADE_END - cycle) / (FADE_END - FADE_START)).coerceIn(0f, 1f)

    private companion object {
        const val CYCLE_MS = 1800L
        // The mark's reach and weight, matched to the launcher asterisk.
        const val ARM_REACH = 0.36f
        const val STROKE_FRACTION = 0.11f
        const val GHOST_ALPHA = 30
        const val THIN_START = 0.55f
        // Of a cycle: each stroke starts this much after the previous one...
        const val STAGGER = 0.14f
        // ...draws over this much...
        const val DRAW = 0.30f
        // ...all hold until here, then fade to the ghost by FADE_END.
        const val FADE_START = 0.78f
        const val FADE_END = 0.95f

        // The three arms of the asterisk, each from one edge through the
        // middle to the other — the same strokes as drawable/ic_asterisk.
        val ARMS = listOf(
            floatArrayOf(0f, -1f, 0f, 1f),
            floatArrayOf(-0.87f, -0.5f, 0.87f, 0.5f),
            floatArrayOf(-0.87f, 0.5f, 0.87f, -0.5f),
        )
    }
}
