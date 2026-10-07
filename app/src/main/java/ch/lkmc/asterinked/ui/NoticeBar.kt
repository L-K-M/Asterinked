package ch.lkmc.asterinked.ui

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Rect
import android.view.Gravity
import android.view.MotionEvent
import android.view.accessibility.AccessibilityManager
import android.widget.Button
import android.widget.ImageView
import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.core.view.isVisible
import ch.lkmc.asterinked.R

/** What a notice reports; picks its icon and how long it stays. */
internal enum class Tone(@param:DrawableRes val icon: Int, @param:ColorRes val tint: Int, val millis: Int) {
    INFO(R.drawable.ic_info, R.color.inverse_on_surface, 3_000),
    SUCCESS(R.drawable.ic_success, R.color.notice_success, 3_000),
    // Errors say what to do next, so they stay long enough to read.
    ERROR(R.drawable.ic_error, R.color.notice_error, 7_000),
}

/**
 * A short message floating above the tools, in place of toasts: Android 12+
 * cuts toasts to two lines, and the error messages here are longer. It slides
 * in, hides by itself (later if the user asked for more time in the
 * accessibility settings) and hides at once when tapped. TalkBack reads it as
 * a polite live region.
 */
internal class NoticeBar(context: Context) : MaxWidthLayout(context) {
    private val ui = Components(context)
    private val icon = ImageView(context)
    private val message = ui.text(TextStyle.MESSAGE).apply { maxLines = MAX_LINES }
    private val accessibility = context.getSystemService(AccessibilityManager::class.java)
    private val hide = Runnable { dismiss() }
    private var tone = Tone.INFO
    private val actionBounds = Rect()
    private val actionButton = Button(context).apply {
        visibility = GONE
        setTextAppearance(android.R.style.TextAppearance_Material_Widget_Button)
        setPadding(ui.dp(Space.S), 0, ui.dp(Space.S), 0)
        minimumHeight = 0
        minimumWidth = 0
    }

    /** The message on screen, or null while hidden. */
    val shown: CharSequence? get() = if (isVisible) message.text else null

    init {
        maxWidth = ui.dp(Size.NOTICE_MAX)
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        background = ui.rounded(ui.color(R.color.inverse_surface), Radius.MEDIUM)
        elevation = ui.dp(Elevation.NOTICE).toFloat()
        setPadding(ui.dp(Space.L), ui.dp(Space.M), ui.dp(Space.L), ui.dp(Space.M))
        minimumHeight = ui.dp(Size.TOUCH)
        accessibilityLiveRegion = ACCESSIBILITY_LIVE_REGION_POLITE
        addView(icon, LayoutParams(ui.dp(Size.ICON_SMALL), ui.dp(Size.ICON_SMALL)).apply { marginEnd = ui.dp(Space.M) })
        addView(message, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = ui.dp(Space.S) })
        addView(actionButton, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
        icon.importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        visibility = GONE
        setOnClickListener { dismiss() }
    }

    /** Shows [text]; false if it gave way to an error still on screen. */
    fun show(text: CharSequence, tone: Tone, actionLabel: CharSequence? = null, action: (() -> Unit)? = null): Boolean {
        // A tool hint must not wipe out an error the user has not read yet.
        if (tone == Tone.INFO && this.tone == Tone.ERROR && isVisible && action == null) return false

        this.tone = tone
        message.text = text
        icon.setImageResource(tone.icon)
        icon.imageTintList = ColorStateList.valueOf(ui.color(tone.tint))
        if (actionLabel != null && action != null) {
            actionButton.text = actionLabel
            actionButton.visibility = VISIBLE
            actionButton.setOnClickListener { action(); dismiss() }
        } else {
            actionButton.visibility = GONE
            actionButton.setOnClickListener(null)
        }
        removeCallbacks(hide)
        postDelayed(hide, accessibility.getRecommendedTimeoutMillis(tone.millis,
            AccessibilityManager.FLAG_CONTENT_ICONS or AccessibilityManager.FLAG_CONTENT_TEXT).toLong())
        // Restarting the animation also cancels a dismissal in progress.
        if (visibility != VISIBLE) {
            visibility = VISIBLE
            alpha = 0f
            translationY = ui.dp(Space.L).toFloat()
        }
        animate().alpha(1f).translationY(0f).setDuration(Motion.MEDIUM).setInterpolator(Motion.EASING).start()
        return true
    }

    // A notice floats over the page, often where the user writes next. A pen
    // touch outside its action goes to the page underneath (by refusing the
    // DOWN, the parent offers it to the next view there), and puts a hint away;
    // errors stay until read. Finger taps still dismiss.
    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked != MotionEvent.ACTION_DOWN || !isPen(event) || onAction(event)) return super.dispatchTouchEvent(event)

        if (tone == Tone.INFO) dismiss()
        return false
    }

    private fun isPen(event: MotionEvent): Boolean {
        val tool = event.getToolType(event.actionIndex)
        return tool == MotionEvent.TOOL_TYPE_STYLUS || tool == MotionEvent.TOOL_TYPE_ERASER
    }

    private fun onAction(event: MotionEvent): Boolean {
        if (!actionButton.isShown) return false
        actionButton.getHitRect(actionBounds)
        return actionBounds.contains(event.x.toInt(), event.y.toInt())
    }

    fun dismiss() {
        removeCallbacks(hide)
        if (visibility != VISIBLE) return

        animate().alpha(0f).translationY(ui.dp(Space.S).toFloat()).setDuration(Motion.SHORT).setInterpolator(Motion.EASING)
            .withEndAction { visibility = GONE }.start()
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(hide)
        super.onDetachedFromWindow()
    }

    private companion object {
        const val MAX_LINES = 5
    }
}
