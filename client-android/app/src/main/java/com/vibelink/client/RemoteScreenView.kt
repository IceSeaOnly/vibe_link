package com.vibelink.client

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import kotlin.math.abs
import kotlin.math.hypot

class RemoteScreenView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {
    interface Listener {
        fun onTap(x: Int, y: Int, imageWidth: Int, imageHeight: Int)
        fun onDoubleTap(x: Int, y: Int, imageWidth: Int, imageHeight: Int)
        fun onRightClick(x: Int, y: Int, imageWidth: Int, imageHeight: Int)
        fun onMove(x: Int, y: Int, imageWidth: Int, imageHeight: Int)
        fun onDrag(fromX: Int, fromY: Int, toX: Int, toY: Int, imageWidth: Int, imageHeight: Int, durationMs: Long)
        fun onRelativeMove(deltaX: Int, deltaY: Int)
        fun onCurrentTap(x: Int, y: Int, imageWidth: Int, imageHeight: Int)
        fun onCurrentDoubleTap(x: Int, y: Int, imageWidth: Int, imageHeight: Int)
        fun onCurrentRightClick(x: Int, y: Int, imageWidth: Int, imageHeight: Int)
        fun onCurrentDragStart(x: Int, y: Int, imageWidth: Int, imageHeight: Int)
        fun onCurrentDragMove(deltaX: Int, deltaY: Int)
        fun onCurrentDragEnd(x: Int, y: Int, imageWidth: Int, imageHeight: Int)
        fun onScroll(deltaX: Int, deltaY: Int)
        fun onShortcutPointSelected(x: Int, y: Int, imageWidth: Int, imageHeight: Int)
        fun onViewportChanged(geometry: ImageGeometry)
    }

