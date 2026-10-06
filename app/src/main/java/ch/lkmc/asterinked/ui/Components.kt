package ch.lkmc.asterinked.ui

import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.animation.StateListAnimator
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.InsetDrawable
import android.graphics.drawable.RippleDrawable
import android.graphics.drawable.StateListDrawable
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.TextView
import android.widget.ToggleButton
import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.annotation.StyleRes
import androidx.core.view.AccessibilityDelegateCompat
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import ch.lkmc.asterinked.R
import kotlin.math.min
import kotlin.math.roundToInt

/** The type scale from styles.xml. */
internal enum class TextStyle(@param:StyleRes val appearance: Int) {
    DISPLAY(R.style.TextAppearance_Asterinked_Display),
    HEADLINE(R.style.TextAppearance_Asterinked_Headline),
    TITLE(R.style.TextAppearance_Asterinked_Title),
    BODY(R.style.TextAppearance_Asterinked_Body),
    LABEL(R.style.TextAppearance_Asterinked_Label),
    CAPTION(R.style.TextAppearance_Asterinked_Caption),
    COUNTER(R.style.TextAppearance_Asterinked_Counter),
    FIELD(R.style.TextAppearance_Asterinked_Field),
}

/** Visible pill height of a primary button; the touch area is never under 48dp. */
internal enum class ButtonSize(val heightDp: Int, val paddingDp: Int) {
    REGULAR(Size.BUTTON, Space.L + Space.XXS),
    LARGE(Size.BUTTON_LARGE, Space.XXL),
}

/** How TalkBack describes a selectable control: one of a set, or on and off. */
internal enum class Role(val className: String) {
    CHOICE(RadioButton::class.java.name),
    TOGGLE(ToggleButton::class.java.name),
}

/**
 * Factories for the app's repeated controls, so every icon button, toggle and
 * primary button looks and behaves the same. Screens are composed in code from
 * these; colours come from the theme tokens, sizes from [Size] and [Space].
 */
internal class Components(private val context: Context) {
    private val density = context.resources.displayMetrics.density

    fun dp(value: Int): Int = (value * density).roundToInt()

    fun color(@ColorRes id: Int): Int = context.getColor(id)

    fun text(style: TextStyle, value: CharSequence = ""): TextView = TextView(context).apply {
        setTextAppearance(style.appearance)
        text = value
    }

    /** A 48dp icon action with a round 40dp touch ripple. */
    fun iconButton(@DrawableRes icon: Int, @StringRes label: Int, action: () -> Unit): ImageButton = ImageButton(context).apply {
        setImageResource(icon)
        scaleType = ImageView.ScaleType.CENTER
        imageTintList = states(selected = color(R.color.on_surface), idle = color(R.color.on_surface))
        background = ripple(content = null, mask = disc(Size.BUTTON))
        describe(label)
        setOnClickListener { action() }
    }

    /**
     * An on/off icon. Off: outlined icon. On: filled icon on an accent disc,
     * because an active input mode changes what touches do and must be obvious.
     */
    fun toggleButton(@DrawableRes off: Int, @DrawableRes on: Int, @StringRes label: Int, action: () -> Unit): ImageButton =
        ImageButton(context).apply {
            setImageDrawable(selectable(off, on))
            scaleType = ImageView.ScaleType.CENTER
            imageTintList = states(selected = color(R.color.on_accent_container), idle = color(R.color.on_surface_variant))
            val disc = disc(Size.BUTTON, color(R.color.accent_container))
            background = ripple(content = whenSelected(disc), mask = disc(Size.BUTTON))
            describe(label)
            setRole(this, Role.TOGGLE)
            setOnClickListener { action() }
        }

    /** One segment of a [SegmentedControl], at [position]: outlined when idle, filled when chosen. */
    fun segment(@DrawableRes idle: Int, @DrawableRes chosen: Int, @StringRes label: Int, position: Int, action: () -> Unit): ImageButton =
        ImageButton(context).apply {
            setImageDrawable(selectable(idle, chosen))
            scaleType = ImageView.ScaleType.CENTER
            imageTintList = states(selected = color(R.color.on_surface), idle = color(R.color.on_surface_variant))
            background = ripple(content = null, mask = disc(Size.THUMB))
            describe(label)
            setRole(this, Role.CHOICE, position)
            setOnClickListener { action() }
        }

