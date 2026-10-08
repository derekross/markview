package com.derekross.markview.feature.reader

import com.derekross.markview.core.markdown.MarkdownParser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ReadAloudTest {
    private val parser = MarkdownParser()

    @Test
    fun utterancesFollowBlocksAndSkipCodeAndMath() {
        val doc = parser.parse(
            """
            # Title

            First paragraph.

            ```kotlin
            val x = 1
            ```

            $$ x^2 $$

            - one
            - two

            > [!TIP]
            > Be kind.
            """.trimIndent(),
        )
        val utterances = ReadAloudController.buildUtterances(doc)
        assertEquals(listOf(0, 1, 4, 5), utterances.map { it.blockIndex })
        assertEquals("Title.", utterances[0].text)
        assertEquals("one. two.", utterances[2].text)
        assertEquals("Tip. Be kind.", utterances[3].text)
    }

    @Test
    fun longBlocksAreSplitOnSentences() {
        val text = (1..50).joinToString(" ") { "Sentence number $it is here." }
        val chunks = ReadAloudController.chunk(text, maxLength = 120)
        assertTrue(chunks.all { it.length <= 120 })
        assertTrue(chunks.all { it.endsWith(".") })
        assertEquals(text, chunks.joinToString(" "))
    }

    @Test
    fun tablesAreReadRowByRow() {
        val doc = parser.parse("| a | b |\n|---|---|\n| 1 | 2 |\n")
        assertEquals("a, b. 1, 2.", ReadAloudController.buildUtterances(doc).single().text)
    }
}
