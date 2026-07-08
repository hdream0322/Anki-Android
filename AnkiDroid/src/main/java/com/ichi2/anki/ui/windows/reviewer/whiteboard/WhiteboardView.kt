/*
 * Copyright (c) 2025 Brayan Oliveira <69634269+brayandso@users.noreply.github.com>
 *
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program. If not, see <http://www.gnu.org/licenses/>.
 */
package com.ichi2.anki.ui.windows.reviewer.whiteboard

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import androidx.core.graphics.createBitmap
import com.ichi2.anki.R
import com.ichi2.anki.ui.windows.reviewer.whiteboard.SmoothPath.Companion.drawPath
import timber.log.Timber

/**
 * A custom view for the whiteboard that handles drawing and touch events.
 */
class WhiteboardView : View {
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context) : this(context, null)

    var onNewPath: ((Path) -> Unit)? = null
    var onEraseGestureStart: ((Float, Float) -> Unit)? = null
    var onEraseGestureMove: ((Float, Float) -> Unit)? = null
    var onEraseGestureEnd: (() -> Unit)? = null
    var isEraserActive: Boolean = false
    var eraserMode: EraserMode = EraserMode.INK
    var isStylusOnlyMode: Boolean = false

    /**
     * Whether the drawing should scale and pan together with the card's own zoom/scroll.
     * When enabled, [setContentTransform] drives what's drawn, and new strokes are recorded
     * in the card's content space (via [inverseContentMatrix]) instead of raw screen pixels.
     */
    var isContentSyncEnabled: Boolean = false
        set(value) {
            if (field == value) return
            field = value
            if (!value) {
                contentMatrix.reset()
                inverseContentMatrix.reset()
            }
            redrawHistory()
        }

    private val contentMatrix = Matrix()
    private val inverseContentMatrix = Matrix()

    private val currentPath = SmoothPath()
    private val currentPaint =
        Paint().apply {
            isAntiAlias = true
            isDither = true
            style = Paint.Style.STROKE
            strokeJoin = Paint.Join.ROUND
            strokeCap = Paint.Cap.ROUND
        }
    private val eraserPreviewPaint =
        Paint(currentPaint).apply {
            color = context.getColor(R.color.whiteboard_eraser)
        }
    private var history: List<DrawingAction> = emptyList()
    private lateinit var bufferCanvas: Canvas
    private lateinit var bufferBitmap: Bitmap
    private val canvasPaint = Paint(Paint.DITHER_FLAG)
    private val historyPaint =
        Paint().apply {
            isAntiAlias = true
            isDither = true
            style = Paint.Style.STROKE
            strokeJoin = Paint.Join.ROUND
            strokeCap = Paint.Cap.ROUND
        }

    private var hasMoved = false
    private var isDrawing = false
    private val multiTouchDetector =
        MultiTouchDetector(
            touchSlop = ViewConfiguration.get(context).scaledTouchSlop,
        )

    fun setOnMultiTouchListener(listener: OnMultiTouchListener) {
        multiTouchDetector.setOnMultiTouchListener(listener)
    }

    fun setOnScrollByListener(listener: OnScrollByListener) {
        multiTouchDetector.setOnScrollByListener(listener)
    }

    /**
     * Mirrors the card's current zoom [scale] and scroll offset ([scrollX], [scrollY]) so the
     * whiteboard's ink stays visually attached to the card content. No-op unless
     * [isContentSyncEnabled] is set.
     */
    fun setContentTransform(
        scale: Float,
        scrollX: Float,
        scrollY: Float,
    ) {
        if (!isContentSyncEnabled) return
        contentMatrix.setScale(scale, scale)
        contentMatrix.postTranslate(-scrollX, -scrollY)
        if (!contentMatrix.invert(inverseContentMatrix)) {
            inverseContentMatrix.reset()
        }
        invalidate()
    }

    /**
     * Recreates the drawing buffer when the view size changes.
     */
    override fun onSizeChanged(
        w: Int,
        h: Int,
        oldw: Int,
        oldh: Int,
    ) {
        super.onSizeChanged(w, h, oldw, oldh)
        // createBitmap requires a width and height > 0; #21096
        if (w <= 0 || h <= 0) {
            Timber.w("Width or height <= 0: w: $w h: $h Bitmap couldn't be created with the new size")
            return
        }
        if (::bufferBitmap.isInitialized) bufferBitmap.recycle()
        bufferBitmap = createBitmap(w, h)
        bufferCanvas = Canvas(bufferBitmap)
        redrawHistory()
    }

    /**
     * Draws the whiteboard content.
     * This includes the historical drawing buffer and the current live path.
     */
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.save()
        canvas.concat(contentMatrix)

        if (isContentSyncEnabled) {
            // The card's zoom/scroll can move content outside the buffer's fixed bounds,
            // so paths are drawn straight from history instead of a screen-space buffer.
            drawActions(canvas, history)
        } else {
            // Draw the committed history
            canvas.drawBitmap(bufferBitmap, 0f, 0f, canvasPaint)
        }

        // Draw the live preview path for the current gesture
        if (isEraserActive) {
            canvas.drawPath(currentPath, eraserPreviewPaint)
        } else {
            // Draw the normal brush or pixel eraser preview
            canvas.drawPath(currentPath, currentPaint)
        }
        canvas.restore()
    }

    /**
     * Handles user touch input for drawing and erasing.
     * Ignores finger input if stylus-only mode is enabled.
     */
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.pointerCount >= 2) {
            isDrawing = false
            currentPath.reset()
            invalidate()

            return multiTouchDetector.onTouchEvent(event)
        }

        if (isStylusOnlyMode && event.getToolType(0) != MotionEvent.TOOL_TYPE_STYLUS) {
            return false
        }

        // Map screen coordinates into the card's content space so strokes stay put when
        // isContentSyncEnabled later re-projects them via a different contentMatrix.
        // A no-op copy when content sync is disabled, since inverseContentMatrix is then identity.
        val contentEvent = MotionEvent.obtain(event).apply { transform(inverseContentMatrix) }
        try {
            val touchX = contentEvent.x
            val touchY = contentEvent.y
            val isPathEraser = isEraserActive && eraserMode == EraserMode.STROKE

            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    isDrawing = true
                    hasMoved = false
                    currentPath.moveTo(touchX, touchY)
                    if (isPathEraser) {
                        onEraseGestureStart?.invoke(touchX, touchY)
                    }
                    invalidate()
                }
                MotionEvent.ACTION_MOVE -> {
                    if (!isDrawing) return false

                    hasMoved = true
                    currentPath.drawAlong(contentEvent)
                    if (isPathEraser) {
                        onEraseGestureMove?.invoke(touchX, touchY)
                    }
                    invalidate()
                }
                MotionEvent.ACTION_UP -> {
                    if (!isDrawing) return false

                    if (isPathEraser) {
                        onEraseGestureEnd?.invoke()
                    } else {
                        if (!hasMoved) {
                            // A single tap. Add a tiny line segment to ensure it has a non-zero length,
                            // which makes it more robust for path operations.
                            currentPath.lineTo(touchX + 0.2f, touchY + 0.2f)
                        }
                        onNewPath?.invoke(currentPath.clone())
                    }
                    // Reset the path for the next gesture
                    currentPath.reset()
                    isDrawing = false
                    invalidate()
                }
                else -> return false
            }
            return true
        } finally {
            contentEvent.recycle()
        }
    }

    /**
     * Replaces the current drawing history with a new set of actions and redraws the buffer.
     */
    fun setHistory(actions: List<DrawingAction>) {
        history = actions
        redrawHistory()
    }

    /**
     * Configures the paint for the live drawing preview based on the current tool.
     */
    fun setCurrentBrush(
        color: Int,
        strokeWidth: Float,
    ) {
        currentPaint.strokeWidth = strokeWidth
        currentPaint.xfermode = null
        currentPaint.color = color

        // Configure the stroke eraser's preview paint separately
        eraserPreviewPaint.strokeWidth = strokeWidth
    }

    /**
     * Redraws all historical paths onto the offscreen buffer, or just triggers a direct
     * redraw when [isContentSyncEnabled] (see [onDraw]).
     */
    private fun redrawHistory() {
        if (isContentSyncEnabled) {
            invalidate()
            return
        }
        if (!::bufferCanvas.isInitialized) return
        bufferCanvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
        drawActions(bufferCanvas, history)
        invalidate()
    }

    /** Draws a list of [DrawingAction]s onto [canvas], in order. */
    private fun drawActions(
        canvas: Canvas,
        actions: List<DrawingAction>,
    ) {
        for (action in actions) {
            historyPaint.strokeWidth = action.strokeWidth
            if (action.isEraser) {
                historyPaint.xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
            } else {
                historyPaint.xfermode = null
                historyPaint.color = action.color
            }
            canvas.drawPath(action.path, historyPaint)
        }
    }
}

