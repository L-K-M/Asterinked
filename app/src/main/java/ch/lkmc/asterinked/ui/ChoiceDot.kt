package ch.lkmc.asterinked.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.View

/**
 * A round choice in the tool strip (an ink colour or a pen width). It draws a
 * filled dot and, when selected, a ring around it. Selection and enabled state
 * come from the standard View flags, so TalkBack announces them.
 */
internal class ChoiceDot(context: Context) : View(context) {
    private val density = resources.displayMetrics.density
    private val dot = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = RING_WIDTH_DP * density
    }

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

    init {
        isClickable = true
        isFocusable = true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val x = width / 2f
        val y = height / 2f
        dot.color = fill
        dot.alpha = if (isEnabled) OPAQUE else DISABLED_ALPHA
        canvas.drawCircle(x, y, radius * density, dot)
        if (!isSelected) return

        ring.color = ringColor
        ring.alpha = dot.alpha
        canvas.drawCircle(x, y, RING_RADIUS_DP * density, ring)
    }

    private companion object {
        const val DEFAULT_RADIUS_DP = 10f
        const val RING_RADIUS_DP = 15f
        const val RING_WIDTH_DP = 2f
        const val OPAQUE = 255
        const val DISABLED_ALPHA = 90
    }
}
