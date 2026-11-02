// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.ai

import anki.stats.CardStatsResponse
import anki.stats.RevlogEntry.ReviewKind
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Turns a card's scheduling stats into a `<study_stats>` block for the AI chat prompt,
 * so the tutor can tell a card that keeps slipping from one the student knows well.
 */
object CardStudyStats {
    private const val SECONDS_PER_DAY = 86_400L
    private const val RECENT_ANSWER_COUNT = 5

    /** Kinds that record a real answer; manual and rescheduled entries have no button press. */
    private val ANSWER_KINDS = setOf(ReviewKind.LEARNING, ReviewKind.REVIEW, ReviewKind.RELEARNING, ReviewKind.FILTERED)

    fun format(
        stats: CardStatsResponse,
        nowSeconds: Long,
    ): String =
        buildString {
            appendLine("<study_stats>")
            appendLine("card_type: ${stats.cardType}")
            appendLine("reviews: ${stats.reviews}")
            appendLine("lapses: ${stats.lapses}")
            appendLine("interval_days: ${stats.interval}")
            if (stats.hasMemoryState()) {
                // FSRS ignores ease, so it would only mislead.
                appendLine("fsrs_stability_days: ${oneDecimal(stats.memoryState.stability)}")
                appendLine("fsrs_difficulty: ${oneDecimal(stats.memoryState.difficulty)} / 10")
                if (stats.hasFsrsRetrievability()) appendLine("fsrs_recall_probability_now: ${percent(stats.fsrsRetrievability)}")
                if (stats.hasDesiredRetention()) appendLine("desired_retention: ${percent(stats.desiredRetention)}")
            } else if (stats.ease > 0) {
                appendLine("ease: ${stats.ease / 10}%")
            }
            recentAnswers(stats, nowSeconds)?.let { appendLine("recent_answers: $it") }
            append("</study_stats>")
        }

    private fun recentAnswers(
        stats: CardStatsResponse,
        nowSeconds: Long,
    ): String? =
        stats.revlogList
            .filter { it.reviewKind in ANSWER_KINDS && it.buttonChosen in 1..4 }
            .sortedByDescending { it.time }
            .take(RECENT_ANSWER_COUNT)
            .takeIf { it.isNotEmpty() }
            ?.joinToString("; ") { "${daysAgo(nowSeconds - it.time)} ${buttonName(it.buttonChosen)}" }

    private fun daysAgo(elapsedSeconds: Long): String =
        when (val days = elapsedSeconds / SECONDS_PER_DAY) {
            0L -> "today"
            1L -> "1 day ago"
            else -> "$days days ago"
        }

    private fun buttonName(button: Int) =
        when (button) {
            1 -> "Again"
            2 -> "Hard"
            3 -> "Good"
            else -> "Easy"
        }

    private fun oneDecimal(value: Float) = String.format(Locale.US, "%.1f", value)

    private fun percent(fraction: Float) = "${(fraction * 100).roundToInt()}%"
}
