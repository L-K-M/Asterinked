package ch.lkmc.asterinked.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.view.View
import android.view.animation.DecelerateInterpolator

/** Draws the asterisk icon progressively using the app's ink style. */
internal class InkLoaderView(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.getColor(ch.lkmc.asterinked.R.color.accent)
        strokeWidth = 6f * resources.displayMetrics.density
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        style = Paint.Style.STROKE
    }

    // Approximate asterisk: 5 crossing lines in a star pattern.
    private val lines = listOf(
        Pair(-30f, -50f) to Pair(30f, 50f),
        Pair(-50f, 0f) to Pair(50f, 0f),
        Pair(-30f, 50f) to Pair(30f, -50f),
        Pair(-15f, -60f) to Pair(15f, 60f),
        Pair(-60f, -15f) to Pair(60f, 15f),
    )

    private var progress = 0f
    private val animator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 1200L
        interpolator = DecelerateInterpolator()
        addUpdateListener {
            progress = it.animatedValue as Float
            invalidate()
        }
    }

    init {
        setBackgroundColor(context.getColor(ch.lkmc.asterinked.R.color.surface))
    }

    fun startAnimation() {
        progress = 0f
        animator.start()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f
        val scale = minOf(width, height) / 120f
        val count = (lines.size * progress).toInt().coerceIn(0, lines.size)
        for (i in 0 until count) {
            val (a, b) = lines[i]
            canvas.drawLine(
                cx + a.x * scale, cy + a.y * scale,
                cx + b.x * scale, cy + b.y * scale,
                paint
            )
        }
    }
}
