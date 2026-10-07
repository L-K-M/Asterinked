package ch.lkmc.asterinked.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BlendMode
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.RectF
import android.graphics.RenderNode
import android.os.Handler
import android.os.Looper
import android.view.GestureDetector
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.ViewConfiguration
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
import kotlin.math.hypot
import kotlin.math.min

internal enum class InputMode { PEN, TOUCH }

/** What a writing gesture does. The stylus eraser end and side button always erase. */
internal enum class InkTool { PEN, ERASER }

internal enum class WritingState { IDLE, ACTIVE }

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
    private var pageScale = 0f
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
    private val eraser = InkEraser(geometryCache::cachedSegments)
    private val erasing: MutableSet<InkStroke> = Collections.newSetFromMap(IdentityHashMap())
    private val eraserRing = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = Color.argb(160, 60, 70, 80) }
    private var eraserAt: InkPoint? = null
    // Where a hovering stylus would land, in page units; null while it is away or down.
    private var hoverAt: InkPoint? = null
    private var hoverErases = false
    private val hoverRing = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val hoverHalo = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = HOVER_HALO_COLOR }
    private val hoverMarker = Paint(Paint.ANTI_ALIAS_FLAG).apply { blendMode = BlendMode.MULTIPLY }
    private var activeErasing = false
    // Hold to straighten: a pen that rests still for HOLD_MS after drawing at
    // least MIN_LINE_DP turns its stroke into a straight line from where it
    // started, with a tick; until it lifts, moving drags the line's end.
    //
    //     ~~~~~~~~~~~~~~~~   (rest)   ----------------   (drag)   -----------\
    //
    // A loop that ends near its start stays as drawn.
    private val hold = Handler(Looper.getMainLooper())
    private val straighten = Runnable { straightenLine() }
    private var holdAnchor: InkPoint? = null
    private var linePressure: Float? = null
    private var lineEnd: InkPoint? = null
    private var activePointer = NO_POINTER
    private var activeColor = Color.BLACK
    private var activeWidth = DEFAULT_WIDTH
    private var activeKind = InkKind.PEN
    private var activeOnStroke: (InkStroke) -> Unit = {}
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
    private var fingerGesture = FingerGesture.TAP
    private var fingerDownX = 0f
    private var fingerDownY = 0f
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
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop

    /** Reports accepted writing/erasing gestures, including cancellation. */
    var onWritingChanged: (WritingState) -> Unit = {}

    /** Called with +1 or -1 when a finger swipes the page at fit zoom in pen mode. */
    var onTurnPage: (Int) -> Unit = {}

    /** Receives the strokes one erase gesture removed, once the gesture ends. */
    var onErase: (Collection<InkStroke>) -> Unit = {}

    /** Called when two fingers tap the page. */
    var onUndo: () -> Unit = {}

    /** Called when three fingers tap the page. */
    var onRedo: () -> Unit = {}

    private val fingerTaps = FingerTaps(ViewConfiguration.get(context).scaledTouchSlop.toFloat())


    /** Called whenever a stylus or its eraser end touches or hovers over the page. */
    var onStylusSeen: () -> Unit = {}

    /**
     * Called when one finger (or a mouse) drags in pen mode without zooming or
     * turning the page. Without a stylus that only nudges the page, so the
     * screen seems to ignore the user.
     */
    var onFingerDragInPenMode: () -> Unit = {}

    // Like configure(), a new tool applies from the next stroke.
    var tool = InkTool.PEN

    private val scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
            gestureScaled = true
            fingerGesture = FingerGesture.OTHER
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
            fingerGesture = FingerGesture.OTHER
            animateZoom(if (zoom > FIT_ZOOM_TOLERANCE) 1f else DOUBLE_TAP_ZOOM, e.x, e.y)
            return true
        }

        override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
            val start = e1 ?: return false
            if (inputMode != InputMode.PEN || gestureScaled || zoom > FIT_ZOOM_TOLERANCE) return false
            val distance = e2.x - start.x
            val horizontal = abs(velocityX) >= abs(velocityY) * SWIPE_DIRECTION_RATIO
            if (!horizontal || abs(velocityX) < swipeVelocity || abs(distance) < swipeDistance) return false
            fingerGesture = FingerGesture.OTHER
            onTurnPage(if (distance < 0) 1 else -1)
            return true
        }
    })

    init {
        setBackgroundColor(context.getColor(R.color.canvas))
        contentDescription = "PDF page. Write with a pen. Pinch to zoom; drag with a finger to pan."
        isFocusable = true
    }

    // New settings apply from the next stroke. The other hand may tap a
    // colour, width or tool while the pen is down; the stroke in progress
    // finishes with the colour, width, kind and eraser state it started with.
    fun configure(mode: InputMode, color: Int, width: Float, kind: InkKind = InkKind.PEN, onStroke: (InkStroke) -> Unit) {
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
            // A pen held still sends no hover; its old spot means nothing here.
            hoverAt = null
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
        updatePageTransform()
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

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        updatePageTransform()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val page = spec ?: return
        val scale = pageScale
        if (scale <= 0f) return

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
        eraserAt?.let { drawEraserRing(canvas, it, scale) }
        hoverAt?.takeIf { isEnabled && liveStroke == null && eraserAt == null }?.let { drawHover(canvas, it, scale) }
        canvas.restore()
    }

    private fun drawEraserRing(canvas: Canvas, at: InkPoint, scale: Float) {
        eraserRing.strokeWidth = density / scale
        canvas.drawCircle(at.x, at.y, eraserRadius(), eraserRing)
    }

    // The ring is as wide as the line at full pressure, but never smaller than a
    // few dp on screen; a pale halo keeps it visible over ink of its own colour.
    // A light highlighter tint would vanish as a ring, so it previews the mark.
    private fun drawHover(canvas: Canvas, at: InkPoint, scale: Float) {
        if (hoverErases) {
            drawEraserRing(canvas, at, scale)
            return
        }
        val radius = maxOf(inkWidth / 2f, HOVER_MIN_RADIUS_DP * density / scale)
        if (inkKind == InkKind.HIGHLIGHTER) {
            hoverMarker.color = inkColor
            canvas.drawCircle(at.x, at.y, radius, hoverMarker)
            return
        }
        val line = HOVER_LINE_DP * density / scale
        hoverHalo.strokeWidth = line * 3f
        canvas.drawCircle(at.x, at.y, radius, hoverHalo)
        hoverRing.color = inkColor
        hoverRing.strokeWidth = line
        canvas.drawCircle(at.x, at.y, radius, hoverRing)
    }

    // Styluses that report hover show where the nib will land, since the glass
    // between nib and pixels makes that spot hard to judge. Finger hover is
    // TalkBack's explore-by-touch and is left to the framework.
    override fun onHoverEvent(event: MotionEvent): Boolean {
        val tool = event.getToolType(0)
        if (tool != MotionEvent.TOOL_TYPE_STYLUS && tool != MotionEvent.TOOL_TYPE_ERASER) return super.onHoverEvent(event)

        // Hover reaches the view before contact, so a pen is noticed even
        // before it writes.
        onStylusSeen()
        val shown = hoverAt
        val hovering = event.actionMasked == MotionEvent.ACTION_HOVER_ENTER || event.actionMasked == MotionEvent.ACTION_HOVER_MOVE
        hoverAt = if (hovering && isEnabled && pageRect.contains(event.x, event.y)) pagePoint(event.x, event.y, 1f) else null
        hoverErases = erasesWhenHovering(event)
        if (shown != null || hoverAt != null) invalidate()
        return true
    }

    // A side-button press or release while the pen hovers arrives here, not as
    // a hover event, so the ring would keep the old tool until the pen moved.
    override fun onGenericMotionEvent(event: MotionEvent): Boolean {
        val button = event.actionMasked == MotionEvent.ACTION_BUTTON_PRESS || event.actionMasked == MotionEvent.ACTION_BUTTON_RELEASE
        if (!button || hoverAt == null) return super.onGenericMotionEvent(event)

        hoverErases = erasesWhenHovering(event)
        invalidate()
        return true
    }

    private fun erasesWhenHovering(event: MotionEvent): Boolean =
        event.getToolType(0) == MotionEvent.TOOL_TYPE_ERASER || tool == InkTool.ERASER || event.buttonState and STYLUS_BUTTONS != 0

    override fun onDetachedFromWindow() {
        cancelZoomAnimation()
        hold.removeCallbacks(straighten)
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

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val action = event.actionMasked
        // Seen before anything else consumes the event: in touch-ink mode the
        // first finger starts a stroke, which the second finger cancels.
        when (fingerTaps.track(event)) {
            UNDO_FINGERS -> onUndo()
            REDO_FINGERS -> onRedo()
        }
        // Cancellation releases ownership even when layout cannot accept input.
        if (action == MotionEvent.ACTION_CANCEL) {
            cancelStroke()
            scaleDetector.onTouchEvent(event)
            gestureDetector.onTouchEvent(event)
            return true
        }
        if (!isEnabled || spec == null || pageRect.isEmpty) return false
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
        if (stylus || eraserEnd) {
            penGesture = true
            onStylusSeen()
        }
        if ((action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_POINTER_DOWN) &&
            (stylus || eraserEnd || (inputMode == InputMode.TOUCH && event.pointerCount == 1))) {
            if (pageRect.contains(event.getX(index), event.getY(index))) {
                hoverAt = null
                activePointer = event.getPointerId(index)
                activeColor = inkColor
                activeWidth = inkWidth
                activeKind = inkKind
                activeOnStroke = onStroke
                activeErasing = eraserEnd || tool == InkTool.ERASER || (stylus && event.buttonState and STYLUS_BUTTONS != 0)
                if (stylus || eraserEnd) requestUnbufferedDispatch(event)
                onWritingChanged(WritingState.ACTIVE)
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
        trackFingerGesture(event)
        scaleDetector.onTouchEvent(event)
        gestureDetector.onTouchEvent(event)
        // After the detectors, which mark a zoom or page turn on this same UP.
        if (action == MotionEvent.ACTION_UP && fingerGesture == FingerGesture.DRAG && inputMode == InputMode.PEN) {
            onFingerDragInPenMode()
        }
        return true
    }

    private fun trackFingerGesture(event: MotionEvent) {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                // Only a finger or a mouse is someone trying to write without a pen.
                val tool = event.getToolType(event.actionIndex)
                val writer = tool == MotionEvent.TOOL_TYPE_FINGER || tool == MotionEvent.TOOL_TYPE_MOUSE
                fingerGesture = if (writer) FingerGesture.TAP else FingerGesture.OTHER
                fingerDownX = event.x
                fingerDownY = event.y
            }
            MotionEvent.ACTION_POINTER_DOWN -> fingerGesture = FingerGesture.OTHER
            MotionEvent.ACTION_MOVE -> {
                val moved = hypot(event.x - fingerDownX, event.y - fingerDownY) > touchSlop
                if (fingerGesture == FingerGesture.TAP && moved) fingerGesture = FingerGesture.DRAG
            }
        }
    }

    // The page follows the centroid of the fingers, so a pinch also pans and
    // lifting one finger never makes the page jump to where the remaining
    // finger is. In touch-ink mode a second finger cancels the stroke the
    // first one started (see onTouchEvent), then both fingers pan.
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
            updatePageTransform()
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
        updatePageTransform()
        invalidate()
    }

    private fun fitScale(page: PageSpec): Float =
        min((width - pageMargin * 2) / page.displayWidth, (height - pageMargin * 2) / page.displayHeight)

    // Publish one transform after every page, size, pan or zoom change, so input
    // before the next frame uses the same placement and scale as drawing.
    private fun updatePageTransform() {
        pageRect.setEmpty()
        pageScale = 0f
        val page = spec
        val fit = page?.let { fitScale(it) } ?: 0f
        if (page == null || fit <= 0f) {
            // A contact cannot survive losing its page coordinate space.
            cancelStroke()
            return
        }

        val scale = fit * zoom
        val pageWidth = page.displayWidth * scale
        val pageHeight = page.displayHeight * scale
        if (alignTopPending) {
            panY = maxPan(pageHeight, height)
            alignTopPending = false
        }
        panX = panX.coerceIn(-maxPan(pageWidth, width), maxPan(pageWidth, width))
        panY = panY.coerceIn(-maxPan(pageHeight, height), maxPan(pageHeight, height))

        pageScale = scale
        pageRect.set((width - pageWidth) / 2f + panX, (height - pageHeight) / 2f + panY, (width + pageWidth) / 2f + panX, (height + pageHeight) / 2f + panY)
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
        if (activeErasing) {
            eraseAlong(event, index)
            return
        }
        addSamples(event, index)
        followHold()
    }

    private fun followHold() {
        val last = points.lastOrNull() ?: return
        if (linePressure != null) {
            reshapeLine(last)
            return
        }
        // Moving past the slop restarts the wait; resting lets it run out.
        val anchor = holdAnchor
        if (anchor != null && distance(anchor, last) <= dpOnPage(HOLD_SLOP_DP)) return
        holdAnchor = last
        hold.removeCallbacks(straighten)
        hold.postDelayed(straighten, HOLD_MS)
    }

    private fun straightenLine() {
        val first = points.firstOrNull() ?: return
        if (liveStroke == null || activeErasing || distance(first, points.last()) < dpOnPage(MIN_LINE_DP)) return

        // One even pressure, so the line keeps the stroke's weight without a taper.
        linePressure = points.map { it.pressure }.average().toFloat()
        performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        reshapeLine(points.last())
    }

    private fun reshapeLine(end: InkPoint) {
        val pressure = linePressure ?: return
        // Dragged back onto its start, the line would shrink to a dot: keep the last one.
        val start = points.first()
        val target = if (distance(start, end) > dpOnPage(HOLD_SLOP_DP)) end else lineEnd ?: return
        lineEnd = target
        points = mutableListOf(start.copy(pressure = pressure), target.copy(pressure = pressure))
        liveStroke = InkStrokeBuilder(activeWidth).also { builder -> points.forEach(builder::add) }
        invalidate()
    }

    private fun dpOnPage(dp: Float): Float {
        if (pageScale <= 0f) return 0f
        return dp * density / pageScale
    }

    private fun distance(a: InkPoint, b: InkPoint): Float = hypot(a.x - b.x, a.y - b.y)

    // Screen pixels to page units; null before the page is laid out.
    private fun pagePoint(x: Float, y: Float, pressure: Float): InkPoint? {
        if (pageScale <= 0f) return null
        val point = InkPoint((x - pageRect.left) / pageScale, (y - pageRect.top) / pageScale, pressure)
        return point.takeIf { it.x.isFinite() && it.y.isFinite() }
    }

    private fun eraseAlong(event: MotionEvent, index: Int) {
        fun erase(x: Float, y: Float) {
            val point = pagePoint(x, y, 1f) ?: return
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
        if (pageScale <= 0f) return 0f
        return ERASER_RADIUS_DP * density / pageScale
    }

    private fun addSamples(event: MotionEvent, index: Int) {
        fun add(x: Float, y: Float, pressure: Float) {
            val point = pagePoint(x, y, if (event.getToolType(index) == MotionEvent.TOOL_TYPE_STYLUS) pressure else TOUCH_PRESSURE) ?: return
            points.add(point)
            (liveStroke ?: InkStrokeBuilder(activeWidth).also { liveStroke = it }).add(point)
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
        // activeWidth is fixed when a stroke starts and outlasts any settings
        // change during it.
        liveStroke?.let { geometryCache.seed(stroke, it.segments()) }
        cancelStroke()
        activeOnStroke(stroke)
    }

    private fun cancelStroke() {
        hold.removeCallbacks(straighten)
        holdAnchor = null
        linePressure = null
        lineEnd = null
        val wasWriting = activePointer != NO_POINTER
        points = mutableListOf()
        liveStroke = null
        // Strokes hidden by a cancelled erase must be drawn again.
        if (erasing.isNotEmpty()) inkNodeStale = true
        erasing.clear()
        eraser.reset()
        eraserAt = null
        activeErasing = false
        activePointer = NO_POINTER
        if (wasWriting) onWritingChanged(WritingState.IDLE)
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
        highlighter.strokeWidth = InkGeometry.strokeWidth(width)
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

    /**
     * Recognizes a quick tap with several fingers: every finger lands and lifts
     * within [MAX_TAP_MS] of the first touch without moving past the touch
     * slop. A stylus in the gesture, a drag or a pinch rules it out.
     */
    private class FingerTaps(private val slop: Float) {
        private val starts = HashMap<Int, PointF>()
        private var ruledOut = false

        /** Returns the number of fingers when [event] completes a tap, otherwise null. */
        fun track(event: MotionEvent): Int? {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    starts.clear()
                    ruledOut = false
                    land(event, 0)
                }
                MotionEvent.ACTION_POINTER_DOWN -> land(event, event.actionIndex)
                MotionEvent.ACTION_MOVE -> for (index in 0 until event.pointerCount) checkMoved(event, index)
                MotionEvent.ACTION_CANCEL -> ruledOut = true
                MotionEvent.ACTION_UP -> {
                    checkMoved(event, 0)
                    val quick = event.eventTime - event.downTime <= MAX_TAP_MS
                    if (!ruledOut && quick && starts.size >= UNDO_FINGERS) return starts.size
                }
            }
            return null
        }

        private fun land(event: MotionEvent, index: Int) {
            if (event.getToolType(index) != MotionEvent.TOOL_TYPE_FINGER || starts.size >= REDO_FINGERS) ruledOut = true
            starts[event.getPointerId(index)] = PointF(event.getX(index), event.getY(index))
        }

        private fun checkMoved(event: MotionEvent, index: Int) {
            val start = starts[event.getPointerId(index)] ?: return
            if (hypot(event.getX(index) - start.x, event.getY(index) - start.y) > slop) ruledOut = true
        }
    }

    /** What a gesture without the pen has done so far. */
    private enum class FingerGesture {
        /** One pointer, still within the touch slop. */
        TAP,
        /** One pointer that moved, and nothing else happened. */
        DRAG,
        /** A second finger, a zoom or a page turn. */
        OTHER,
    }

    private companion object {
        const val NO_POINTER = -1
        const val UNDO_FINGERS = 2
        const val REDO_FINGERS = 3
        // Longer than a one-finger tap: several fingers rarely land at once.
        const val MAX_TAP_MS = 300L
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
        // Longer than a pause between letters; short enough to feel deliberate.
        const val HOLD_MS = 600L
        const val HOLD_SLOP_DP = 3f
        const val MIN_LINE_DP = 24f
        const val HOVER_MIN_RADIUS_DP = 4f
        const val HOVER_LINE_DP = 1.5f
        val HOVER_HALO_COLOR = Color.argb(150, 255, 255, 255)
        // Most pens report the side button as primary; older S Pens report secondary.
        const val STYLUS_BUTTONS = MotionEvent.BUTTON_STYLUS_PRIMARY or MotionEvent.BUTTON_SECONDARY
        const val SHADOW_RADIUS_DP = 6f
        const val SHADOW_OFFSET_DP = 1.5f
        val SHADOW_COLOR = Color.argb(56, 30, 40, 60)
    }
}
