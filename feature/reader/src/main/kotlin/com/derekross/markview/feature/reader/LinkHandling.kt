package com.derekross.markview.feature.reader

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import com.derekross.markview.core.data.DocumentSource
import com.derekross.markview.core.data.FolderRepository
import com.derekross.markview.core.data.LinkResolver

/** What tapping a link should do. */
sealed interface LinkAction {
    data class ScrollToAnchor(val anchor: String) : LinkAction
    data class OpenDocument(val source: DocumentSource) : LinkAction
    data class OpenExternal(val uri: String) : LinkAction
    data object Unresolvable : LinkAction
}

object LinkRouter {
    /** Decides how to handle [href] found in a document loaded from [source]. */
    fun route(source: DocumentSource, href: String): LinkAction {
        val trimmed = href.trim()
        if (trimmed.startsWith("#")) return LinkAction.ScrollToAnchor(percentDecode(trimmed.removePrefix("#")))
        val resolved = LinkResolver.resolve(source, trimmed) ?: return LinkAction.Unresolvable
        val path = resolved.substringBefore('#').substringBefore('?')
        val isMarkdown = FolderRepository.isMarkdownName(path.substringAfterLast('/')) && !path.endsWith(".txt")
        return when {
            resolved.startsWith("http://") || resolved.startsWith("https://") ->
                if (isMarkdown) {
                    LinkAction.OpenDocument(DocumentSource.Remote(LinkResolver.toRawUrl(path)))
                } else {
                    LinkAction.OpenExternal(resolved)
                }
            isMarkdown && (resolved.startsWith("content://") || resolved.startsWith("file://")) ->
                LinkAction.OpenDocument(DocumentSource.Local(Uri.parse(path), (source as? DocumentSource.Local)?.treeUri))
            else -> LinkAction.OpenExternal(resolved)
        }
    }

    private fun percentDecode(text: String): String =
        runCatching { java.net.URLDecoder.decode(text.replace("+", "%2B"), "UTF-8") }.getOrDefault(text)
}

internal fun Context.openExternal(uri: String): Boolean {
    val parsed = Uri.parse(uri)
    return try {
        if (parsed.scheme == "http" || parsed.scheme == "https") {
            CustomTabsIntent.Builder().setShowTitle(true).build().launchUrl(this, parsed)
        } else {
            startActivity(Intent(Intent.ACTION_VIEW, parsed).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
        true
    } catch (_: ActivityNotFoundException) {
        false
    }
}

internal fun Context.shareDocument(source: DocumentSource, title: String, rawText: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        when (source) {
            is DocumentSource.Local -> {
                type = "text/markdown"
                putExtra(Intent.EXTRA_STREAM, source.uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            is DocumentSource.Remote -> {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, source.url)
            }
            is DocumentSource.Inline -> {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, rawText)
            }
        }
        putExtra(Intent.EXTRA_TITLE, title)
        putExtra(Intent.EXTRA_SUBJECT, title)
    }
    startActivity(Intent.createChooser(intent, "Share “$title”"))
}

internal fun Context.openWith(source: DocumentSource.Local): Boolean = try {
    startActivity(
        Intent.createChooser(
            Intent(Intent.ACTION_VIEW).setDataAndType(source.uri, "text/markdown")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION),
            "Open with",
        ),
    )
    true
} catch (_: ActivityNotFoundException) {
    false
}
