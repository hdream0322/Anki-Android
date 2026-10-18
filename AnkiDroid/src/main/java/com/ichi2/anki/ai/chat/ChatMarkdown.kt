// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.ai.chat

import org.commonmark.ext.gfm.strikethrough.StrikethroughExtension
import org.commonmark.ext.gfm.tables.TablesExtension
import org.commonmark.parser.Parser
import org.commonmark.renderer.html.HtmlRenderer

/**
 * Converts an LLM reply (Markdown with embedded LaTeX) into HTML for the chat WebView.
 *
 * Math is lifted out before Markdown parsing, so that `_`, `*` and `\` inside formulas are not
 * consumed as Markdown syntax, and is put back with the `\(…\)` / `\[…\]` delimiters MathJax is
 * configured for. LLMs commonly emit `$…$` and `$$…$$` as well, which are normalised to those.
 *
 * Raw HTML in the reply is escaped and unsafe link schemes are dropped: the text comes from a
 * remote model whose input includes card content, so it is not trusted.
 */
object ChatMarkdown {
    private val extensions = listOf(TablesExtension.create(), StrikethroughExtension.create())
    private val parser = Parser.builder().extensions(extensions).build()
    private val renderer =
        HtmlRenderer
            .builder()
            .extensions(extensions)
            .escapeHtml(true)
            .sanitizeUrls(true)
            // Chat replies use single newlines as line breaks
            .softbreak("<br>\n")
            .build()

    // Code alternatives come first so that math-like text inside code is matched (and kept) as code.
    // An unterminated fence runs to the end, as happens while a reply is still streaming.
    // Groups are numbered, not named: named groups need API 26.
    private val TOKEN =
        Regex(
            // 1: fenced code, 2: its marker
            """((?:^|\n)[ \t]*(```|~~~)[\s\S]*?(?:\n[ \t]*\2|$(?![\s\S])))""" +
                // 3: inline code, 4: its backticks
                """|((`+)[\s\S]+?\4)""" +
                // 5: $$display$$
                """|\$\$([\s\S]+?)\$\$""" +
                // 6: \[display\]
                """|\\\[([\s\S]+?)\\\]""" +
                // 7: \(inline\)
                """|\\\(([\s\S]+?)\\\)""" +
                // 8: $inline$, by Pandoc's rule, so that amounts such as "$5 and $10" stay as text
                """|\$(?![\s$])([^$\n]+?)(?<![\s\\])\$(?!\d)""",
        )

    private const val PLACEHOLDER_START = '\uE000'
    private const val PLACEHOLDER_END = '\uE001'
    private val PLACEHOLDER = Regex("""$PLACEHOLDER_START(\d+)$PLACEHOLDER_END""")

    fun toHtml(markdown: String): String {
        val formulas = mutableListOf<String>()
        val protected =
            TOKEN.replace(markdown) { match ->
                val groups = match.groups
                val formula =
                    (groups[5] ?: groups[6])?.let { "\\[${it.value}\\]" }
                        ?: (groups[7] ?: groups[8])?.let { "\\(${it.value}\\)" }
                        ?: return@replace match.value
                formulas.add(formula)
                "$PLACEHOLDER_START${formulas.lastIndex}$PLACEHOLDER_END"
            }
        val html = renderer.render(parser.parse(protected))
        return PLACEHOLDER.replace(html) { match -> escapeHtml(formulas[match.groupValues[1].toInt()]) }
    }

    private fun escapeHtml(text: String) =
        text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
}
