/*
 *  Copyright (c) 2026 Deurim Fork
 *
 *  This program is free software; you can redistribute it and/or modify it under
 *  the terms of the GNU General Public License as published by the Free Software
 *  Foundation; either version 3 of the License, or (at your option) any later
 *  version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY
 *  WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 *  PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License along with
 *  this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.ichi2.anki.reviewer

import android.animation.ValueAnimator
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.view.View
import android.widget.ProgressBar
import androidx.annotation.VisibleForTesting
import androidx.core.graphics.withSave
import com.ichi2.anki.settings.enums.ProgressGlowSpeed
import kotlin.math.PI
import kotlin.math.cos

/**
 * Windows-style "glow" for the reviewer's session progress bar: a soft highlight that
 * repeatedly sweeps from the start of the bar to the end of the filled part.
 *
 * Installed as the bar's foreground so the layouts don't need an extra view.
 */
class ProgressGlowDrawable private constructor(
    private val progressBar: ProgressBar,
    speed: ProgressGlowSpeed,
) : Drawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val shaderMatrix = Matrix()
    private var glowWidth = 0f
    private var phase = 0f

    private val animator =
        ValueAnimator.ofFloat(0f, 1f).apply {
            duration = speed.cycleMs
            repeatCount = ValueAnimator.INFINITE
            addUpdateListener {
                phase = it.animatedValue as Float
                invalidateSelf()
            }
        }

    private val attachListener =
        object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) = animator.start()

            override fun onViewDetachedFromWindow(v: View) = animator.cancel()
        }

    override fun onBoundsChange(bounds: Rect) {
        glowWidth = bounds.width() * GLOW_WIDTH_FRACTION
        val highlight = Color.argb(GLOW_ALPHA, 255, 255, 255)
        paint.shader =
            LinearGradient(
                0f,
                0f,
                glowWidth,
                0f,
                intArrayOf(Color.TRANSPARENT, highlight, Color.TRANSPARENT),
                null,
                Shader.TileMode.CLAMP,
            )
    }

    override fun draw(canvas: Canvas) {
        val max = progressBar.max
        if (max <= 0 || progressBar.progress <= 0 || glowWidth <= 0f) return
        val sweep = sweepFraction(phase) ?: return

        val b = bounds
        val fillWidth = b.width() * progressBar.progress.toFloat() / max
        val offset = glowOffset(sweep, fillWidth, glowWidth)
        val rtl = progressBar.layoutDirection == View.LAYOUT_DIRECTION_RTL

        canvas.withSave {
            if (rtl) {
                clipRect(b.right - fillWidth, b.top.toFloat(), b.right.toFloat(), b.bottom.toFloat())
                // mirror the sweep so it still travels from the start of the bar
                scale(-1f, 1f, b.exactCenterX(), 0f)
            } else {
                clipRect(b.left.toFloat(), b.top.toFloat(), b.left + fillWidth, b.bottom.toFloat())
            }
            shaderMatrix.setTranslate(b.left + offset, 0f)
            paint.shader?.setLocalMatrix(shaderMatrix)
            drawRect(b.left + offset, b.top.toFloat(), b.left + offset + glowWidth, b.bottom.toFloat(), paint)
        }
    }

    override fun setAlpha(alpha: Int) {
        paint.alpha = alpha
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        paint.colorFilter = colorFilter
    }

    @Suppress("OVERRIDE_DEPRECATION")
    override fun getOpacity() = PixelFormat.TRANSLUCENT

    companion object {
        /** Portion of [ProgressGlowSpeed.cycleMs] spent sweeping; the rest is a pause, as on Windows. */
        private const val SWEEP_PORTION = 0.6f

        private const val GLOW_WIDTH_FRACTION = 0.25f
        private const val GLOW_ALPHA = 150

        /** Adds (or removes) the glow on [progressBar]. */
        fun ProgressBar.setGlowEnabled(
            enabled: Boolean,
            speed: ProgressGlowSpeed = ProgressGlowSpeed.MEDIUM,
        ) {
            (foreground as? ProgressGlowDrawable)?.let {
                removeOnAttachStateChangeListener(it.attachListener)
                it.animator.cancel()
            }
            if (!enabled) {
                foreground = null
                return
            }
            val glow = ProgressGlowDrawable(this, speed)
            foreground = glow
            addOnAttachStateChangeListener(glow.attachListener)
            if (isAttachedToWindow) glow.animator.start()
        }

        /**
         * Maps the animator's [phase] (0..1 over a whole cycle) to how far the sweep has
         * travelled (0..1), or `null` during the pause between sweeps.
         */
        @VisibleForTesting
        fun sweepFraction(phase: Float): Float? {
            if (phase >= SWEEP_PORTION) return null
            // ease in/out, same curve as AccelerateDecelerateInterpolator
            val t = phase / SWEEP_PORTION
            return (cos((t + 1) * PI) / 2 + 0.5).toFloat()
        }

        /**
         * Left edge of the glow, relative to the start of the bar. The glow starts fully
         * before the bar and ends fully past the filled part, so it enters and leaves smoothly.
         */
        @VisibleForTesting
        fun glowOffset(
            sweep: Float,
            fillWidth: Float,
            glowWidth: Float,
        ): Float = -glowWidth + sweep * (fillWidth + glowWidth)
    }
}
