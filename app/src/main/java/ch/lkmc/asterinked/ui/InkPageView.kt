package ch.lkmc.asterinked.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import ch.lkmc.asterinked.document.PageSpec
import ch.lkmc.asterinked.ink.InkEraser
import ch.lkmc.asterinked.ink.InkGeometry
import ch.lkmc.asterinked.ink.InkPoint
import ch.lkmc.asterinked.ink.InkSegment
import ch.lkmc.asterinked.ink.InkStroke
import java.util.Collections
import java.util.IdentityHashMap
import kotlin.math.min

internal enum class InputMode { PEN, TOUCH }

/** What a writing gesture does. The stylus eraser end and side button always erase. */
internal enum class InkTool { PEN, ERASER }

internal class InkPageView(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
    private val bitmapPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val pageRect = RectF()
    private var preview: Bitmap? = null
    private var spec: PageSpec? = null
    private var strokes = emptyList<InkStroke>()
    private var geometry = emptyList<Pair<InkStroke, List<InkSegment>>>()
    private var points = mutableListOf<InkPoint>()
    private val eraser = InkEraser()
    private val erasing: MutableSet<InkStroke> = Collections.newSetFromMap(IdentityHashMap())
    private val eraserRing = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = Color.argb(160, 60, 70, 80) }
    private var eraserAt: InkPoint? = null
    private var activeErasing = false
    private var activePointer = NO_POINTER
    private var activeColor = Color.BLACK
    private var activeWidth = DEFAULT_WIDTH
    private var pageKey: String? = null
    private var zoom = 1f
    private var panX = 0f
    private var panY = 0f
    private var lastX = 0f
    private var lastY = 0f
    private var inputMode = InputMode.PEN
    private var inkColor = Color.rgb(25, 38, 46)
    private var inkWidth = DEFAULT_WIDTH
    private var onStroke: (InkStroke) -> Unit = {}

    /** Receives the strokes one erase gesture removed, once the gesture ends. */
    var onErase: (Collection<InkStroke>) -> Unit = {}

    var tool = InkTool.PEN
        set(value) {
            if (field == value) return
            cancelStroke()
            field = value
        }
    private val scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            val previous = zoom
            zoom = (zoom * detector.scaleFactor).coerceIn(1f, MAX_ZOOM)
            val ratio = zoom / previous
            panX = (panX - detector.focusX + width / 2f) * ratio + detector.focusX - width / 2f
            panY = (panY - detector.focusY + height / 2f) * ratio + detector.focusY - height / 2f
            invalidate()
            return true
        }
    })

    init {
        setBackgroundColor(Color.rgb(232, 235, 231))
        contentDescription = "PDF page. Write with a pen. Pinch to zoom; drag with a finger to pan."
        isFocusable = true
    }

    fun configure(mode: InputMode, color: Int, width: Float, onStroke: (InkStroke) -> Unit) {
        cancelStroke()
        inputMode = mode
        inkColor = color
        inkWidth = width
        this.onStroke = onStroke
    }

    fun show(state: EditorState) {
        val draft = state.draft
        val key = draft?.let { "${it.source.name}:${it.page}" }
        if (pageKey != key) {
            cancelStroke()
            zoom = 1f
            panX = 0f
            panY = 0f
            pageKey = key
        }
        if (state.busy) cancelStroke()
        isEnabled = !state.busy && draft != null
        preview = state.preview
        spec = draft?.let { state.pages[it.page] }
        val nextStrokes = draft?.ink?.get(draft.page).orEmpty()
        if (nextStrokes != strokes) {
            strokes = nextStrokes
            geometry = strokes.map { it to InkGeometry.segments(it) }
        }
        invalidate()
    }

    fun resetZoom() {
        zoom = 1f
        panX = 0f
        panY = 0f
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val page = spec ?: return
        val bitmap = preview ?: return
        val fit = min((width - PAGE_MARGIN * 2) / page.displayWidth, (height - PAGE_MARGIN * 2) / page.displayHeight)
        if (fit <= 0f) return
        val scale = fit * zoom
        val pageWidth = page.displayWidth * scale
        val pageHeight = page.displayHeight * scale
        panX = panX.coerceIn(-maxPan(pageWidth, width), maxPan(pageWidth, width))
        panY = panY.coerceIn(-maxPan(pageHeight, height), maxPan(pageHeight, height))
        pageRect.set((width - pageWidth) / 2f + panX, (height - pageHeight) / 2f + panY, (width + pageWidth) / 2f + panX, (height + pageHeight) / 2f + panY)
        canvas.drawBitmap(bitmap, null, pageRect, bitmapPaint)
        canvas.save()
        canvas.clipRect(pageRect)
        canvas.translate(pageRect.left, pageRect.top)
        canvas.scale(scale, scale)
        // Strokes under the eraser disappear at once and return if the gesture is cancelled.
        for ((stroke, segments) in geometry) if (stroke !in erasing) drawInk(canvas, stroke, segments)
        if (points.isNotEmpty()) {
            val stroke = InkStroke(points, activeColor, activeWidth)
            drawInk(canvas, stroke, InkGeometry.segments(stroke))
        }
        eraserAt?.let {
            eraserRing.strokeWidth = resources.displayMetrics.density / scale
            canvas.drawCircle(it.x, it.y, eraserRadius(), eraserRing)
        }
        canvas.restore()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isEnabled || spec == null) return false
        val action = event.actionMasked
        if (action == MotionEvent.ACTION_CANCEL) {
            cancelStroke()
            return true
        }

        // Track the pen by pointer ID: a palm may become pointer index zero.
        if (activePointer != NO_POINTER) {
            val index = event.findPointerIndex(activePointer)
            if (index < 0) { cancelStroke(); return true }
            if (event.getToolType(index) == MotionEvent.TOOL_TYPE_FINGER && event.pointerCount > 1) {
                cancelStroke()
            } else {
                val pointerUp = action == MotionEvent.ACTION_UP ||
                    (action == MotionEvent.ACTION_POINTER_UP && event.getPointerId(event.actionIndex) == activePointer)
                if (action == MotionEvent.ACTION_MOVE || pointerUp) track(event, index)
                if (pointerUp) {
                    when {
                        event.flags and MotionEvent.FLAG_CANCELED != 0 -> cancelStroke()
                        activeErasing -> finishErase()
                        else -> finishStroke()
                    }
                    performClick()
                }
                return true
            }
        }

        val index = event.actionIndex
        val stylus = event.getToolType(index) == MotionEvent.TOOL_TYPE_STYLUS
        val eraserEnd = event.getToolType(index) == MotionEvent.TOOL_TYPE_ERASER
        if ((action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_POINTER_DOWN) &&
            (stylus || eraserEnd || (inputMode == InputMode.TOUCH && event.pointerCount == 1))) {
            if (pageRect.contains(event.getX(index), event.getY(index))) {
                activePointer = event.getPointerId(index)
                activeColor = inkColor
                activeWidth = inkWidth
                activeErasing = eraserEnd || tool == InkTool.ERASER || (stylus && event.buttonState and STYLUS_BUTTONS != 0)
                if (stylus || eraserEnd) requestUnbufferedDispatch(event)
                track(event, index)
                parent?.requestDisallowInterceptTouchEvent(true)
                return true
            }
        }
        if (stylus || eraserEnd) return true

        scaleDetector.onTouchEvent(event)
        when (action) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN, MotionEvent.ACTION_POINTER_UP -> {
                lastX = event.x; lastY = event.y
            }
            MotionEvent.ACTION_MOVE -> {
                if (!scaleDetector.isInProgress && event.pointerCount == 1 && inputMode == InputMode.PEN) {
                    panX += event.x - lastX
                    panY += event.y - lastY
                    invalidate()
                }
                lastX = event.x; lastY = event.y
            }
        }
        return true
    }

    override fun performClick(): Boolean { super.performClick(); return true }

    private fun track(event: MotionEvent, index: Int) {
        if (activeErasing) eraseAlong(event, index) else addSamples(event, index)
    }

    private fun eraseAlong(event: MotionEvent, index: Int) {
        val page = spec ?: return
        fun erase(x: Float, y: Float) {
            val point = InkPoint((x - pageRect.left) / pageRect.width() * page.displayWidth,
                (y - pageRect.top) / pageRect.height() * page.displayHeight, 1f)
            if (!point.x.isFinite() || !point.y.isFinite()) return
            val candidates = strokes.filter { it !in erasing }
            erasing.addAll(eraser.hits(candidates, eraserAt, point, eraserRadius()))
            eraserAt = point
        }
        for (history in 0 until event.historySize) erase(event.getHistoricalX(index, history), event.getHistoricalY(index, history))
        erase(event.getX(index), event.getY(index))
        invalidate()
    }

    private fun finishErase() {
        val erased = strokes.filter { it in erasing }
        cancelStroke()
        if (erased.isNotEmpty()) onErase(erased)
    }

    // A constant size on screen: zooming in makes the eraser more precise on the page.
    private fun eraserRadius(): Float {
        val page = spec ?: return 0f
        return ERASER_RADIUS_DP * resources.displayMetrics.density * page.displayWidth / pageRect.width()
    }

    private fun addSamples(event: MotionEvent, index: Int) {
        val page = spec ?: return
        fun add(x: Float, y: Float, pressure: Float) {
            val point = InkPoint((x - pageRect.left) / pageRect.width() * page.displayWidth,
                (y - pageRect.top) / pageRect.height() * page.displayHeight,
                if (event.getToolType(index) == MotionEvent.TOOL_TYPE_STYLUS) pressure else TOUCH_PRESSURE)
            if (point.x.isFinite() && point.y.isFinite()) points.add(point)
        }
        // Historical samples retain curves during fast writing and batched input.
        for (history in 0 until event.historySize) {
            add(event.getHistoricalX(index, history), event.getHistoricalY(index, history), event.getHistoricalPressure(index, history))
        }
        add(event.getX(index), event.getY(index), event.getPressure(index))
        invalidate()
    }

    private fun finishStroke() {
        val stroke = InkStroke(points.toList(), activeColor, activeWidth)
        cancelStroke()
        onStroke(stroke)
    }

    private fun cancelStroke() {
        points = mutableListOf()
        erasing.clear()
        eraser.reset()
        eraserAt = null
        activeErasing = false
        activePointer = NO_POINTER
        parent?.requestDisallowInterceptTouchEvent(false)
        invalidate()
    }

    private fun drawInk(canvas: Canvas, stroke: InkStroke, segments: List<InkSegment>) {
        paint.color = stroke.color
        for (segment in segments) {
            paint.strokeWidth = segment.width
            val start = segment.start
            val end = segment.end
            if (start.x == end.x && start.y == end.y) canvas.drawCircle(start.x, start.y, segment.width / 2f, paint)
            else canvas.drawLine(start.x, start.y, end.x, end.y, paint)
        }
    }

    private fun maxPan(pageSize: Float, viewSize: Int): Float = ((pageSize - viewSize) / 2f + PAGE_MARGIN).coerceAtLeast(0f)

    private companion object {
        const val NO_POINTER = -1
        const val DEFAULT_WIDTH = 2.2f
        const val TOUCH_PRESSURE = 0.65f
        const val MAX_ZOOM = 5f
        const val PAGE_MARGIN = 24f
        const val ERASER_RADIUS_DP = 10f
        // Most pens report the side button as primary; older S Pens report secondary.
        const val STYLUS_BUTTONS = MotionEvent.BUTTON_STYLUS_PRIMARY or MotionEvent.BUTTON_SECONDARY
    }
}
