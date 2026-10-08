package com.derekross.markview.core.data

import android.net.Uri
import android.provider.DocumentsContract

/** Where a document came from. Determines how relative links and images resolve. */
sealed interface DocumentSource {
    /** Stable identity used for recents and reading positions. */
    val key: String

    /** A content:// or file:// document. [treeUri] is set when it was opened through a folder grant. */
    data class Local(val uri: Uri, val treeUri: Uri? = null) : DocumentSource {
        override val key: String get() = uri.toString()
    }

    /** A document fetched over http(s). */
    data class Remote(val url: String) : DocumentSource {
        override val key: String get() = url
    }

    /** Text pasted or shared into the app; not persisted to recents. */
    data class Inline(val id: String) : DocumentSource {
        override val key: String get() = "inline:$id"
    }

    companion object {
        fun fromKey(key: String, treeUri: String? = null): DocumentSource = when {
            key.startsWith("http://") || key.startsWith("https://") -> Remote(key)
            key.startsWith("inline:") -> Inline(key.removePrefix("inline:"))
            else -> Local(Uri.parse(key), treeUri?.let(Uri::parse))
        }
    }
}

/** Resolves relative references (images, links) against a document's location. */
object LinkResolver {
    private val SCHEME = Regex("^[a-zA-Z][a-zA-Z0-9+.-]*:")

    fun isAbsolute(ref: String): Boolean = SCHEME.containsMatchIn(ref) || ref.startsWith("//")

    /**
     * Returns an absolute reference for [ref], or null when it can't be resolved
     * (for example, a relative image next to a file opened without folder access).
     */
    fun resolve(source: DocumentSource, ref: String): String? {
        if (ref.isBlank()) return null
        if (ref.startsWith("//")) return "https:$ref"
        if (isAbsolute(ref)) return ref
        return when (source) {
            is DocumentSource.Remote -> resolveUrl(source.url, ref)
            is DocumentSource.Local -> resolveLocal(source, ref)
            is DocumentSource.Inline -> null
        }
    }

    /** RFC 3986-ish resolution for http(s) bases, enough for Markdown relative paths. */
    fun resolveUrl(base: String, ref: String): String = runCatching { java.net.URI(base).resolve(ref.replace(" ", "%20")).toString() }
        .getOrElse { ref }

    private fun resolveLocal(source: DocumentSource.Local, ref: String): String? {
        val uri = source.uri
        if (uri.scheme == "file") {
            val parent = uri.path?.substringBeforeLast('/') ?: return null
            return "file://" + normalizePath("$parent/${ref.substringBefore('#').substringBefore('?')}")
        }
        val tree = source.treeUri ?: return null
        // Path-style document IDs ("primary:Docs/notes/readme.md") are used by the external storage
        // provider and most file managers; resolve relative paths against them.
        val docId = runCatching { DocumentsContract.getDocumentId(uri) }.getOrNull() ?: return null
        val colon = docId.indexOf(':')
        if (colon < 0) return null
        val root = docId.substring(0, colon + 1)
        val path = docId.substring(colon + 1)
        val parent = path.substringBeforeLast('/', missingDelimiterValue = "")
        val target = normalizePath(if (parent.isEmpty()) decode(ref) else "$parent/${decode(ref)}").trimStart('/')
        return DocumentsContract.buildDocumentUriUsingTree(tree, root + target).toString()
    }

    private fun decode(ref: String) = Uri.decode(ref.substringBefore('#').substringBefore('?'))

    /** Collapses `.` and `..` segments. */
    fun normalizePath(path: String): String {
        val absolute = path.startsWith("/")
        val out = ArrayDeque<String>()
        for (segment in path.split('/')) when (segment) {
            "", "." -> Unit
            ".." -> if (out.isNotEmpty()) out.removeLast()
            else -> out.addLast(segment)
        }
        return (if (absolute) "/" else "") + out.joinToString("/")
    }

    /**
     * GitHub/GitLab/Codeberg "blob" page URLs render HTML; rewrite them to raw Markdown.
     * Also maps gist pages to their raw endpoint.
     */
    fun toRawUrl(url: String): String {
        val github = Regex("""^https?://github\.com/([^/]+)/([^/]+)/blob/(.+)$""").find(url)
        if (github != null) {
            val (owner, repo, rest) = github.destructured
            return "https://raw.githubusercontent.com/$owner/$repo/$rest"
        }
        val gitlab = Regex("""^(https?://gitlab\.com/.+)/-/blob/(.+)$""").find(url)
        if (gitlab != null) return "${gitlab.groupValues[1]}/-/raw/${gitlab.groupValues[2]}"
        val codeberg = Regex("""^(https?://codeberg\.org/[^/]+/[^/]+)/src/(.+)$""").find(url)
        if (codeberg != null) return "${codeberg.groupValues[1]}/raw/${codeberg.groupValues[2]}"
        val gist = Regex("""^https?://gist\.github\.com/([^/]+)/([0-9a-f]+)/?$""").find(url)
        if (gist != null) return "https://gist.githubusercontent.com/${gist.groupValues[1]}/${gist.groupValues[2]}/raw"
        // A bare repo URL: show its README.
        val repo = Regex("""^https?://github\.com/([^/]+)/([^/#?]+)/?$""").find(url)
        if (repo != null) {
            val (owner, name) = repo.destructured
            return "https://raw.githubusercontent.com/$owner/${name.removeSuffix(".git")}/HEAD/README.md"
        }
        return url
    }
}
