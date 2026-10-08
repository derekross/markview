package com.derekross.markview.core.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LinkResolverTest {
    @Test
    fun githubBlobUrlsBecomeRaw() {
        assertEquals(
            "https://raw.githubusercontent.com/owner/repo/main/docs/README.md",
            LinkResolver.toRawUrl("https://github.com/owner/repo/blob/main/docs/README.md"),
        )
    }

    @Test
    fun bareRepoUrlsOpenTheReadme() {
        assertEquals(
            "https://raw.githubusercontent.com/owner/repo/HEAD/README.md",
            LinkResolver.toRawUrl("https://github.com/owner/repo"),
        )
    }

    @Test
    fun gitlabAndCodebergBlobUrlsBecomeRaw() {
        assertEquals("https://gitlab.com/g/p/-/raw/main/a.md", LinkResolver.toRawUrl("https://gitlab.com/g/p/-/blob/main/a.md"))
        assertEquals("https://codeberg.org/u/r/raw/branch/main/a.md", LinkResolver.toRawUrl("https://codeberg.org/u/r/src/branch/main/a.md"))
    }

    @Test
    fun otherUrlsAreUntouched() {
        assertEquals("https://example.com/a.md", LinkResolver.toRawUrl("https://example.com/a.md"))
    }

    @Test
    fun relativeUrlsResolveAgainstRemoteBase() {
        val base = "https://raw.githubusercontent.com/o/r/main/docs/guide.md"
        assertEquals("https://raw.githubusercontent.com/o/r/main/docs/img/a.png", LinkResolver.resolveUrl(base, "img/a.png"))
        assertEquals("https://raw.githubusercontent.com/o/r/main/logo.svg", LinkResolver.resolveUrl(base, "../logo.svg"))
        assertEquals("https://raw.githubusercontent.com/o/r/main/docs/my%20pic.png", LinkResolver.resolveUrl(base, "my pic.png"))
    }

    @Test
    fun normalizesDotSegments() {
        assertEquals("/a/c/d", LinkResolver.normalizePath("/a/b/../c/./d"))
        assertEquals("c", LinkResolver.normalizePath("a/../../c"))
    }

    @Test
    fun detectsAbsoluteReferences() {
        assertTrue(LinkResolver.isAbsolute("https://x.y"))
        assertTrue(LinkResolver.isAbsolute("mailto:a@b.c"))
        assertTrue(!LinkResolver.isAbsolute("docs/a.md"))
    }

    @Test
    fun recognisesMarkdownFileNames() {
        assertTrue(FolderRepository.isMarkdownName("README.MD"))
        assertTrue(!FolderRepository.isMarkdownName("photo.png"))
    }
}
