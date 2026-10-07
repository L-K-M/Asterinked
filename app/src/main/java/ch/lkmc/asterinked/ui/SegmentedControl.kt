package ch.lkmc.asterinked.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.widget.LinearLayout
import androidx.core.graphics.ColorUtils
import ch.lkmc.asterinked.R

/**
 * Mutually exclusive choices on a shared track. A raised thumb slides to the
 * chosen segment and its icon fills in:
 *
 *   ( [✎]  ▮   ⌫  )   track: surface_track, full pill
 *      ^ thumb: surface_thumb, inset from the segment, soft shadow
 *
 * Segments are ordinary views ([Components.segment]); the chosen one carries
 * the standard selected flag, which drives its icon and TalkBack state.
 */
internal class SegmentedControl(context: Context) : LinearLayout(context) {
    private val density = resources.displayMetrics.density
    private val inset = (Size.TOUCH - Size.THUMB) / 2f * density
    private val trackColor = context.getColor(R.color.surface_track)
    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = trackColor }
    private val thumbPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val thumbColor = context.getColor(R.color.surface_thumb)
    private val shadowColor = context.getColor(R.color.shadow)
    // A translucent thumb would let its own shadow show through and look darker
    // than the track. Disabled, it is painted opaque instead, in the colour the
    // dimmed thumb would have over the dimmed track on the bar.
    private val disabledThumbColor = ColorUtils.compositeColors(
        ColorUtils.setAlphaComponent(thumbColor, Alpha.DISABLED),
        ColorUtils.compositeColors(ColorUtils.setAlphaComponent(trackColor, Alpha.DISABLED), context.getColor(R.color.surface)),
    )
    private val track = RectF()
    private val thumb = RectF()
    private var thumbX = 0f
    private var animator: ValueAnimator? = null
    private var chosen = NONE

    init {
        orientation = HORIZONTAL
        setWillNotDraw(false)
    }

    /** Moves the thumb to [index]; it slides once the control is on screen. */
    fun select(index: Int) {
        if (index == chosen) return
        for (child in 0 until childCount) getChildAt(child).isSelected = child == index
        val slide = chosen != NONE && isLaidOut
        chosen = index
        animator?.cancel()
        if (!slide) {
            invalidate()
            return
        }

        animator = ValueAnimator.ofFloat(thumbX, targetX()).apply {
            duration = Motion.MEDIUM
            interpolator = Motion.EASING
            addUpdateListener {
                thumbX = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    override fun setEnabled(enabled: Boolean) {
        super.setEnabled(enabled)
        for (child in 0 until childCount) getChildAt(child).isEnabled = enabled
        invalidate()
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        super.onLayout(changed, l, t, r, b)
        if (animator?.isRunning != true) thumbX = targetX()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val alpha = if (isEnabled) Alpha.OPAQUE else Alpha.DISABLED
        track.set(0f, 0f, width.toFloat(), height.toFloat())
        trackPaint.alpha = alpha
        canvas.drawRoundRect(track, height / 2f, height / 2f, trackPaint)
        val segment = getChildAt(chosen) ?: return

        thumb.set(thumbX + inset, segment.top + inset, thumbX + segment.width - inset, segment.bottom - inset)
        if (isEnabled) {
            thumbPaint.color = thumbColor
            thumbPaint.setShadowLayer(SHADOW_RADIUS_DP * density, 0f, SHADOW_OFFSET_DP * density, shadowColor)
        } else {
            thumbPaint.color = disabledThumbColor
            thumbPaint.clearShadowLayer()
        }
        canvas.drawRoundRect(thumb, thumb.height() / 2f, thumb.height() / 2f, thumbPaint)
    }

    override fun onDetachedFromWindow() {
        animator?.cancel()
        super.onDetachedFromWindow()
    }

    private fun targetX(): Float = getChildAt(chosen)?.left?.toFloat() ?: 0f

    private companion object {
        const val NONE = -1
        const val SHADOW_RADIUS_DP = 2f
        const val SHADOW_OFFSET_DP = 0.5f
    }
}
