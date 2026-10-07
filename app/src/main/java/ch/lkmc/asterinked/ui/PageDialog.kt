package ch.lkmc.asterinked.ui

import android.app.AlertDialog
import android.content.Context
import android.graphics.drawable.StateListDrawable
import android.text.InputFilter
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import androidx.core.graphics.ColorUtils
import androidx.core.widget.doAfterTextChanged
import ch.lkmc.asterinked.R
import java.util.Locale

/**
 * Asks which page to show. The field opens with the keyboard up and the
 * current page selected, so typing replaces it:
 *
 *   Go to page
 *   [  3  ]  of 12
 *   Enter a page from 1 to 12      <- only while the number is out of range
 *                     Cancel   Go
 *
 * Go and the keyboard's Go key jump; both do nothing until the number is a
 * page of this document. [go] receives a zero-based page index.
 */
internal fun showPageDialog(
    context: Context,
    ui: Components,
    current: Int,
    count: Int,
    annotated: List<Int>,
    go: (Int) -> Unit,
): AlertDialog {
    lateinit var dialog: AlertDialog

    fun navigate(page: Int) {
        go(page)
        dialog.dismiss()
    }

    val field = EditText(context).apply {
        setTextAppearance(TextStyle.FIELD.appearance)
        inputType = InputType.TYPE_CLASS_NUMBER
        imeOptions = EditorInfo.IME_ACTION_GO or EditorInfo.IME_FLAG_NO_EXTRACT_UI
        isSingleLine = true
        gravity = Gravity.CENTER
        filters = arrayOf(InputFilter.LengthFilter(count.toString().length))
        hint = context.getString(R.string.page_number)
        background = fieldBackground(ui)
        // Neutral, so only an out-of-range number shows the accent.
        highlightColor = ColorUtils.setAlphaComponent(ui.color(R.color.on_surface), SELECTION_ALPHA)
        setPadding(ui.dp(Space.M), 0, ui.dp(Space.M), 0)
        // ASCII digits: what the number keyboard types and toIntOrNull() reads back.
        setText(String.format(Locale.ROOT, "%d", current + 1))
        setSelectAllOnFocus(true)
    }
    val total = ui.text(TextStyle.BODY, context.getString(R.string.of_pages, count))
    val problem = ui.text(TextStyle.CAPTION, context.getString(R.string.page_out_of_range, count)).apply {
        setTextColor(ui.color(R.color.accent))
        accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        visibility = View.GONE
    }
    // The default top gravity keeps LinearLayout's baseline alignment, which
    // sets "of N" on the line of the number; centring would drop it.
    val row = LinearLayout(context).apply {
        addView(field, LinearLayout.LayoutParams(ui.dp(Size.PAGE_FIELD_WIDTH), ui.dp(Size.BUTTON_LARGE + Space.XS)))
        addView(total, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            marginStart = ui.dp(Space.M)
        })
    }
    val content = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(ui.dp(Space.XL), ui.dp(Space.S), ui.dp(Space.XL), 0)
        addView(row)
        addView(problem, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            topMargin = ui.dp(Space.S)
        })
        val summary = if (annotated.isEmpty()) context.getString(R.string.no_notes)
            else context.resources.getQuantityString(R.plurals.annotated_pages, annotated.size, annotated.size)
        addView(ui.text(TextStyle.CAPTION, summary), LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            topMargin = ui.dp(Space.L)
            bottomMargin = ui.dp(Space.XS)
        })

        fun noteButton(label: Int, destinationLabel: Int, page: Int?) = ui.textButton(label) {
            page?.let(::navigate)
        }.apply {
            isEnabled = page != null
            gravity = Gravity.START or Gravity.CENTER_VERTICAL
            if (page != null) text = context.getString(destinationLabel, page + 1)
        }
        // Boundaries stay disabled rather than silently wrapping to the other end.
        val before = annotated.lastOrNull { it < current }
        val after = annotated.firstOrNull { it > current }
        addView(noteButton(R.string.previous_note, R.string.previous_note_page, before))
        addView(noteButton(R.string.next_note, R.string.next_note_page, after))
    }

    fun chosen(): Int? = field.text.toString().toIntOrNull()?.takeIf { it in 1..count }?.minus(1)
    dialog = AlertDialog.Builder(context)
        .setTitle(R.string.go_to_page)
        // The actions remain reachable with large text or a short keyboard viewport.
        .setView(ScrollView(context).apply { addView(content) })
        .setNegativeButton(android.R.string.cancel, null)
        .setPositiveButton(R.string.go) { _, _ -> chosen()?.let(go) }
        .create()
    dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)

    // An empty field is not an error yet; it only disables Go.
    fun validate(): Int? {
        val page = chosen()
        val outOfRange = page == null && field.text.isNotEmpty()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.isEnabled = page != null
        problem.visibility = if (outOfRange) View.VISIBLE else View.GONE
        field.isActivated = outOfRange
        return page
    }
    field.doAfterTextChanged { validate() }
    field.setOnEditorActionListener { _, action, _ ->
        if (action != EditorInfo.IME_ACTION_GO) return@setOnEditorActionListener false
        validate()?.let {
            navigate(it)
        }
        true
    }
    dialog.setOnShowListener {
        validate()
        field.requestFocus()
    }
    dialog.show()
    return dialog
}

// A filled field with a graphite edge while focused. Activated marks an
// out-of-range number, which turns the edge accent like the message below.
private fun fieldBackground(ui: Components) = StateListDrawable().apply {
    val fill = ui.color(R.color.surface_track)
    fun edged(color: Int) = ui.rounded(fill, Radius.MEDIUM).apply { setStroke(ui.dp(FOCUS_STROKE_DP), color) }
    addState(intArrayOf(android.R.attr.state_activated), edged(ui.color(R.color.accent)))
    addState(intArrayOf(android.R.attr.state_focused), edged(ui.color(R.color.on_surface)))
    addState(intArrayOf(), ui.rounded(fill, Radius.MEDIUM))
}

private const val FOCUS_STROKE_DP = 2
// 20%: a selection tint the digits stay readable through.
private const val SELECTION_ALPHA = 51
