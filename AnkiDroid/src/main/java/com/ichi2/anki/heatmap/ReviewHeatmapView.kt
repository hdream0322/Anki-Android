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
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View
import androidx.core.graphics.ColorUtils
import com.google.android.material.color.MaterialColors
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/**
 * A GitHub-contributions-style grid that visualises how many cards were reviewed each day
 * (green, in the past) and how many are scheduled to come due (grey, in the future).
 *
 * Columns are weeks (oldest on the left), rows are days of the week (Sunday at the top).
 * Month labels run along the top and a few weekday labels down the left. Tapping a day
 * reports it through [onDaySelected] and highlights it.
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
        private val labelPaint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize =
                    TypedValue.applyDimension(
                        TypedValue.COMPLEX_UNIT_SP,
                        10f,
                        resources.displayMetrics,
                    )
                // Theme-aware muted text colour: follows light/dark automatically.
                color =
                    MaterialColors.getColor(
                        context,
                        com.google.android.material.R.attr.colorOnSurfaceVariant,
                        0x99808080.toInt(),
                    )
            }
        private val selectionPaint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = dp(2f)
                color = Color.parseColor("#FF8C00") // a warm accent that stands out on green & grey
            }
        private val rect = RectF()

        private var data: ReviewHeatmapData? = null
        private var selectedDate: LocalDate? = null

        init {
            // Required so the touch stream (DOWN…UP) is delivered to this view at all.
            isClickable = true
        }

        /**
         * Invoked when the user taps a day cell, with the date and how much activity it has
         * (reviews done for past/today, cards coming due for the future) and whether it is
         * in the future.
         */
        var onDaySelected: ((date: LocalDate, count: Int, isFuture: Boolean) -> Unit)? = null

        /** Resolved cell edge length, recomputed in [onMeasure] from the available width. */
        private var cellSize = minCell

        private val labelHeight = labelPaint.fontMetrics.let { it.descent - it.ascent }

        /** Space reserved above the grid for month labels. */
        private val monthLabelInset = labelHeight + dp(3f)

        /** Space reserved to the left of the grid for weekday labels. */
        private val weekdayLabelInset =
            WEEKDAY_LABEL_ROWS
                .maxOf { labelPaint.measureText(shortWeekday(it)) } + dp(4f)

        /**
         * Neutral colour for days with no activity: the theme's on-surface colour at low
         * opacity, so empty cells stay subtle on both light and dark backgrounds.
         */
        private val emptyColor =
            ColorUtils.setAlphaComponent(
                MaterialColors.getColor(
                    context,
                    com.google.android.material.R.attr.colorOnSurface,
                    0xFF808080.toInt(),
                ),
                0x1F,
            )

        /** GitHub-style 4-step green palette, unchanged across themes (deliberately branded). */
        private val levelColors =
            intArrayOf(
                0xFF9BE9A8.toInt(),
                0xFF40C463.toInt(),
                0xFF30A14E.toInt(),
                0xFF216E39.toInt(),
            )

        /**
         * Grey 4-step palette for *future* days, scaled by how many cards come due.
         * Alpha-modulated neutral grey so it reads on both light and dark themes and stays
         * visually distinct from the green review history.
         */
        private val dueColors =
            intArrayOf(
                0x40808080,
                0x70808080,
                0xA0808080.toInt(),
                0xD0808080.toInt(),
            )

        fun setData(data: ReviewHeatmapData) {
            this.data = data
            selectedDate = null
            requestLayout()
            invalidate()
        }

        override fun onMeasure(
            widthMeasureSpec: Int,
            heightMeasureSpec: Int,
        ) {
            val width = MeasureSpec.getSize(widthMeasureSpec)
            val weeks = data?.weekCount ?: DEFAULT_HEATMAP_WEEKS
            val usableWidth =
                width - paddingLeft - paddingRight - weekdayLabelInset - cellGap * (weeks - 1)
            cellSize = (usableWidth / weeks).coerceIn(minCell, maxCell)

            val gridHeight = ROWS * cellSize + (ROWS - 1) * cellGap
            // Extra room so the selection ring on the bottom (Saturday) row is not clipped.
            val height =
                (monthLabelInset + gridHeight + selectionPaint.strokeWidth + paddingTop + paddingBottom)
                    .toInt()
            setMeasuredDimension(width, resolveSize(height, heightMeasureSpec))
        }

        /** Total width of weekday gutter + grid, used to centre the whole thing horizontally. */
        private fun contentWidth(): Float {
            val weeks = data?.weekCount ?: DEFAULT_HEATMAP_WEEKS
            val gridWidth = weeks * cellSize + (weeks - 1) * cellGap
            return weekdayLabelInset + gridWidth
        }

        /** X where content starts: padding plus the centring margin for wide panes. */
        private fun contentLeft(): Float {
            val available = width - paddingLeft - paddingRight
            val margin = max(0f, (available - contentWidth()) / 2f)
            return paddingLeft + margin
        }

        /** X of the leftmost grid cell (after the centred weekday label gutter). */
        private fun gridLeft(): Float = contentLeft() + weekdayLabelInset

        /** Y of the topmost grid cell (after padding and the month label strip). */
        private fun gridTop(): Float = paddingTop + monthLabelInset

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val data = this.data ?: return

            val weeks = data.weekCount
            val offsetX = gridLeft()
            val offsetY = gridTop()
            val step = cellSize + cellGap

            // Weekday labels down the left (Sunday is row 0).
            for (row in WEEKDAY_LABEL_ROWS) {
                val text = shortWeekday(row)
                val cy = offsetY + row * step + cellSize / 2f - (labelPaint.descent() + labelPaint.ascent()) / 2f
                canvas.drawText(text, contentLeft(), cy, labelPaint)
            }

            var lastLabelledMonth = -1
            for (column in 0 until weeks) {
                // Month label whenever a column's first day starts a not-yet-labelled month.
                val weekStart = data.startDate.plusDays(column * 7L)
                if (weekStart.monthValue != lastLabelledMonth) {
                    lastLabelledMonth = weekStart.monthValue
                    val name =
                        weekStart.month.getDisplayName(TextStyle.SHORT, Locale.getDefault())
                    canvas.drawText(
                        name,
                        offsetX + column * step,
                        paddingTop - labelPaint.ascent(),
                        labelPaint,
                    )
                }

                for (row in 0 until ROWS) {
                    val date = data.startDate.plusDays((column * 7L) + row)
                    if (date > data.endDate) continue // days beyond the forecast window

                    cellPaint.color =
                        if (date > data.today) {
                            // Future: colour by how many cards are scheduled to come due.
                            colorFor(data.dueByDate[date] ?: 0, data.maxDue, dueColors)
                        } else {
                            // Past/today: colour by how many reviews were done.
                            colorFor(data.countsByDate[date] ?: 0, data.maxCount, levelColors)
                        }

                    val left = offsetX + column * step
                    val top = offsetY + row * step
                    rect.set(left, top, left + cellSize, top + cellSize)
                    canvas.drawRoundRect(rect, cornerRadius, cornerRadius, cellPaint)

                    if (date == selectedDate) {
                        val inset = selectionPaint.strokeWidth / 2f
                        rect.inset(-inset, -inset)
                        canvas.drawRoundRect(rect, cornerRadius, cornerRadius, selectionPaint)
                    }
                }
            }
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            // Consume DOWN so the rest of the gesture (including UP) is delivered here.
            if (event.action == MotionEvent.ACTION_DOWN) {
                return data != null
            }
            if (event.action != MotionEvent.ACTION_UP) {
                return super.onTouchEvent(event)
            }
            val data = this.data ?: return super.onTouchEvent(event)
            val step = cellSize + cellGap
            val column = floor((event.x - gridLeft()) / step).toInt()
            val row = floor((event.y - gridTop()) / step).toInt()
            if (column < 0 || column >= data.weekCount || row < 0 || row >= ROWS) {
                return super.onTouchEvent(event)
            }
            val date = data.startDate.plusDays((column * 7L) + row)
            if (date < data.startDate || date > data.endDate) {
                return super.onTouchEvent(event)
            }
            selectedDate = date
            invalidate()
            val isFuture = date > data.today
            val count =
                if (isFuture) data.dueByDate[date] ?: 0 else data.countsByDate[date] ?: 0
            onDaySelected?.invoke(date, count, isFuture)
            performClick()
            return true
        }

        override fun performClick(): Boolean {
            super.performClick()
            return true
        }

        private fun colorFor(
            count: Int,
            maxCount: Int,
            palette: IntArray,
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
            return palette[min(level, palette.size - 1)]
        }

        private fun shortWeekday(row: Int): String {
            // Row 0 is Sunday; DayOfWeek is Monday-based, so offset accordingly.
            val day = if (row == 0) DayOfWeek.SUNDAY else DayOfWeek.of(row)
            return day.getDisplayName(TextStyle.SHORT, Locale.getDefault())
        }

        companion object {
            private const val ROWS = 7

            /** Rows (Sun=0) to label on the left: Mon, Wed, Fri — enough to orient without clutter. */
            private val WEEKDAY_LABEL_ROWS = intArrayOf(1, 3, 5)
        }
    }