    /**
     * The screen's main action: a filled graphite pill. The view is at least
     * 48dp tall for touch while the visible pill is [ButtonSize.heightDp]; a
     * press shrinks it slightly.
     */
    fun primaryButton(@StringRes label: Int, size: ButtonSize, @DrawableRes icon: Int? = null, action: () -> Unit): Button =
        Button(context).apply {
            setText(label)
            setTextAppearance(TextStyle.LABEL.appearance)
            isAllCaps = false
            val content = ColorStateList(DISABLED_OR_DEFAULT, intArrayOf(color(R.color.on_surface_disabled), color(R.color.on_primary)))
            setTextColor(content)
            gravity = Gravity.CENTER
            minWidth = 0
            minimumWidth = 0
            val height = maxOf(size.heightDp, Size.TOUCH)
            minHeight = dp(height)
            minimumHeight = dp(height)
            val inset = dp((height - size.heightDp) / 2)
            val pill = GradientDrawable().apply {
                cornerRadius = dp(FULL_ROUND_DP).toFloat()
                color = ColorStateList(DISABLED_OR_DEFAULT, intArrayOf(color(R.color.primary_disabled), color(R.color.primary)))
            }
            background = RippleDrawable(ColorStateList.valueOf(withAlpha(color(R.color.on_primary), PRESSED_ALPHA)),
                InsetDrawable(pill, 0, inset, 0, inset), null)
            // After the background, which would otherwise replace the padding with its insets.
            setPadding(dp(size.paddingDp), 0, dp(size.paddingDp), 0)
            icon?.let {
                val drawable = context.getDrawable(it)!!.mutate().apply { setBounds(0, 0, dp(Size.ICON_SMALL), dp(Size.ICON_SMALL)) }
                setCompoundDrawablesRelative(drawable, null, null, null)
                compoundDrawablePadding = dp(Space.S)
                compoundDrawableTintList = content
            }
            stateListAnimator = pressShrink()
            setOnClickListener { action() }
        }

