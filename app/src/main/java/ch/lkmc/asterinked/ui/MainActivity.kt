package ch.lkmc.asterinked.ui

import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ComponentName
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.net.Uri
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
import androidx.core.content.FileProvider
import androidx.core.content.IntentCompat
import androidx.core.os.BundleCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import ch.lkmc.asterinked.R
import ch.lkmc.asterinked.ink.InkKind
import java.io.File

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
    private lateinit var share: Button
    private lateinit var undo: Button
    private lateinit var redo: Button
    private lateinit var previous: Button
    private lateinit var next: Button
    private lateinit var input: Button
    private lateinit var highlight: Button
    private lateinit var color: Button
    private lateinit var thickness: Button
    private lateinit var eraser: Button
    private lateinit var fit: Button
    private var mode = InputMode.PEN
    private var tool = InkTool.PEN
    private var kind = InkKind.PEN
    private var colorIndex = 0
    private var widthIndex = 1
    // A PDF handed over by another app, waiting until the editor is idle so the
    // unsaved-notes check sees the restored draft.
    private var incoming: Uri? = null

    private val openPdf = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(model::open)
    }
    private val savePdf = registerForActivityResult(ActivityResultContracts.CreateDocument(PDF_MIME)) { uri ->
        uri?.let(model::export)
    }
    private var confirming: AlertDialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        colorIndex = savedInstanceState?.getInt(COLOR_KEY)?.coerceIn(COLORS.indices) ?: 0
        widthIndex = savedInstanceState?.getInt(WIDTH_KEY)?.coerceIn(WIDTHS.indices) ?: 1
        mode = savedInstanceState?.getString(MODE_KEY)?.let { InputMode.valueOf(it) } ?: InputMode.PEN
        tool = savedInstanceState?.getString(TOOL_KEY)?.let { InkTool.valueOf(it) } ?: InkTool.PEN
        kind = savedInstanceState?.getString(KIND_KEY)?.let { InkKind.valueOf(it) } ?: InkKind.PEN
        incoming = savedInstanceState?.let { BundleCompat.getParcelable(it, INCOMING_KEY, Uri::class.java) }
        buildLayout()
        configurePen()
        if (savedInstanceState == null) receive(intent)
        model.state.observe(this, ::show)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        receive(intent)
        model.state.value?.let(::show)
    }

    override fun onDestroy() {
        confirming?.dismiss()
        super.onDestroy()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt(COLOR_KEY, colorIndex)
        outState.putInt(WIDTH_KEY, widthIndex)
        outState.putString(MODE_KEY, mode.name)
        outState.putString(TOOL_KEY, tool.name)
        outState.putString(KIND_KEY, kind.name)
        incoming?.let { outState.putParcelable(INCOMING_KEY, it) }
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
        share = button(R.string.share, actions, model::share)
        undo = button(R.string.undo, actions, model::undo)
        redo = button(R.string.redo, actions, model::redo)
        root.addView(scroll(actions))

        val tools = row()
        input = button(R.string.pen_only, tools) {
            mode = if (mode == InputMode.PEN) InputMode.TOUCH else InputMode.PEN
            configurePen()
        }
        highlight = button(R.string.highlighter, tools) {
            kind = if (kind == InkKind.HIGHLIGHTER) InkKind.PEN else InkKind.HIGHLIGHTER
            configurePen()
        }
        color = button(COLOR_NAMES[colorIndex], tools) {
            AlertDialog.Builder(this).setTitle(R.string.color_label)
                .setSingleChoiceItems(colorNames().map(::getString).toTypedArray(), colorIndex) { dialog, index ->
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
        eraser = button(R.string.eraser, tools) {
            tool = if (tool == InkTool.ERASER) InkTool.PEN else InkTool.ERASER
            configurePen()
        }
        fit = button(R.string.fit, tools) { page.resetZoom() }
        root.addView(scroll(tools))

        val workspace = android.widget.FrameLayout(this)
        page = InkPageView(this).apply { onTurnPage = ::turnPage }
        page.onErase = model::eraseStrokes
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
        val highlighting = kind == InkKind.HIGHLIGHTER
        // The same colour and width choices pick a highlighter tint and a line-height width.
        val inkColor = (if (highlighting) HIGHLIGHT_COLORS else COLORS)[colorIndex]
        val inkWidth = (if (highlighting) HIGHLIGHT_WIDTHS else WIDTHS)[widthIndex]
        input.setText(if (mode == InputMode.PEN) R.string.pen_only else R.string.touch_ink)
        color.setText(colorNames()[colorIndex])
        color.setTextColor(COLORS[colorIndex])
        thickness.setText(WIDTH_NAMES[widthIndex])
        highlight.backgroundTintList = if (highlighting) ColorStateList.valueOf(HIGHLIGHT_COLORS[colorIndex]) else null
        hint.setText(when {
            tool == InkTool.ERASER -> R.string.eraser_hint
            highlighting -> R.string.highlight_hint
            mode == InputMode.PEN -> R.string.input_hint
            else -> R.string.touch_hint
        })
        // A tint shows the active eraser without replacing the text colors, so the
        // disabled state still greys the label.
        eraser.isActivated = tool == InkTool.ERASER
        eraser.backgroundTintList = if (tool == InkTool.ERASER) ColorStateList.valueOf(ACTIVE_TOOL_COLOR) else null
        page.configure(mode, inkColor, inkWidth, kind, model::addStroke)
        page.tool = tool
    }

    private fun colorNames() = if (kind == InkKind.HIGHLIGHTER) HIGHLIGHT_NAMES else COLOR_NAMES

    private fun show(state: EditorState) {
        val draft = state.draft
        val ready = draft != null && !state.busy
        title.text = draft?.name ?: getString(R.string.app_name)
        open.isEnabled = !state.busy
        save.isEnabled = ready
        share.isEnabled = ready
        undo.isEnabled = ready && state.canUndo
        redo.isEnabled = ready && state.canRedo
        previous.isEnabled = ready && draft!!.page > 0
        next.isEnabled = ready && draft!!.page < state.pages.lastIndex
        listOf(input, highlight, color, thickness, eraser, fit).forEach { it.isEnabled = ready }
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
        state.shared?.let {
            model.acknowledgeShare()
            sendToShareSheet(it)
        }
        // Cleared only once the user decides, so a rotation during the prompt asks
        // again; a newer PDF that arrived meanwhile stays pending.
        val uri = incoming
        if (uri != null && !state.busy && confirming == null) {
            confirmReplacing(keep = { if (incoming == uri) incoming = null }) {
                if (incoming == uri) incoming = null
                model.open(uri)
            }
        }
    }

    // VIEW carries the PDF as data, SEND as a stream extra. A task relaunched from
    // Recents replays its original intent, whose read grant has usually expired.
    private fun receive(intent: Intent) {
        if (intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY != 0) return
        incoming = when (intent.action) {
            Intent.ACTION_VIEW -> intent.data
            Intent.ACTION_SEND -> IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
            else -> null
        } ?: incoming
    }

    private fun turnPage(delta: Int) {
        model.state.value?.draft?.let { model.goToPage(it.page + delta) }
    }

    private fun requestOpen() {
        if (model.state.value?.busy != false) return
        confirmReplacing { launchPicker { openPdf.launch(arrayOf(PDF_MIME)) } }
    }

    private fun confirmReplacing(keep: () -> Unit = {}, open: () -> Unit) {
        if (model.state.value?.draft?.dirty != true) {
            open()
            return
        }
        confirming = AlertDialog.Builder(this)
            .setTitle(R.string.open_another).setMessage(R.string.unsaved_prompt)
            .setNegativeButton(R.string.keep_editing) { _, _ -> keep() }
            .setPositiveButton(R.string.open_anyway) { _, _ -> open() }
            .setOnCancelListener { keep() }
            .setOnDismissListener { confirming = null }
            .show()
    }

    private fun sendToShareSheet(file: File) {
        val uri = FileProvider.getUriForFile(this, "$packageName$FILE_AUTHORITY_SUFFIX", file)
        val send = Intent(Intent.ACTION_SEND)
            .setType(PDF_MIME)
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        // Explicit ClipData carries the read grant through the chooser on every version.
        send.clipData = ClipData.newRawUri(file.name, uri)
        val chooser = Intent.createChooser(send, getString(R.string.share_title))
            .putExtra(Intent.EXTRA_EXCLUDE_COMPONENTS, arrayOf(ComponentName(this, MainActivity::class.java)))
        launchPicker { startActivity(chooser) }
    }

    private fun exportName(): String = model.state.value?.draft?.exportName ?: DEFAULT_EXPORT_NAME

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
        const val TOOL_KEY = "inkTool"
        const val KIND_KEY = "inkKind"
        const val INCOMING_KEY = "incomingPdf"
        // Matches android:authorities="${'$'}{applicationId}.files" in the manifest.
        const val FILE_AUTHORITY_SUFFIX = ".files"
        const val DEFAULT_EXPORT_NAME = "Document-annotated.pdf"
        const val MATCH = LinearLayout.LayoutParams.MATCH_PARENT
        const val WRAP = LinearLayout.LayoutParams.WRAP_CONTENT
        val PAPER_COLOR = Color.rgb(248, 247, 242)
        val INK_COLOR = Color.rgb(34, 89, 79)
        val ACTIVE_TOOL_COLOR = Color.rgb(196, 222, 214)
        val COLORS = intArrayOf(Color.rgb(25, 38, 46), Color.rgb(32, 85, 184), Color.rgb(179, 47, 61), Color.rgb(32, 113, 73))
        val COLOR_NAMES = intArrayOf(R.string.black, R.string.blue, R.string.red, R.string.green)
        val WIDTHS = floatArrayOf(1.2f, 2.2f, 4f)
        val WIDTH_NAMES = intArrayOf(R.string.fine, R.string.medium, R.string.bold)
        // Light tints: multiplied with the page they mark text without hiding it.
        val HIGHLIGHT_COLORS = intArrayOf(Color.rgb(255, 228, 92), Color.rgb(159, 211, 255), Color.rgb(255, 168, 207), Color.rgb(168, 235, 158))
        val HIGHLIGHT_NAMES = intArrayOf(R.string.yellow, R.string.blue, R.string.pink, R.string.green)
        val HIGHLIGHT_WIDTHS = floatArrayOf(8f, 12f, 18f)
    }
}
