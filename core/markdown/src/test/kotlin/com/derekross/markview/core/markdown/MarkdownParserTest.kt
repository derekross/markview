package com.derekross.markview.core.markdown

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MarkdownParserTest {
    private val parser = MarkdownParser()

    @Test
    fun headingsGetGithubStyleUniqueAnchors() {
        val doc = parser.parse("# Hello, World!\n\n## Setup\n\ntext\n\n## Setup\n")
        assertEquals(listOf("hello-world", "setup", "setup-1"), doc.headings.map { it.anchor })
        assertEquals(listOf(0, 1, 3), doc.headings.map { it.blockIndex })
        assertEquals(3, doc.blockIndexForAnchor("#setup-1"))
        assertEquals("Hello, World!", doc.title)
    }

    @Test
    fun frontMatterProvidesTitle() {
        val doc = parser.parse("---\ntitle: My Post\ntags:\n  - a\n  - b\n---\n\n# Heading\n")
        assertEquals("My Post", doc.title)
        assertEquals(listOf("a", "b"), doc.frontMatter["tags"])
        assertIs<MdBlock.Heading>(doc.blocks.first())
    }

    @Test
    fun gfmTablesKeepAlignmentAndPadRows() {
        val doc = parser.parse("| a | b | c |\n|:--|:-:|--:|\n| 1 | 2 |\n")
        val table = assertIs<MdBlock.Table>(doc.blocks.single())
        assertEquals(listOf(CellAlignment.Start, CellAlignment.Center, CellAlignment.End), table.header.map { it.alignment })
        assertEquals(3, table.rows.single().size)
    }

    @Test
    fun taskListItemsAreDetected() {
        val doc = parser.parse("- [x] done\n- [ ] todo\n- plain\n")
        val list = assertIs<MdBlock.ListBlock>(doc.blocks.single())
        assertEquals(listOf(true, false, null), list.items.map { it.checked })
        assertEquals("done", list.items.first().blocks.single().plainText().trim())
    }

    @Test
    fun alertsBecomeCallouts() {
        val doc = parser.parse("> [!WARNING]\n> Be careful.\n")
        val callout = assertIs<MdBlock.Callout>(doc.blocks.single())
        assertEquals(CalloutType.Warning, callout.type)
        assertEquals("Be careful.", callout.blocks.single().plainText())
    }

    @Test
    fun plainQuotesStayQuotes() {
        val doc = parser.parse("> just a quote\n")
        assertIs<MdBlock.Quote>(doc.blocks.single())
    }

    @Test
    fun inlineAndDisplayMath() {
        val doc = parser.parse("Euler: \$e^{i\\pi}+1=0\$ costs \$5 and \$10.\n\n\$\$\n\\int_0^1 x\\,dx\n\$\$\n")
        val para = assertIs<MdBlock.Paragraph>(doc.blocks[0])
        val maths = para.content.filterIsInstance<MdInline.Math>()
        assertEquals(listOf("e^{i\\pi}+1=0"), maths.map { it.tex })
        assertTrue(para.content.plainText().contains("\$5 and \$10"))
        assertEquals("\\int_0^1 x\\,dx", assertIs<MdBlock.MathBlock>(doc.blocks[1]).tex)
    }

    @Test
    fun singleLineDisplayMath() {
        val doc = parser.parse("\$\$ a^2 + b^2 = c^2 \$\$\n\nafter\n")
        assertEquals("a^2 + b^2 = c^2", assertIs<MdBlock.MathBlock>(doc.blocks[0]).tex)
        assertIs<MdBlock.Paragraph>(doc.blocks[1])
    }

    @Test
    fun fencedMathAndCodeLanguages() {
        val doc = parser.parse("```math\nx^2\n```\n\n```Kotlin title=\"a\"\nval x = 1\n```\n")
        assertEquals("x^2", assertIs<MdBlock.MathBlock>(doc.blocks[0]).tex)
        val code = assertIs<MdBlock.CodeBlock>(doc.blocks[1])
        assertEquals("kotlin", code.language)
        assertEquals("val x = 1", code.code)
    }

    @Test
    fun standaloneImagesArePromotedToBlocks() {
        val doc = parser.parse("![alt text](pic.png \"T\")\n\n[![badge](b.svg)](https://x.y)\n")
        val img = assertIs<MdBlock.Image>(doc.blocks[0])
        assertEquals("pic.png", img.url)
        assertEquals("alt text", img.alt)
        val linked = assertIs<MdBlock.Image>(doc.blocks[1])
        assertEquals("https://x.y", linked.link)
    }

    @Test
    fun htmlImageRowsBecomeInlineImages() {
        val html = "<p align=\"center\">\n<a href=\"https://ci\"><img src=\"ci.svg\" alt=\"CI\"></a>\n<img src=\"lic.svg\">\n</p>\n"
        val para = assertIs<MdBlock.Paragraph>(parser.parse(html).blocks.single())
        val link = assertIs<MdInline.Link>(para.content.first())
        assertEquals("https://ci", link.destination)
        assertEquals("lic.svg", para.content.filterIsInstance<MdInline.Image>().single().url)
    }

    @Test
    fun footnotesAreNumberedByFirstReference() {
        val doc = parser.parse("A[^b] and B[^a].\n\n[^a]: First def.\n[^b]: Second def.\n")
        val refs = assertIs<MdBlock.Paragraph>(doc.blocks.single()).content.filterIsInstance<MdInline.FootnoteRef>()
        assertEquals(listOf("b" to 1, "a" to 2), refs.map { it.label to it.number })
        assertEquals("Second def.", doc.footnotes.getValue("b").blocks.single().plainText())
    }

    @Test
    fun statsCountProseNotCode() {
        val doc = parser.parse("one two three\n\n```\nnot counted here\n```\n")
        assertEquals(3, doc.stats.wordCount)
        assertEquals(1, doc.stats.readingMinutes)
        assertNull(MdDocument.Empty.title)
    }

    @Test
    fun strikethroughAndAutolinks() {
        val doc = parser.parse("~~gone~~ visit https://example.com now")
        val content = assertIs<MdBlock.Paragraph>(doc.blocks.single()).content
        assertIs<MdInline.Strikethrough>(content.first())
        assertEquals("https://example.com", content.filterIsInstance<MdInline.Link>().single().destination)
    }
}
