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

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.RobolectricTest
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.test.assertNull

@RunWith(AndroidJUnit4::class)
class ReviewHeatmapDataTest : RobolectricTest() {
    @Test
    fun `review before rollover counts toward the previous Anki day`() {
        val rollover = col.getPreferences().scheduling.rollover
        assumeTrue("needs a non-midnight rollover", rollover > 0)
        val today = ankiToday()

        // Wall-clock "today" at (rollover - 30 min), e.g. 03:30 with the default 4 AM rollover.
        // This is always in the past, and belongs to the Anki day before `today`.
        val card = addBasicNote().firstCard()
        val reviewMs =
            today
                .atTime(rollover - 1, 30)
                .atZone(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()
        insertReview(reviewMs, card.id)

        val data = col.fetchReviewHeatmapData(card.did)

        assertEquals(1, data.countsByDate[today.minusDays(1)])
        assertNull(data.countsByDate[today])
    }

    @Test
    fun `review just after rollover counts toward that Anki day`() {
        val rollover = col.getPreferences().scheduling.rollover
        val today = ankiToday()
        val yesterday: LocalDate = today.minusDays(1)

        val card = addBasicNote().firstCard()
        val reviewMs =
            yesterday
                .atTime(rollover, 0)
                .atZone(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()
        insertReview(reviewMs, card.id)

        val data = col.fetchReviewHeatmapData(card.did)

        assertEquals(1, data.countsByDate[yesterday])
    }

    @Test
    fun `day with no reviews due keeps the streak alive without counting`() {
        val today = ankiToday()
        val card = addBasicNote().firstCard()
        // Reviewed 3 days ago and scheduled 2 days out, so nothing was due 2 days ago.
        insertReview(reviewMs(today.minusDays(3)), card.id, ivl = 2)
        insertReview(reviewMs(today.minusDays(1)), card.id, ivl = 10)

        val data = col.fetchReviewHeatmapData(card.did)

        assertEquals(2, data.currentStreak)
        assertEquals(2, data.longestStreak)
    }

    @Test
    fun `day with a review due but not studied breaks the streak`() {
        val today = ankiToday()
        val card = addBasicNote().firstCard()
        // Reviewed 3 days ago and due again 2 days ago, but skipped that day.
        insertReview(reviewMs(today.minusDays(3)), card.id, ivl = 1)
        insertReview(reviewMs(today.minusDays(1)), card.id, ivl = 10)

        val data = col.fetchReviewHeatmapData(card.did)

        assertEquals(1, data.currentStreak)
        assertEquals(1, data.longestStreak)
    }

    @Test
    fun `rest days bridge the longest streak`() {
        val today = ankiToday()
        val card = addBasicNote().firstCard()
        insertReview(reviewMs(today.minusDays(10)), card.id, ivl = 2)
        // today - 9 is a rest day
        insertReview(reviewMs(today.minusDays(8)), card.id, ivl = 1)
        insertReview(reviewMs(today.minusDays(7)), card.id, ivl = 1)
        // today - 6 had a review due but was skipped: the streak above ends at 3
        insertReview(reviewMs(today.minusDays(5)), card.id, ivl = 30)

        val data = col.fetchReviewHeatmapData(card.did)

        assertEquals(3, data.longestStreak)
        // Nothing has been due since today - 5, so the streak is still alive.
        assertEquals(1, data.currentStreak)
    }

    @Test
    fun `rest days exclude studied days, today and days before the first review`() {
        val today = ankiToday()
        val card = addBasicNote().firstCard()
        insertReview(reviewMs(today.minusDays(3)), card.id, ivl = 30)

        val data = col.fetchReviewHeatmapData(card.did)

        assertEquals(setOf(today.minusDays(2), today.minusDays(1)), data.restDays)
    }

    @Test
    fun `overdue review card is not a rest day`() {
        val today = ankiToday()
        val card = addBasicNote().firstCard()
        insertReview(reviewMs(today.minusDays(4)), card.id, ivl = 1)
        makeReviewCard(card.id, dueDaysAgo = 3)

        val data = col.fetchReviewHeatmapData(card.did)

        assertEquals(emptySet<LocalDate>(), data.restDays)
    }

    @Test
    fun `unfinished learning card is not a rest day`() {
        val today = ankiToday()
        val card = addBasicNote().firstCard()
        insertReview(reviewMs(today.minusDays(3)), card.id, ivl = -600, type = 0)
        col.db.execute("UPDATE cards SET type = 1, queue = 1, due = 0 WHERE id = ?", card.id)

        val data = col.fetchReviewHeatmapData(card.did)

        assertEquals(emptySet<LocalDate>(), data.restDays)
    }

    @Test
    fun `set due date uses the card's actual due day`() {
        val today = ankiToday()
        val card = addBasicNote().firstCard()
        insertReview(reviewMs(today.minusDays(10)), card.id, ivl = 30)
        // "Set due date" logs the card's interval, not the days until it is due.
        insertReview(reviewMs(today.minusDays(9)), card.id, ivl = 30, ease = 0, type = 4)
        makeReviewCard(card.id, dueDaysAgo = 5)

        val data = col.fetchReviewHeatmapData(card.did)

        assertEquals((6..9).map { today.minusDays(it.toLong()) }.toSet(), data.restDays)
    }

    @Test
    fun `card in a filtered deck still counts for its home deck`() {
        val today = ankiToday()
        val card = addBasicNote().firstCard()
        val homeDeck = card.did
        insertReview(reviewMs(today.minusDays(4)), card.id, ivl = 1)
        makeReviewCard(card.id, dueDaysAgo = 3)
        val filtered = addDeck("Filtered")
        col.db.execute(
            "UPDATE cards SET did = ?, odid = ?, odue = due, due = -100000 WHERE id = ?",
            filtered,
            homeDeck,
            card.id,
        )

        val data = col.fetchReviewHeatmapData(homeDeck)

        assertEquals(1, data.countsByDate[today.minusDays(4)])
        assertEquals(emptySet<LocalDate>(), data.restDays)
    }

    @Test
    fun `reset to new ends the due run`() {
        val today = ankiToday()
        val card = addBasicNote().firstCard()
        insertReview(reviewMs(today.minusDays(5)), card.id, ivl = 1)
        insertReview(reviewMs(today.minusDays(5)) + 1000, card.id, ivl = 0, ease = 0, type = 4)

        val data = col.fetchReviewHeatmapData(card.did)

        assertEquals((1..4).map { today.minusDays(it.toLong()) }.toSet(), data.restDays)
    }

    @Test
    fun `filtered preview does not end the due run`() {
        val today = ankiToday()
        val card = addBasicNote().firstCard()
        insertReview(reviewMs(today.minusDays(6)), card.id, ivl = 2)
        // Previewing in a filtered deck without rescheduling leaves the due date alone.
        insertReview(reviewMs(today.minusDays(5)), card.id, ivl = 0, type = 3)
        insertReview(reviewMs(today.minusDays(2)), card.id, ivl = 30)

        val data = col.fetchReviewHeatmapData(card.did)

        assertEquals(setOf(today.minusDays(1)), data.restDays)
    }

    @Test
    fun `review before the heatmap window still schedules days inside it`() {
        val today = ankiToday()
        val card = addBasicNote().firstCard()
        val start = col.fetchReviewHeatmapData(card.did).startDate
        insertReview(reviewMs(start.minusDays(5)), card.id, ivl = 10)
        insertReview(reviewMs(start.plusDays(8)), card.id, ivl = 1000)

        val data = col.fetchReviewHeatmapData(card.did)

        // Due from start + 5 until it was reviewed at start + 8.
        val restDays =
            (0..4).map { start.plusDays(it.toLong()) }.toSet() +
                generateSequence(start.plusDays(9)) { it.plusDays(1) }.takeWhile { it < today }
        assertEquals(restDays, data.restDays)
    }

    private fun makeReviewCard(
        cardId: Long,
        dueDaysAgo: Int,
    ) {
        col.db.execute(
            "UPDATE cards SET type = 2, queue = 2, ivl = 1, due = ? WHERE id = ?",
            col.sched.today - dueDaysAgo,
            cardId,
        )
    }

    private fun ankiToday(): LocalDate {
        val rollover = col.getPreferences().scheduling.rollover
        val now = LocalDateTime.now()
        return if (now.hour < rollover) now.toLocalDate().minusDays(1) else now.toLocalDate()
    }

    /** Half an hour after the rollover on [day], so it is unambiguously that Anki day. */
    private fun reviewMs(day: LocalDate): Long =
        day
            .atTime(col.getPreferences().scheduling.rollover, 30)
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()

    private fun insertReview(
        epochMs: Long,
        cardId: Long,
        ivl: Int = 1,
        ease: Int = 3,
        type: Int = 1,
    ) {
        col.db.execute(
            "INSERT INTO revlog (id, cid, usn, ease, ivl, lastIvl, factor, time, type) " +
                "VALUES (?, ?, -1, ?, ?, 0, 2500, 1000, ?)",
            epochMs,
            cardId,
            ease,
            ivl,
            type,
        )
    }
}
