/*
 * Copyright (c) 2026 AnkiDroid Open Source Team
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
 * this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.ichi2.anki.heatmap

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import java.time.LocalDate
import kotlin.math.max
import kotlin.math.min

/**
 * A GitHub-contributions-style grid that visualises how many cards were reviewed each day.
 *
 * Columns are weeks (oldest on the left), rows are days of the week (Sunday at the top).
 * Cell colour intensity scales with the number of reviews relative to the busiest day.
 *
 * Pure custom-drawing (Canvas) so it needs no WebView or external JS libraries. Feed it data
 * with [setData]; it sizes its own height based on the available width.
 */
class ReviewHeatmapView
    @JvmOverloads
    constructor(
        context: Context,
        attrs: AttributeSet? = null,
        defStyleAttr: Int = 0,
    ) : View(context, attrs, defStyleAttr) {
        private val density = resources.displayMetrics.density

        private fun dp(value: Float) = value * density

        private val cellGap = dp(3f)
        private val minCell = dp(7f)
        private val maxCell = dp(16f)
        private val cornerRadius = dp(2f)

        private val cellPaint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
            }
        private val rect = RectF()

        private var data: ReviewHeatmapData? = null

        /** Resolved cell edge length, recomputed in [onMeasure] from the available width. */
        private var cellSize = minCell

        /** GitHub-style 4-step green palette plus a neutral colour for days with no reviews. */
        private val emptyColor = 0x1F808080 // ~12% grey, readable on light and dark themes
        private val levelColors =
            intArrayOf(
                0xFF9BE9A8.toInt(),
                0xFF40C463.toInt(),
                0xFF30A14E.toInt(),
                0xFF216E39.toInt(),
            )

        fun setData(data: ReviewHeatmapData) {
            this.data = data
            requestLayout()
            invalidate()
        }

        override fun onMeasure(
            widthMeasureSpec: Int,
            heightMeasureSpec: Int,
        ) {
            val width = MeasureSpec.getSize(widthMeasureSpec)
            val weeks = data?.weekCount ?: DEFAULT_HEATMAP_WEEKS
            val usableWidth = width - paddingLeft - paddingRight - cellGap * (weeks - 1)
            cellSize = (usableWidth / weeks).coerceIn(minCell, maxCell)

            val gridHeight = ROWS * cellSize + (ROWS - 1) * cellGap
            val height = (gridHeight + paddingTop + paddingBottom).toInt()
            setMeasuredDimension(width, resolveSize(height, heightMeasureSpec))
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val data = this.data ?: return

            val weeks = data.weekCount
            val gridWidth = weeks * cellSize + (weeks - 1) * cellGap
            // Centre the grid horizontally within whatever width we were given.
            val offsetX = paddingLeft + max(0f, (width - paddingLeft - paddingRight - gridWidth) / 2f)
            val offsetY = paddingTop.toFloat()

            for (column in 0 until weeks) {
                for (row in 0 until ROWS) {
                    val date = data.startDate.plusDays((column * 7L) + row)
                    if (date > data.endDate) continue // future days within the current week

                    val count = data.countsByDate[date] ?: 0
                    cellPaint.color = colorFor(count, data.maxCount)

                    val left = offsetX + column * (cellSize + cellGap)
                    val top = offsetY + row * (cellSize + cellGap)
                    rect.set(left, top, left + cellSize, top + cellSize)
                    canvas.drawRoundRect(rect, cornerRadius, cornerRadius, cellPaint)
                }
            }
        }

        private fun colorFor(
            count: Int,
            maxCount: Int,
        ): Int {
            if (count <= 0 || maxCount <= 0) return emptyColor
            val ratio = count.toDouble() / maxCount
            val level =
                when {
                    ratio <= 0.25 -> 0
                    ratio <= 0.50 -> 1
                    ratio <= 0.75 -> 2
                    else -> 3
                }
            return levelColors[min(level, levelColors.size - 1)]
        }

        companion object {
            private const val ROWS = 7
        }
    }
