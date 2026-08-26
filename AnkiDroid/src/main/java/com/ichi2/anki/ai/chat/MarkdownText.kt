// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.ai.chat

import android.graphics.Typeface
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.StyleSpan
import android.text.style.TypefaceSpan

/**
 * Renders the small subset of Markdown emphasis (bold, italic, inline code) that LLM replies
 * commonly use, without pulling in a full Markdown parsing library. Block-level syntax (headers,
 * lists, links, code fences) is intentionally out of scope.
 */
object MarkdownText {
    private val TOKEN = Regex("""\*\*(.+?)\*\*|__(.+?)__|`([^`]+?)`|\*([^*]+?)\*|_([^_]+?)_""")

    fun render(text: String): Spanned {
        val builder = SpannableStringBuilder()
        var lastEnd = 0
        for (match in TOKEN.findAll(text)) {
            builder.append(text, lastEnd, match.range.first)

            val bold = match.groupValues[1].ifEmpty { match.groupValues[2] }
            val code = match.groupValues[3]
            val italic = match.groupValues[4].ifEmpty { match.groupValues[5] }
            when {
                bold.isNotEmpty() -> appendStyled(builder, bold, StyleSpan(Typeface.BOLD))
                code.isNotEmpty() -> appendStyled(builder, code, TypefaceSpan("monospace"))
                italic.isNotEmpty() -> appendStyled(builder, italic, StyleSpan(Typeface.ITALIC))
            }

            lastEnd = match.range.last + 1
        }
        builder.append(text, lastEnd, text.length)
        return builder
    }

    private fun appendStyled(
        builder: SpannableStringBuilder,
        content: String,
        span: Any,
    ) {
        val start = builder.length
        builder.append(content)
        builder.setSpan(span, start, builder.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    }
}
