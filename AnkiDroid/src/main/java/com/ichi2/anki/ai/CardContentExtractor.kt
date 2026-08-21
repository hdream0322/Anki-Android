package com.ichi2.anki.ai

import com.ichi2.anki.backend.stripHTMLAndSpecialFields

/**
 * Converts a card's rendered HTML (question/answer) into plain text suitable for
 * sending to an LLM. Images and audio are omitted entirely rather than described,
 * per the design decision to keep v1 text-only.
 */
object CardContentExtractor {
    fun extract(
        questionHtml: String,
        answerHtml: String,
    ): String {
        val question = clean(questionHtml)
        val answer = clean(answerHtml)
        return "Front: $question\nBack: $answer"
    }

    private fun clean(html: String): String =
        stripHTMLAndSpecialFields(html)
            .replace(Regex("\\s+"), " ")
            .trim()
}
