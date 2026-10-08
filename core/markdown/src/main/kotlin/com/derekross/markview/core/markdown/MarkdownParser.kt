package com.derekross.markview.core.markdown

import org.commonmark.ext.autolink.AutolinkExtension
import org.commonmark.ext.footnotes.FootnoteDefinition
import org.commonmark.ext.footnotes.FootnoteReference
import org.commonmark.ext.footnotes.FootnotesExtension
import org.commonmark.ext.front.matter.YamlFrontMatterExtension
import org.commonmark.ext.front.matter.YamlFrontMatterVisitor
import org.commonmark.ext.gfm.alerts.Alert
import org.commonmark.ext.gfm.alerts.AlertTitle
import org.commonmark.ext.gfm.alerts.AlertsExtension
import org.commonmark.ext.gfm.strikethrough.Strikethrough
import org.commonmark.ext.gfm.strikethrough.StrikethroughExtension
import org.commonmark.ext.gfm.tables.TableBlock
import org.commonmark.ext.gfm.tables.TableBody
import org.commonmark.ext.gfm.tables.TableHead
import org.commonmark.ext.gfm.tables.TableRow
import org.commonmark.ext.gfm.tables.TablesExtension
import org.commonmark.ext.task.list.items.TaskListItemMarker
import org.commonmark.ext.task.list.items.TaskListItemsExtension
import org.commonmark.node.BlockQuote
import org.commonmark.node.BulletList
import org.commonmark.node.Code
import org.commonmark.node.Document
import org.commonmark.node.Emphasis
import org.commonmark.node.FencedCodeBlock
import org.commonmark.node.HardLineBreak
import org.commonmark.node.Heading
import org.commonmark.node.HtmlBlock
import org.commonmark.node.HtmlInline
import org.commonmark.node.Image
import org.commonmark.node.IndentedCodeBlock
import org.commonmark.node.Link
import org.commonmark.node.LinkReferenceDefinition
import org.commonmark.node.ListBlock
import org.commonmark.node.ListItem
import org.commonmark.node.Node
import org.commonmark.node.OrderedList
import org.commonmark.node.Paragraph
import org.commonmark.node.SoftLineBreak
import org.commonmark.node.StrongEmphasis
import org.commonmark.node.Text
import org.commonmark.node.ThematicBreak
import org.commonmark.parser.Parser
import org.commonmark.ext.gfm.tables.TableCell as CmTableCell

/**
 * Parses Markdown source into an [MdDocument].
 *
 * Thread-safe: the underlying commonmark [Parser] is immutable, and every [parse] call uses its
 * own [Converter] state. Call from a background dispatcher; large files take tens of milliseconds.
 */
class MarkdownParser {

    private val parser: Parser = Parser.builder()
        .extensions(
            listOf(
                YamlFrontMatterExtension.create(),
                TablesExtension.create(),
                StrikethroughExtension.create(),
                TaskListItemsExtension.create(),
                AutolinkExtension.create(),
                FootnotesExtension.create(),
                AlertsExtension.builder().allowCustomTitles(true).build(),
                MathExtension.create(),
            ),
        )
        .build()

    fun parse(source: String): MdDocument {
        val root = parser.parse(source.replace("\r\n", "\n"))
        val frontMatter = YamlFrontMatterVisitor.readData(root)
        return Converter().convert(root, frontMatter)
    }

    private class Converter {
        private val slugger = Slugger()
        private val footnoteNumbers = LinkedHashMap<String, Int>()
        private val footnoteDefinitions = LinkedHashMap<String, FootnoteDefinition>()

        fun convert(root: Node, frontMatter: Map<String, List<String>>): MdDocument {
            root.children().filterIsInstance<FootnoteDefinition>().forEach { footnoteDefinitions[it.label] = it }

            val blocks = convertBlocks(root)
            val headings = blocks.mapIndexedNotNull { index, block ->
                (block as? MdBlock.Heading)?.let { HeadingRef(index, it.level, it.text, it.anchor) }
            }
            // Footnotes are numbered in order of first reference, like GitHub.
            val footnotes = LinkedHashMap<String, MdFootnote>()
            var i = 0
            while (i < footnoteNumbers.size) {
                val (label, number) = footnoteNumbers.entries.elementAt(i)
                footnoteDefinitions[label]?.let { footnotes[label] = MdFootnote(label, number, convertBlocks(it)) }
                i++
            }
            val words = blocks.sumOf { block -> if (block is MdBlock.CodeBlock) 0 else countWords(block.plainText()) }
            return MdDocument(blocks, frontMatter, headings, footnotes, DocumentStats.fromWords(words))
        }

        private fun convertBlocks(parent: Node): List<MdBlock> = buildList {
            for (node in parent.children()) addAll(convertBlock(node))
        }