/**
 * A wrapper around a [Path] which supports smooth drawing & state tracking via [drawAlong]
 */
private class SmoothPath(
    private val path: Path = Path(),
) {
    // for efficiency use two primitives rather than a 'point' class
    private var lastX = 0f
    private var lastY = 0f

    /**
     * Extracts and draws a smooth curve from the [MotionEvent]
     */
    fun drawAlong(event: MotionEvent) {
        // use historySize for cases when the touchscreen samples faster than the screen
        for (i in 0 until event.historySize) {
            val hx = event.getHistoricalX(i)
            val hy = event.getHistoricalY(i)
            // draw Bézier curves between the midpoints, ensuring a continuous curve
            path.quadTo(lastX, lastY, (lastX + hx) / 2f, (lastY + hy) / 2f)
            lastX = hx
            lastY = hy
        }
        // draw the current event
        val x = event.x
        val y = event.y
        path.quadTo(lastX, lastY, (lastX + x) / 2f, (lastY + y) / 2f)
        lastX = x
        lastY = y
    }

    // Methods are reimplemented rather than using inheritance to ensure nothing is forgotten

    /** @see Path.lineTo */
    fun lineTo(
        x: Float,
        y: Float,
    ) {
        path.lineTo(x, y)
        lastX = x
        lastY = y
    }

    /** @see Path.moveTo */
    fun moveTo(
        x: Float,
        y: Float,
    ) {
        path.moveTo(x, y)
        lastX = x
        lastY = y
    }

    /** @see Path.reset */
    fun reset() {
        path.reset()
        lastX = 0f
        lastY = 0f
    }

    fun clone() = Path(path)

    companion object {
        /** @see Canvas.drawPath */
        fun Canvas.drawPath(
            path: SmoothPath,
            paint: Paint,
        ) {
            this.drawPath(path.path, paint)
        }
    }
}
