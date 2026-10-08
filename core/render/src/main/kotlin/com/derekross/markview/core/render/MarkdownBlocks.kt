package com.derekross.markview.core.render

import android.content.ClipData
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckBox
import androidx.compose.material.icons.outlined.CheckBoxOutlineBlank
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Done
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.ReportProblem
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material.icons.automirrored.outlined.WrapText
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.toClipEntry
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.derekross.markview.core.markdown.CalloutType
import com.derekross.markview.core.markdown.CellAlignment
import com.derekross.markview.core.markdown.MdBlock
import com.derekross.markview.core.markdown.MdDocument
import com.derekross.markview.core.markdown.MdInline
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Emits one lazy item per top-level block. Item keys are `block-<index>` so hosts can map
 * lazy-list indices back to block indices (offset by however many header items they add).
 */
fun LazyListScope.markdownBlocks(
    document: MdDocument,
    search: SearchHighlight,
    itemModifier: Modifier = Modifier,
) {
    items(
        count = document.blocks.size,
        key = { "block-$it" },
        contentType = { document.blocks[it]::class },
    ) { index ->
        val block = document.blocks[index]
        val theme = LocalMarkdownTheme.current
        val top = when {
            index == 0 -> 0.dp
            block is MdBlock.Heading && block.level <= 2 -> theme.blockSpacing * 2.2f
            block is MdBlock.Heading -> theme.blockSpacing * 1.5f
            else -> theme.blockSpacing
        }
        MarkdownBlock(block, index, search, itemModifier.padding(top = top))
    }
}

