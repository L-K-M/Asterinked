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
/** An action offered on a notice, like a snackbar's. */
internal class NoticeAction(val label: CharSequence, val run: () -> Unit)

internal class NoticeBar(context: Context) : MaxWidthLayout(context) {
    private val ui = Components(context)
    private val icon = ImageView(context)
    private val message = ui.text(TextStyle.MESSAGE).apply { maxLines = MAX_LINES }
    // A borderless text action, accent-tinted like a snackbar action.
    private val action = Button(context, null, android.R.attr.borderlessButtonStyle).apply {
        isAllCaps = false
        setTextAppearance(TextStyle.LABEL.appearance)
        setTextColor(ui.color(R.color.notice_action))
        minimumWidth = ui.dp(Size.TOUCH)
        minimumHeight = ui.dp(Size.TOUCH)
        setPadding(ui.dp(Space.M), 0, ui.dp(Space.M), 0)
        visibility = GONE
        setOnClickListener {
            // Dismiss first: an action that shows a follow-up notice (a failed
            // "Save copy" reporting its own error) must not kill what it raised.
            // Clear the tag so a double-tap during the fade cannot run it twice.
            val pending = tag as? NoticeAction
            tag = null
            dismiss()
            pending?.run()
        }
    }
    private val accessibility = context.getSystemService(AccessibilityManager::class.java)
    private val hide = Runnable { dismiss() }
    private var tone = Tone.INFO
    private val actionBounds = Rect()

    /** The message on screen, or null while hidden. */
    val shown: CharSequence? get() = if (isVisible) message.text else null

    /** The label of the action on the current notice, or null. */
    val actionLabel: CharSequence? get() = if (isVisible && action.isVisible) action.text else null

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
        addView(action, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply { marginStart = ui.dp(Space.S) })
        icon.importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        visibility = GONE
        setOnClickListener { dismiss() }
    }

    /** Returns false when an unread error suppressed the notice. */
    fun show(text: CharSequence, tone: Tone, action: NoticeAction? = null): Boolean {
        // Nothing that can wait wipes out an error the user has not read yet:
        // a success flash must not hide a failed write's Save copy action.
        if (tone != Tone.ERROR && this.tone == Tone.ERROR && isVisible) return false

        this.tone = tone
        message.text = text
        this.action.tag = action
        this.action.text = action?.label
        this.action.contentDescription = action?.label
        this.action.visibility = if (action == null) GONE else VISIBLE
        icon.setImageResource(tone.icon)
        icon.imageTintList = ColorStateList.valueOf(ui.color(tone.tint))
        removeCallbacks(hide)
        // An action needs longer than a glance: give the user time to reach it.
        val millis = if (action == null) tone.millis else ACTION_MILLIS
        // A notice with a control also earns the interactive-content extension.
        val controls = if (action == null) 0 else AccessibilityManager.FLAG_CONTENT_CONTROLS
        postDelayed(hide, accessibility.getRecommendedTimeoutMillis(millis,
            AccessibilityManager.FLAG_CONTENT_ICONS or AccessibilityManager.FLAG_CONTENT_TEXT or controls).toLong())
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
        if (!action.isShown) return false
        action.getHitRect(actionBounds)
        return actionBounds.contains(event.x.toInt(), event.y.toInt())
    }

    /** Runs after a shown notice has fully dismissed; notices suppressed by it
     *  can then surface without waiting on another state change. */
    var onDismissed: (() -> Unit)? = null

    fun dismiss() {
        removeCallbacks(hide)
        if (visibility != VISIBLE) return

        animate().alpha(0f).translationY(ui.dp(Space.S).toFloat()).setDuration(Motion.SHORT).setInterpolator(Motion.EASING)
            .withEndAction { visibility = GONE; onDismissed?.invoke() }.start()
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(hide)
        super.onDetachedFromWindow()
    }

    private companion object {
        const val MAX_LINES = 5
        const val ACTION_MILLIS = 10_000
    }
}
