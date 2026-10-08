package com.derekross.markview.core.render

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.em
import com.derekross.markview.core.markdown.MdInline

/**
 * Converts inline Markdown to an [AnnotatedString] with clickable links.
 *
 * Inline math is shown as styled TeX source for now; the SVG math renderer (roadmap phase 2)
 * will replace it with typeset output via inline content placeholders.
 */
internal fun buildInlineText(
    inlines: List<MdInline>,
    theme: MarkdownTheme,
    callbacks: MarkdownCallbacks,
    search: SearchHighlight = SearchHighlight.None,
    blockIndex: Int = -1,
): AnnotatedString {
    val text = buildAnnotatedString { appendInlines(inlines, theme, callbacks) }
    return if (search.query.isBlank()) text else text.withSearchHighlights(search, blockIndex, theme)
}

private fun AnnotatedString.Builder.appendInlines(
    inlines: List<MdInline>,
    theme: MarkdownTheme,
    callbacks: MarkdownCallbacks,
) {
    for (inline in inlines) when (inline) {
        is MdInline.Text -> append(inline.text)
        is MdInline.Emphasis -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { appendInlines(inline.children, theme, callbacks) }
        is MdInline.Strong -> withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { appendInlines(inline.children, theme, callbacks) }
        is MdInline.Strikethrough -> withStyle(
            SpanStyle(textDecoration = TextDecoration.LineThrough, color = theme.mutedColor),
        ) { appendInlines(inline.children, theme, callbacks) }
        is MdInline.Code -> withStyle(
            SpanStyle(
                fontFamily = theme.codeFontFamily,
                fontSize = 0.88.em,
                background = theme.inlineCodeBackground,
                color = theme.textColor,
            ),
        ) { append(" ${inline.code} ") }
        is MdInline.Link -> {
            val destination = inline.destination
            withLink(
                LinkAnnotation.Clickable(
                    tag = destination,
                    styles = TextLinkStyles(
                        style = SpanStyle(color = theme.linkColor, textDecoration = TextDecoration.Underline),
                        pressedStyle = SpanStyle(color = theme.linkColor, background = theme.linkColor.copy(alpha = 0.12f)),
                    ),
                    linkInteractionListener = { callbacks.onLinkClick(destination) },
                ),
            ) { appendInlines(inline.children, theme, callbacks) }
        }
        is MdInline.Image -> withStyle(SpanStyle(color = theme.mutedColor, fontStyle = FontStyle.Italic)) {
            // Images inside running text are shown as their alt text; standalone images render as blocks.
            append(inline.alt.ifBlank { "image" })
        }
        is MdInline.FootnoteRef -> {
            val label = inline.label
            withLink(
                LinkAnnotation.Clickable(
                    tag = "footnote:$label",
                    styles = TextLinkStyles(SpanStyle(color = theme.linkColor, fontWeight = FontWeight.SemiBold)),
                    linkInteractionListener = { callbacks.onFootnoteClick(label) },
                ),
            ) {
                withStyle(SpanStyle(baselineShift = BaselineShift.Superscript, fontSize = 0.7.em)) {
                    append(" ${inline.number}")
                }
            }
        }
        is MdInline.Math -> withStyle(
            SpanStyle(fontFamily = theme.codeFontFamily, fontSize = 0.9.em, fontStyle = FontStyle.Italic, color = theme.accentColor),
        ) { append(inline.tex) }
        is MdInline.Html -> Unit
        MdInline.SoftBreak -> append(' ')
        MdInline.HardBreak -> append('\n')
    }
}

/** Highlights every case-insensitive match of the query; the current match gets a stronger color. */
internal fun AnnotatedString.withSearchHighlights(search: SearchHighlight, blockIndex: Int, theme: MarkdownTheme): AnnotatedString {
    val query = search.query
    if (query.isBlank()) return this
    val ranges = findOccurrences(text, query)
    if (ranges.isEmpty()) return this
    return buildAnnotatedString {
        append(this@withSearchHighlights)
        ranges.forEachIndexed { occurrence, range ->
            val current = blockIndex == search.currentBlock && occurrence == search.currentOccurrence
            addStyle(
                SpanStyle(background = if (current) theme.searchHighlightCurrent else theme.searchHighlight, color = Color.Unspecified),
                range.first,
                range.last + 1,
            )
        }
    }
}

/** Case-insensitive, non-overlapping occurrences of [query] in [text]. Shared with search navigation. */
fun findOccurrences(text: String, query: String): List<IntRange> {
    if (query.isEmpty()) return emptyList()
    val result = mutableListOf<IntRange>()
    var index = text.indexOf(query, ignoreCase = true)
    while (index >= 0) {
        result += index until index + query.length
        index = text.indexOf(query, index + query.length, ignoreCase = true)
    }
    return result
}
