// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.ai

import com.ichi2.anki.backend.stripHTMLAndSpecialFields

/**
 * Converts a card's rendered HTML (question/answer) into plain text suitable for
 * sending to an LLM. Images and audio are omitted entirely rather than described,
 * per the design decision to keep v1 text-only.
 */
object CardContentExtractor {
    const val INSTRUCTION =
        "You are helping a student understand this Anki flashcard. Answer questions about it clearly and concisely. " +
            "Always reply in Korean, even if the card or the question is in another language."

    /** Anki's `{{FrontSide}}<hr id=answer>` convention, which marks where the back's own content starts. */
    private val ANSWER_DIVIDER = Regex("""<hr[^>]*\bid\s*=\s*["']?answer\b[^>]*>""", RegexOption.IGNORE_CASE)

    fun extract(
        questionHtml: String,
        answerHtml: String,
    ): String {
        val question = clean(questionHtml)
        val answer = backWithoutFront(question, answerHtml)
        return "$INSTRUCTION\n\nFront: $question\nBack: $answer"
    }

    /**
     * The back usually repeats the front via `{{FrontSide}}`; drop the repeat to save tokens.
     * A cloze back differs from its front, so it is kept whole.
     */
    private fun backWithoutFront(
        question: String,
        answerHtml: String,
    ): String {
        val answer = clean(answerHtml)
        val afterDivider = ANSWER_DIVIDER.find(answerHtml)?.let { clean(answerHtml.substring(it.range.last + 1)) }
        if (!afterDivider.isNullOrEmpty()) return afterDivider
        if (question.isNotEmpty() && answer.startsWith(question)) {
            answer
                .removePrefix(question)
                .trim()
                .takeIf { it.isNotEmpty() }
                ?.let { return it }
        }
        return answer
    }

    private fun clean(html: String): String =
        stripHTMLAndSpecialFields(html)
            .replace(Regex("\\s+"), " ")
            .trim()
}
