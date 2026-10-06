package ch.lkmc.asterinked.ui

import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ComponentName
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.graphics.text.LineBreaker
import android.text.TextUtils
import android.transition.Fade
import android.transition.TransitionManager
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.KeyboardShortcutGroup
import android.view.KeyboardShortcutInfo
import android.view.Menu
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.FileProvider
import androidx.core.content.IntentCompat
import androidx.core.content.edit
import androidx.core.graphics.Insets
import androidx.core.os.BundleCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.core.view.updatePaddingRelative
import ch.lkmc.asterinked.R
import ch.lkmc.asterinked.ink.InkKind
import java.io.File

/*
 * The editor is one screen in three states (welcome, loading, editing):
 *
 *   +--------------------------------------------+
 *   | [open]  Name.pdf              [share][Save] |  top bar
 *   |         * Unexported notes                 |
 *   +--------------------------------------------+
 *   |                 +--------+                 |
 *   |                 |  page  |                 |  workspace: page, progress line,
 *   |                 +--------+                 |  page pill, notices, welcome
 *   |             ( <  3 / 12  >  [] )           |
 *   +--------------------------------------------+
 *   | [undo][redo]   (pen|marker|eraser)  [hand] |  tool bar: one row when it fits,
 *   | (o)(o)(o)(o)                     . o O     |  otherwise tools over ink
 *   +--------------------------------------------+
 *
 * Bars draw behind the system bars and pad themselves; welcome and loading
 * hide both bars and use the whole screen.
 */
internal class MainActivity : ComponentActivity() {
    private val model: EditorViewModel by viewModels()
    private val settings by lazy { getSharedPreferences(SETTINGS, MODE_PRIVATE) }
    private val ui by lazy { Components(this) }
    private lateinit var root: ViewGroup
    private lateinit var topBar: View
    private lateinit var title: TextView
    private lateinit var status: TextView
    private lateinit var open: ImageButton
    private lateinit var share: ImageButton
    private lateinit var save: View
    private lateinit var workspace: FrameLayout
    private lateinit var page: InkPageView
    private lateinit var progress: ProgressBar
    private lateinit var pagePill: View
    private lateinit var previous: ImageButton
    private lateinit var next: ImageButton
    private lateinit var counter: TextView
    private lateinit var fit: ImageButton
    private lateinit var welcome: View
    private lateinit var loading: View
    private lateinit var notice: NoticeBar
    private lateinit var toolBar: View
    private lateinit var undo: ImageButton
    private lateinit var redo: ImageButton
    private lateinit var tools: SegmentedControl
    private lateinit var fingerDrawing: ImageButton
    private lateinit var swatches: List<ChoiceDot>
    private lateinit var widths: List<ChoiceDot>
    private var screen: Screen? = null
    private var systemBars = Insets.NONE
    private var mode = InputMode.PEN
    private var colorIndex = DEFAULT_COLOR
    private var widthIndex = DEFAULT_WIDTH
    private var kind = InkKind.PEN
    private var tool = InkTool.PEN
    // A PDF handed over by another app, waiting until the editor is idle so the
    // unsaved-notes check sees the restored draft.
    private var incoming: Uri? = null
    private var confirming: AlertDialog? = null
    private var pageDialog: AlertDialog? = null