    /**
     * The primary action as a graphite disc with an icon, for when its label
     * would crowd out everything else (very large text on a phone).
     */
    fun primaryIconButton(@DrawableRes icon: Int, @StringRes label: Int, action: () -> Unit): ImageButton = ImageButton(context).apply {
        setImageResource(icon)
        minimumWidth = dp(Size.TOUCH)
        minimumHeight = dp(Size.TOUCH)
        scaleType = ImageView.ScaleType.CENTER
        imageTintList = ColorStateList(DISABLED_OR_DEFAULT, intArrayOf(color(R.color.on_surface_disabled), color(R.color.on_primary)))
        val fill = ColorStateList(DISABLED_OR_DEFAULT, intArrayOf(color(R.color.primary_disabled), color(R.color.primary)))
        val disc = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            color = fill
        }
        val inset = dp((Size.TOUCH - Size.BUTTON) / 2)
        background = RippleDrawable(ColorStateList.valueOf(withAlpha(color(R.color.on_primary), PRESSED_ALPHA)),
            InsetDrawable(disc, inset), null)
        describe(label)
        stateListAnimator = pressShrink()
        setOnClickListener { action() }
    }

    /** An ink colour or pen width at [position] in its row, announced as one of a set. */
    fun choice(@StringRes label: Int, position: Int, action: () -> Unit): ChoiceDot = ChoiceDot(context).apply {
        ringColor = color(R.color.on_surface)
        outlineColor = color(R.color.outline)
        backdrop = color(R.color.surface)
        background = ripple(content = null, mask = disc(Size.BUTTON))
        describe(label)
        setRole(this, Role.CHOICE, position)
        setOnClickListener { action() }
    }

    /** A thin vertical rule between related groups. */
    fun divider(): View = View(context).apply { setBackgroundColor(color(R.color.outline_variant)) }

    /** A floating surface: a raised pill with a hairline edge so it reads over the white page too. */
    fun raisedPill(): GradientDrawable = GradientDrawable().apply {
        cornerRadius = dp(FULL_ROUND_DP).toFloat()
        setColor(color(R.color.surface_raised))
        setStroke(dp(Size.HAIRLINE), color(R.color.outline_variant))
    }

    fun rounded(color: Int, radiusDp: Int): GradientDrawable = GradientDrawable().apply {
        cornerRadius = dp(radiusDp).toFloat()
        setColor(color)
    }

    /** A circle of [diameterDp], centred in a [Size.TOUCH] square view. */
    fun disc(diameterDp: Int, color: Int = color(R.color.on_surface)): Drawable {
        val circle = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(color)
        }
        return InsetDrawable(circle, dp((Size.TOUCH - diameterDp) / 2))
    }

    // The mask bounds the touch ripple; without content the ripple is all that shows.
    fun ripple(content: Drawable?, mask: Drawable?): RippleDrawable =
        RippleDrawable(ColorStateList.valueOf(color(R.color.ripple)), content, mask)

    /**
     * Gives a selectable control its TalkBack role and checked state. A
     * [position] in a row marked with [choiceGroup] adds "2 of 3" to the announcement.
     */
    fun setRole(view: View, role: Role, position: Int = NO_POSITION) {
        ViewCompat.setAccessibilityDelegate(view, object : AccessibilityDelegateCompat() {
            override fun onInitializeAccessibilityNodeInfo(host: View, info: AccessibilityNodeInfoCompat) {
                super.onInitializeAccessibilityNodeInfo(host, info)
                info.className = role.className
                info.isCheckable = true
                info.setChecked(if (host.isSelected) AccessibilityNodeInfoCompat.CHECKED_STATE_TRUE else AccessibilityNodeInfoCompat.CHECKED_STATE_FALSE)
                // Checked already says it; "selected" on top would be read twice.
                info.isSelected = false
                if (position == NO_POSITION) return
                info.setCollectionItemInfo(AccessibilityNodeInfoCompat.CollectionItemInfoCompat.obtain(0, 1, position, 1, false, host.isSelected))
            }
        })
    }

    /** Marks [view] as a single-choice row of [count] controls placed with [setRole]. */
    fun choiceGroup(view: View, count: Int) {
        ViewCompat.setAccessibilityDelegate(view, object : AccessibilityDelegateCompat() {
            override fun onInitializeAccessibilityNodeInfo(host: View, info: AccessibilityNodeInfoCompat) {
                super.onInitializeAccessibilityNodeInfo(host, info)
                info.setCollectionInfo(AccessibilityNodeInfoCompat.CollectionInfoCompat.obtain(1, count, false,
                    AccessibilityNodeInfoCompat.CollectionInfoCompat.SELECTION_MODE_SINGLE))
            }
        })
    }

    private fun View.describe(@StringRes label: Int) {
        contentDescription = context.getString(label)
        tooltipText = contentDescription
    }

    // Icon and tint follow the view's selected and enabled flags.
    private fun selectable(@DrawableRes idle: Int, @DrawableRes chosen: Int) = StateListDrawable().apply {
        addState(intArrayOf(android.R.attr.state_selected), context.getDrawable(chosen))
        addState(intArrayOf(), context.getDrawable(idle))
    }

    private fun whenSelected(drawable: Drawable) = StateListDrawable().apply {
        setEnterFadeDuration(Motion.SHORT.toInt())
        setExitFadeDuration(Motion.SHORT.toInt())
        addState(intArrayOf(android.R.attr.state_selected), drawable)
    }

    private fun states(selected: Int, idle: Int) = ColorStateList(
        arrayOf(intArrayOf(-android.R.attr.state_enabled), intArrayOf(android.R.attr.state_selected), intArrayOf()),
        intArrayOf(color(R.color.on_surface_disabled), selected, idle),
    )

    private fun pressShrink() = StateListAnimator().apply {
        fun scaleTo(scale: Float) = ObjectAnimator.ofPropertyValuesHolder(null as Any?,
            PropertyValuesHolder.ofFloat(View.SCALE_X, scale), PropertyValuesHolder.ofFloat(View.SCALE_Y, scale),
        ).setDuration(Motion.SHORT).apply { interpolator = Motion.EASING }
        addState(intArrayOf(android.R.attr.state_pressed, android.R.attr.state_enabled), scaleTo(PRESSED_SCALE))
        addState(intArrayOf(), scaleTo(1f))
    }

    private fun withAlpha(color: Int, alpha: Int) = (color and RGB_MASK) or (alpha shl ALPHA_SHIFT)

    private companion object {
        const val NO_POSITION = -1
        val DISABLED_OR_DEFAULT = arrayOf(intArrayOf(-android.R.attr.state_enabled), intArrayOf())
        // Larger than any control; GradientDrawable clamps it to a full pill.
        const val FULL_ROUND_DP = 100
        const val PRESSED_SCALE = 0.97f
        const val PRESSED_ALPHA = 0x33
        const val RGB_MASK = 0x00FFFFFF
        const val ALPHA_SHIFT = 24
    }
}

/**
 * A column whose width never exceeds [maxWidth] pixels, so text stays
 * readable on tablets. Center it with its layout gravity.
 */
internal class MaxWidthLayout(context: Context) : LinearLayout(context) {
    var maxWidth = Int.MAX_VALUE

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = when {
            MeasureSpec.getMode(widthMeasureSpec) != MeasureSpec.UNSPECIFIED ->
                MeasureSpec.makeMeasureSpec(min(MeasureSpec.getSize(widthMeasureSpec), maxWidth), MeasureSpec.EXACTLY)
            maxWidth == Int.MAX_VALUE -> widthMeasureSpec
            else -> MeasureSpec.makeMeasureSpec(maxWidth, MeasureSpec.AT_MOST)
        }
        super.onMeasure(width, heightMeasureSpec)
    }
}
