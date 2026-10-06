package ch.lkmc.asterinked.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.View
import androidx.core.graphics.ColorUtils

/**
 * A round choice in the tool bar (an ink colour or a pen width). It draws a
 * filled dot and, when selected, a ring around it that grows in. Selection and
 * enabled state come from the standard View flags, so TalkBack announces them.
 *
 * A dot too close to the bar colour (graphite in the dark theme, yellow
 * highlighter in the light one) gets a hairline outline so it stays visible.
 */
internal class ChoiceDot(context: Context) : View(context) {
    private val density = resources.displayMetrics.density
    private val dot = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = RING_WIDTH_DP * density
    }
    private val edge = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = EDGE_WIDTH_DP * density
    }
    // 0 = no ring, 1 = full ring.
    private var ringProgress = 0f
    private var ringAnimator: ValueAnimator? = null

    var fill = Color.BLACK
        set(value) {
            field = value
            invalidate()
        }

    /** Radius of the dot in dp. */
    var radius = DEFAULT_RADIUS_DP
        set(value) {
            field = value
            invalidate()
        }

    var ringColor = Color.BLACK
        set(value) {
            field = value
            invalidate()
        }

    var outlineColor = Color.GRAY
        set(value) {
            field = value
            invalidate()
        }

    /** The colour behind the dot, to decide whether it needs an outline. */
    var backdrop = Color.WHITE
        set(value) {
            field = value
            invalidate()
        }

    init {
        isClickable = true
        isFocusable = true
    }

    /** Whether [color] would be hard to see on the [backdrop]. */
    fun blendsIn(color: Int): Boolean =
        ColorUtils.calculateContrast(color or OPAQUE_MASK, backdrop or OPAQUE_MASK) < MIN_CONTRAST

    override fun setSelected(selected: Boolean) {
        val changed = selected != isSelected
        super.setSelected(selected)
        if (!changed) return

        ringAnimator?.cancel()
        val target = if (selected) 1f else 0f
        if (!isLaidOut) {
            ringProgress = target
            invalidate()
            return
        }
        ringAnimator = ValueAnimator.ofFloat(ringProgress, target).apply {
            duration = Motion.SHORT
            interpolator = Motion.EASING
            addUpdateListener {
                ringProgress = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val x = width / 2f
        val y = height / 2f
        val alpha = if (isEnabled) Alpha.OPAQUE else Alpha.DISABLED
        val radiusPx = radius * density
        dot.color = fill
        dot.alpha = alpha
        canvas.drawCircle(x, y, radiusPx, dot)
        if (blendsIn(fill)) {
            edge.color = outlineColor
            edge.alpha = alpha
            canvas.drawCircle(x, y, radiusPx - edge.strokeWidth / 2f, edge)
        }
        if (ringProgress <= 0f) return

        ring.color = ringColor
        ring.alpha = (alpha * ringProgress).toInt()
        val ringRadius = RING_RADIUS_DP * density * (RING_START_SCALE + (1f - RING_START_SCALE) * ringProgress)
        canvas.drawCircle(x, y, ringRadius, ring)
    }

    override fun onDetachedFromWindow() {
        ringAnimator?.cancel()
        super.onDetachedFromWindow()
    }

    private companion object {
        const val DEFAULT_RADIUS_DP = 11f
        const val RING_RADIUS_DP = 16f
        const val RING_WIDTH_DP = 2f
        const val RING_START_SCALE = 0.8f
        const val EDGE_WIDTH_DP = 1f
        // Highlighter yellow on the light bar is about 1.3:1, graphite on the dark
        // bar about 1.2:1; below this the outline carries the shape.
        const val MIN_CONTRAST = 1.6
        const val OPAQUE_MASK = 0xFF000000.toInt()
    }
}