    private val openPdf = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(model::open)
    }
    private val savePdf = registerForActivityResult(ActivityResultContracts.CreateDocument(PDF_MIME)) { uri ->
        uri?.let(model::export)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Transparent bars in both themes; the bars underneath supply the colour,
        // so the system must not add its own contrast scrim.
        val transparent = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT)
        enableEdgeToEdge(transparent, transparent)
        window.isNavigationBarContrastEnforced = false
        super.onCreate(savedInstanceState)
        // Pen settings persist across launches, not only across recreation.
        colorIndex = settings.getInt(COLOR_KEY, DEFAULT_COLOR).coerceIn(COLORS.indices)
        widthIndex = settings.getInt(WIDTH_KEY, DEFAULT_WIDTH).coerceIn(WIDTHS.indices)
        mode = InputMode.entries.firstOrNull { it.name == settings.getString(MODE_KEY, null) } ?: InputMode.PEN
        kind = InkKind.entries.firstOrNull { it.name == settings.getString(KIND_KEY, null) } ?: InkKind.PEN
        // The eraser is a momentary tool: it survives rotation, but a new launch writes.
        tool = savedInstanceState?.getString(TOOL_KEY)?.let { InkTool.valueOf(it) } ?: InkTool.PEN
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
        pageDialog?.dismiss()
        super.onDestroy()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString(TOOL_KEY, tool.name)
        incoming?.let { outState.putParcelable(INCOMING_KEY, it) }
        super.onSaveInstanceState(outState)
    }

    // Hardware keyboards: the usual editing shortcuts, routed through the
    // buttons so they obey exactly the same enabled states.
    override fun onKeyShortcut(keyCode: Int, event: KeyEvent): Boolean {
        val target = when (keyCode) {
            KeyEvent.KEYCODE_Z -> if (event.isShiftPressed) redo else undo
            KeyEvent.KEYCODE_Y -> redo
            KeyEvent.KEYCODE_S -> save
            KeyEvent.KEYCODE_O -> open
            else -> return super.onKeyShortcut(keyCode, event)
        }
        if (target.isShown && target.isEnabled) target.performClick()
        return true
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        val target = when (keyCode) {
            KeyEvent.KEYCODE_PAGE_UP -> previous
            KeyEvent.KEYCODE_PAGE_DOWN -> next
            else -> return super.onKeyDown(keyCode, event)
        }
        if (target.isShown && target.isEnabled) target.performClick()
        return true
    }

    override fun onProvideKeyboardShortcuts(data: MutableList<KeyboardShortcutGroup>, menu: Menu?, deviceId: Int) {
        fun shortcut(label: Int, key: Int, modifiers: Int = KeyEvent.META_CTRL_ON) = KeyboardShortcutInfo(getString(label), key, modifiers)
        data.add(KeyboardShortcutGroup(getString(R.string.app_name), listOf(
            shortcut(R.string.undo, KeyEvent.KEYCODE_Z),
            shortcut(R.string.redo, KeyEvent.KEYCODE_Z, KeyEvent.META_CTRL_ON or KeyEvent.META_SHIFT_ON),
            shortcut(R.string.save_copy, KeyEvent.KEYCODE_S),
            shortcut(R.string.open_pdf, KeyEvent.KEYCODE_O),
            shortcut(R.string.previous, KeyEvent.KEYCODE_PAGE_UP, 0),
            shortcut(R.string.next, KeyEvent.KEYCODE_PAGE_DOWN, 0),
        )))
    }

    private fun buildLayout() {
        val column = column().apply { setBackgroundColor(ui.color(R.color.surface)) }
        topBar = buildTopBar()
        column.addView(topBar, LinearLayout.LayoutParams(MATCH, WRAP))

        workspace = FrameLayout(this)
        page = InkPageView(this).apply {
            onTurnPage = ::turnPage
            onErase = model::eraseStrokes
        }
        workspace.addView(page, FrameLayout.LayoutParams(MATCH, MATCH))
        progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            isIndeterminate = true
            indeterminateTintList = ColorStateList.valueOf(ui.color(R.color.accent))
            visibility = View.GONE
        }
        workspace.addView(progress, FrameLayout.LayoutParams(MATCH, ui.dp(Size.PROGRESS), Gravity.TOP))
        // On landscape screens the corner is free canvas beside the page.
        val landscape = resources.configuration.screenWidthDp > resources.configuration.screenHeightDp
        pagePill = buildPagePill()
        val pillGravity = Gravity.BOTTOM or if (landscape) Gravity.END else Gravity.CENTER_HORIZONTAL
        workspace.addView(pagePill, FrameLayout.LayoutParams(WRAP, WRAP, pillGravity).apply {
            setMargins(ui.dp(Space.L), 0, ui.dp(Space.L), ui.dp(Space.L))
        })
        welcome = buildWelcome()
        workspace.addView(welcome, FrameLayout.LayoutParams(MATCH, MATCH))
        loading = buildLoading()
        workspace.addView(loading, FrameLayout.LayoutParams(WRAP, WRAP, Gravity.CENTER))
        notice = NoticeBar(this)
        workspace.addView(notice, FrameLayout.LayoutParams(MATCH, WRAP, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply {
            setMargins(ui.dp(Space.L), 0, ui.dp(Space.L), ui.dp(Space.L))
        })
        column.addView(workspace, LinearLayout.LayoutParams(MATCH, 0, 1f))

        toolBar = buildToolBar()
        column.addView(toolBar, LinearLayout.LayoutParams(MATCH, WRAP))
        root = column
        ViewCompat.setOnApplyWindowInsetsListener(column) { _, insets ->
            systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            applyInsets()
            WindowInsetsCompat.CONSUMED
        }
        setContentView(column)
    }

    private fun buildTopBar(): View {
        val short = resources.configuration.screenHeightDp < COMPACT_HEIGHT_DP
        val bar = row().apply { minimumHeight = ui.dp(if (short) Size.TOP_BAR_COMPACT else Size.TOP_BAR) }
        open = ui.iconButton(R.drawable.ic_open, R.string.open_pdf) { requestOpen() }
        bar.addView(open, square())
        val heading = column().apply { setPadding(ui.dp(Space.XS), ui.dp(Space.S), ui.dp(Space.S), ui.dp(Space.S)) }
        // File names differ most at their end ("Report v2 final.pdf"), so the middle gives way.
        // Aligned to the bar's start, not the text's: an English name in a
        // right-to-left layout would otherwise drift away from its status dot.
        title = ui.text(TextStyle.TITLE).apply {
            isSingleLine = true
            ellipsize = TextUtils.TruncateAt.MIDDLE
            textAlignment = View.TEXT_ALIGNMENT_VIEW_START
        }
        status = ui.text(TextStyle.CAPTION).apply {
            isSingleLine = true
            textAlignment = View.TEXT_ALIGNMENT_VIEW_START
            ellipsize = TextUtils.TruncateAt.END
            compoundDrawablePadding = ui.dp(Space.XS + Space.XXS)
        }
        heading.addView(title)
        heading.addView(status)
        bar.addView(heading, LinearLayout.LayoutParams(0, WRAP, 1f))
        share = ui.iconButton(R.drawable.ic_share, R.string.share) { model.share() }
        bar.addView(share, square())
        // With very large text on a phone the label would leave no room for the
        // file name; the action keeps its graphite weight as an icon.
        val crowded = resources.configuration.fontScale >= LARGE_FONT_SCALE && resources.configuration.screenWidthDp < SAVE_LABEL_MIN_WIDTH_DP
        val saveCopy = { launchPicker { savePdf.launch(exportName()) } }
        save = if (crowded) ui.primaryIconButton(R.drawable.ic_save, R.string.save_copy, saveCopy)
            else ui.primaryButton(R.string.save_copy, ButtonSize.REGULAR, action = saveCopy)
        bar.addView(save, LinearLayout.LayoutParams(WRAP, WRAP).apply { marginStart = ui.dp(Space.XS) })
        return bar
    }

    // Previous, the page number (tap to jump), next, then Fit.
    private fun buildPagePill(): View {
        val pill = row().apply {
            background = ui.raisedPill()
            elevation = ui.dp(Elevation.RAISED).toFloat()
            setPadding(ui.dp(Space.XXS), 0, ui.dp(Space.XXS), 0)
        }
        previous = ui.iconButton(R.drawable.ic_previous, R.string.previous) { turnPage(-1) }
        pill.addView(previous, square())
        counter = ui.text(TextStyle.COUNTER).apply {
            gravity = Gravity.CENTER
            minWidth = ui.dp(COUNTER_MIN_WIDTH_DP)
            setPadding(ui.dp(Space.XS), 0, ui.dp(Space.XS), 0)
            background = ui.ripple(content = null, mask = ui.rounded(Color.WHITE, Radius.SMALL))
            tooltipText = getString(R.string.go_to_page)
            setOnClickListener { askForPage() }
        }
        pill.addView(counter, LinearLayout.LayoutParams(WRAP, ui.dp(Size.TOUCH)))
        next = ui.iconButton(R.drawable.ic_next, R.string.next) { turnPage(1) }
        pill.addView(next, square())
        pill.addView(ui.divider(), LinearLayout.LayoutParams(ui.dp(Size.HAIRLINE), ui.dp(Size.DIVIDER)).apply {
            setMargins(ui.dp(Space.XS), 0, ui.dp(Space.XS), 0)
        })
        fit = ui.iconButton(R.drawable.ic_fit, R.string.fit) { page.resetZoom() }
        pill.addView(fit, square())
        return pill
    }

    // History, writing tools and the finger toggle, ink colours, pen widths.
    // Measured controls and available width choose the rows, so narrow windows
    // and system insets never squeeze or clip the touch targets.
    private fun buildToolBar(): View {
        undo = ui.iconButton(R.drawable.ic_undo, R.string.undo) { model.undo() }
        redo = ui.iconButton(R.drawable.ic_redo, R.string.redo) { model.redo() }
        tools = SegmentedControl(this)
        for (choice in ToolChoice.entries) {
            tools.addView(ui.segment(choice.icon, choice.chosenIcon, choice.label, choice.ordinal) { selectTool(choice) }, square())
        }
        ui.choiceGroup(tools, ToolChoice.entries.size)
        fingerDrawing = ui.toggleButton(R.drawable.ic_touch, R.drawable.ic_touch_filled, R.string.draw_with_finger) { toggleFingerDrawing() }
        swatches = COLORS.indices.map { index -> ui.choice(COLOR_NAMES[index], index) { pickColor(index) } }
        widths = WIDTHS.indices.map { index ->
            ui.choice(WIDTH_NAMES[index], index) { pickWidth(index) }.apply { radius = WIDTH_DOTS_DP[index] }
        }

        val history = group(undo, redo)
        val colors = group(*swatches.toTypedArray()).also { ui.choiceGroup(it, swatches.size) }
        val sizes = group(*widths.toTypedArray()).also { ui.choiceGroup(it, widths.size) }

        fun wideRow() = row().apply {
            gravity = Gravity.CENTER
            addView(history, LinearLayout.LayoutParams(WRAP, WRAP))
            addView(tools, gap(Space.XL))
            addView(fingerDrawing, square().apply { marginStart = ui.dp(Space.S) })
            addView(colors, gap(Space.XL))
            addView(sizes, gap(Space.L))
        }

        val initialRow = wideRow()
        initialRow.measure(View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
            View.MeasureSpec.makeMeasureSpec(ui.dp(Size.TOOL_ROW), View.MeasureSpec.EXACTLY))
        val singleRowWidth = initialRow.measuredWidth
        val doubleRowWidth = maxOf(
            maxOf(history.measuredWidth, fingerDrawing.measuredWidth) * 2 + tools.measuredWidth,
            colors.measuredWidth + sizes.measuredWidth,
        )

        fun stackedRows(arrangement: ToolBarRows) = column().apply {
            // Equal side cells centre the tools where possible. On narrower
            // windows each side keeps its own content width instead.
            val sideWidth = if (arrangement == ToolBarRows.DOUBLE) 0 else WRAP
            val writing = row()
            writing.addView(history, LinearLayout.LayoutParams(sideWidth, WRAP, 1f))
            writing.addView(tools, LinearLayout.LayoutParams(WRAP, WRAP))
            writing.addView(row().apply {
                gravity = Gravity.END or Gravity.CENTER_VERTICAL
                addView(fingerDrawing, square())
            }, LinearLayout.LayoutParams(sideWidth, WRAP, 1f))
            addView(writing, LinearLayout.LayoutParams(MATCH, ui.dp(Size.TOOL_ROW)))

            if (arrangement == ToolBarRows.TRIPLE) {
                for (choices in listOf(colors, sizes)) {
                    addView(row().apply {
                        gravity = Gravity.CENTER
                        addView(choices, LinearLayout.LayoutParams(WRAP, WRAP))
                    }, LinearLayout.LayoutParams(MATCH, ui.dp(Size.TOOL_ROW)))
                }
                return@apply
            }

            val ink = row()
            ink.addView(colors, LinearLayout.LayoutParams(0, WRAP, 1f))
            ink.addView(sizes, LinearLayout.LayoutParams(WRAP, WRAP))
            addView(ink, LinearLayout.LayoutParams(MATCH, ui.dp(Size.TOOL_ROW)))
        }

        return object : LinearLayout(this) {
            private var arrangement = ToolBarRows.SINGLE

            init {
                orientation = VERTICAL
                setPadding(0, ui.dp(Space.XS), 0, ui.dp(Space.XS))
                addView(initialRow, LayoutParams(MATCH, ui.dp(Size.TOOL_ROW)))
            }

            override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
                val available = if (MeasureSpec.getMode(widthMeasureSpec) == MeasureSpec.UNSPECIFIED) Int.MAX_VALUE
                    else MeasureSpec.getSize(widthMeasureSpec) - paddingLeft - paddingRight
                val next = when {
                    available >= singleRowWidth -> ToolBarRows.SINGLE
                    available >= doubleRowWidth -> ToolBarRows.DOUBLE
                    else -> ToolBarRows.TRIPLE
                }
                if (next != arrangement) {
                    arrangement = next
                    // Reuse the controls so reflow preserves selection and listeners.
                    for (control in listOf(history, tools, fingerDrawing, colors, sizes)) {
                        (control.parent as? ViewGroup)?.removeView(control)
                    }
                    removeAllViews()
                    val content = if (next == ToolBarRows.SINGLE) wideRow() else stackedRows(next)
                    val height = if (next == ToolBarRows.SINGLE) ui.dp(Size.TOOL_ROW) else WRAP
                    addView(content, LayoutParams(MATCH, height))
                }
                super.onMeasure(widthMeasureSpec, heightMeasureSpec)
            }
        }
    }

    private fun buildWelcome(): View {
        val content = MaxWidthLayout(this).apply {
            maxWidth = ui.dp(Size.CONTENT_MAX)
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(ui.dp(Space.XL), ui.dp(Space.XXL), ui.dp(Space.XL), ui.dp(Space.XXL))
        }
        content.addView(ImageView(this).apply {
            setImageResource(R.drawable.ic_asterisk)
            imageTintList = ColorStateList.valueOf(ui.color(R.color.accent))
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }, LinearLayout.LayoutParams(ui.dp(Size.MARK), ui.dp(Size.MARK)))
        // Balanced breaks keep a centred heading from ending on one orphaned word.
        content.addView(ui.text(TextStyle.DISPLAY, getString(R.string.welcome_title)).apply {
            gravity = Gravity.CENTER
            breakStrategy = LineBreaker.BREAK_STRATEGY_BALANCED
            ViewCompat.setAccessibilityHeading(this, true)
        }, spaced(Space.XL))
        content.addView(ui.text(TextStyle.BODY, getString(R.string.welcome_body)).apply {
            gravity = Gravity.CENTER
            breakStrategy = LineBreaker.BREAK_STRATEGY_BALANCED
            setLineSpacing(0f, BODY_LINE_SPACING)
        }, spaced(Space.M))
        content.addView(ui.primaryButton(R.string.open_pdf, ButtonSize.LARGE, R.drawable.ic_open) { requestOpen() }, spaced(Space.XXL))
        content.addView(ui.text(TextStyle.CAPTION, getString(R.string.welcome_caption)).apply {
            gravity = Gravity.CENTER
            breakStrategy = LineBreaker.BREAK_STRATEGY_BALANCED
            setLineSpacing(0f, BODY_LINE_SPACING)
        }, spaced(Space.L))

        // Scrolls only when large text no longer fits; otherwise centred.
        return ScrollView(this).apply {
            isFillViewport = true
            isVerticalScrollBarEnabled = false
            setBackgroundColor(ui.color(R.color.surface))
            addView(FrameLayout(context).apply {
                addView(content, FrameLayout.LayoutParams(MATCH, WRAP, Gravity.CENTER))
            }, FrameLayout.LayoutParams(MATCH, MATCH))
        }
    }

    private fun buildLoading(): View = column().apply {
        gravity = Gravity.CENTER_HORIZONTAL
        addView(ProgressBar(context).apply {
            isIndeterminate = true
            indeterminateTintList = ColorStateList.valueOf(ui.color(R.color.accent))
        }, LinearLayout.LayoutParams(ui.dp(Size.SPINNER), ui.dp(Size.SPINNER)))
        addView(ui.text(TextStyle.CAPTION, getString(R.string.opening)), spaced(Space.M))
    }

    private fun configurePen() {
        val highlighting = kind == InkKind.HIGHLIGHTER
        // The same colour and width choices pick a highlighter tint and a line-height width.
        val colors = if (highlighting) HIGHLIGHT_COLORS else COLORS
        val names = if (highlighting) HIGHLIGHT_NAMES else COLOR_NAMES
        tools.select(currentTool().ordinal)
        fingerDrawing.isSelected = mode == InputMode.TOUCH
        swatches.forEachIndexed { index, swatch ->
            swatch.isSelected = index == colorIndex
            swatch.fill = colors[index]
            swatch.contentDescription = getString(names[index])
            swatch.tooltipText = swatch.contentDescription
        }
        // Width dots preview the current ink colour, unless it would vanish
        // into the bar (graphite at night, yellow by day).
        widths.forEachIndexed { index, dot ->
            dot.isSelected = index == widthIndex
            val ink = colors[colorIndex]
            dot.fill = if (dot.blendsIn(ink)) ui.color(R.color.on_surface_variant) else ink
        }
        val width = (if (highlighting) HIGHLIGHT_WIDTHS else WIDTHS)[widthIndex]
        page.configure(mode, colors[colorIndex], width, kind, model::addStroke)
        page.tool = tool
        settings.edit {
            putInt(COLOR_KEY, colorIndex)
            putInt(WIDTH_KEY, widthIndex)
            putString(MODE_KEY, mode.name)
            putString(KIND_KEY, kind.name)
        }
    }

    private fun currentTool(): ToolChoice = when {
        tool == InkTool.ERASER -> ToolChoice.ERASER
        kind == InkKind.HIGHLIGHTER -> ToolChoice.HIGHLIGHTER
        else -> ToolChoice.PEN
    }

    private fun selectTool(choice: ToolChoice) {
        if (choice == currentTool()) return

        // The eraser leaves the ink kind alone, so the colours keep showing the
        // pen or highlighter palette it interrupted.
        when (choice) {
            ToolChoice.PEN -> { kind = InkKind.PEN; tool = InkTool.PEN }
            ToolChoice.HIGHLIGHTER -> { kind = InkKind.HIGHLIGHTER; tool = InkTool.PEN }
            ToolChoice.ERASER -> tool = InkTool.ERASER
        }
        tick()
        configurePen()
        choice.hint?.let(::hint)
    }

    private fun toggleFingerDrawing() {
        mode = if (mode == InputMode.TOUCH) InputMode.PEN else InputMode.TOUCH
        tick()
        configurePen()
        // The mode changes what fingers do; say so once, where the user is looking.
        hint(if (mode == InputMode.PEN) R.string.input_hint else R.string.touch_hint)
    }

    // Picking ink means writing: it also puts the eraser away.
    private fun pickColor(index: Int) {
        colorIndex = index
        tool = InkTool.PEN
        tick()
        configurePen()
    }

    private fun pickWidth(index: Int) {
        widthIndex = index
        tool = InkTool.PEN
        tick()
        configurePen()
    }

    private fun tick() {
        root.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
    }

    private fun hint(message: Int) = notice.show(getString(message), Tone.INFO)

    private fun show(state: EditorState) {
        val draft = state.draft
        val ready = draft != null && !state.busy
        showScreen(when {
            draft != null -> Screen.EDITOR
            state.busy -> Screen.LOADING
            else -> Screen.WELCOME
        })
        title.text = draft?.name.orEmpty()
        showStatus(state.statusText())
        progress.visibility = if (draft != null && state.busy) View.VISIBLE else View.GONE

        open.isEnabled = !state.busy
        save.isEnabled = ready
        share.isEnabled = ready
        undo.isEnabled = ready && state.canUndo
        redo.isEnabled = ready && state.canRedo
        fit.isEnabled = ready
        previous.isEnabled = ready && draft!!.page > 0
        next.isEnabled = ready && draft!!.page < state.pages.lastIndex
        tools.isEnabled = ready
        (listOf(fingerDrawing) + swatches + widths).forEach { it.isEnabled = ready }
        if (draft != null) {
            counter.text = getString(R.string.page_position, draft.page + 1, state.pages.size)
            counter.contentDescription = getString(R.string.page_count, draft.page + 1, state.pages.size)
            counter.isEnabled = ready && state.pages.size > 1
        }

        page.show(state)
        state.message?.let {
            val action = when (it.action) {
                MessageAction.SAVE_COPY -> NoticeAction(getString(R.string.save_copy)) { launchPicker { savePdf.launch(exportName()) } }
                null -> null
            }
            notice.show(it.text, it.tone, action)
            model.acknowledgeMessage()
        }
        state.shared?.let {
            model.acknowledgeShare()
            sendToShareSheet(it)
        }
        state.exported?.let {
            model.acknowledgeExport()
            notice.show(getString(R.string.pdf_saved), Tone.SUCCESS,
                NoticeAction(getString(R.string.open)) { openExported(it) })
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

    // Welcome and loading take the whole screen; the editor brings its bars.
    // Switching cross-fades, except on the first frame.
    private fun showScreen(next: Screen) {
        if (screen == next) return
        if (screen != null) TransitionManager.beginDelayedTransition(root, Fade().setDuration(Motion.SHORT))
        screen = next
        val editing = next == Screen.EDITOR
        listOf(topBar, toolBar, pagePill).forEach { it.visibility = if (editing) View.VISIBLE else View.GONE }
        // Invisible rather than gone, so the page keeps its size for the editor.
        page.visibility = if (editing) View.VISIBLE else View.INVISIBLE
        welcome.visibility = if (next == Screen.WELCOME) View.VISIBLE else View.GONE
        loading.visibility = if (next == Screen.LOADING) View.VISIBLE else View.GONE
        workspace.setBackgroundColor(ui.color(if (editing) R.color.canvas else R.color.surface))
        if (next == Screen.LOADING) {
            // Restoring a draft is usually instant; only a slow load shows the spinner.
            loading.alpha = 0f
            loading.animate().alpha(1f).setStartDelay(Motion.LOADING_DELAY).setDuration(Motion.SHORT).start()
        }
        applyInsets()
    }

    // The line under the title: a red dot while notes are unexported, a check
    // once they all are.
    private fun showStatus(text: Int?) {
        status.text = text?.let(::getString).orEmpty()
        status.visibility = if (text == null) View.GONE else View.VISIBLE
        val marker: Drawable? = when (text) {
            R.string.unsaved -> GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(ui.color(R.color.accent))
                setBounds(0, 0, ui.dp(Size.STATUS_DOT), ui.dp(Size.STATUS_DOT))
            }
            R.string.saved -> getDrawable(R.drawable.ic_success)!!.mutate().apply {
                setTint(ui.color(R.color.on_surface_variant))
                setBounds(0, 0, ui.dp(Size.STATUS_ICON), ui.dp(Size.STATUS_ICON))
            }
            else -> null
        }
        status.setCompoundDrawablesRelative(marker, null, null, null)
    }

    // Bars extend behind the system bars and pad their content out of them;
    // the page keeps clear of side insets (landscape navigation, cutouts).
    private fun applyInsets() {
        if (screen == null) return
        val editing = screen == Screen.EDITOR
        // Insets are physical sides; the top bar's padding follows the reading
        // direction, so Open stays above Undo right to left as well.
        val rightToLeft = resources.configuration.layoutDirection == View.LAYOUT_DIRECTION_RTL
        val startInset = if (rightToLeft) systemBars.right else systemBars.left
        val endInset = if (rightToLeft) systemBars.left else systemBars.right
        topBar.updatePaddingRelative(start = startInset + ui.dp(Space.XS), top = systemBars.top, end = endInset + ui.dp(Space.M))
        // Both bars inset their outer icons alike, so they line up vertically.
        toolBar.updatePadding(left = systemBars.left + ui.dp(Space.XS), right = systemBars.right + ui.dp(Space.XS),
            bottom = systemBars.bottom + ui.dp(Space.XS))
        workspace.updatePadding(left = systemBars.left, right = systemBars.right,
            top = if (editing) 0 else systemBars.top, bottom = if (editing) 0 else systemBars.bottom)
        // Notices sit above the page pill, so page navigation stays in reach.
        (notice.layoutParams as FrameLayout.LayoutParams).bottomMargin =
            ui.dp(if (editing) Space.L + Size.TOUCH + Space.S else Space.L)
        notice.requestLayout()
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

    private fun askForPage() {
        val state = model.state.value ?: return
        val draft = state.draft ?: return
        pageDialog = showPageDialog(this, ui, draft.page, state.pages.size, model::goToPage)
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

    // The export destination is ours to read back; the grant reaches the
    // chosen viewer through ClipData, and Asterinked itself is excluded so the
    // copy cannot be mistaken for a document to annotate.
    private fun openExported(uri: Uri) {
        val view = Intent(Intent.ACTION_VIEW).setDataAndType(uri, PDF_MIME)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        view.clipData = ClipData.newRawUri(uri.lastPathSegment ?: exportName(), uri)
        val chooser = Intent.createChooser(view, getString(R.string.open_title))
            .putExtra(Intent.EXTRA_EXCLUDE_COMPONENTS, arrayOf(ComponentName(this, MainActivity::class.java)))
        launchPicker(R.string.no_viewer) { startActivity(chooser) }
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

    private fun launchPicker(failure: Int = R.string.no_picker, action: () -> Unit) {
        try { action() } catch (_: ActivityNotFoundException) {
            notice.show(getString(failure), Tone.ERROR)
        }
    }

    private fun column() = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    private fun row() = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
    private fun group(vararg views: View) = row().apply { views.forEach { addView(it, square()) } }
    private fun square() = LinearLayout.LayoutParams(ui.dp(Size.TOUCH), ui.dp(Size.TOUCH))
    private fun gap(space: Int) = LinearLayout.LayoutParams(WRAP, WRAP).apply { marginStart = ui.dp(space) }
    private fun spaced(space: Int) = LinearLayout.LayoutParams(WRAP, WRAP).apply { topMargin = ui.dp(space) }

    private enum class Screen { WELCOME, LOADING, EDITOR }

    private enum class ToolBarRows { SINGLE, DOUBLE, TRIPLE }

    /** The writing tools, in tool bar order. */
    private enum class ToolChoice(val icon: Int, val chosenIcon: Int, val label: Int, val hint: Int?) {
        PEN(R.drawable.ic_pen, R.drawable.ic_pen_filled, R.string.pen, null),
        HIGHLIGHTER(R.drawable.ic_highlighter, R.drawable.ic_highlighter_filled, R.string.highlighter, R.string.highlight_hint),
        ERASER(R.drawable.ic_eraser, R.drawable.ic_eraser_filled, R.string.eraser, R.string.eraser_hint),
    }

    private companion object {
        const val PDF_MIME = "application/pdf"
        const val SETTINGS = "pen"
        const val COLOR_KEY = "penColor"
        const val WIDTH_KEY = "penWidth"
        const val MODE_KEY = "inputMode"
        const val KIND_KEY = "inkKind"
        const val TOOL_KEY = "inkTool"
        const val INCOMING_KEY = "incomingPdf"
        // Matches android:authorities="${'$'}{applicationId}.files" in the manifest.
        const val FILE_AUTHORITY_SUFFIX = ".files"
        const val DEFAULT_EXPORT_NAME = "Document-annotated.pdf"
        const val DEFAULT_COLOR = 0
        const val DEFAULT_WIDTH = 1
        const val MATCH = LinearLayout.LayoutParams.MATCH_PARENT
        const val WRAP = LinearLayout.LayoutParams.WRAP_CONTENT
        const val COUNTER_MIN_WIDTH_DP = 64
        const val BODY_LINE_SPACING = 1.2f
        // Large-text phones use the icon-only Save button.
        const val SAVE_LABEL_MIN_WIDTH_DP = 680
        // Phones in landscape: a lower top bar leaves more height for the page.
        const val COMPACT_HEIGHT_DP = 480
        // Android's "largest" text sizes start around 1.5x.
        const val LARGE_FONT_SCALE = 1.5f

        val COLORS = intArrayOf(Color.rgb(25, 38, 46), Color.rgb(32, 85, 184), Color.rgb(179, 47, 61), Color.rgb(32, 113, 73))
        val COLOR_NAMES = intArrayOf(R.string.black, R.string.blue, R.string.red, R.string.green)
        val WIDTHS = floatArrayOf(1.2f, 2.2f, 4f)
        val WIDTH_NAMES = intArrayOf(R.string.fine, R.string.medium, R.string.bold)
        val WIDTH_DOTS_DP = floatArrayOf(3f, 5f, 8f)
        // Light tints: multiplied with the page they mark text without hiding it.
        val HIGHLIGHT_COLORS = intArrayOf(Color.rgb(255, 228, 92), Color.rgb(159, 211, 255), Color.rgb(255, 168, 207), Color.rgb(168, 235, 158))
        val HIGHLIGHT_NAMES = intArrayOf(R.string.yellow, R.string.blue, R.string.pink, R.string.green)
        val HIGHLIGHT_WIDTHS = floatArrayOf(8f, 12f, 18f)
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
