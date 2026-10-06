package ch.lkmc.asterinked.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BlendMode
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.RenderNode
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.animation.DecelerateInterpolator
import ch.lkmc.asterinked.R
import ch.lkmc.asterinked.document.PageSpec
import ch.lkmc.asterinked.ink.InkEraser
import ch.lkmc.asterinked.ink.InkGeometry
import ch.lkmc.asterinked.ink.InkGeometryCache
import ch.lkmc.asterinked.ink.InkKind
import ch.lkmc.asterinked.ink.InkPoint
import ch.lkmc.asterinked.ink.InkSegment
import ch.lkmc.asterinked.ink.InkStroke
import ch.lkmc.asterinked.ink.InkStrokeBuilder
import java.util.Collections
import java.util.IdentityHashMap
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.min

internal enum class InputMode { PEN, TOUCH }

/** What a writing gesture does. The stylus eraser end and side button always erase. */
internal enum class InkTool { PEN, ERASER }

internal class InkPageView(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
    // Multiplying with the page keeps text under a highlight dark, like the export.
    private val highlighter = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        blendMode = BlendMode.MULTIPLY
    }
    private val bitmapPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val pageShadow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        val density = resources.displayMetrics.density
        setShadowLayer(SHADOW_RADIUS_DP * density, 0f, SHADOW_OFFSET_DP * density, SHADOW_COLOR)
    }
    private val pageRect = RectF()
    private var preview: Bitmap? = null
    private var spec: PageSpec? = null
    private var strokes = emptyList<InkStroke>()
    private val geometryCache = InkGeometryCache()
    private var geometry = emptyList<List<InkSegment>>()
    // Highlights are few and multiplied with the page, so they are drawn directly,
    // outside the cached pen layer; their paths are cached per stroke instance.
    private var highlightPaths = IdentityHashMap<InkStroke, Path>()
    private var points = mutableListOf<InkPoint>()
    private var liveStroke: InkStrokeBuilder? = null
    // Committed ink is recorded once per edit (page units) and replayed under the
    // current zoom, so frames drawn while writing no longer re-issue every segment.
    // The outer node applies the view transform and is kept as a GPU layer, so
    // unchanged ink is not re-rasterized for each pen sample either.
    private val inkNode = RenderNode("committedInk").apply { setClipToBounds(false) }
    private val inkLayer = RenderNode("committedInkLayer").apply { setUseCompositingLayer(true, null) }
    private val recordedLayerRect = RectF()
    private var inkNodeStale = true
    private val eraser = InkEraser()
    private val erasing: MutableSet<InkStroke> = Collections.newSetFromMap(IdentityHashMap())
    private val eraserRing = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = Color.argb(160, 60, 70, 80) }
    private var eraserAt: InkPoint? = null
    // A pen hovers before it touches: the cursor shows where it would land.
    private val hoverRing = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; alpha = HOVER_CURSOR_ALPHA }
    private val hoverDot = Paint(Paint.ANTI_ALIAS_FLAG).apply { alpha = HOVER_CURSOR_ALPHA }
    private var hoverX = 0f
    private var hoverY = 0f
    private var hoverVisible = false
    private var hoverEraserEnd = false
    private val hoverDirty = Rect()
    private var activeErasing = false
    private var activePointer = NO_POINTER
    private var activeColor = Color.BLACK
    private var activeWidth = DEFAULT_WIDTH
    private var activeKind = InkKind.PEN
    private var pageKey: String? = null
    private var documentKey: String? = null
    private var zoom = 1f
    private var panX = 0f
    private var panY = 0f
    private var alignTopPending = false
    private var lastFocusX = 0f
    private var lastFocusY = 0f
    private var gestureScaled = false
    private var penGesture = false
    private var zoomAnimator: ValueAnimator? = null
    private var inputMode = InputMode.PEN
    private var inkColor = Color.rgb(25, 38, 46)
    private var inkWidth = DEFAULT_WIDTH
    private var inkKind = InkKind.PEN
    private var onStroke: (InkStroke) -> Unit = {}
    private val density = resources.displayMetrics.density
    private val pageMargin = PAGE_MARGIN_DP * density
    private val swipeDistance = SWIPE_DISTANCE_DP * density
    private val swipeVelocity = SWIPE_VELOCITY_DP * density

    /** Called with +1 or -1 when a finger swipes the page at fit zoom in pen mode. */
    var onTurnPage: (Int) -> Unit = {}

    /** Receives the strokes one erase gesture removed, once the gesture ends. */
    var onErase: (Collection<InkStroke>) -> Unit = {}

    var tool = InkTool.PEN
        set(value) {
            if (field == value) return
            cancelStroke()
            field = value
        }

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
        setBackgroundColor(context.getColor(R.color.canvas))
        contentDescription = "PDF page. Write with a pen. Pinch to zoom; drag with a finger to pan."
        isFocusable = true
    }

    fun configure(mode: InputMode, color: Int, width: Float, kind: InkKind = InkKind.PEN, onStroke: (InkStroke) -> Unit) {
        cancelStroke()
        inputMode = mode
        inkColor = color
        inkWidth = width
        inkKind = kind
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
        if (nextStrokes !== strokes) {
            strokes = nextStrokes
            geometry = geometryCache.update(strokes)
            highlightPaths = IdentityHashMap<InkStroke, Path>().also { next ->
                for (stroke in strokes) if (stroke.kind == InkKind.HIGHLIGHTER) next[stroke] = highlightPaths[stroke] ?: highlightPath(stroke)
            }
            inkNodeStale = true
        }
        invalidate()
    }

    fun resetZoom() {
        animateZoom(1f, width / 2f, height / 2f)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val page = spec ?: return
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
        // A page still rendering is drawn blank so its ink and the pen work at once.
        canvas.drawRect(pageRect, pageShadow)
        preview?.let { canvas.drawBitmap(it, null, pageRect, bitmapPaint) }
        val cached = canvas.isHardwareAccelerated
        if (cached) {
            // Multiply needs the page underneath, which the transparent pen layer
            // lacks, so highlights are drawn directly and sit under all pen ink.
            canvas.save()
            canvas.clipRect(pageRect)
            canvas.translate(pageRect.left, pageRect.top)
            canvas.scale(scale, scale)
            drawHighlights(canvas)
            canvas.restore()
            drawCommittedInk(canvas, page, scale)
        }
        canvas.save()
        canvas.clipRect(pageRect)
        canvas.translate(pageRect.left, pageRect.top)
        canvas.scale(scale, scale)
        if (!cached) {
            drawHighlights(canvas)
            drawStrokes(canvas, InkKind.PEN)
        }
        liveStroke?.takeIf { activeKind == InkKind.PEN }?.let { live ->
            drawInk(canvas, activeColor, live.settled)
            drawInk(canvas, activeColor, live.tail)
        }
        eraserAt?.let {
            eraserRing.strokeWidth = resources.displayMetrics.density / scale
            canvas.drawCircle(it.x, it.y, eraserRadius(), eraserRing)
        }
        hoverAt?.let {
            val eraseMode = hoverEraseMode()
            hoverRing.strokeWidth = density / scale
            hoverRing.color = if (eraseMode) eraserRing.color else inkColor
            // setColor carries no alpha; the cursor stays a ghost in ink mode too.
            hoverRing.alpha = HOVER_CURSOR_ALPHA
            hoverDot.color = hoverRing.color
            // setColor carries no alpha; the dot stays a ghost too.
            hoverDot.alpha = HOVER_CURSOR_ALPHA
            val radius = if (eraseMode) eraserRadius() else maxOf(inkWidth / 2f, hoverMinRadius())
            canvas.drawCircle(it.x, it.y, radius, hoverRing)
            canvas.drawCircle(it.x, it.y, density / scale, hoverDot)
        }
        canvas.restore()
    }

    /** The page point under a hovering stylus; nothing while writing or off-page. */
    val hoverAt: InkPoint?
        get() {
            val page = spec ?: return null
            if (!hoverVisible || !isEnabled || activePointer != NO_POINTER || !pageRect.contains(hoverX, hoverY)) return null
            return InkPoint((hoverX - pageRect.left) / pageRect.width() * page.displayWidth,
                (hoverY - pageRect.top) / pageRect.height() * page.displayHeight, 0f)
        }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        inkNode.discardDisplayList()
        inkLayer.discardDisplayList()
        inkNodeStale = true
    }

    private fun drawCommittedInk(canvas: Canvas, page: PageSpec, scale: Float) {
        val rerecordInk = inkNodeStale || !inkNode.hasDisplayList()
        if (rerecordInk) {
            inkNode.setPosition(0, 0, page.displayWidth.toInt() + 1, page.displayHeight.toInt() + 1)
            val recording = inkNode.beginRecording()
            try { drawStrokes(recording, InkKind.PEN) } finally { inkNode.endRecording() }
            inkNodeStale = false
        }
        // Re-record the layer whenever its content or transform changes; otherwise
        // HWUI reuses the rasterized layer as-is.
        if (rerecordInk || recordedLayerRect != pageRect || !inkLayer.hasDisplayList() ||
            inkLayer.width != width || inkLayer.height != height) {
            inkLayer.setPosition(0, 0, width, height)
            val recording = inkLayer.beginRecording()
            try {
                recording.clipRect(pageRect)
                recording.translate(pageRect.left, pageRect.top)
                recording.scale(scale, scale)
                recording.drawRenderNode(inkNode)
            } finally {
                inkLayer.endRecording()
            }
            recordedLayerRect.set(pageRect)
        }
        canvas.drawRenderNode(inkLayer)
    }

    // Committed highlights, then the one being drawn: all of them sit under pen ink.
    private fun drawHighlights(canvas: Canvas) {
        drawStrokes(canvas, InkKind.HIGHLIGHTER)
        if (liveStroke == null || activeKind != InkKind.HIGHLIGHTER) return

        drawHighlight(canvas, activeColor, activeWidth, highlightPath(InkStroke(points, activeColor, activeWidth, activeKind)))
    }

    // Strokes under the eraser disappear at once and return if the gesture is cancelled.
    // Callers draw highlights before pen ink, as the export does.
    private fun drawStrokes(canvas: Canvas, kind: InkKind) {
        for (index in strokes.indices) {
            val stroke = strokes[index]
            if (stroke in erasing || stroke.kind != kind) continue
            when (stroke.kind) {
                InkKind.PEN -> drawInk(canvas, stroke.color, geometry[index])
                InkKind.HIGHLIGHTER -> drawHighlight(canvas, stroke.color, stroke.width, highlightPaths.getValue(stroke))
            }
        }
    }

    // Only pens hover; fingers and mice pass through to the default handling.
    override fun onHoverEvent(event: MotionEvent): Boolean {
        val tool = event.getToolType(0)
        if (tool != MotionEvent.TOOL_TYPE_STYLUS && tool != MotionEvent.TOOL_TYPE_ERASER) return super.onHoverEvent(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_HOVER_ENTER, MotionEvent.ACTION_HOVER_MOVE -> {
                if (hoverVisible) invalidateHover()
                hoverX = event.x
                hoverY = event.y
                hoverVisible = true
                // A flipped pen previews the eraser even while PEN is selected.
                hoverEraserEnd = tool == MotionEvent.TOOL_TYPE_ERASER
                invalidateHover()
            }
            MotionEvent.ACTION_HOVER_EXIT -> {
                invalidateHover()
                hoverVisible = false
            }
        }
        return true
    }

    private fun hoverEraseMode() = tool == InkTool.ERASER || hoverEraserEnd

    // Hover events arrive at stylus rates; the dirty rect scopes the redraw
    // where the framework honors it (HW views may repaint more than asked).
    private fun invalidateHover() {
        val page = spec ?: return
        val px = pageRect.width() / page.displayWidth
        val reach = (if (hoverEraseMode()) eraserRadius() else maxOf(inkWidth / 2f, hoverMinRadius())) * px + density + 1
        // Floor/ceil, not truncation: a sliver under a pixel still leaves a trail.
        hoverDirty.set(floor(hoverX - reach).toInt(), floor(hoverY - reach).toInt(),
            ceil(hoverX + reach).toInt(), ceil(hoverY + reach).toInt())
        invalidate(hoverDirty)
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
        if (action == MotionEvent.ACTION_DOWN) penGesture = false

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
        if (stylus || eraserEnd) penGesture = true
        if ((action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_POINTER_DOWN) &&
            (stylus || eraserEnd || (inputMode == InputMode.TOUCH && event.pointerCount == 1))) {
            if (pageRect.contains(event.getX(index), event.getY(index))) {
                activePointer = event.getPointerId(index)
                activeColor = inkColor
                activeWidth = inkWidth
                activeKind = inkKind
                activeErasing = eraserEnd || tool == InkTool.ERASER || (stylus && event.buttonState and STYLUS_BUTTONS != 0)
                if (stylus || eraserEnd) requestUnbufferedDispatch(event)
                track(event, index)
                parent?.requestDisallowInterceptTouchEvent(true)
                return true
            }
        }
        if (stylus || eraserEnd) return true
        // A palm that touched while the pen was down stays inert until every pointer
        // lifts, so it cannot pan, zoom or turn the page on its way off the screen.
        if (penGesture) return true

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
            // The cached ink must be re-recorded without the newly hidden strokes.
            if (erasing.addAll(eraser.hits(candidates, eraserAt, point, eraserRadius()))) inkNodeStale = true
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

    // The cursor never shrinks under a readable ring, however fine the pen is.
    private fun hoverMinRadius(): Float {
        val page = spec ?: return 0f
        return HOVER_MIN_RADIUS_DP * density * page.displayWidth / pageRect.width()
    }

    private fun addSamples(event: MotionEvent, index: Int) {
        val page = spec ?: return
        fun add(x: Float, y: Float, pressure: Float) {
            val point = InkPoint((x - pageRect.left) / pageRect.width() * page.displayWidth,
                (y - pageRect.top) / pageRect.height() * page.displayHeight,
                if (event.getToolType(index) == MotionEvent.TOOL_TYPE_STYLUS) pressure else TOUCH_PRESSURE)
            if (point.x.isFinite() && point.y.isFinite()) {
                points.add(point)
                (liveStroke ?: InkStrokeBuilder(activeWidth).also { liveStroke = it }).add(point)
            }
        }
        // Historical samples retain curves during fast writing and batched input.
        for (history in 0 until event.historySize) {
            add(event.getHistoricalX(index, history), event.getHistoricalY(index, history), event.getHistoricalPressure(index, history))
        }
        add(event.getX(index), event.getY(index), event.getPressure(index))
        invalidate()
    }

    private fun finishStroke() {
        val stroke = InkStroke(points.toList(), activeColor, activeWidth, activeKind)
        // The live builder already smoothed these exact samples with this width:
        // activeWidth is fixed when a stroke starts, and configure() cancels any
        // live stroke before the pen settings change.
        liveStroke?.let { geometryCache.seed(stroke, it.segments()) }
        cancelStroke()
        onStroke(stroke)
    }

    private fun cancelStroke() {
        points = mutableListOf()
        liveStroke = null
        // Strokes hidden by a cancelled erase must be drawn again.
        if (erasing.isNotEmpty()) inkNodeStale = true
        erasing.clear()
        eraser.reset()
        eraserAt = null
        activeErasing = false
        activePointer = NO_POINTER
        parent?.requestDisallowInterceptTouchEvent(false)
        invalidate()
    }

    private fun highlightPath(stroke: InkStroke): Path = Path().apply {
        val line = InkGeometry.centerline(stroke)
        if (line.isEmpty()) return@apply
        moveTo(line.first().x, line.first().y)
        // A zero-length segment with round caps keeps a tap visible.
        lineTo(line.first().x, line.first().y)
        for (point in line.drop(1)) lineTo(point.x, point.y)
    }

    private fun drawHighlight(canvas: Canvas, color: Int, width: Float, path: Path) {
        highlighter.color = color
        highlighter.strokeWidth = width
        canvas.drawPath(path, highlighter)
    }

    private fun drawInk(canvas: Canvas, color: Int, segments: List<InkSegment>) {
        paint.color = color
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
        const val ERASER_RADIUS_DP = 10f
        const val HOVER_MIN_RADIUS_DP = 4f
        const val HOVER_CURSOR_ALPHA = 160
        // Most pens report the side button as primary; older S Pens report secondary.
        const val STYLUS_BUTTONS = MotionEvent.BUTTON_STYLUS_PRIMARY or MotionEvent.BUTTON_SECONDARY
        const val SHADOW_RADIUS_DP = 6f
        const val SHADOW_OFFSET_DP = 1.5f
        val SHADOW_COLOR = Color.argb(56, 30, 40, 60)
    }
}
