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

import com.ichi2.anki.libanki.Collection
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

/**
 * Aggregated review-history data backing the review heatmap shown on the study options screen.
 *
 * This is a clean-room implementation inspired by the *idea* of Glutanimate's "Review Heatmap"
 * Anki add-on. No source code or assets from that add-on are used here.
 *
 * @param countsByDate number of reviews performed on each day in `[startDate, endDate]`
 * @param currentStreak number of consecutive days (ending today, or yesterday if nothing was
 *  studied yet today) on which at least one review happened
 * @param longestStreak the longest run of consecutive studied days anywhere in the period
 * @param dailyAverage mean number of reviews per day across the displayed period
 * @param totalReviews total number of reviews performed across the whole period
 * @param daysLearned number of distinct days in the period with at least one review
 * @param daysLearnedPercent [daysLearned] as a percentage of all days in the period (0-100)
 * @param dueByDate number of cards scheduled to come due on each future day after [today]
 * @param startDate first (oldest) day in the grid; always a Sunday so columns are whole weeks
 * @param today "today" in the device timezone; the boundary between history and forecast
 * @param endDate last (newest) day in the grid; a Saturday in the forecast window
 * @param maxCount the highest single-day review count in the past period (0 if there were none)
 * @param maxDue the highest single-day forecast count in the future window (0 if there are none)
 */
data class ReviewHeatmapData(
    val countsByDate: Map<LocalDate, Int>,
    val currentStreak: Int,
    val longestStreak: Int,
    val dailyAverage: Int,
    val totalReviews: Int,
    val daysLearned: Int,
    val daysLearnedPercent: Int,
    val dueByDate: Map<LocalDate, Int>,
    val startDate: LocalDate,
    val today: LocalDate,
    val endDate: LocalDate,
    val maxCount: Int,
    val maxDue: Int,
) {
    /** Number of week columns the grid should render. */
    val weekCount: Int
        get() = (ChronoUnit.DAYS.between(startDate, endDate) / 7).toInt() + 1
}

/**
 * Reads the `revlog` table and aggregates per-day review counts for the most recent [weeks]
 * weeks (default ~6 months), computing the current streak and daily average.
 *
 * Must be called inside a `withCol { }` block (it runs synchronous DB queries).
 *
 * Manual reschedules are excluded (`ease = 0`), matching how Anki's own statistics count
 * "real" reviews.
 */
fun Collection.fetchReviewHeatmapData(
    weeks: Int = DEFAULT_HEATMAP_WEEKS,
    forecastWeeks: Int = DEFAULT_FORECAST_WEEKS,
): ReviewHeatmapData {
    val today = LocalDate.now()
    // Snap the grid so each column is a full Sunday-to-Saturday week.
    val lastWeekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY))
    val startDate = lastWeekStart.minusWeeks((weeks - 1).toLong())

    val cutoffMs =
        startDate
            .atStartOfDay(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()

    val counts = HashMap<LocalDate, Int>()
    var maxCount = 0
    db
        .query(
            "SELECT date(id / 1000, 'unixepoch', 'localtime') AS d, count() " +
                "FROM revlog WHERE id >= ? AND ease > 0 GROUP BY d",
            cutoffMs,
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val day = cursor.getString(0) ?: continue
                val count = cursor.getInt(1)
                val date = runCatching { LocalDate.parse(day) }.getOrNull() ?: continue
                if (date < startDate || date > today) continue
                counts[date] = count
                if (count > maxCount) maxCount = count
            }
        }

    val totalDays = (ChronoUnit.DAYS.between(startDate, today) + 1).coerceAtLeast(1)
    val daysLearned = counts.count { it.value > 0 }
    val totalReviews = counts.values.sum()

    // Forecast: review/day-learn cards (queue 2 & 3) store `due` as a day number relative to
    // collection creation, the same scale as `sched.today`. Map each future due-day onto a
    // calendar date and only keep what fits inside the forward window the grid will draw.
    val todayDayNum = sched.today
    val horizonDayNum = todayDayNum + forecastWeeks * 7
    val due = HashMap<LocalDate, Int>()
    var maxDue = 0
    db
        .query(
            "SELECT due, count() FROM cards " +
                "WHERE queue IN (2, 3) AND due > ? AND due <= ? GROUP BY due",
            todayDayNum,
            horizonDayNum,
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val dueDayNum = cursor.getInt(0)
                val count = cursor.getInt(1)
                val date = today.plusDays((dueDayNum - todayDayNum).toLong())
                due[date] = count
                if (count > maxDue) maxDue = count
            }
        }
    // Extend the grid forward to the Saturday that closes the last forecast week.
    val endDate =
        lastWeekStart
            .plusWeeks(forecastWeeks.toLong())
            .plusDays(6)

    return ReviewHeatmapData(
        countsByDate = counts,
        currentStreak = computeCurrentStreak(counts, today),
        longestStreak = computeLongestStreak(counts, startDate, today),
        dailyAverage = Math.round(totalReviews.toDouble() / totalDays).toInt(),
        totalReviews = totalReviews,
        daysLearned = daysLearned,
        daysLearnedPercent = Math.round(daysLearned * 100.0 / totalDays).toInt(),
        dueByDate = due,
        startDate = startDate,
        today = today,
        endDate = endDate,
        maxCount = maxCount,
        maxDue = maxDue,
    )
}

private fun computeCurrentStreak(
    counts: Map<LocalDate, Int>,
    today: LocalDate,
): Int {
    // If nothing has been studied yet today, the streak can still be "alive" from yesterday.
    var day = if ((counts[today] ?: 0) > 0) today else today.minusDays(1)
    var streak = 0
    while ((counts[day] ?: 0) > 0) {
        streak++
        day = day.minusDays(1)
    }
    return streak
}

private fun computeLongestStreak(
    counts: Map<LocalDate, Int>,
    startDate: LocalDate,
    today: LocalDate,
): Int {
    var longest = 0
    var run = 0
    var day = startDate
    while (day <= today) {
        if ((counts[day] ?: 0) > 0) {
            run++
            if (run > longest) longest = run
        } else {
            run = 0
        }
        day = day.plusDays(1)
    }
    return longest
}

/** ~6 months, which fits comfortably in the study-options side pane on tablets. */
const val DEFAULT_HEATMAP_WEEKS = 26

/** ~5 weeks of forward scheduling, enough to see upcoming load without dwarfing the history. */
const val DEFAULT_FORECAST_WEEKS = 5
