// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.ai.chat

import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.not
import org.junit.Test

class ChatMarkdownTest {
    @Test
    fun `plain text becomes a paragraph`() {
        assertThat(ChatMarkdown.toHtml("just plain text").trim(), equalTo("<p>just plain text</p>"))
    }

    @Test
    fun `block level markdown is rendered`() {
        val html = ChatMarkdown.toHtml("# Title\n\n- one\n- two\n\n```\ncode\n```")

        assertThat(html, containsString("<h1>Title</h1>"))
        assertThat(html, containsString("<li>one</li>"))
        assertThat(html, containsString("<pre><code>code\n</code></pre>"))
    }

    @Test
    fun `tables and strikethrough are rendered`() {
        val html = ChatMarkdown.toHtml("| a | b |\n|---|---|\n| 1 | 2 |\n\n~~gone~~")

        assertThat(html, containsString("<table>"))
        assertThat(html, containsString("<td>1</td>"))
        assertThat(html, containsString("<del>gone</del>"))
    }

    @Test
    fun `single newlines are kept as line breaks`() {
        assertThat(ChatMarkdown.toHtml("line one\nline two"), containsString("line one<br>"))
    }

    @Test
    fun `raw html is escaped`() {
        val html = ChatMarkdown.toHtml("<script>alert(1)</script> <img src=x onerror=alert(1)>")

        assertThat(html, not(containsString("<script>")))
        assertThat(html, not(containsString("<img")))
        assertThat(html, containsString("&lt;script&gt;"))
    }

    @Test
    fun `javascript links are neutralised`() {
        assertThat(ChatMarkdown.toHtml("[x](javascript:alert(1))"), not(containsString("javascript:")))
    }

    @Test
    fun `inline dollar math is normalised to MathJax inline delimiters`() {
        assertThat(ChatMarkdown.toHtml("area is \$\\pi r^2\$ here"), containsString("area is \\(\\pi r^2\\) here"))
    }

    @Test
    fun `display dollar math is normalised to MathJax display delimiters`() {
        assertThat(ChatMarkdown.toHtml("\$\$\n\\int_0^1 x\\,dx\n\$\$"), containsString("\\[\n\\int_0^1 x\\,dx\n\\]"))
    }

    @Test
    fun `backslash delimited math survives markdown escaping`() {
        val html = ChatMarkdown.toHtml("inline \\(a_1\\) and display \\[b_2\\]")

        assertThat(html, containsString("\\(a_1\\)"))
        assertThat(html, containsString("\\[b_2\\]"))
    }

    @Test
    fun `markdown emphasis characters inside math are left alone`() {
        assertThat(ChatMarkdown.toHtml("\$a_1 * b_2 * c\$"), containsString("\\(a_1 * b_2 * c\\)"))
    }

    @Test
    fun `html special characters inside math are escaped`() {
        assertThat(ChatMarkdown.toHtml("\$a < b\$"), containsString("\\(a &lt; b\\)"))
    }

    @Test
    fun `dollar amounts are not treated as math`() {
        val html = ChatMarkdown.toHtml("costs \$5 and \$10")

        assertThat(html, containsString("costs \$5 and \$10"))
        assertThat(html, not(containsString("\\(")))
    }

    @Test
    fun `math syntax inside code is left as code`() {
        val html = ChatMarkdown.toHtml("`\$x\$` and\n\n```\n\$\$y\$\$\n```")

        assertThat(html, containsString("<code>\$x\$</code>"))
        assertThat(html, containsString("<pre><code>\$\$y\$\$\n</code></pre>"))
    }
}
