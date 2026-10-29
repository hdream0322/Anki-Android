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
import com.ichi2.anki.libanki.DeckId
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
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
 *  studied yet today) on which at least one review happened. [restDays] do not break the run
 *  but are not counted either
 * @param longestStreak the longest run of consecutive studied days anywhere in the period,
 *  bridging [restDays] the same way
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
 * @param restDays past days with no reviews because no review card was due, so there was
 *  nothing to study
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
    val restDays: Set<LocalDate> = emptySet(),
) {
    /** Number of week columns the grid should render. */
    val weekCount: Int
        get() = (ChronoUnit.DAYS.between(startDate, endDate) / 7).toInt() + 1
}

/**
 * Reads the `revlog` table and aggregates per-day review counts for the most recent [weeks]
 * weeks (default ~6 months), computing the current streak and daily average.
 *
 * Scoped to [deckId] and all of its subdecks: history is matched by the deck the card
 * currently lives in (or its home deck while it sits in a filtered deck), and the forecast
 * counts only that deck's scheduled cards.
 *
 * Must be called inside a `withCol { }` block (it runs synchronous DB queries).
 *
 * Review counts exclude manual reschedules (`ease = 0`), matching how Anki's own statistics
 * count "real" reviews; rest-day tracking includes them, since they move the due date.
 */
fun Collection.fetchReviewHeatmapData(
    deckId: DeckId,
    weeks: Int = DEFAULT_HEATMAP_WEEKS,
    forecastWeeks: Int = DEFAULT_FORECAST_WEEKS,
): ReviewHeatmapData {
    val now = LocalDateTime.now()
    val rolloverHour = getPreferences().scheduling.rollover
    val today = if (now.hour < rolloverHour) now.toLocalDate().minusDays(1) else now.toLocalDate()
    // Snap the grid so each column is a full Sunday-to-Saturday week.
    val lastWeekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY))
    val startDate = lastWeekStart.minusWeeks((weeks - 1).toLong())

    // Anki days begin at the rollover hour (default 4 AM), not midnight.
    val cutoffMs =
        startDate
            .atTime(rolloverHour, 0)
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()

    // The deck plus every subdeck. Ids come from the backend, so inlining them is safe.
    val deckIdList = decks.deckAndChildIds(deckId).joinToString(",")
    val inDeck = "(cards.did IN ($deckIdList) OR cards.odid IN ($deckIdList))"

    val counts = HashMap<LocalDate, Int>()
    var maxCount = 0
    db
        .query(
            // Shift back by the rollover so e.g. a 03:30 review lands on the previous Anki day.
            "SELECT date(revlog.id / 1000 - ? * 3600, 'unixepoch', 'localtime') AS d, count() " +
                "FROM revlog JOIN cards ON cards.id = revlog.cid " +
                "WHERE revlog.id >= ? AND revlog.ease > 0 " +
                "AND $inDeck GROUP BY d",
            rolloverHour,
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
                "WHERE queue IN (2, 3) AND due > ? AND due <= ? " +
                "AND did IN ($deckIdList) GROUP BY due",
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

    // Rebuild each card's past due dates from its review history, so days on which nothing
    // was due can be told apart from days the user skipped. Only rows inside the window are
    // read, plus each card's last row before it, which says what was due when the window
    // opens. Rows with `ivl = 0` other than manual resets (e.g. filtered-deck previews) leave
    // the due date alone, so they are skipped.
    val scheduling = "NOT (revlog.ivl = 0 AND revlog.type != $REVLOG_MANUAL)"
    val history = HashMap<Long, MutableList<ScheduledReview>>()
    val parsedDates = HashMap<String, LocalDate>()
    db
        .query(
            "SELECT revlog.cid, date(revlog.id / 1000 - ? * 3600, 'unixepoch', 'localtime'), " +
                "revlog.ivl, revlog.type " +
                "FROM revlog JOIN cards ON cards.id = revlog.cid " +
                "WHERE $inDeck AND $scheduling AND (revlog.id >= ? OR revlog.id IN (" +
                "SELECT max(revlog.id) FROM revlog JOIN cards ON cards.id = revlog.cid " +
                "WHERE $inDeck AND $scheduling AND revlog.id < ? GROUP BY revlog.cid)) " +
                "ORDER BY revlog.id",
            rolloverHour,
            cutoffMs,
            cutoffMs,
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val day = cursor.getString(1) ?: continue
                val date = parsedDates[day] ?: runCatching { LocalDate.parse(day) }.getOrNull() ?: continue
                parsedDates[day] = date
                val type = cursor.getInt(3)
                history
                    .getOrPut(cursor.getLong(0)) { mutableListOf() }
                    .add(ScheduledReview(date, cursor.getInt(2), isManual = type == REVLOG_MANUAL || type == REVLOG_RESCHEDULED))
            }
        }

    // Where each card waiting on a review stands now: the scheduler's own due day beats any
    // reconstruction (e.g. "set due date" logs the interval, not the due day). A card in a
    // filtered deck keeps its home-deck due date in `odue`. Suspended, buried and new cards
    // are not waiting on a review.
    val pendingFrom = HashMap<Long, LocalDate>()
    db
        .query(
            "SELECT cards.id, cards.queue, CASE WHEN cards.odid != 0 THEN cards.odue ELSE cards.due END " +
                "FROM cards WHERE cards.queue IN (1, 2, 3) AND $inDeck",
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val cardId = cursor.getLong(0)
                // queue 1 = intraday learning (`due` is a timestamp), 2 = review, 3 = day-learn
                pendingFrom[cardId] =
                    if (cursor.getInt(1) == 1) {
                        // Overdue since the day after its last step, if that step is known.
                        history[cardId]?.lastOrNull()?.date?.plusDays(1) ?: continue
                    } else {
                        today.plusDays(cursor.getLong(2) - todayDayNum)
                    }
            }
        }
    val restDays = computeRestDays(history, pendingFrom, counts, startDate, today)

    return ReviewHeatmapData(
        countsByDate = counts,
        currentStreak = computeCurrentStreak(counts, restDays, startDate, today),
        longestStreak = computeLongestStreak(counts, restDays, startDate, today),
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
        restDays = restDays,
    )
}