        private fun convertBlock(node: Node): List<MdBlock> = when (node) {
            is Heading -> {
                val content = convertInlines(node)
                val text = content.plainText().trim()
                listOf(MdBlock.Heading(node.level, content, text, slugger.slug(text)))
            }
            is Paragraph -> listOf(paragraphOrImage(node))
            is Alert -> {
                val titleNode = node.firstChild as? AlertTitle
                val title = titleNode?.let { convertInlines(it).plainText().trim() }?.takeIf { it.isNotEmpty() }
                val body = node.children().filter { it !is AlertTitle }.flatMap { convertBlock(it) }
                listOf(MdBlock.Callout(CalloutType.from(node.type), title, body))
            }
            is BlockQuote -> listOf(MdBlock.Quote(convertBlocks(node)))
            is BulletList -> listOf(convertList(node, ordered = false, start = 1))
            is OrderedList -> listOf(convertList(node, ordered = true, start = node.markerStartNumber ?: 1))
            is FencedCodeBlock -> {
                val language = node.info?.trim()?.split(' ', '\t', '{')?.firstOrNull()?.lowercase()?.takeIf { it.isNotEmpty() }
                val code = node.literal.removeSuffix("\n")
                if (language == "math") listOf(MdBlock.MathBlock(code.trim()))
                else listOf(MdBlock.CodeBlock(language, code))
            }
            is IndentedCodeBlock -> listOf(MdBlock.CodeBlock(null, node.literal.removeSuffix("\n")))
            is DisplayMath -> listOf(MdBlock.MathBlock(node.tex))
            is TableBlock -> listOf(convertTable(node))
            is ThematicBreak -> listOf(MdBlock.ThematicBreak)
            is HtmlBlock -> convertHtmlBlock(node.literal)
            is FootnoteDefinition, is LinkReferenceDefinition -> emptyList()
            is Document -> convertBlocks(node)
            else -> {
                // Unknown custom blocks (e.g. front matter) contribute their children, if any.
                if (node.javaClass.simpleName.startsWith("YamlFrontMatter")) emptyList() else convertBlocks(node)
            }
        }

        private fun paragraphOrImage(node: Paragraph): MdBlock {
            val inlines = convertInlines(node)
            val meaningful = inlines.filterNot { it is MdInline.Text && it.text.isBlank() || it is MdInline.SoftBreak }
            val single = meaningful.singleOrNull()
            if (single is MdInline.Image) return MdBlock.Image(single.url, single.alt, single.title)
            if (single is MdInline.Link) {
                val inner = single.children.filterNot { it is MdInline.Text && it.text.isBlank() }.singleOrNull()
                if (inner is MdInline.Image) return MdBlock.Image(inner.url, inner.alt, inner.title, link = single.destination)
            }
            return MdBlock.Paragraph(inlines)
        }

        private fun convertList(node: ListBlock, ordered: Boolean, start: Int): MdBlock.ListBlock {
            val items = node.children().filterIsInstance<ListItem>().map { item ->
                var checked: Boolean? = null
                val blocks = mutableListOf<MdBlock>()
                for (child in item.children()) {
                    if (child is TaskListItemMarker) {
                        checked = child.isChecked
                        continue
                    }
                    if (child is Paragraph && checked == null) {
                        (child.firstChild as? TaskListItemMarker)?.let { checked = it.isChecked }
                    }
                    blocks += convertBlock(child)
                }
                ListItem(checked, blocks)
            }
            return MdBlock.ListBlock(ordered, start, node.isTight, items)
        }

        private fun convertTable(node: TableBlock): MdBlock.Table {
            var header = emptyList<TableCell>()
            val rows = mutableListOf<List<TableCell>>()
            for (section in node.children()) {
                val sectionRows = section.children().filterIsInstance<TableRow>().map { row ->
                    row.children().filterIsInstance<CmTableCell>().map { cell ->
                        TableCell(
                            content = convertInlines(cell),
                            alignment = when (cell.alignment) {
                                CmTableCell.Alignment.CENTER -> CellAlignment.Center
                                CmTableCell.Alignment.RIGHT -> CellAlignment.End
                                else -> CellAlignment.Start
                            },
                        )
                    }
                }
                when (section) {
                    is TableHead -> header = sectionRows.firstOrNull().orEmpty()
                    is TableBody -> rows += sectionRows
                }
            }
            val columns = maxOf(header.size, rows.maxOfOrNull { it.size } ?: 0)
            fun List<TableCell>.padded() = this + List(columns - size) { TableCell(emptyList(), CellAlignment.Start) }
            return MdBlock.Table(header.padded(), rows.map { it.padded() })
        }

        private fun convertHtmlBlock(html: String): List<MdBlock> {
            val images = HtmlScraper.images(html)
            if (images.size == 1) {
                val img = images.single()
                return listOf(MdBlock.Image(img.url, img.alt, null, img.link))
            }
            if (images.size > 1) {
                // A row of badges or logos: keep them inline so they flow and wrap.
                val inlines = images.flatMapIndexed { index, img ->
                    val image = MdInline.Image(img.url, img.alt, null)
                    val node = if (img.link != null) MdInline.Link(img.link, null, listOf(image)) else image
                    if (index == 0) listOf(node) else listOf(MdInline.Text(" "), node)
                }
                return listOf(MdBlock.Paragraph(inlines))
            }
            val text = HtmlScraper.text(html)
            return if (text.isBlank()) emptyList() else listOf(MdBlock.Html(text))
        }