/** Renders a single block (recursively for containers). */
@Composable
fun MarkdownBlock(
    block: MdBlock,
    blockIndex: Int,
    search: SearchHighlight = SearchHighlight.None,
    modifier: Modifier = Modifier,
) {
    val theme = LocalMarkdownTheme.current
    when (block) {
        is MdBlock.Heading -> HeadingBlock(block, blockIndex, search, modifier)
        is MdBlock.Paragraph -> ParagraphBlock(block.content, blockIndex, search, modifier)
        is MdBlock.Quote -> QuoteBlock(block, search, modifier)
        is MdBlock.Callout -> CalloutBlock(block, search, modifier)
        is MdBlock.ListBlock -> ListBlockView(block, depth = 0, search = search, modifier = modifier)
        is MdBlock.CodeBlock -> CodeBlockView(block.code, block.language, search, modifier)
        is MdBlock.MathBlock -> MathBlockView(block.tex, modifier)
        is MdBlock.Table -> TableBlock(block, search, modifier)
        is MdBlock.Image -> Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            MarkdownImage(block.url, block.alt, block.link)
            val caption = block.title
            if (!caption.isNullOrBlank()) {
                Text(
                    caption,
                    style = theme.body.copy(fontSize = theme.body.fontSize * 0.82f, fontStyle = FontStyle.Italic),
                    color = theme.mutedColor,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
        is MdBlock.Html -> Text(
            block.raw,
            style = theme.body.copy(fontSize = theme.body.fontSize * 0.9f),
            color = theme.mutedColor,
            modifier = modifier,
        )
        MdBlock.ThematicBreak -> Box(modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                repeat(3) { Box(Modifier.size(5.dp).clip(CircleShape).background(theme.dividerColor)) }
            }
        }
    }
}

@Composable
private fun HeadingBlock(block: MdBlock.Heading, blockIndex: Int, search: SearchHighlight, modifier: Modifier) {
    val theme = LocalMarkdownTheme.current
    val callbacks = LocalMarkdownCallbacks.current
    val text = remember(block, theme, search) { buildInlineText(block.content, theme, callbacks, search, blockIndex) }
    Text(
        text = text,
        style = theme.heading(block.level),
        modifier = modifier.fillMaxWidth().semantics { heading() },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ParagraphBlock(content: List<MdInline>, blockIndex: Int, search: SearchHighlight, modifier: Modifier) {
    val theme = LocalMarkdownTheme.current
    val callbacks = LocalMarkdownCallbacks.current
    if (content.isImageRow()) {
        // Badge rows (shields.io etc.) flow and wrap like they do on GitHub.
        FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            for (inline in content) when (inline) {
                is MdInline.Image -> MarkdownImage(inline.url, inline.alt, null, inline = true)
                is MdInline.Link -> inline.children.filterIsInstance<MdInline.Image>().firstOrNull()?.let {
                    MarkdownImage(it.url, it.alt, inline.destination, inline = true)
                }
                else -> Unit
            }
        }
        return
    }
    val text = remember(content, theme, search) { buildInlineText(content, theme, callbacks, search, blockIndex) }
    Text(text = text, style = theme.body, modifier = modifier.fillMaxWidth())
}

private fun List<MdInline>.isImageRow(): Boolean {
    var images = 0
    for (inline in this) when {
        inline is MdInline.Image -> images++
        inline is MdInline.Link && inline.children.any { it is MdInline.Image } &&
            inline.children.all { it is MdInline.Image || it is MdInline.Text && it.text.isBlank() } -> images++
        inline is MdInline.Text && inline.text.isBlank() -> Unit
        inline is MdInline.SoftBreak || inline is MdInline.HardBreak -> Unit
        else -> return false
    }
    return images >= 2
}

@Composable
private fun NestedBlocks(blocks: List<MdBlock>, search: SearchHighlight, spacing: androidx.compose.ui.unit.Dp) {
    // Nested content never gets "current match" emphasis (block index -1); matches are still tinted.
    val nestedSearch = if (search.query.isBlank()) search else search.copy(currentBlock = -2)
    blocks.forEachIndexed { i, child ->
        MarkdownBlock(child, -1, nestedSearch, if (i == 0) Modifier else Modifier.padding(top = spacing))
    }
}

@Composable
private fun QuoteBlock(block: MdBlock.Quote, search: SearchHighlight, modifier: Modifier) {
    val theme = LocalMarkdownTheme.current
    Row(modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        Box(
            Modifier.width(4.dp).fillMaxHeight().clip(RoundedCornerShape(2.dp))
                .background(theme.accentColor.copy(alpha = 0.45f)),
        )
        Column(Modifier.padding(start = 16.dp)) {
            androidx.compose.runtime.CompositionLocalProvider(
                LocalMarkdownTheme provides theme.copy(body = theme.body.copy(color = theme.mutedColor, fontStyle = FontStyle.Italic)),
            ) {
                NestedBlocks(block.blocks, search, theme.blockSpacing * 0.75f)
            }
        }
    }
}

private data class CalloutStyle(val icon: ImageVector, val color: Color)

@Composable
private fun CalloutBlock(block: MdBlock.Callout, search: SearchHighlight, modifier: Modifier) {
    val theme = LocalMarkdownTheme.current
    val ext = theme.extended
    val style = when (block.type) {
        CalloutType.Note -> CalloutStyle(Icons.Outlined.Info, ext.note)
        CalloutType.Tip -> CalloutStyle(Icons.Outlined.Lightbulb, ext.tip)
        CalloutType.Important -> CalloutStyle(Icons.Outlined.ReportProblem, ext.important)
        CalloutType.Warning -> CalloutStyle(Icons.Outlined.WarningAmber, ext.warning)
        CalloutType.Caution -> CalloutStyle(Icons.Outlined.ErrorOutline, ext.caution)
    }
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(style.color.copy(alpha = if (ext.isDark) 0.14f else 0.08f))
            .border(1.dp, style.color.copy(alpha = 0.22f), shape)
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(style.icon, contentDescription = null, tint = style.color, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                block.title ?: block.type.label,
                style = theme.body.copy(fontWeight = FontWeight.SemiBold, fontSize = theme.body.fontSize * 0.95f, color = style.color),
            )
        }
        if (block.blocks.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            NestedBlocks(block.blocks, search, theme.blockSpacing * 0.75f)
        }
    }
}

private val bulletGlyphs = listOf("•", "◦", "▪")

