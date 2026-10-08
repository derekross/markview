package com.derekross.markview.core.render

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf

/** Interaction hooks and resolution strategy supplied by the host screen. */
@Immutable
class MarkdownCallbacks(
    /** A link was tapped. `#anchor` links are passed through unchanged for the host to resolve. */
    val onLinkClick: (String) -> Unit = {},
    /** A footnote reference was tapped. */
    val onFootnoteClick: (label: String) -> Unit = {},
    /** An image was tapped (receives the resolved model). */
    val onImageClick: (model: Any, alt: String) -> Unit = { _, _ -> },
    /** Code was copied from a code block. */
    val onCodeCopied: () -> Unit = {},
    /** Maps a Markdown image URL (possibly relative) to something Coil can load. */
    val resolveImage: (String) -> Any? = { it },
)

val LocalMarkdownCallbacks = staticCompositionLocalOf { MarkdownCallbacks() }

/** Find-in-document state passed down to highlight matches. */
@Immutable
data class SearchHighlight(val query: String, val currentBlock: Int = -1, val currentOccurrence: Int = -1) {
    companion object {
        val None = SearchHighlight("")
    }
}