        private fun convertInlines(parent: Node): List<MdInline> {
            val out = mutableListOf<MdInline>()
            for (node in parent.children()) {
                val inline: MdInline? = when (node) {
                    is Text -> MdInline.Text(node.literal)
                    is Emphasis -> MdInline.Emphasis(convertInlines(node))
                    is StrongEmphasis -> MdInline.Strong(convertInlines(node))
                    is Strikethrough -> MdInline.Strikethrough(convertInlines(node))
                    is Code -> MdInline.Code(node.literal)
                    is Link -> MdInline.Link(node.destination, node.title, convertInlines(node))
                    is Image -> MdInline.Image(node.destination, convertInlines(node).plainText(), node.title)
                    is InlineMath -> MdInline.Math(node.tex)
                    is FootnoteReference -> {
                        if (node.label in footnoteDefinitions) {
                            val number = footnoteNumbers.getOrPut(node.label) { footnoteNumbers.size + 1 }
                            MdInline.FootnoteRef(node.label, number)
                        } else MdInline.Text("[^${node.label}]")
                    }
                    is HtmlInline -> htmlInline(node.literal)
                    is SoftLineBreak -> MdInline.SoftBreak
                    is HardLineBreak -> MdInline.HardBreak
                    is TaskListItemMarker -> null
                    else -> {
                        // Unknown inline containers contribute their children directly.
                        convertInlines(node).forEach { out.appendMerging(it) }
                        null
                    }
                }
                if (inline != null) out.appendMerging(inline)
            }
            return out
        }

        private fun MutableList<MdInline>.appendMerging(inline: MdInline) {
            val last = lastOrNull()
            if (inline is MdInline.Text && last is MdInline.Text) {
                this[lastIndex] = MdInline.Text(last.text + inline.text)
            } else {
                add(inline)
            }
        }

        private fun htmlInline(raw: String): MdInline? {
            val lower = raw.lowercase()
            return when {
                lower.startsWith("<br") -> MdInline.HardBreak
                lower.startsWith("<img") -> HtmlScraper.images(raw).firstOrNull()?.let { MdInline.Image(it.url, it.alt, null) }
                else -> null // Formatting tags like <b>/<kbd> are dropped; their text content is kept as Text nodes.
            }
        }

        private fun Node.children(): List<Node> = buildList {
            var child = firstChild
            while (child != null) {
                add(child)
                child = child.next
            }
        }
    }

    companion object {
        private val WORD = Regex("""[\p{L}\p{N}][\p{L}\p{N}'’-]*""")

        internal fun countWords(text: String): Int = WORD.findAll(text).count()
    }
}

/** GitHub-compatible heading slugs, de-duplicated within a document (`intro`, `intro-1`, …). */
class Slugger {
    private val seen = HashMap<String, Int>()

    fun slug(text: String): String {
        val base = text.lowercase()
            .replace(Regex("""[^\p{L}\p{N}\s_-]"""), "")
            .trim()
            .replace(Regex("""\s"""), "-")
        val count = seen[base]
        seen[base] = (count ?: -1) + 1
        return if (count == null) base else "$base-${count + 1}"
    }
}

internal object HtmlScraper {
    data class Img(val url: String, val alt: String, val link: String?)

    private val IMG = Regex("""<img\b[^>]*>""", RegexOption.IGNORE_CASE)
    private val ANCHOR = Regex("""<a\b[^>]*href\s*=\s*["']([^"']+)["'][^>]*>(.*?)</a>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    private val TAG = Regex("""<[^>]+>""")

    private fun attr(tag: String, name: String): String? =
        Regex("""\b$name\s*=\s*["']([^"']*)["']""", RegexOption.IGNORE_CASE).find(tag)?.groupValues?.get(1)

    fun images(html: String): List<Img> {
        val linked = ANCHOR.findAll(html).flatMap { anchor ->
            val href = anchor.groupValues[1]
            IMG.findAll(anchor.groupValues[2]).map { it.range.first + anchor.groups[2]!!.range.first to href }
        }.toMap()
        return IMG.findAll(html).mapNotNull { match ->
            val src = attr(match.value, "src") ?: return@mapNotNull null
            Img(src, attr(match.value, "alt").orEmpty(), linked[match.range.first])
        }.toList()
    }

    fun text(html: String): String = html
        .replace(Regex("""<br\s*/?>""", RegexOption.IGNORE_CASE), "\n")
        .replace(TAG, "")
        .replace("&nbsp;", " ").replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"")
        .lines().joinToString("\n") { it.trim() }
        .trim()
}
