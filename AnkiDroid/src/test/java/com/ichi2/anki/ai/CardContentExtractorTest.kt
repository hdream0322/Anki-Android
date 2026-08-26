// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.ai

import org.junit.Assert.assertEquals
import org.junit.Test

class CardContentExtractorTest {
    @Test
    fun `strips html tags from question and answer`() {
        val question = "<div style=\"text-align:center\">What is <b>2+2</b>?</div>"
        val answer = "<div>What is <b>2+2</b>?</div><hr id=answer><div>4</div>"

        val result = CardContentExtractor.extract(question, answer)

        assertEquals(
            "${CardContentExtractor.INSTRUCTION}\n\nFront: What is 2+2?\nBack: What is 2+2?4",
            result,
        )
    }

    @Test
    fun `strips sound and image special fields`() {
        val question = "Listen: [anki:play:q:0]"
        val answer = "<img src=\"pic.jpg\">A picture"

        val result = CardContentExtractor.extract(question, answer)

        assertEquals("${CardContentExtractor.INSTRUCTION}\n\nFront: Listen:\nBack: A picture", result)
    }

    @Test
    fun `collapses repeated whitespace left by stripped tags`() {
        val question = "<div>  Multiple   spaces  </div>"
        val answer = "<div>Answer</div>"

        val result = CardContentExtractor.extract(question, answer)

        assertEquals("${CardContentExtractor.INSTRUCTION}\n\nFront: Multiple spaces\nBack: Answer", result)
    }

    @Test
    fun `prepends an instruction sentence framing the assistant's role`() {
        val result = CardContentExtractor.extract("Q", "A")

        assertEquals(true, result.startsWith(CardContentExtractor.INSTRUCTION))
    }
}
