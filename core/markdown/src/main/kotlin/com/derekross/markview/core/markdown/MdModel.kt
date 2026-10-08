package com.derekross.markview.core.markdown

/**
 * Immutable, UI-agnostic representation of a parsed Markdown document.
 *
 * The reader renders [blocks] one per lazy list item, so every top-level block index is a
 * stable scroll anchor used for the table of contents, reading progress and position memory.
 */
data class MdDocument(
    val blocks: List<MdBlock>,
    val frontMatter: Map<String, List<String>>,
    val headings: List<HeadingRef>,
    val footnotes: Map<String, MdFootnote>,
    val stats: DocumentStats,
) {
    /** Best guess at a human title: front matter `title`, else the first H1, else null. */
    val title: String?
        get() = frontMatter["title"]?.firstOrNull()?.takeIf { it.isNotBlank() }
            ?: headings.firstOrNull { it.level == 1 }?.text

    /** Index into [blocks] for a heading anchor (GitHub-style slug), or null. */
    fun blockIndexForAnchor(anchor: String): Int? =
        headings.firstOrNull { it.anchor.equals(anchor.removePrefix("#"), ignoreCase = true) }?.blockIndex

    /** The heading that owns the given block index (the last heading at or before it). */
    fun headingForBlock(blockIndex: Int): HeadingRef? = headings.lastOrNull { it.blockIndex <= blockIndex }

    companion object {
        val Empty = MdDocument(emptyList(), emptyMap(), emptyList(), emptyMap(), DocumentStats(0, 0))
    }
}

data class HeadingRef(val blockIndex: Int, val level: Int, val text: String, val anchor: String)

data class MdFootnote(val label: String, val number: Int, val blocks: List<MdBlock>)

data class DocumentStats(val wordCount: Int, val readingMinutes: Int) {
    companion object {
        const val WORDS_PER_MINUTE = 230

        fun fromWords(words: Int) = DocumentStats(
            wordCount = words,
            readingMinutes = if (words == 0) 0 else maxOf(1, (words + WORDS_PER_MINUTE / 2) / WORDS_PER_MINUTE),
        )
    }
}

sealed interface MdBlock {
    data class Heading(val level: Int, val content: List<MdInline>, val text: String, val anchor: String) : MdBlock
    data class Paragraph(val content: List<MdInline>) : MdBlock
    data class Quote(val blocks: List<MdBlock>) : MdBlock
    data class Callout(val type: CalloutType, val title: String?, val blocks: List<MdBlock>) : MdBlock
    data class ListBlock(val ordered: Boolean, val start: Int, val tight: Boolean, val items: List<ListItem>) : MdBlock
    data class CodeBlock(val language: String?, val code: String) : MdBlock
    data class MathBlock(val tex: String) : MdBlock
    data class Table(val header: List<TableCell>, val rows: List<List<TableCell>>) : MdBlock
    data class Image(val url: String, val alt: String, val title: String?, val link: String? = null) : MdBlock
    data class Html(val raw: String) : MdBlock
    data object ThematicBreak : MdBlock
}

data class ListItem(val checked: Boolean?, val blocks: List<MdBlock>)

data class TableCell(val content: List<MdInline>, val alignment: CellAlignment)

enum class CellAlignment { Start, Center, End }

enum class CalloutType(val label: String) {
    Note("Note"), Tip("Tip"), Important("Important"), Warning("Warning"), Caution("Caution");

    companion object {
        fun from(raw: String): CalloutType = entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: Note
    }
}

sealed interface MdInline {
    data class Text(val text: String) : MdInline
    data class Emphasis(val children: List<MdInline>) : MdInline
    data class Strong(val children: List<MdInline>) : MdInline
    data class Strikethrough(val children: List<MdInline>) : MdInline
    data class Code(val code: String) : MdInline
    data class Link(val destination: String, val title: String?, val children: List<MdInline>) : MdInline
    data class Image(val url: String, val alt: String, val title: String?) : MdInline
    data class FootnoteRef(val label: String, val number: Int) : MdInline
    data class Math(val tex: String) : MdInline
    data class Html(val raw: String) : MdInline
    data object SoftBreak : MdInline
    data object HardBreak : MdInline
}

/** Flattens inline content to plain text (used for headings, alt text, search and TTS). */
fun List<MdInline>.plainText(): String = buildString { appendPlain(this@plainText) }

private fun StringBuilder.appendPlain(inlines: List<MdInline>) {
    for (inline in inlines) when (inline) {
        is MdInline.Text -> append(inline.text)
        is MdInline.Emphasis -> appendPlain(inline.children)
        is MdInline.Strong -> appendPlain(inline.children)
        is MdInline.Strikethrough -> appendPlain(inline.children)
        is MdInline.Code -> append(inline.code)
        is MdInline.Link -> appendPlain(inline.children)
        is MdInline.Image -> append(inline.alt)
        is MdInline.FootnoteRef -> Unit
        is MdInline.Math -> append(inline.tex)
        is MdInline.Html -> Unit
        MdInline.SoftBreak -> append(' ')
        MdInline.HardBreak -> append('\n')
    }
}

/** Plain text of a block, recursively (used for search and word counts). */
fun MdBlock.plainText(): String = when (this) {
    is MdBlock.Heading -> text
    is MdBlock.Paragraph -> content.plainText()
    is MdBlock.Quote -> blocks.joinToString("\n") { it.plainText() }
    is MdBlock.Callout -> listOfNotNull(title, blocks.joinToString("\n") { it.plainText() }).joinToString("\n")
    is MdBlock.ListBlock -> items.joinToString("\n") { item -> item.blocks.joinToString("\n") { it.plainText() } }
    is MdBlock.CodeBlock -> code
    is MdBlock.MathBlock -> tex
    is MdBlock.Table -> (listOf(header) + rows).joinToString("\n") { row -> row.joinToString("\t") { it.content.plainText() } }
    is MdBlock.Image -> alt
    is MdBlock.Html -> ""
    MdBlock.ThematicBreak -> ""
}
