package ch.lkmc.asterinked.ui

import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import ch.lkmc.asterinked.R

internal class MainActivity : ComponentActivity() {
    private val model: EditorViewModel by viewModels()
    private lateinit var page: InkPageView
    private lateinit var title: TextView
    private lateinit var status: TextView
    private lateinit var counter: TextView
    private lateinit var hint: TextView
    private lateinit var welcome: LinearLayout
    private lateinit var open: Button
    private lateinit var save: Button
    private lateinit var undo: Button
    private lateinit var redo: Button
    private lateinit var previous: Button
    private lateinit var next: Button
    private lateinit var input: Button
    private lateinit var color: Button
    private lateinit var thickness: Button
    private lateinit var fit: Button
    private var mode = InputMode.PEN
    private var colorIndex = 0
    private var widthIndex = 1

    private val openPdf = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(model::open)
    }
    private val savePdf = registerForActivityResult(ActivityResultContracts.CreateDocument(PDF_MIME)) { uri ->
        uri?.let(model::export)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        colorIndex = savedInstanceState?.getInt(COLOR_KEY)?.coerceIn(COLORS.indices) ?: 0
        widthIndex = savedInstanceState?.getInt(WIDTH_KEY)?.coerceIn(WIDTHS.indices) ?: 1
        mode = savedInstanceState?.getString(MODE_KEY)?.let { InputMode.valueOf(it) } ?: InputMode.PEN
        buildLayout()
        configurePen()
        model.state.observe(this, ::show)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt(COLOR_KEY, colorIndex)
        outState.putInt(WIDTH_KEY, widthIndex)
        outState.putString(MODE_KEY, mode.name)
        super.onSaveInstanceState(outState)
    }

    private fun buildLayout() {
        val root = column().apply { setBackgroundColor(PAPER_COLOR) }
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        val header = row().apply { setPadding(dp(16), dp(8), dp(12), 0) }
        title = label(getString(R.string.app_name), 22f).apply {
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            setTextColor(INK_COLOR)
        }
        header.addView(title, LinearLayout.LayoutParams(0, dp(48), 1f))
        root.addView(header)

        val actions = row()
        open = button(R.string.open_pdf, actions) { requestOpen() }
        save = button(R.string.save_copy, actions) { launchPicker { savePdf.launch(exportName()) } }
        undo = button(R.string.undo, actions, model::undo)
        redo = button(R.string.redo, actions, model::redo)
        root.addView(scroll(actions))

        val tools = row()
        input = button(R.string.pen_only, tools) {
            mode = if (mode == InputMode.PEN) InputMode.TOUCH else InputMode.PEN
            configurePen()
        }
        color = button(COLOR_NAMES[colorIndex], tools) {
            AlertDialog.Builder(this).setTitle(R.string.color_label)
                .setSingleChoiceItems(COLOR_NAMES.map(::getString).toTypedArray(), colorIndex) { dialog, index ->
                    colorIndex = index
                    configurePen()
                    dialog.dismiss()
                }.show()
        }
        thickness = button(WIDTH_NAMES[widthIndex], tools) {
            AlertDialog.Builder(this).setTitle(R.string.width_label)
                .setSingleChoiceItems(WIDTH_NAMES.map(::getString).toTypedArray(), widthIndex) { dialog, index ->
                    widthIndex = index
                    configurePen()
                    dialog.dismiss()
                }.show()
        }
        fit = button(R.string.fit, tools) { page.resetZoom() }
        root.addView(scroll(tools))

        val workspace = android.widget.FrameLayout(this)
        page = InkPageView(this).apply { onTurnPage = ::turnPage }
        workspace.addView(page, android.widget.FrameLayout.LayoutParams(MATCH, MATCH))
        welcome = column().apply {
            gravity = Gravity.CENTER
            setPadding(dp(32), dp(24), dp(32), dp(24))
            setBackgroundColor(PAPER_COLOR)
            addView(label(getString(R.string.welcome_title), 28f).apply { gravity = Gravity.CENTER })
            addView(label(getString(R.string.welcome_body), 16f).apply { gravity = Gravity.CENTER; setPadding(0, dp(20), 0, dp(24)) })
            button(R.string.open_pdf, this) { requestOpen() }
        }
        workspace.addView(welcome, android.widget.FrameLayout.LayoutParams(MATCH, MATCH))
        root.addView(workspace, LinearLayout.LayoutParams(MATCH, 0, 1f))

        hint = label("", 12f).apply { gravity = Gravity.CENTER; setPadding(0, dp(6), 0, 0) }
        root.addView(hint)
        val navigation = row().apply { gravity = Gravity.CENTER }
        previous = button(R.string.previous, navigation) { turnPage(-1) }
        counter = label("", 14f).apply { gravity = Gravity.CENTER }
        navigation.addView(counter, LinearLayout.LayoutParams(0, dp(48), 1f))
        next = button(R.string.next, navigation) { turnPage(1) }
        root.addView(navigation)
        status = label("", 12f).apply { gravity = Gravity.CENTER; setPadding(0, 0, 0, dp(8)) }
        root.addView(status)
        setContentView(root)
    }

    private fun configurePen() {
        input.setText(if (mode == InputMode.PEN) R.string.pen_only else R.string.touch_ink)
        color.setText(COLOR_NAMES[colorIndex])
        color.setTextColor(COLORS[colorIndex])
        thickness.setText(WIDTH_NAMES[widthIndex])
        hint.setText(if (mode == InputMode.PEN) R.string.input_hint else R.string.touch_hint)
        page.configure(mode, COLORS[colorIndex], WIDTHS[widthIndex], model::addStroke)
    }

    private fun show(state: EditorState) {
        val draft = state.draft
        val ready = draft != null && !state.busy
        title.text = draft?.name ?: getString(R.string.app_name)
        open.isEnabled = !state.busy
        save.isEnabled = ready
        undo.isEnabled = ready && !draft?.ink?.get(draft.page).isNullOrEmpty()
        redo.isEnabled = ready && state.canRedo
        previous.isEnabled = ready && draft!!.page > 0
        next.isEnabled = ready && draft!!.page < state.pages.lastIndex
        listOf(input, color, thickness, fit).forEach { it.isEnabled = ready }
        welcome.visibility = if (draft == null) View.VISIBLE else View.GONE
        counter.text = if (draft == null) "" else getString(R.string.page_count, draft.page + 1, state.pages.size)
        status.text = when {
            state.busy -> getString(R.string.working)
            draft == null -> ""
            draft.dirty -> getString(R.string.unsaved)
            else -> getString(R.string.saved)
        }
        page.show(state)
        state.message?.let {
            Toast.makeText(this, it, Toast.LENGTH_LONG).show()
            model.acknowledgeMessage()
        }
    }

    private fun turnPage(delta: Int) {
        model.state.value?.draft?.let { model.goToPage(it.page + delta) }
    }

    private fun requestOpen() {
        if (model.state.value?.busy != false) return
        if (model.state.value?.draft?.dirty != true) {
            launchPicker { openPdf.launch(arrayOf(PDF_MIME)) }
            return
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.open_another).setMessage(R.string.unsaved_prompt)
            .setNegativeButton(R.string.keep_editing, null)
            .setPositiveButton(R.string.open_anyway) { _, _ -> launchPicker { openPdf.launch(arrayOf(PDF_MIME)) } }
            .show()
    }

    private fun exportName(): String {
        val original = model.state.value?.draft?.name ?: "Document.pdf"
        return original.replace(Regex("(?i)\\.pdf$"), "") + "-annotated.pdf"
    }

    private fun launchPicker(action: () -> Unit) {
        try { action() } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, R.string.no_picker, Toast.LENGTH_LONG).show()
        }
    }

    private fun column() = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    private fun row() = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
    private fun label(value: String, size: Float) = TextView(this).apply { text = value; textSize = size; gravity = Gravity.CENTER_VERTICAL }
    private fun scroll(content: View) = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false; addView(content) }
    private fun button(label: Int, parent: LinearLayout, action: () -> Unit): Button = Button(this).apply {
        setText(label)
        isAllCaps = false
        minHeight = dp(48)
        setOnClickListener { action() }
        parent.addView(this, LinearLayout.LayoutParams(WRAP, WRAP))
    }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    private companion object {
        const val PDF_MIME = "application/pdf"
        const val COLOR_KEY = "penColor"
        const val WIDTH_KEY = "penWidth"
        const val MODE_KEY = "inputMode"
        const val MATCH = LinearLayout.LayoutParams.MATCH_PARENT
        const val WRAP = LinearLayout.LayoutParams.WRAP_CONTENT
        val PAPER_COLOR = Color.rgb(248, 247, 242)
        val INK_COLOR = Color.rgb(34, 89, 79)
        val COLORS = intArrayOf(Color.rgb(25, 38, 46), Color.rgb(32, 85, 184), Color.rgb(179, 47, 61), Color.rgb(32, 113, 73))
        val COLOR_NAMES = intArrayOf(R.string.black, R.string.blue, R.string.red, R.string.green)
        val WIDTHS = floatArrayOf(1.2f, 2.2f, 4f)
        val WIDTH_NAMES = intArrayOf(R.string.fine, R.string.medium, R.string.bold)
    }
}
