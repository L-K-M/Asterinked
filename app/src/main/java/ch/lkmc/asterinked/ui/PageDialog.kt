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
internal fun showPageDialog(context: Context, ui: Components, current: Int, count: Int, go: (Int) -> Unit): AlertDialog {
    val field = EditText(context).apply {
        setTextAppearance(TextStyle.FIELD.appearance)
        inputType = InputType.TYPE_CLASS_NUMBER
        imeOptions = EditorInfo.IME_ACTION_GO or EditorInfo.IME_FLAG_NO_EXTRACT_UI
        isSingleLine = true
        gravity = Gravity.CENTER
        filters = arrayOf(InputFilter.LengthFilter(count.toString().length))
        hint = context.getString(R.string.page_number)
        background = fieldBackground(ui)
        setPadding(ui.dp(Space.M), 0, ui.dp(Space.M), 0)
        // ASCII digits: what the number keyboard types and toIntOrNull() reads back.
        setText(String.format(Locale.ROOT, "%d", current + 1))
        setSelectAllOnFocus(true)
    }
    val total = ui.text(TextStyle.BODY, context.getString(R.string.of_pages, count))
    val problem = ui.text(TextStyle.CAPTION, context.getString(R.string.page_out_of_range, count)).apply {
        setTextColor(ui.color(R.color.accent))
        visibility = View.INVISIBLE
    }
    val row = LinearLayout(context).apply {
        gravity = Gravity.CENTER_VERTICAL
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
    }

    fun chosen(): Int? = field.text.toString().toIntOrNull()?.takeIf { it in 1..count }?.minus(1)
    val dialog = AlertDialog.Builder(context)
        .setTitle(R.string.go_to_page)
        .setView(content)
        .setNegativeButton(android.R.string.cancel, null)
        .setPositiveButton(R.string.go) { _, _ -> chosen()?.let(go) }
        .create()
    dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)

    // An empty field is not an error yet; it only disables Go.
    fun validate(): Int? {
        val page = chosen()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.isEnabled = page != null
        problem.visibility = if (page != null || field.text.isEmpty()) View.INVISIBLE else View.VISIBLE
        return page
    }
    field.doAfterTextChanged { validate() }
    field.setOnEditorActionListener { _, action, _ ->
        if (action != EditorInfo.IME_ACTION_GO) return@setOnEditorActionListener false
        validate()?.let {
            go(it)
            dialog.dismiss()
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

// A filled field that gains an accent edge while focused.
private fun fieldBackground(ui: Components) = StateListDrawable().apply {
    val fill = ui.color(R.color.surface_track)
    addState(intArrayOf(android.R.attr.state_focused), ui.rounded(fill, Radius.MEDIUM).apply {
        setStroke(ui.dp(FOCUS_STROKE_DP), ui.color(R.color.accent))
    })
    addState(intArrayOf(), ui.rounded(fill, Radius.MEDIUM))
}

private const val FOCUS_STROKE_DP = 2
