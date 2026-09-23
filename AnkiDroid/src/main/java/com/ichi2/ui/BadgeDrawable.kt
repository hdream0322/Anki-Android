// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.ui

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.DrawableWrapper

/**
 * Creates a new wrapper around the specified drawable.
 *
 * @param dr the drawable to wrap
 */
class BadgeDrawable(
    dr: Drawable?,
) : DrawableWrapper(dr) {
    private val paint: Paint = Paint()
    private val ringPaint: Paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var ringColor: Int? = null
    private var badge: Drawable? = null
    private var text: String? = null
    private var textX = 0f
    private var textY = 0f

    fun setBadgeDrawable(view: Drawable) {
        badge = view
        invalidateSize()
    }

    /**
     * Draws a ring of [color] around a dot badge, separating it from the icon.
     * Use the color of the background behind the icon.
     */
    fun setRingColor(color: Int) {
        ringColor = color
        ringPaint.color = color
    }

    private fun invalidateSize() {
        if (!isShowingText) {
            invalidateDotSize()
            return
        }
        // This goes out of bounds - it seems to be fine
        val size = (intrinsicWidth * iconScale).toInt()
        paint.textSize = (size * 0.8).toFloat()
        val left = left.toInt()
        val bottom = bottom.toInt()
        val right = left + size
        val top = bottom - size
        if (badge != null) {
            badge!!.setBounds(left, top, right, bottom)
        }
        val vcenter = (top + bottom) / 2.0f
        textX = (left + right) / 2.0f
        textY = vcenter - (paint.descent() + paint.ascent()) / 2
    }

    /**
     * Places the dot at the top-right corner, outside the arc of the sync icon.
     * The dot goes slightly out of bounds, into the padding of the button.
     */
    private fun invalidateDotSize() {
        val w = intrinsicWidth
        val h = intrinsicHeight
        val radius = w * DOT_SCALE / 2
        val cx = w * DOT_CENTER_X
        val cy = h * DOT_CENTER_Y
        badge?.setBounds(
            (cx - radius).toInt(),
            (cy - radius).toInt(),
            (cx + radius).toInt(),
            (cy + radius).toInt(),
        )
    }

    private val bottom: Double
        get() {
            val h = intrinsicHeight
            return if (isShowingText) {
                h * 0.45
            } else {
                h * iconScale
            }
        }
    private val left: Double
        get() {
            val w = intrinsicWidth
            return if (isShowingText) {
                w * 0.55
            } else {
                w - w * iconScale
            }
        }
    private val iconScale: Double
        get() =
            if (isShowingText) {
                ICON_SCALE_TEXT
            } else {
                ICON_SCALE_BARE
            }
    private val isShowingText: Boolean
        get() = text != null && text!!.isNotEmpty()

    fun setText(c: Char) {
        text = String(charArrayOf(c))
        invalidateSize()
    }

    override fun draw(canvas: Canvas) {
        super.draw(canvas)
        if (badge != null) {
            if (ringColor != null && !isShowingText) {
                val b = badge!!.bounds
                val ringRadius = b.width() / 2f + (intrinsicWidth * DOT_RING_SCALE).toFloat()
                canvas.drawCircle(b.exactCenterX(), b.exactCenterY(), ringRadius, ringPaint)
            }
            badge!!.draw(canvas)
            if (text != null) {
                canvas.drawText(text!!, textX, textY, paint)
            }
        }
    }

    companion object {
        const val ICON_SCALE_TEXT = 0.70
        const val ICON_SCALE_BARE = 0.40

        // Dot badge geometry, as fractions of the icon size (24dp icon: 7.2dp dot at 22dp, 2dp)
        const val DOT_SCALE = 0.30
        const val DOT_CENTER_X = 0.92
        const val DOT_CENTER_Y = 0.08
        const val DOT_RING_SCALE = 0.07
    }

    init {
        paint.typeface = Typeface.DEFAULT_BOLD
        paint.textAlign = Paint.Align.CENTER
        paint.color = Color.WHITE
    }
}
