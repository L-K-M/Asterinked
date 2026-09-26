package ch.lkmc.asterinked.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.animation.DecelerateInterpolator
import ch.lkmc.asterinked.document.PageSpec
import ch.lkmc.asterinked.ink.InkGeometry
import ch.lkmc.asterinked.ink.InkPoint
import ch.lkmc.asterinked.ink.InkSegment
import ch.lkmc.asterinked.ink.InkStroke
import kotlin.math.abs
import kotlin.math.min

internal enum class InputMode { PEN, TOUCH }

internal class InkPageView(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
    private val bitmapPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val pageRect = RectF()
    private var preview: Bitmap? = null
    private var spec: PageSpec? = null
    private var strokes = emptyList<InkStroke>()
    private var geometry = emptyList<Pair<InkStroke, List<InkSegment>>>()
    private var points = mutableListOf<InkPoint>()
    private var activePointer = NO_POINTER
    private var activeColor = Color.BLACK
    private var activeWidth = DEFAULT_WIDTH
    private var pageKey: String? = null
    private var documentKey: String? = null
    private var zoom = 1f
    private var panX = 0f
    private var panY = 0f
    private var alignTopPending = false
    private var lastFocusX = 0f
    private var lastFocusY = 0f
    private var gestureScaled = false
    private var zoomAnimator: ValueAnimator? = null
    private var inputMode = InputMode.PEN
    private var inkColor = Color.rgb(25, 38, 46)
    private var inkWidth = DEFAULT_WIDTH
    private var onStroke: (InkStroke) -> Unit = {}
    private val density = resources.displayMetrics.density
    private val pageMargin = PAGE_MARGIN_DP * density
    private val swipeDistance = SWIPE_DISTANCE_DP * density
    private val swipeVelocity = SWIPE_VELOCITY_DP * density

    /** Called with +1 or -1 when a finger swipes the page at fit zoom in pen mode. */
    var onTurnPage: (Int) -> Unit = {}

    private val scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
            gestureScaled = true
            cancelZoomAnimation()
            return true
        }

        override fun onScale(detector: ScaleGestureDetector): Boolean {
            zoomAround(zoom * detector.scaleFactor, detector.focusX, detector.focusY)
            return true
        }
    })
    private val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        // ScaleGestureDetector (quick scale is on by default) turns a double-tap-and-
        // drag into a one-finger zoom. Toggling only on the second tap's UP, and
        // only if no scale happened, keeps that gesture working.
        override fun onDoubleTapEvent(e: MotionEvent): Boolean {
            if (inputMode != InputMode.PEN || e.actionMasked != MotionEvent.ACTION_UP || gestureScaled) return false
            animateZoom(if (zoom > FIT_ZOOM_TOLERANCE) 1f else DOUBLE_TAP_ZOOM, e.x, e.y)
            return true
        }

        override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
            val start = e1 ?: return false
            if (inputMode != InputMode.PEN || gestureScaled || zoom > FIT_ZOOM_TOLERANCE) return false
            val distance = e2.x - start.x
            val horizontal = abs(velocityX) >= abs(velocityY) * SWIPE_DIRECTION_RATIO
            if (!horizontal || abs(velocityX) < swipeVelocity || abs(distance) < swipeDistance) return false
            onTurnPage(if (distance < 0) 1 else -1)
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
        val document = draft?.source?.name
        val key = draft?.let { "${it.source.name}:${it.page}" }
        if (pageKey != key) {
            cancelStroke()
            cancelZoomAnimation()
            if (document != null && document == documentKey) {
                // Keep the zoom and column across page turns; start at the page top.
                alignTopPending = true
            } else {
                zoom = 1f
                panX = 0f
                panY = 0f
            }
            pageKey = key
            documentKey = document
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
        animateZoom(1f, width / 2f, height / 2f)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val page = spec ?: return
        val bitmap = preview ?: return
        val fit = fitScale(page)
        if (fit <= 0f) return
        val scale = fit * zoom
        val pageWidth = page.displayWidth * scale
        val pageHeight = page.displayHeight * scale
        if (alignTopPending) {
            panY = maxPan(pageHeight, height)
            alignTopPending = false
        }
        clampPan()
        pageRect.set((width - pageWidth) / 2f + panX, (height - pageHeight) / 2f + panY, (width + pageWidth) / 2f + panX, (height + pageHeight) / 2f + panY)
        canvas.drawBitmap(bitmap, null, pageRect, bitmapPaint)
        canvas.save()
        canvas.clipRect(pageRect)
        canvas.translate(pageRect.left, pageRect.top)
        canvas.scale(scale, scale)
        for ((stroke, segments) in geometry) drawInk(canvas, stroke, segments)
        if (points.isNotEmpty()) {
            val stroke = InkStroke(points, activeColor, activeWidth)
            drawInk(canvas, stroke, InkGeometry.segments(stroke))
        }
        canvas.restore()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isEnabled || spec == null) return false
        val action = event.actionMasked
        if (action == MotionEvent.ACTION_CANCEL) {
            cancelStroke()
            scaleDetector.onTouchEvent(event)
            gestureDetector.onTouchEvent(event)
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
                if (action == MotionEvent.ACTION_MOVE || pointerUp) addSamples(event, index)
                if (pointerUp) {
                    if (event.flags and MotionEvent.FLAG_CANCELED == 0) finishStroke() else cancelStroke()
                    performClick()
                }
                return true
            }
        }

        val index = event.actionIndex
        val stylus = event.getToolType(index) == MotionEvent.TOOL_TYPE_STYLUS
        if ((action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_POINTER_DOWN) &&
            (stylus || (inputMode == InputMode.TOUCH && event.pointerCount == 1))) {
            if (pageRect.contains(event.getX(index), event.getY(index))) {
                activePointer = event.getPointerId(index)
                activeColor = inkColor
                activeWidth = inkWidth
                if (stylus) requestUnbufferedDispatch(event)
                addSamples(event, index)
                parent?.requestDisallowInterceptTouchEvent(true)
                return true
            }
        }
        if (stylus || event.getToolType(index) == MotionEvent.TOOL_TYPE_ERASER) return true

        if (action == MotionEvent.ACTION_DOWN) {
            gestureScaled = false
            cancelZoomAnimation()
        }
        // Pan before scaling: the pinch then zooms around where the fingers are now.
        followFingers(event)
        scaleDetector.onTouchEvent(event)
        gestureDetector.onTouchEvent(event)
        return true
    }

    // The page follows the centroid of the fingers, so a pinch also pans, two
    // fingers pan in touch-ink mode, and lifting one finger never makes the page
    // jump to where the remaining finger is.
    private fun followFingers(event: MotionEvent) {
        val lifting = if (event.actionMasked == MotionEvent.ACTION_POINTER_UP) event.actionIndex else -1
        var sumX = 0f
        var sumY = 0f
        var count = 0
        for (pointer in 0 until event.pointerCount) {
            if (pointer == lifting) continue
            sumX += event.getX(pointer)
            sumY += event.getY(pointer)
            count++
        }
        if (count == 0) return

        val focusX = sumX / count
        val focusY = sumY / count
        if (event.actionMasked == MotionEvent.ACTION_MOVE) {
            panX += focusX - lastFocusX
            panY += focusY - lastFocusY
            clampPan()
            invalidate()
        }
        lastFocusX = focusX
        lastFocusY = focusY
    }

    private fun zoomAround(target: Float, focusX: Float, focusY: Float) {
        val previous = zoom
        zoom = target.coerceIn(1f, MAX_ZOOM)
        val ratio = zoom / previous
        panX = (panX - focusX + width / 2f) * ratio + focusX - width / 2f
        panY = (panY - focusY + height / 2f) * ratio + focusY - height / 2f
        clampPan()
        invalidate()
    }

    private fun fitScale(page: PageSpec): Float =
        min((width - pageMargin * 2) / page.displayWidth, (height - pageMargin * 2) / page.displayHeight)

    // Clamp as soon as pan or zoom changes, not only when a frame is drawn, so
    // gestures never build on an out-of-range pan between frames.
    private fun clampPan() {
        val page = spec ?: return
        val scale = fitScale(page) * zoom
        if (scale <= 0f) return
        val pageWidth = page.displayWidth * scale
        val pageHeight = page.displayHeight * scale
        panX = panX.coerceIn(-maxPan(pageWidth, width), maxPan(pageWidth, width))
        panY = panY.coerceIn(-maxPan(pageHeight, height), maxPan(pageHeight, height))
    }

    private fun animateZoom(target: Float, focusX: Float, focusY: Float) {
        cancelZoomAnimation()
        zoomAnimator = ValueAnimator.ofFloat(zoom, target).apply {
            duration = ZOOM_ANIMATION_MS
            interpolator = DecelerateInterpolator()
            addUpdateListener { zoomAround(it.animatedValue as Float, focusX, focusY) }
            start()
        }
    }

    private fun cancelZoomAnimation() {
        zoomAnimator?.cancel()
        zoomAnimator = null
    }

    override fun performClick(): Boolean { super.performClick(); return true }

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

    private fun maxPan(pageSize: Float, viewSize: Int): Float = ((pageSize - viewSize) / 2f + pageMargin).coerceAtLeast(0f)

    private companion object {
        const val NO_POINTER = -1
        const val DEFAULT_WIDTH = 2.2f
        const val TOUCH_PRESSURE = 0.65f
        const val MAX_ZOOM = 5f
        const val DOUBLE_TAP_ZOOM = 2.5f
        const val FIT_ZOOM_TOLERANCE = 1.01f
        const val ZOOM_ANIMATION_MS = 220L
        const val PAGE_MARGIN_DP = 12f
        const val SWIPE_DISTANCE_DP = 64f
        const val SWIPE_VELOCITY_DP = 600f
        const val SWIPE_DIRECTION_RATIO = 1.5f
    }
}
