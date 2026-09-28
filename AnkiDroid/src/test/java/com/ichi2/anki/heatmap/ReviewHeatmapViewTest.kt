/*
 *  Copyright (c) 2026 AnkiDroid (deurim fork)
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

package com.ichi2.anki.heatmap

import android.view.View.MeasureSpec
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.RobolectricTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

@RunWith(AndroidJUnit4::class)
class ReviewHeatmapViewTest : RobolectricTest() {
    private val density get() = targetContext.resources.displayMetrics.density

    @Test
    fun `grid fits inside a narrow pane`() {
        val view = measuredView(widthDp = 200)

        assertTrue(
            "content ${view.contentWidth()} must fit in ${view.measuredWidth}",
            view.contentWidth() <= view.measuredWidth,
        )
    }

    @Test
    fun `narrow pane drops the oldest weeks and keeps the newest`() {
        val view = measuredView(widthDp = 200)

        assertTrue(view.firstVisibleWeek > 0)
        assertEquals(sampleData().weekCount, view.firstVisibleWeek + view.visibleWeeks)
    }

    @Test
    fun `wide pane shows every week`() {
        val view = measuredView(widthDp = 800)

        assertEquals(0, view.firstVisibleWeek)
        assertEquals(sampleData().weekCount, view.visibleWeeks)
    }

    private fun measuredView(widthDp: Int): ReviewHeatmapView {
        val widthPx = (widthDp * density).toInt()
        return ReviewHeatmapView(targetContext).apply {
            setData(sampleData())
            measure(
                MeasureSpec.makeMeasureSpec(widthPx, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED),
            )
            layout(0, 0, measuredWidth, measuredHeight)
        }
    }

    private fun sampleData(): ReviewHeatmapData {
        val today = LocalDate.of(2026, 9, 28)
        val lastWeekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY))
        return ReviewHeatmapData(
            countsByDate = mapOf(today to 3),
            currentStreak = 1,
            longestStreak = 1,
            dailyAverage = 0,
            totalReviews = 3,
            daysLearned = 1,
            daysLearnedPercent = 1,
            dueByDate = emptyMap(),
            startDate = lastWeekStart.minusWeeks((DEFAULT_HEATMAP_WEEKS - 1).toLong()),
            today = today,
            endDate = lastWeekStart.plusWeeks(DEFAULT_FORECAST_WEEKS.toLong()).plusDays(6),
            maxCount = 3,
            maxDue = 0,
        )
    }
}
