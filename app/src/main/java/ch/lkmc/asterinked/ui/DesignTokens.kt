package ch.lkmc.asterinked.ui

import android.view.animation.PathInterpolator

/*
 * Layout tokens in dp, shared by every screen. Colours and type live in
 * res/values (colors.xml, styles.xml) because they change with the theme.
 *
 *   Space   4-point scale for padding and gaps
 *   Size    controls; every tappable thing is at least TOUCH tall and wide
 *   Radius  corner radii, small to large; pills use half their height
 *   Alpha   paint alpha for drawn controls
 *   Motion  durations in ms and the one easing curve
 */

internal object Space {
    const val XXS = 2
    const val XS = 4
    const val S = 8
    const val M = 12
    const val L = 16
    const val XL = 24
    const val XXL = 32
}

internal object Size {
    const val TOUCH = 48
    const val ICON = 24
    const val ICON_SMALL = 20
    const val STATUS_ICON = 14
    const val STATUS_DOT = 8
    const val TOP_BAR = 64
    const val TOP_BAR_COMPACT = 56
    const val TOOL_ROW = 56
    const val THUMB = 40
    const val BUTTON = 40
    const val BUTTON_LARGE = 52
    const val PAGE_FIELD_WIDTH = 96
    const val HAIRLINE = 1
    const val DIVIDER = 20
    const val PROGRESS = 3
    const val SPINNER = 36
    const val MARK = 64
    const val CONTENT_MAX = 420
    const val NOTICE_MAX = 560
}

internal object Radius {
    const val SMALL = 8
    const val MEDIUM = 12
}

internal object Elevation {
    const val RAISED = 3
    const val NOTICE = 6
}

internal object Alpha {
    const val OPAQUE = 255
    // 38%, Material's opacity for disabled content.
    const val DISABLED = 97
}

internal object Motion {
    const val SHORT = 150L
    const val MEDIUM = 220L
    // Content that only appears after this long avoids a spinner flash on fast loads.
    const val LOADING_DELAY = 300L
    // Material's standard easing: quick start, gentle settle.
    val EASING = PathInterpolator(0.2f, 0f, 0f, 1f)
}
