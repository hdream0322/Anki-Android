// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.ai

import anki.cards.FsrsMemoryState
import anki.stats.CardStatsResponse
import anki.stats.CardStatsResponse.StatsRevlogEntry
import anki.stats.RevlogEntry.ReviewKind
import org.junit.Assert.assertEquals
import org.junit.Test

class CardStudyStatsTest {
    private val now = 100L * DAY

    @Test
    fun `formats an SM-2 card with its ease`() {
        val stats =
            CardStatsResponse
                .newBuilder()
                .setCardType("Review")
                .setReviews(12)
                .setLapses(2)
                .setInterval(34)
                .setEase(2500)
                .build()

        assertEquals(
            """
            <study_stats>
            card_type: Review
            reviews: 12
            lapses: 2
            interval_days: 34
            ease: 250%
            </study_stats>
            """.trimIndent(),
            CardStudyStats.format(stats, now),
        )
    }

    @Test
    fun `formats FSRS memory state instead of ease`() {
        val stats =
            CardStatsResponse
                .newBuilder()
                .setCardType("Review")
                .setReviews(5)
                .setLapses(0)
                .setInterval(20)
                .setEase(2500)
                .setMemoryState(FsrsMemoryState.newBuilder().setStability(41.24f).setDifficulty(6.31f))
                .setFsrsRetrievability(0.873f)
                .setDesiredRetention(0.9f)
                .build()

        assertEquals(
            """
            <study_stats>
            card_type: Review
            reviews: 5
            lapses: 0
            interval_days: 20
            fsrs_stability_days: 41.2
            fsrs_difficulty: 6.3 / 10
            fsrs_recall_probability_now: 87%
            desired_retention: 90%
            </study_stats>
            """.trimIndent(),
            CardStudyStats.format(stats, now),
        )
    }

    @Test
    fun `lists the latest answers newest first and skips manual entries`() {
        val stats =
            CardStatsResponse
                .newBuilder()
                .setCardType("Review")
                .setReviews(4)
                .setLapses(1)
                .setInterval(3)
                .setEase(2300)
                .addRevlog(answer(now - 40 * DAY, button = 3))
                .addRevlog(answer(now - 3 * DAY, button = 1))
                .addRevlog(
                    StatsRevlogEntry
                        .newBuilder()
                        .setTime(now - 2 * DAY)
                        .setReviewKind(ReviewKind.MANUAL)
                        .setButtonChosen(0),
                ).addRevlog(answer(now, button = 4))
                .build()

        assertEquals(
            """
            <study_stats>
            card_type: Review
            reviews: 4
            lapses: 1
            interval_days: 3
            ease: 230%
            recent_answers: today Easy; 3 days ago Again; 40 days ago Good
            </study_stats>
            """.trimIndent(),
            CardStudyStats.format(stats, now),
        )
    }

    @Test
    fun `keeps only the five most recent answers`() {
        val builder = CardStatsResponse.newBuilder().setCardType("Review").setEase(2500)
        (1..7).forEach { builder.addRevlog(answer(now - it * DAY, button = 3)) }

        val recent = CardStudyStats.format(builder.build(), now).lines().single { it.startsWith("recent_answers") }

        assertEquals("recent_answers: 1 day ago Good; 2 days ago Good; 3 days ago Good; 4 days ago Good; 5 days ago Good", recent)
    }

    private fun answer(
        time: Long,
        button: Int,
    ) = StatsRevlogEntry
        .newBuilder()
        .setTime(time)
        .setReviewKind(ReviewKind.REVIEW)
        .setButtonChosen(button)

    private companion object {
        const val DAY = 86_400L
    }
}