@Composable
private fun ListBlockView(block: MdBlock.ListBlock, depth: Int, search: SearchHighlight, modifier: Modifier) {
    val theme = LocalMarkdownTheme.current
    val itemGap = if (block.tight) theme.blockSpacing * 0.35f else theme.blockSpacing * 0.7f
    val markerWidth = if (block.ordered) (14 + 9 * (block.start + block.items.size - 1).toString().length).dp else 22.dp
    Column(modifier.fillMaxWidth()) {
        block.items.forEachIndexed { i, item ->
            Row(Modifier.padding(top = if (i == 0) 0.dp else itemGap)) {
                Box(Modifier.width(markerWidth), contentAlignment = Alignment.TopStart) {
                    when {
                        item.checked != null -> Icon(
                            if (item.checked == true) Icons.Outlined.CheckBox else Icons.Outlined.CheckBoxOutlineBlank,
                            contentDescription = if (item.checked == true) "Done" else "Not done",
                            tint = if (item.checked == true) theme.accentColor else theme.mutedColor,
                            modifier = Modifier.padding(top = 2.dp).size(20.dp),
                        )
                        block.ordered -> Text(
                            "${block.start + i}.",
                            style = theme.body.copy(color = theme.mutedColor, fontFeatureSettings = "tnum"),
                        )
                        else -> Text(bulletGlyphs[depth % bulletGlyphs.size], style = theme.body.copy(color = theme.accentColor))
                    }
                }
                Column(Modifier.weight(1f)) {
                    val nestedSearch = if (search.query.isBlank()) search else search.copy(currentBlock = -2)
                    val itemTheme = if (item.checked == true) theme.copy(body = theme.body.copy(color = theme.mutedColor)) else theme
                    androidx.compose.runtime.CompositionLocalProvider(LocalMarkdownTheme provides itemTheme) {
                        item.blocks.forEachIndexed { j, child ->
                            val childModifier = if (j == 0) Modifier else Modifier.padding(top = itemGap)
                            if (child is MdBlock.ListBlock) {
                                ListBlockView(child, depth + 1, nestedSearch, childModifier)
                            } else {
                                MarkdownBlock(child, -1, nestedSearch, childModifier)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun CodeBlockView(code: String, language: String?, search: SearchHighlight, modifier: Modifier) {
    val theme = LocalMarkdownTheme.current
    val clipboard = LocalClipboard.current
    val callbacks = LocalMarkdownCallbacks.current
    val scope = rememberCoroutineScope()
    var copied by remember { mutableStateOf(false) }
    var wrap by remember { mutableStateOf(false) }
    val highlighted = remember(code, language, theme.extended.isDark, search.query) {
        CodeHighlighter.highlight(code, language, theme.extended.isDark).let {
            if (search.query.isBlank()) it else it.withSearchHighlights(search.copy(currentBlock = -2), -1, theme)
        }
    }
    LaunchedEffect(copied) {
        if (copied) {
            delay(1600)
            copied = false
        }
    }
    val shape = RoundedCornerShape(16.dp)
    Column(modifier.fillMaxWidth().clip(shape).background(theme.codeBlockBackground)) {
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                language?.uppercase() ?: "CODE",
                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.sp),
                color = theme.mutedColor,
                modifier = Modifier.weight(1f),
            )
            IconToggleButton(checked = wrap, onCheckedChange = { wrap = it }) {
                Icon(
                    Icons.AutoMirrored.Outlined.WrapText,
                    contentDescription = "Wrap lines",
                    tint = if (wrap) theme.accentColor else theme.mutedColor,
                    modifier = Modifier.size(18.dp),
                )
            }
            IconButton(onClick = {
                scope.launch {
                    clipboard.setClipEntry(ClipData.newPlainText("code", code).toClipEntry())
                    copied = true
                    callbacks.onCodeCopied()
                }
            }) {
                Icon(
                    if (copied) Icons.Outlined.Done else Icons.Outlined.ContentCopy,
                    contentDescription = "Copy code",
                    tint = if (copied) theme.accentColor else theme.mutedColor,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        val textModifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
        if (wrap) {
            Text(highlighted, style = theme.code, modifier = textModifier)
        } else {
            Box(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                Text(highlighted, style = theme.code, softWrap = false, modifier = textModifier)
            }
        }
    }
}

@Composable
private fun MathBlockView(tex: String, modifier: Modifier) {
    val theme = LocalMarkdownTheme.current
    Box(
        modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(theme.codeBlockBackground.copy(alpha = 0.6f))
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            tex,
            style = theme.code.copy(fontStyle = FontStyle.Italic, color = theme.accentColor),
            softWrap = false,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TableBlock(block: MdBlock.Table, search: SearchHighlight, modifier: Modifier) {
    val theme = LocalMarkdownTheme.current
    val callbacks = LocalMarkdownCallbacks.current
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(14.dp)
    val columns = block.header.size
    val nestedSearch = if (search.query.isBlank()) search else search.copy(currentBlock = -2)
    val cellStyle = theme.body.copy(fontSize = theme.body.fontSize * 0.9f)
    Box(
        modifier.fillMaxWidth().clip(shape).border(1.dp, theme.dividerColor, shape)
            .horizontalScroll(rememberScrollState()),
    ) {
        Layout(
            content = {
                val rows = listOf(block.header) + block.rows
                rows.forEachIndexed { r, row ->
                    row.forEach { cell ->
                        val text = remember(cell, theme, search) { buildInlineText(cell.content, theme, callbacks, nestedSearch, -1) }
                        val background = when {
                            r == 0 -> colors.surfaceContainerHigh
                            r % 2 == 0 -> colors.surfaceContainerLow.copy(alpha = 0.6f)
                            else -> Color.Transparent
                        }
                        Box(Modifier.background(background).padding(horizontal = 14.dp, vertical = 10.dp)) {
                            Text(
                                text,
                                style = if (r == 0) cellStyle.copy(fontWeight = FontWeight.SemiBold) else cellStyle,
                                textAlign = when (cell.alignment) {
                                    CellAlignment.Start -> TextAlign.Start
                                    CellAlignment.Center -> TextAlign.Center
                                    CellAlignment.End -> TextAlign.End
                                },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            },
        ) { measurables, constraints ->
            if (columns == 0) return@Layout layout(0, 0) {}
            val rowCount = measurables.size / columns
            val minCol = 56.dp.roundToPx()
            val maxCol = 320.dp.roundToPx()
            val colWidths = IntArray(columns) { c ->
                (0 until rowCount).maxOf { r -> measurables[r * columns + c].maxIntrinsicWidth(Constraints.Infinity) }
                    .coerceIn(minCol, maxCol)
            }
            // If the table is narrower than the viewport, stretch columns proportionally to fill it.
            val available = if (constraints.hasBoundedWidth) constraints.maxWidth else 0
            val total = colWidths.sum()
            if (available in (total + 1)..Int.MAX_VALUE) {
                val extra = available - total
                var given = 0
                for (c in 0 until columns) {
                    val add = if (c == columns - 1) extra - given else extra * colWidths[c] / total
                    colWidths[c] += add
                    given += add
                }
            }
            val rowHeights = IntArray(rowCount) { r ->
                (0 until columns).maxOf { c -> measurables[r * columns + c].minIntrinsicHeight(colWidths[c]) }
            }
            val placeables = measurables.mapIndexed { i, m ->
                val r = i / columns
                val c = i % columns
                m.measure(Constraints.fixed(colWidths[c], rowHeights[r]))
            }
            val width = colWidths.sum()
            val dividerPx = 1.dp.roundToPx()
            val height = rowHeights.sum()
            layout(width, height) {
                var y = 0
                for (r in 0 until rowCount) {
                    var x = 0
                    for (c in 0 until columns) {
                        placeables[r * columns + c].place(x, y)
                        x += colWidths[c]
                    }
                    y += rowHeights[r]
                }
                check(dividerPx >= 0)
            }
        }
    }
}

/** Numbered footnote list rendered after the document body. */
@Composable
fun FootnotesSection(document: MdDocument, modifier: Modifier = Modifier) {
    if (document.footnotes.isEmpty()) return
    val theme = LocalMarkdownTheme.current
    Column(modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(theme.dividerColor))
        Text(
            "Footnotes",
            style = MaterialTheme.typography.labelLarge,
            color = theme.mutedColor,
            modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
        )
        val small = theme.copy(body = theme.body.copy(fontSize = theme.body.fontSize * 0.88f))
        androidx.compose.runtime.CompositionLocalProvider(LocalMarkdownTheme provides small) {
            document.footnotes.values.sortedBy { it.number }.forEach { note ->
                Row(Modifier.padding(vertical = 4.dp)) {
                    Text("${note.number}.", style = small.body.copy(color = theme.accentColor), modifier = Modifier.widthIn(min = 28.dp))
                    Column(Modifier.weight(1f)) { NestedBlocks(note.blocks, SearchHighlight.None, 6.dp) }
                }
            }
        }
    }
}