    private val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG)
    private val cursorPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var bitmap: Bitmap? = null
    private var downX = 0f
    private var downY = 0f
    private var downTime = 0L
    private var lastMoveX = 0f
    private var lastMoveY = 0f
    private var lastTapX = 0f
    private var lastTapY = 0f
    private var lastTapTime = 0L
    private var multiStartY = 0f
    private var multiStartX = 0f
    private var multiStartDistance = 0f
    private var multiStartTime = 0L
    private var multiStartZoom = 1f
    private var suppressNextUp = false
    private var longPressReady = false
    private var movedPointer = false
    private var isLongDragging = false
    private var isTrackpadDragging = false
    private var dragStartImage: Pair<Int, Int>? = null
    private var cursorImageX: Int? = null
    private var cursorImageY: Int? = null
    private var lastMoveSentAt = 0L
    private var lastTrackpadMoveEventTime = 0L
    private var zoomMultiplier = 1f
    private var panX = 0f
    private var panY = 0f
    private var shortcutCaptureMode = false
    private var trackpadCursorSynced = false
    var listener: Listener? = null
    var noFrameText: String = "No frame"
        set(value) {
            field = value
            invalidate()
        }
    var videoUnderlayMode: Boolean = false
        set(value) {
            field = value
            invalidate()
        }
    var interactionMode: InteractionMode = InteractionMode.SCREEN
        set(value) {
            field = value
            suppressNextUp = false
            longPressReady = false
            movedPointer = false
            isLongDragging = false
            isTrackpadDragging = false
            dragStartImage = null
            if (value == InteractionMode.SCREEN && !shortcutCaptureMode) {
                cursorImageX = null
                cursorImageY = null
            }
            if (value == InteractionMode.TRACKPAD) {
                bitmap?.let { ensureTrackpadCursor(it) }
                trackpadCursorSynced = false
            }
            notifyViewportChanged()
        }

    fun zoomIn() {
        zoomMultiplier = (zoomMultiplier * 1.25f).coerceAtMost(4f)
        invalidate()
    }

    fun zoomOut() {
        zoomMultiplier = (zoomMultiplier / 1.25f).coerceAtLeast(1f)
        invalidate()
    }

    fun resetZoom() {
        zoomMultiplier = 1f
        panX = 0f
        panY = 0f
        invalidate()
    }

    fun panBy(deltaX: Float, deltaY: Float) {
        panX += deltaX
        panY += deltaY
        invalidate()
    }

    fun setFrame(frame: Bitmap) {
        val old = bitmap
        bitmap = frame
        if (old != null && old != frame && !old.isRecycled) old.recycle()
        if (interactionMode == InteractionMode.TRACKPAD) {
            ensureTrackpadCursor(frame)
            trackpadCursorSynced = false
        }
        notifyViewportChanged(frame)
        invalidate()
    }

    fun startShortcutCapture(): Boolean {
        val frame = bitmap ?: return false
        shortcutCaptureMode = true
        updateCursor(frame.width / 2, frame.height / 2)
        return true
    }

    override fun onDraw(canvas: Canvas) {
        if (!videoUnderlayMode) {
            canvas.drawColor(Color.rgb(18, 24, 38))
        }
        val frame = bitmap
        if (frame == null) {
            if (videoUnderlayMode) return
            paint.color = Color.WHITE
            paint.textSize = 16f * resources.displayMetrics.scaledDensity
            canvas.drawText(noFrameText, 24f, height / 2f, paint)
            return
        }
        val geometry = currentGeometry(frame)
        val destinationLeft = geometry.offsetX
        val destinationTop = geometry.offsetY
        val destinationRight = geometry.offsetX + frame.width * geometry.scale
        val destinationBottom = geometry.offsetY + frame.height * geometry.scale
        if (!videoUnderlayMode) {
            canvas.drawBitmap(
                frame,
                null,
                RectF(destinationLeft, destinationTop, destinationRight, destinationBottom),
                paint
            )
        }
        drawCursor(canvas, geometry)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val frame = bitmap ?: return true
        if (shortcutCaptureMode) {
            return handleShortcutCapture(event, frame)
        }
        if (event.pointerCount >= 2) {
            if (interactionMode == InteractionMode.SCREEN) {
                handleScreenPinch(event)
            } else if (interactionMode == InteractionMode.TRACKPAD) {
                handleTrackpadTwoFinger(event)
            } else {
                handlePointerTwoFinger(event, frame)
            }
            return true
        }
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                downTime = event.eventTime
                lastMoveX = event.x
                lastMoveY = event.y
                lastTrackpadMoveEventTime = event.eventTime
                longPressReady = false
                movedPointer = false
                isLongDragging = false
                isTrackpadDragging = false
                dragStartImage = null
                suppressNextUp = false
                parent?.requestDisallowInterceptTouchEvent(true)
                if (interactionMode == InteractionMode.POINTER || interactionMode == InteractionMode.TRACKPAD) {
                    postDelayed({
                        if ((interactionMode == InteractionMode.POINTER || interactionMode == InteractionMode.TRACKPAD) &&
                            !movedPointer &&
                            !isLongDragging &&
                            !isTrackpadDragging &&
                            distanceFromDown(lastMoveX, lastMoveY) <= dragThresholdPx()
                        ) {
                            longPressReady = true
                        }
                    }, ViewConfiguration.getLongPressTimeout().toLong())
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (interactionMode == InteractionMode.SCREEN) {
                    panX += event.x - lastMoveX
                    panY += event.y - lastMoveY
                    lastMoveX = event.x
                    lastMoveY = event.y
                    notifyViewportChanged(frame)
                    invalidate()
                } else if (interactionMode == InteractionMode.TRACKPAD) {
                    handleTrackpadMove(event)
                } else {
                    val distance = distanceFromDown(event.x, event.y)
                    val geometry = currentGeometry(frame)
                    if (longPressReady && distance > dragThresholdPx()) {
                        if (!isLongDragging) {
                            dragStartImage = CoordinateMapper.mapViewToImage(downX, downY, geometry)
                            isLongDragging = true
                        }
                        CoordinateMapper.mapViewToImage(event.x, event.y, geometry)?.let {
                            updateCursor(it.first, it.second)
                        }
                    } else if (!longPressReady && distance > moveThresholdPx()) {
                        movedPointer = true
                        longPressReady = false
                        CoordinateMapper.mapViewToImage(event.x, event.y, geometry)?.let {
                            updateCursor(it.first, it.second)
                            maybeSendMove(it.first, it.second, frame)
                        }
                    }
                }
            }
            MotionEvent.ACTION_UP -> {
                parent?.requestDisallowInterceptTouchEvent(false)
                if (suppressNextUp) {
                    suppressNextUp = false
                    return true
                }
                val duration = event.eventTime - downTime
                val distance = distanceFromDown(event.x, event.y)
                val geometry = currentGeometry(frame)
                if (interactionMode == InteractionMode.SCREEN) {
                    if (isDoubleTap(event)) {
                        lastTapTime = 0L
                        toggleDoubleTapZoom(frame, event.x, event.y)
                    } else if (distance <= dragThresholdPx()) {
                        rememberTap(event)
                    }
                    return true
                }
                if (interactionMode == InteractionMode.TRACKPAD) {
                    handleTrackpadUp(event)
                    return true
                }
                if (isLongDragging && distance > dragThresholdPx()) {
                    val from = dragStartImage ?: CoordinateMapper.mapViewToImage(downX, downY, geometry)
                    val to = CoordinateMapper.mapViewToImage(event.x, event.y, geometry)
                    if (from != null && to != null) {
                        updateCursor(to.first, to.second)
                        listener?.onDrag(from.first, from.second, to.first, to.second, frame.width, frame.height, duration)
                    }
                    longPressReady = false
                    isLongDragging = false
                    dragStartImage = null
                    return true
                }
                if (movedPointer) {
                    CoordinateMapper.mapViewToImage(event.x, event.y, geometry)?.let {
                        updateCursor(it.first, it.second)
                        listener?.onMove(it.first, it.second, frame.width, frame.height)
                    }
                    movedPointer = false
                    longPressReady = false
                    return true
                }
                val point = CoordinateMapper.mapViewToImage(event.x, event.y, geometry) ?: return true
                updateCursor(point.first, point.second)
                if (isDoubleTap(event)) {
                    lastTapTime = 0L
                    listener?.onDoubleTap(point.first, point.second, frame.width, frame.height)
                } else {
                    rememberTap(event)
                    postDelayed({
                        if (lastTapTime == event.eventTime) {
                            listener?.onTap(point.first, point.second, frame.width, frame.height)
                        }
                    }, 330L)
                }
            }
        }
        return true
    }

    private fun handleShortcutCapture(event: MotionEvent, frame: Bitmap): Boolean {
        val geometry = currentGeometry(frame)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                CoordinateMapper.mapViewToImage(event.x, event.y, geometry)?.let {
                    updateCursor(it.first, it.second)
                }
            }
            MotionEvent.ACTION_UP -> {
                parent?.requestDisallowInterceptTouchEvent(false)
                shortcutCaptureMode = false
                CoordinateMapper.mapViewToImage(event.x, event.y, geometry)?.let {
                    updateCursor(it.first, it.second)
                    listener?.onShortcutPointSelected(it.first, it.second, frame.width, frame.height)
                }
            }
            MotionEvent.ACTION_CANCEL -> {
                shortcutCaptureMode = false
                parent?.requestDisallowInterceptTouchEvent(false)
            }
        }
        return true
    }

    private fun handleScreenPinch(event: MotionEvent) {
        val distance = pointerDistance(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_POINTER_DOWN, MotionEvent.ACTION_DOWN -> {
                multiStartDistance = distance
                multiStartZoom = zoomMultiplier
                parent?.requestDisallowInterceptTouchEvent(true)
            }
            MotionEvent.ACTION_MOVE -> {
                if (multiStartDistance > 0f) {
                    zoomMultiplier = (multiStartZoom * (distance / multiStartDistance)).coerceIn(1f, 5f)
                    notifyViewportChanged()
                    invalidate()
                }
            }
            MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                parent?.requestDisallowInterceptTouchEvent(false)
                suppressNextUp = true
            }
        }
    }

    private fun handlePointerTwoFinger(event: MotionEvent, frame: Bitmap) {
        val x = (event.getX(0) + event.getX(1)) / 2f
        val y = (event.getY(0) + event.getY(1)) / 2f
        when (event.actionMasked) {
            MotionEvent.ACTION_POINTER_DOWN, MotionEvent.ACTION_DOWN -> {
                multiStartX = x
                multiStartY = y
                multiStartTime = event.eventTime
                parent?.requestDisallowInterceptTouchEvent(true)
            }
            MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_UP -> {
                val deltaX = (multiStartX - x).toInt()
                val deltaY = (multiStartY - y).toInt()
                val duration = event.eventTime - multiStartTime
                if (abs(deltaX) > scrollThresholdPx() || abs(deltaY) > scrollThresholdPx()) {
                    listener?.onScroll(deltaX, deltaY)
                } else if (duration < 420L) {
                    val point = CoordinateMapper.mapViewToImage(x, y, currentGeometry(frame))
                    if (point != null) listener?.onRightClick(point.first, point.second, frame.width, frame.height)
                }
                suppressNextUp = true
                parent?.requestDisallowInterceptTouchEvent(false)
            }
        }
    }

    private fun handleTrackpadMove(event: MotionEvent) {
        val distance = distanceFromDown(event.x, event.y)
        if (longPressReady && distance > dragThresholdPx()) {
            if (!isTrackpadDragging) {
                isTrackpadDragging = true
                trackpadTarget()?.let {
                    trackpadCursorSynced = true
                    listener?.onCurrentDragStart(it.x, it.y, it.imageWidth, it.imageHeight)
                }
            }
            sendTrackpadDelta(event.x, event.y, event.eventTime, dragged = true)
            return
        }

        if (!longPressReady && (distance > moveThresholdPx() || movedPointer)) {
            movedPointer = true
            longPressReady = false
            sendTrackpadDelta(event.x, event.y, event.eventTime, dragged = false)
        }
    }

    private fun handleTrackpadUp(event: MotionEvent) {
        if (isTrackpadDragging) {
            trackpadTarget()?.let {
                listener?.onCurrentDragEnd(it.x, it.y, it.imageWidth, it.imageHeight)
            }
            isTrackpadDragging = false
            longPressReady = false
            movedPointer = false
            return
        }

        if (movedPointer) {
            movedPointer = false
            longPressReady = false
            return
        }

        if (isDoubleTap(event)) {
            lastTapTime = 0L
            trackpadTarget()?.let {
                trackpadCursorSynced = true
                listener?.onCurrentDoubleTap(it.x, it.y, it.imageWidth, it.imageHeight)
            }
        } else {
            rememberTap(event)
            postDelayed({
                if (lastTapTime == event.eventTime) {
                    trackpadTarget()?.let {
                        trackpadCursorSynced = true
                        listener?.onCurrentTap(it.x, it.y, it.imageWidth, it.imageHeight)
                    }
                }
            }, 330L)
        }
    }

    private fun sendTrackpadDelta(x: Float, y: Float, eventTime: Long, dragged: Boolean) {
        val frame = bitmap
        val elapsedMs = (eventTime - lastTrackpadMoveEventTime).coerceAtLeast(1L)
        val delta = TrackpadMotionController.relativeDelta(x - lastMoveX, y - lastMoveY, trackpadSensitivity(), elapsedMs)
        lastMoveX = x
        lastMoveY = y
        lastTrackpadMoveEventTime = eventTime
        if (delta.isEmpty) return
        val now = android.os.SystemClock.uptimeMillis()
        if (now - lastMoveSentAt < 16L) return
        lastMoveSentAt = now
        if (frame != null) {
            moveTrackpadCursor(delta.dx, delta.dy, frame)
            if (!trackpadCursorSynced) {
                trackpadTarget(frame)?.let {
                    listener?.onMove(it.x, it.y, it.imageWidth, it.imageHeight)
                    trackpadCursorSynced = true
                }
            }
        }
        if (dragged) {
            listener?.onCurrentDragMove(delta.dx, delta.dy)
        } else {
            listener?.onRelativeMove(delta.dx, delta.dy)
        }
    }

    private fun handleTrackpadTwoFinger(event: MotionEvent) {
        val x = (event.getX(0) + event.getX(1)) / 2f
        val y = (event.getY(0) + event.getY(1)) / 2f
        when (event.actionMasked) {
            MotionEvent.ACTION_POINTER_DOWN, MotionEvent.ACTION_DOWN -> {
                multiStartX = x
                multiStartY = y
                multiStartTime = event.eventTime
                parent?.requestDisallowInterceptTouchEvent(true)
            }
            MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_UP -> {
                val deltaX = (multiStartX - x).toInt()
                val deltaY = (multiStartY - y).toInt()
                val duration = event.eventTime - multiStartTime
                if (abs(deltaX) > scrollThresholdPx() || abs(deltaY) > scrollThresholdPx()) {
                    listener?.onScroll(deltaX, deltaY)
                } else if (duration < 420L) {
                    trackpadTarget()?.let {
                        trackpadCursorSynced = true
                        listener?.onCurrentRightClick(it.x, it.y, it.imageWidth, it.imageHeight)
                    }
                }
                suppressNextUp = true
                parent?.requestDisallowInterceptTouchEvent(false)
            }
            MotionEvent.ACTION_CANCEL -> {
                suppressNextUp = true
                parent?.requestDisallowInterceptTouchEvent(false)
            }
        }
    }

    private fun currentGeometry(frame: Bitmap): ImageGeometry {
        val base = widthFillGeometry(width, height, frame.width, frame.height)
        val scale = base.scale * zoomMultiplier
        val drawnWidth = frame.width * scale
        val drawnHeight = frame.height * scale
        return base.copy(
            scale = scale,
            offsetX = (width - drawnWidth) / 2f + panX,
            offsetY = (height - drawnHeight) / 2f + panY
        )
    }

    private fun drawCursor(canvas: Canvas, geometry: ImageGeometry) {
        if (interactionMode == InteractionMode.SCREEN && !shortcutCaptureMode) return
        val imageX = cursorImageX ?: return
        val imageY = cursorImageY ?: return
        val x = geometry.offsetX + imageX * geometry.scale
        val y = geometry.offsetY + imageY * geometry.scale
        if (x < -cursorRadiusPx() || x > width + cursorRadiusPx() ||
            y < -cursorRadiusPx() || y > height + cursorRadiusPx()
        ) {
            return
        }
        cursorPaint.style = Paint.Style.FILL
        cursorPaint.color = 0xFF2563EB.toInt()
        canvas.drawCircle(x, y, cursorRadiusPx(), cursorPaint)
        cursorPaint.style = Paint.Style.STROKE
        cursorPaint.strokeWidth = 2.5f * resources.displayMetrics.density
        cursorPaint.color = Color.WHITE
        canvas.drawCircle(x, y, cursorRadiusPx(), cursorPaint)
        cursorPaint.strokeWidth = 1.5f * resources.displayMetrics.density
        cursorPaint.color = 0xFF111827.toInt()
        canvas.drawLine(x - cursorRadiusPx() * 1.8f, y, x + cursorRadiusPx() * 1.8f, y, cursorPaint)
        canvas.drawLine(x, y - cursorRadiusPx() * 1.8f, x, y + cursorRadiusPx() * 1.8f, cursorPaint)
    }

    private fun updateCursor(x: Int, y: Int) {
        cursorImageX = x
        cursorImageY = y
        invalidate()
    }

    private fun trackpadTarget(frame: Bitmap? = bitmap): AbsolutePointerTarget? {
        val currentFrame = frame ?: return null
        return TrackpadPointerCommand.target(cursorImageX, cursorImageY, currentFrame.width, currentFrame.height)
    }

    private fun ensureTrackpadCursor(frame: Bitmap) {
        if (cursorImageX != null && cursorImageY != null) return
        val point = TrackpadMotionController.initialCursor(frame.width, frame.height)
        updateCursor(point.x, point.y)
    }

    private fun moveTrackpadCursor(deltaX: Int, deltaY: Int, frame: Bitmap) {
        val currentX = cursorImageX
        val currentY = cursorImageY
        if (currentX == null || currentY == null) {
            ensureTrackpadCursor(frame)
            return
        }
        val point = TrackpadMotionController.moveCursor(currentX, currentY, deltaX, deltaY, frame.width, frame.height)
        updateCursor(point.x, point.y)
    }

    private fun toggleDoubleTapZoom(frame: Bitmap, tapX: Float, tapY: Float) {
        val base = widthFillGeometry(width, height, frame.width, frame.height)
        val result = ScreenZoomController.nextDoubleTapZoom(
            zoomMultiplier,
            panX,
            panY,
            tapX,
            tapY,
            base.offsetX,
            base.offsetY
        )
        zoomMultiplier = result.zoom
        panX = result.panX
        panY = result.panY
        notifyViewportChanged(frame)
        invalidate()
    }

    private fun notifyViewportChanged(frame: Bitmap? = bitmap) {
        val currentFrame = frame ?: return
        if (width <= 0 || height <= 0) {
            post { notifyViewportChanged(currentFrame) }
            return
        }
        listener?.onViewportChanged(currentGeometry(currentFrame))
    }

    private fun maybeSendMove(x: Int, y: Int, frame: Bitmap) {
        val now = android.os.SystemClock.uptimeMillis()
        if (now - lastMoveSentAt < 45L) return
        lastMoveSentAt = now
        listener?.onMove(x, y, frame.width, frame.height)
    }

    private fun widthFillGeometry(viewWidth: Int, viewHeight: Int, imageWidth: Int, imageHeight: Int): ImageGeometry {
        if (viewWidth <= 0 || viewHeight <= 0 || imageWidth <= 0 || imageHeight <= 0) {
            return ImageGeometry(viewWidth, viewHeight, imageWidth, imageHeight, 1f, 0f, 0f)
        }
        val scale = viewWidth.toFloat() / imageWidth.toFloat()
        val drawnHeight = imageHeight * scale
        return ImageGeometry(
            viewWidth = viewWidth,
            viewHeight = viewHeight,
            imageWidth = imageWidth,
            imageHeight = imageHeight,
            scale = scale,
            offsetX = 0f,
            offsetY = (viewHeight - drawnHeight) / 2f
        )
    }

    private fun isDoubleTap(event: MotionEvent): Boolean {
        return event.eventTime - lastTapTime < 320L &&
            hypot((event.x - lastTapX).toDouble(), (event.y - lastTapY).toDouble()) < doubleTapRadiusPx()
    }

    private fun rememberTap(event: MotionEvent) {
        lastTapTime = event.eventTime
        lastTapX = event.x
        lastTapY = event.y
    }

    private fun distanceFromDown(x: Float, y: Float): Float {
        return hypot((x - downX).toDouble(), (y - downY).toDouble()).toFloat()
    }

    private fun pointerDistance(event: MotionEvent): Float {
        if (event.pointerCount < 2) return 0f
        return hypot(
            (event.getX(0) - event.getX(1)).toDouble(),
            (event.getY(0) - event.getY(1)).toDouble()
        ).toFloat()
    }

    private fun dragThresholdPx(): Float = 10f * resources.displayMetrics.density
    private fun moveThresholdPx(): Float = 4f * resources.displayMetrics.density
    private fun doubleTapRadiusPx(): Float = 48f * resources.displayMetrics.density
    private fun scrollThresholdPx(): Float = 12f * resources.displayMetrics.density
    private fun cursorRadiusPx(): Float = 7f * resources.displayMetrics.density
    private fun trackpadSensitivity(): Float = 1.5f
}
