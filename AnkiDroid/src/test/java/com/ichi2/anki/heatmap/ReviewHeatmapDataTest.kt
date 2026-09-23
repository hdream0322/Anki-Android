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
import org.junit.Assert.assertNull
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

@RunWith(AndroidJUnit4::class)
class ReviewHeatmapDataTest : RobolectricTest() {
    @Test
    fun `review before rollover counts toward the previous Anki day`() {
        val rollover = col.getPreferences().scheduling.rollover
        assumeTrue("needs a non-midnight rollover", rollover > 0)
        val now = LocalDateTime.now()
        val today = if (now.hour < rollover) now.toLocalDate().minusDays(1) else now.toLocalDate()

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
        val now = LocalDateTime.now()
        val today = if (now.hour < rollover) now.toLocalDate().minusDays(1) else now.toLocalDate()
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

    private fun insertReview(
        epochMs: Long,
        cardId: Long,
    ) {
        col.db.execute(
            "INSERT INTO revlog (id, cid, usn, ease, ivl, lastIvl, factor, time, type) " +
                "VALUES (?, ?, -1, 3, 1, 0, 2500, 1000, 1)",
            epochMs,
            cardId,
        )
    }
}