/** One revlog entry: the Anki day it happened on and the interval it scheduled. */
internal data class ScheduledReview(
    val date: LocalDate,
    /** Days until the next review; negative is a learning step in seconds; 0 is a reset. */
    val ivl: Int,
    /**
     * A manual change (reset, "set due date", FSRS reschedule). Its `ivl` is the card's
     * interval rather than the days until it is due, so the due day is unknown.
     */
    val isManual: Boolean = false,
)

/**
 * Days in `[startDate, today)` with no reviews and no review card due, given each card's
 * chronological [history]. A card counts as due from the day a review scheduled it for
 * until its next revlog row. After a manual change, it is treated as not due until its
 * next row: the true due day is unknown, so this leans towards keeping the streak. After
 * its final row, a card is due from [pendingFrom] if it has an entry there.
 * Days before the deck's first review are never rest days.
 */
internal fun computeRestDays(
    history: Map<Long, List<ScheduledReview>>,
    pendingFrom: Map<Long, LocalDate>,
    counts: Map<LocalDate, Int>,
    startDate: LocalDate,
    today: LocalDate,
): Set<LocalDate> {
    val firstReview = history.values.mapNotNull { it.firstOrNull()?.date }.minOrNull() ?: return emptySet()
    val dueDays = HashSet<LocalDate>()

    fun markDue(
        from: LocalDate,
        until: LocalDate,
    ) {
        var day = maxOf(from, startDate)
        while (day < until && day < today) {
            dueDays.add(day)
            day = day.plusDays(1)
        }
    }
    for (reviews in history.values) {
        reviews.zipWithNext { review, next ->
            dueFrom(review)?.let { markDue(it, next.date) }
        }
    }
    for (from in pendingFrom.values) markDue(from, today)

    val restDays = HashSet<LocalDate>()
    var day = maxOf(startDate, firstReview)
    while (day < today) {
        if ((counts[day] ?: 0) == 0 && day !in dueDays) restDays.add(day)
        day = day.plusDays(1)
    }
    return restDays
}

/** The day [review] made its card due, or null if it left the card not due (or unknown). */
private fun dueFrom(review: ScheduledReview): LocalDate? =
    when {
        review.isManual -> null
        review.ivl > 0 -> review.date.plusDays(review.ivl.toLong())
        review.ivl < 0 -> review.date.plusDays(1) // unfinished learning step
        else -> null
    }

private fun computeCurrentStreak(
    counts: Map<LocalDate, Int>,
    restDays: Set<LocalDate>,
    startDate: LocalDate,
    today: LocalDate,
): Int {
    // If nothing has been studied yet today, the streak can still be "alive" from yesterday.
    var day = if ((counts[today] ?: 0) > 0) today else today.minusDays(1)
    var streak = 0
    while (day >= startDate) {
        when {
            (counts[day] ?: 0) > 0 -> streak++
            day !in restDays -> break
        }
        day = day.minusDays(1)
    }
    return streak
}

private fun computeLongestStreak(
    counts: Map<LocalDate, Int>,
    restDays: Set<LocalDate>,
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
        } else if (day !in restDays) {
            run = 0
        }
        day = day.plusDays(1)
    }
    return longest
}

/** `revlog.type` of manual changes: reset to new and "set due date". */
private const val REVLOG_MANUAL = 4

/** `revlog.type` of bulk reschedules, e.g. by FSRS after its parameters change. */
private const val REVLOG_RESCHEDULED = 5

/** ~6 months, which fits comfortably in the study-options side pane on tablets. */
const val DEFAULT_HEATMAP_WEEKS = 26

/** ~5 weeks of forward scheduling, enough to see upcoming load without dwarfing the history. */
const val DEFAULT_FORECAST_WEEKS = 5
