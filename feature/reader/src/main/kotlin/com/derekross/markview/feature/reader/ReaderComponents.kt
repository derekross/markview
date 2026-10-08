package com.derekross.markview.feature.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.derekross.markview.core.markdown.HeadingRef
import com.derekross.markview.core.markdown.MdBlock
import com.derekross.markview.core.markdown.MdDocument
import com.derekross.markview.core.render.LocalMarkdownTheme
import java.text.NumberFormat

/** Title, metadata and front-matter summary shown above the document body. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun DocumentHeader(document: MdDocument, fallbackTitle: String, modifier: Modifier = Modifier) {
    val theme = LocalMarkdownTheme.current
    val colors = MaterialTheme.colorScheme
    val fm = document.frontMatter
    val firstHeading = document.blocks.firstOrNull() as? MdBlock.Heading
    // Only show a separate title when the body doesn't already open with it.
    val title = (document.title ?: fallbackTitle).takeUnless { firstHeading != null && firstHeading.level == 1 && firstHeading.text == it }
    val author = fm["author"]?.firstOrNull() ?: fm["authors"]?.firstOrNull()
    val date = fm["date"]?.firstOrNull() ?: fm["published"]?.firstOrNull()
    val summary = fm["description"]?.firstOrNull() ?: fm["summary"]?.firstOrNull() ?: fm["subtitle"]?.firstOrNull()
    val tags = (fm["tags"].orEmpty() + fm["categories"].orEmpty() + fm["keywords"].orEmpty())
        .flatMap { it.split(',') }.map { it.trim().trim('"', '\'', '[', ']') }.filter { it.isNotEmpty() }.distinct()

    Column(modifier.fillMaxWidth().padding(top = 8.dp, bottom = 20.dp)) {
        if (title != null) {
            Text(title, style = theme.heading(1).copy(fontSize = theme.heading(1).fontSize * 1.1f))
            Spacer(Modifier.height(12.dp))
        }
        if (summary != null) {
            Text(summary, style = theme.body.copy(fontStyle = FontStyle.Italic, color = theme.mutedColor))
            Spacer(Modifier.height(12.dp))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Schedule, contentDescription = null, tint = colors.primary, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            val meta = buildList {
                if (document.stats.readingMinutes > 0) add("${document.stats.readingMinutes} min read")
                add("${NumberFormat.getIntegerInstance().format(document.stats.wordCount)} words")
                author?.let(::add)
                date?.let(::add)
            }.joinToString("  ·  ")
            Text(meta, style = MaterialTheme.typography.labelLarge, color = colors.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        if (tags.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                tags.take(12).forEach { tag ->
                    Surface(shape = CircleShape, color = colors.secondaryContainer) {
                        Text(
                            "#$tag",
                            style = MaterialTheme.typography.labelMedium,
                            color = colors.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        )
                    }
                }
            }
        }
    }
}

/** Table of contents: indented headings with the current section highlighted. */
@Composable
internal fun TableOfContents(
    document: MdDocument,
    currentHeading: HeadingRef?,
    onSelect: (HeadingRef) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val headings = document.headings
    val minLevel = headings.minOfOrNull { it.level } ?: 1
    val listState = rememberLazyListState()
    val currentIndex = headings.indexOf(currentHeading)
    LaunchedEffect(Unit) { if (currentIndex > 3) listState.scrollToItem(currentIndex - 2) }
    Column(modifier) {
        Row(Modifier.padding(horizontal = 24.dp), verticalAlignment = Alignment.Bottom) {
            Text("Contents", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            Text(
                "${headings.size} sections · ${document.stats.readingMinutes} min",
                style = MaterialTheme.typography.labelMedium,
                color = colors.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(12.dp))
        if (headings.isEmpty()) {
            Text(
                "This document has no headings.",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
                modifier = Modifier.padding(24.dp),
            )
        }
        LazyColumn(state = listState, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)) {
            itemsIndexed(headings, key = { _, h -> h.blockIndex }) { _, heading ->
                val selected = heading == currentHeading
                val indent = ((heading.level - minLevel).coerceAtMost(4) * 16).dp
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (selected) colors.secondaryContainer else colors.surface.copy(alpha = 0f))
                        .clickable { onSelect(heading) }
                        .padding(start = 12.dp + indent, end = 12.dp, top = 11.dp, bottom = 11.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (selected) {
                        Surface(shape = CircleShape, color = colors.primary, modifier = Modifier.size(6.dp)) {}
                        Spacer(Modifier.width(10.dp))
                    }
                    Text(
                        heading.text,
                        style = if (heading.level == minLevel) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyMedium,
                        fontWeight = if (selected) FontWeight.SemiBold else null,
                        color = if (selected) colors.onSecondaryContainer else if (heading.level == minLevel) colors.onSurface else colors.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
