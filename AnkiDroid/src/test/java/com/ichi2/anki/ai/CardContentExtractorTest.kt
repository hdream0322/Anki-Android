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
            prompt("What is 2+2?", "4"),
            result,
        )
    }

    @Test
    fun `strips sound and image special fields`() {
        val question = "Listen: [anki:play:q:0]"
        val answer = "<img src=\"pic.jpg\">A picture"

        val result = CardContentExtractor.extract(question, answer)

        assertEquals(prompt("Listen:", "A picture"), result)
    }

    @Test
    fun `collapses repeated whitespace left by stripped tags`() {
        val question = "<div>  Multiple   spaces  </div>"
        val answer = "<div>Answer</div>"

        val result = CardContentExtractor.extract(question, answer)

        assertEquals(prompt("Multiple spaces", "Answer"), result)
    }

    @Test
    fun `drops the repeated front when the back has no answer divider`() {
        val result = CardContentExtractor.extract("<b>Capital of France?</b>", "Capital of France?<br>Paris")

        assertEquals(prompt("Capital of France?", "Paris"), result)
    }

    @Test
    fun `keeps a cloze back whole since it differs from the front`() {
        val result = CardContentExtractor.extract("The capital is [...]", "The capital is Paris")

        assertEquals(prompt("The capital is [...]", "The capital is Paris"), result)
    }

    @Test
    fun `keeps the whole back when nothing follows the divider`() {
        val result = CardContentExtractor.extract("Q", "Q<hr id=answer>")

        assertEquals(prompt("Q", "Q"), result)
    }

    @Test
    fun `instruction asks for Korean replies`() {
        assertEquals(true, CardContentExtractor.INSTRUCTION.contains("Korean"))
    }

    @Test
    fun `places the card above the instruction`() {
        val result = CardContentExtractor.extract("Q", "A")

        assertEquals(true, result.startsWith("<card>"))
        assertEquals(true, result.endsWith(CardContentExtractor.INSTRUCTION))
    }

    @Test
    fun `uses a custom instruction in place of the default`() {
        val result = CardContentExtractor.extract("Q", "A", instruction = "Explain like I'm five.")

        assertEquals(prompt("Q", "A", instruction = "Explain like I'm five."), result)
    }

    @Test
    fun `falls back to the default instruction when none is set`() {
        assertEquals(CardContentExtractor.INSTRUCTION, CardContentExtractor.instructionOrDefault(null))
    }

    @Test
    fun `falls back to the default instruction when the custom one is blank`() {
        assertEquals(CardContentExtractor.INSTRUCTION, CardContentExtractor.instructionOrDefault("  \n "))
    }

    @Test
    fun `trims a custom instruction`() {
        assertEquals("Be brief.", CardContentExtractor.instructionOrDefault("  Be brief.\n"))
    }

    @Test
    fun `includes the deck and tags when known`() {
        val result = CardContentExtractor.extract("Q", "A", deckName = "Lang::English", tags = listOf("toeic", "verb"))

        assertEquals(
            "<card>\n<deck>Lang::English</deck>\n<tags>toeic verb</tags>\n<front>Q</front>\n<back>A</back>\n</card>\n\n" +
                CardContentExtractor.INSTRUCTION,
            result,
        )
    }

    @Test
    fun `default instruction explains the study state tag`() {
        assertEquals(true, CardContentExtractor.INSTRUCTION.contains("<study_state>"))
    }

    private fun prompt(
        front: String,
        back: String,
        instruction: String = CardContentExtractor.INSTRUCTION,
    ) = "<card>\n<front>$front</front>\n<back>$back</back>\n</card>\n\n$instruction"
}
