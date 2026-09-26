package ch.lkmc.asterinked.ui

import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Bundle
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.NumberPicker
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.edit
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import ch.lkmc.asterinked.R

internal class MainActivity : ComponentActivity() {
    private val model: EditorViewModel by viewModels()
    private val settings by lazy { getSharedPreferences(SETTINGS, MODE_PRIVATE) }
    private lateinit var page: InkPageView
    private lateinit var title: TextView
    private lateinit var status: TextView
    private lateinit var open: ImageButton
    private lateinit var undo: ImageButton
    private lateinit var redo: ImageButton
    private lateinit var fit: ImageButton
    private lateinit var save: Button
    private lateinit var tools: View
    private lateinit var penMode: ImageButton
    private lateinit var touchMode: ImageButton
    private lateinit var swatches: List<ChoiceDot>
    private lateinit var widths: List<ChoiceDot>
    private lateinit var pagePill: View
    private lateinit var previous: ImageButton
    private lateinit var next: ImageButton
    private lateinit var counter: TextView
    private lateinit var welcome: View
    private lateinit var loading: View
    private var mode = InputMode.PEN
    private var colorIndex = DEFAULT_COLOR
    private var widthIndex = DEFAULT_WIDTH

    private val openPdf = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(model::open)
    }
    private val savePdf = registerForActivityResult(ActivityResultContracts.CreateDocument(PDF_MIME)) { uri ->
        uri?.let(model::export)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Pen settings persist across launches, not only across recreation.
        colorIndex = settings.getInt(COLOR_KEY, DEFAULT_COLOR).coerceIn(COLORS.indices)
        widthIndex = settings.getInt(WIDTH_KEY, DEFAULT_WIDTH).coerceIn(WIDTHS.indices)
        mode = InputMode.entries.firstOrNull { it.name == settings.getString(MODE_KEY, null) } ?: InputMode.PEN
        buildLayout()
        configurePen()
        model.state.observe(this, ::show)
    }

    private fun buildLayout() {
        val root = column().apply { setBackgroundColor(CHROME) }
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        val bar = row().apply { setPadding(dp(4), 0, dp(12), 0) }
        open = iconButton(R.drawable.ic_open, R.string.open_pdf, bar) { requestOpen() }
        val heading = column().apply { setPadding(dp(8), 0, dp(4), 0) }
        title = label(getString(R.string.app_name), TITLE_SIZE, TEXT).apply {
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            typeface = Typeface.create(MEDIUM_FONT, Typeface.NORMAL)
        }
        status = label("", STATUS_SIZE, MUTED).apply { maxLines = 1 }
        heading.addView(title)
        heading.addView(status)
        bar.addView(heading, LinearLayout.LayoutParams(0, WRAP, 1f))
        undo = iconButton(R.drawable.ic_undo, R.string.undo, bar) { model.undo() }
        redo = iconButton(R.drawable.ic_redo, R.string.redo, bar) { model.redo() }
        save = pillButton(R.string.save_copy, bar) { launchPicker { savePdf.launch(exportName()) } }
        root.addView(bar, LinearLayout.LayoutParams(MATCH, dp(BAR_HEIGHT_DP)))

        val workspace = FrameLayout(this)
        page = InkPageView(this)
        workspace.addView(page, FrameLayout.LayoutParams(MATCH, MATCH))
        pagePill = buildPagePill()
        workspace.addView(pagePill, FrameLayout.LayoutParams(WRAP, WRAP, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply {
            bottomMargin = dp(PILL_MARGIN_DP)
        })
        welcome = buildWelcome()
        workspace.addView(welcome, FrameLayout.LayoutParams(MATCH, MATCH))
        loading = ProgressBar(this).apply {
            isIndeterminate = true
            indeterminateTintList = ColorStateList.valueOf(ACCENT)
        }
        workspace.addView(loading, FrameLayout.LayoutParams(dp(SPINNER_DP), dp(SPINNER_DP), Gravity.CENTER))
        root.addView(workspace, LinearLayout.LayoutParams(MATCH, 0, 1f))

        tools = buildToolStrip()
        root.addView(tools, LinearLayout.LayoutParams(MATCH, dp(BAR_HEIGHT_DP)))
        setContentView(root)
    }

    // Input mode as a two-button segment, then ink colours, then pen widths.
    // Centred on wide screens; scrolls on phones narrower than the strip.
    private fun buildToolStrip(): View {
        val strip = row().apply {
            gravity = Gravity.CENTER
            setPadding(dp(4), 0, dp(4), 0)
        }
        val modes = row().apply { background = rounded(SEGMENT, dp(SEGMENT_RADIUS_DP)) }
        penMode = iconButton(R.drawable.ic_pen, R.string.pen_only, modes) { changeMode(InputMode.PEN) }
        touchMode = iconButton(R.drawable.ic_touch, R.string.touch_ink, modes) { changeMode(InputMode.TOUCH) }
        strip.addView(modes)
        strip.addView(divider())
        swatches = COLORS.indices.map { index ->
            choice(strip, COLOR_NAMES[index]) {
                colorIndex = index
                configurePen()
            }.apply { fill = COLORS[index] }
        }
        strip.addView(divider())
        widths = WIDTHS.indices.map { index ->
            choice(strip, WIDTH_NAMES[index]) {
                widthIndex = index
                configurePen()
            }.apply { radius = WIDTH_DOTS_DP[index] }
        }
        return HorizontalScrollView(this).apply {
            isFillViewport = true
            isHorizontalScrollBarEnabled = false
            addView(strip)
        }
    }

    private fun buildPagePill(): View {
        val pill = row().apply {
            background = rounded(PILL, dp(PILL_RADIUS_DP))
            setPadding(dp(2), 0, dp(2), 0)
        }
        previous = iconButton(R.drawable.ic_previous, R.string.previous, pill, PILL_ICON) { turnPage(-1) }
        counter = label("", COUNTER_SIZE, Color.WHITE).apply {
            gravity = Gravity.CENTER
            minWidth = dp(COUNTER_MIN_WIDTH_DP)
            setPadding(dp(4), 0, dp(4), 0)
            background = ripple(null, rounded(Color.WHITE, dp(PILL_RADIUS_DP)))
            tooltipText = getString(R.string.go_to_page)
            setOnClickListener { askForPage() }
        }
        pill.addView(counter, LinearLayout.LayoutParams(WRAP, dp(PILL_HEIGHT_DP)))
        next = iconButton(R.drawable.ic_next, R.string.next, pill, PILL_ICON) { turnPage(1) }
        pill.addView(View(this).apply { setBackgroundColor(PILL_DIVIDER) }, LinearLayout.LayoutParams(dp(1), dp(DIVIDER_HEIGHT_DP)))
        fit = iconButton(R.drawable.ic_fit, R.string.fit, pill, PILL_ICON) { page.resetZoom() }
        return pill
    }

    private fun buildWelcome(): View = column().apply {
        gravity = Gravity.CENTER
        setPadding(dp(32), dp(24), dp(32), dp(24))
        setBackgroundColor(CANVAS)
        addView(ImageView(context).apply {
            setImageResource(R.drawable.ic_asterisk)
            imageTintList = ColorStateList.valueOf(ACCENT)
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }, LinearLayout.LayoutParams(dp(MARK_DP), dp(MARK_DP)))
        addView(label(getString(R.string.welcome_title), WELCOME_TITLE_SIZE, TEXT).apply {
            gravity = Gravity.CENTER
            typeface = Typeface.create(MEDIUM_FONT, Typeface.NORMAL)
            setPadding(0, dp(20), 0, 0)
        })
        addView(label(getString(R.string.welcome_body), WELCOME_BODY_SIZE, MUTED).apply {
            gravity = Gravity.CENTER
            setPadding(0, dp(12), 0, dp(28))
        })
        pillButton(R.string.open_pdf, this) { requestOpen() }
    }

    private fun configurePen() {
        select(penMode, mode == InputMode.PEN)
        select(touchMode, mode == InputMode.TOUCH)
        swatches.forEachIndexed { index, swatch -> swatch.isSelected = index == colorIndex }
        // Width dots preview the current ink colour.
        widths.forEachIndexed { index, dot ->
            dot.isSelected = index == widthIndex
            dot.fill = COLORS[colorIndex]
        }
        page.configure(mode, COLORS[colorIndex], WIDTHS[widthIndex], model::addStroke)
        settings.edit {
            putInt(COLOR_KEY, colorIndex)
            putInt(WIDTH_KEY, widthIndex)
            putString(MODE_KEY, mode.name)
        }
    }

    private fun changeMode(next: InputMode) {
        if (mode == next) return
        mode = next
        configurePen()
        // The mode changes what fingers do; say so once, where the user is looking.
        Toast.makeText(this, if (mode == InputMode.PEN) R.string.input_hint else R.string.touch_hint, Toast.LENGTH_SHORT).show()
    }

    private fun show(state: EditorState) {
        val draft = state.draft
        val ready = draft != null && !state.busy
        title.text = draft?.name ?: getString(R.string.app_name)
        status.text = state.statusText()?.let(::getString).orEmpty()
        status.setTextColor(if (draft?.dirty == true && !state.busy) ACCENT else MUTED)
        status.visibility = if (status.text.isEmpty()) View.GONE else View.VISIBLE

        // Editor controls exist only once there is a document to edit.
        val editing = if (draft != null) View.VISIBLE else View.GONE
        listOf(open, undo, redo, save, tools, pagePill).forEach { it.visibility = editing }
        open.isEnabled = !state.busy
        save.isEnabled = ready
        undo.isEnabled = ready && !draft?.ink?.get(draft.page).isNullOrEmpty()
        redo.isEnabled = ready && state.canRedo
        fit.isEnabled = ready
        previous.isEnabled = ready && draft!!.page > 0
        next.isEnabled = ready && draft!!.page < state.pages.lastIndex
        (listOf(penMode, touchMode) + swatches + widths).forEach { it.isEnabled = ready }
        if (draft != null) {
            counter.text = getString(R.string.page_position, draft.page + 1, state.pages.size)
            counter.contentDescription = getString(R.string.page_count, draft.page + 1, state.pages.size)
            counter.isEnabled = ready && state.pages.size > 1
        }

        // While a draft restores or a PDF opens, show progress rather than the welcome.
        welcome.visibility = if (draft == null && !state.busy) View.VISIBLE else View.GONE
        loading.visibility = if (draft == null && state.busy) View.VISIBLE else View.GONE
        page.show(state)
        state.message?.let {
            Toast.makeText(this, it, Toast.LENGTH_LONG).show()
            model.acknowledgeMessage()
        }
    }

    private fun turnPage(delta: Int) {
        model.state.value?.draft?.let { model.goToPage(it.page + delta) }
    }

    private fun askForPage() {
        val state = model.state.value ?: return
        val draft = state.draft ?: return
        val picker = NumberPicker(this).apply {
            minValue = 1
            maxValue = state.pages.size
            value = draft.page + 1
            wrapSelectorWheel = false
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.go_to_page)
            .setView(picker)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(android.R.string.ok) { _, _ -> model.goToPage(picker.value - 1) }
            .show()
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
    private fun label(value: String, size: Float, color: Int) = TextView(this).apply {
        text = value
        textSize = size
        setTextColor(color)
        gravity = Gravity.CENTER_VERTICAL
    }

    private fun iconButton(icon: Int, label: Int, parent: LinearLayout, tint: Int = ICON, action: () -> Unit) = ImageButton(this).apply {
        setImageResource(icon)
        imageTintList = enabledColors(tint, DISABLED)
        background = ripple(null, oval())
        contentDescription = getString(label)
        tooltipText = contentDescription
        setOnClickListener { action() }
        parent.addView(this, LinearLayout.LayoutParams(dp(TOUCH_TARGET_DP), dp(TOUCH_TARGET_DP)))
    }

    private fun pillButton(label: Int, parent: LinearLayout, action: () -> Unit) = Button(this).apply {
        setText(label)
        isAllCaps = false
        textSize = BUTTON_TEXT_SIZE
        typeface = Typeface.create(MEDIUM_FONT, Typeface.NORMAL)
        setTextColor(enabledColors(Color.WHITE, MUTED))
        stateListAnimator = null
        minHeight = dp(PILL_BUTTON_HEIGHT_DP)
        minimumHeight = dp(PILL_BUTTON_HEIGHT_DP)
        setPadding(dp(18), 0, dp(18), 0)
        val shape = GradientDrawable().apply {
            cornerRadius = dp(PILL_BUTTON_HEIGHT_DP) / 2f
            color = enabledColors(ACCENT, DISABLED_FILL)
        }
        background = ripple(shape)
        setOnClickListener { action() }
        parent.addView(this, LinearLayout.LayoutParams(WRAP, dp(PILL_BUTTON_HEIGHT_DP)))
    }

    private fun choice(parent: LinearLayout, label: Int, action: () -> Unit) = ChoiceDot(this).apply {
        contentDescription = getString(label)
        tooltipText = contentDescription
        ringColor = ACCENT
        background = ripple(null, oval())
        setOnClickListener { action() }
        parent.addView(this, LinearLayout.LayoutParams(dp(CHOICE_DP), dp(TOUCH_TARGET_DP)))
    }

    private fun select(button: ImageButton, selected: Boolean) {
        button.isSelected = selected
        button.imageTintList = enabledColors(if (selected) ACCENT else ICON, DISABLED)
        val highlight = if (selected) rounded(SELECTED, dp(SEGMENT_RADIUS_DP)) else null
        button.background = ripple(highlight, highlight ?: oval())
    }

    private fun divider() = View(this).apply {
        setBackgroundColor(DIVIDER)
        layoutParams = LinearLayout.LayoutParams(dp(1), dp(DIVIDER_HEIGHT_DP)).apply { setMargins(dp(8), 0, dp(8), 0) }
    }

    private fun rounded(color: Int, radius: Int) = GradientDrawable().apply {
        cornerRadius = radius.toFloat()
        setColor(color)
    }

    private fun oval() = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(Color.WHITE)
    }

    // The mask bounds the touch ripple; it defaults to the visible content.
    private fun ripple(content: Drawable?, mask: Drawable? = content) = RippleDrawable(ColorStateList.valueOf(RIPPLE), content, mask)

    private fun enabledColors(enabled: Int, disabled: Int) = ColorStateList(
        arrayOf(intArrayOf(-android.R.attr.state_enabled), intArrayOf()),
        intArrayOf(disabled, enabled),
    )

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    private companion object {
        const val PDF_MIME = "application/pdf"
        const val SETTINGS = "pen"
        const val COLOR_KEY = "penColor"
        const val WIDTH_KEY = "penWidth"
        const val MODE_KEY = "inputMode"
        const val DEFAULT_COLOR = 0
        const val DEFAULT_WIDTH = 1
        const val MATCH = LinearLayout.LayoutParams.MATCH_PARENT
        const val WRAP = LinearLayout.LayoutParams.WRAP_CONTENT
        const val MEDIUM_FONT = "sans-serif-medium"
        const val TITLE_SIZE = 17f
        const val STATUS_SIZE = 12f
        const val COUNTER_SIZE = 14f
        const val BUTTON_TEXT_SIZE = 14f
        const val WELCOME_TITLE_SIZE = 26f
        const val WELCOME_BODY_SIZE = 15f
        const val BAR_HEIGHT_DP = 56
        const val TOUCH_TARGET_DP = 44
        const val CHOICE_DP = 40
        const val PILL_BUTTON_HEIGHT_DP = 36
        const val PILL_HEIGHT_DP = 40
        const val PILL_RADIUS_DP = 20
        const val PILL_MARGIN_DP = 16
        const val SEGMENT_RADIUS_DP = 18
        const val COUNTER_MIN_WIDTH_DP = 64
        const val DIVIDER_HEIGHT_DP = 24
        const val SPINNER_DP = 40
        const val MARK_DP = 88

        // Palette taken from the launcher icon: slate lines, white page, red asterisk.
        val CHROME = Color.rgb(251, 251, 253)
        val CANVAS = Color.rgb(232, 236, 243)
        val TEXT = Color.rgb(43, 50, 64)
        val MUTED = Color.rgb(104, 114, 132)
        val ICON = Color.rgb(74, 85, 104)
        val DISABLED = Color.rgb(185, 193, 206)
        val DISABLED_FILL = Color.rgb(222, 226, 233)
        val ACCENT = Color.rgb(211, 17, 28)
        val SELECTED = Color.rgb(252, 228, 229)
        val SEGMENT = Color.rgb(238, 241, 246)
        val DIVIDER = Color.rgb(222, 226, 233)
        val RIPPLE = Color.argb(40, 43, 50, 64)
        val PILL = Color.argb(214, 43, 50, 64)
        val PILL_ICON = Color.WHITE
        val PILL_DIVIDER = Color.argb(70, 255, 255, 255)

        val COLORS = intArrayOf(Color.rgb(25, 38, 46), Color.rgb(32, 85, 184), Color.rgb(179, 47, 61), Color.rgb(32, 113, 73))
        val COLOR_NAMES = intArrayOf(R.string.black, R.string.blue, R.string.red, R.string.green)
        val WIDTHS = floatArrayOf(1.2f, 2.2f, 4f)
        val WIDTH_NAMES = intArrayOf(R.string.fine, R.string.medium, R.string.bold)
        val WIDTH_DOTS_DP = floatArrayOf(3f, 5f, 8f)
    }
}

/** The line under the title; a document without any notes is not "all exported". */
internal fun EditorState.statusText(): Int? {
    val draft = draft ?: return null
    return when {
        busy -> R.string.working
        draft.dirty -> R.string.unsaved
        draft.ink.values.all { it.isEmpty() } -> R.string.no_notes
        else -> R.string.saved
    }
}
