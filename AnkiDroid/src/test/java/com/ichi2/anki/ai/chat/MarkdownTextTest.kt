// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.ai.chat

import android.graphics.Typeface
import android.text.style.StyleSpan
import android.text.style.TypefaceSpan
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.RobolectricTest
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MarkdownTextTest : RobolectricTest() {
    @Test
    fun `plain text with no markdown is unchanged`() {
        val result = MarkdownText.render("just plain text")
        assertThat(result.toString(), equalTo("just plain text"))
    }

    @Test
    fun `bold text is stripped of asterisks and styled bold`() {
        val result = MarkdownText.render("this is **important**")

        assertThat(result.toString(), equalTo("this is important"))
        val start = "this is ".length
        val end = start + "important".length
        val spans = result.getSpans(start, end, StyleSpan::class.java)
        assertThat(spans.single().style, equalTo(Typeface.BOLD))
    }

    @Test
    fun `bold text with double underscores is also recognised`() {
        val result = MarkdownText.render("this is __important__")

        assertThat(result.toString(), equalTo("this is important"))
    }

    @Test
    fun `italic text is stripped of asterisks and styled italic`() {
        val result = MarkdownText.render("this is *subtle*")

        assertThat(result.toString(), equalTo("this is subtle"))
        val start = "this is ".length
        val end = start + "subtle".length
        val spans = result.getSpans(start, end, StyleSpan::class.java)
        assertThat(spans.single().style, equalTo(Typeface.ITALIC))
    }

    @Test
    fun `inline code is stripped of backticks and styled monospace`() {
        val result = MarkdownText.render("run `gradlew build`")

        assertThat(result.toString(), equalTo("run gradlew build"))
        val start = "run ".length
        val end = start + "gradlew build".length
        val spans = result.getSpans(start, end, TypefaceSpan::class.java)
        assertThat(spans.single().family, equalTo("monospace"))
    }

    @Test
    fun `multiple markdown tokens in one string are all rendered`() {
        val result = MarkdownText.render("**bold** and *italic* and `code`")

        assertThat(result.toString(), equalTo("bold and italic and code"))
        assertThat(result.getSpans(0, "bold".length, StyleSpan::class.java).single().style, equalTo(Typeface.BOLD))
    }

    @Test
    fun `a lone unmatched asterisk is left as plain text`() {
        val result = MarkdownText.render("3 * 4 = 12")
        assertThat(result.toString(), equalTo("3 * 4 = 12"))
    }
}
