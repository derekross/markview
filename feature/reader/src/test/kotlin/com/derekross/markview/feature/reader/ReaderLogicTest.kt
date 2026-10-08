package com.derekross.markview.feature.reader

import com.derekross.markview.core.data.DocumentSource
import com.derekross.markview.core.markdown.MarkdownParser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ReaderLogicTest {
    private val remote = DocumentSource.Remote("https://raw.githubusercontent.com/o/r/main/docs/guide.md")

    @Test
    fun anchorsScrollInPlace() {
        assertEquals(LinkAction.ScrollToAnchor("getting-started"), LinkRouter.route(remote, "#getting-started"))
        assertEquals(LinkAction.ScrollToAnchor("café"), LinkRouter.route(remote, "#caf%C3%A9"))
    }

    @Test
    fun relativeMarkdownLinksOpenInApp() {
        val action = assertIs<LinkAction.OpenDocument>(LinkRouter.route(remote, "../CONTRIBUTING.md#setup"))
        assertEquals("https://raw.githubusercontent.com/o/r/main/CONTRIBUTING.md", action.source.key)
    }

    @Test
    fun githubBlobMarkdownLinksOpenRaw() {
        val action = assertIs<LinkAction.OpenDocument>(LinkRouter.route(remote, "https://github.com/a/b/blob/main/README.md"))
        assertEquals("https://raw.githubusercontent.com/a/b/main/README.md", action.source.key)
    }

    @Test
    fun otherLinksOpenExternally() {
        assertEquals(LinkAction.OpenExternal("https://example.com/page"), LinkRouter.route(remote, "https://example.com/page"))
        assertEquals(LinkAction.OpenExternal("mailto:hi@example.com"), LinkRouter.route(remote, "mailto:hi@example.com"))
    }

    @Test
    fun relativeLinksInPastedTextAreUnresolvable() {
        assertEquals(LinkAction.Unresolvable, LinkRouter.route(DocumentSource.Inline("x"), "other.md"))
    }

    @Test
    fun searchFindsEveryOccurrencePerBlock() {
        val doc = MarkdownParser().parse("# Cats\n\ncats and CATS\n\n- a cat\n")
        val matches = ReaderViewModel.computeMatches(doc, "cat")
        assertEquals(listOf(SearchMatch(0, 0), SearchMatch(1, 0), SearchMatch(1, 1), SearchMatch(2, 0)), matches)
    }
}
