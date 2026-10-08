package com.derekross.markview

import android.content.Intent
import android.net.Uri
import android.os.Build
import com.derekross.markview.core.data.AppContainer
import com.derekross.markview.core.data.DocumentSource
import com.derekross.markview.core.data.LinkResolver

/** Turns an incoming VIEW/SEND intent into something the reader can open. */
internal object IntentParser {
    fun parse(intent: Intent?, container: AppContainer): DocumentSource? {
        intent ?: return null
        return when (intent.action) {
            Intent.ACTION_VIEW, Intent.ACTION_EDIT -> intent.data?.let { fromUri(it, container) }
            Intent.ACTION_SEND -> {
                val stream = if (Build.VERSION.SDK_INT >= 33) {
                    intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(Intent.EXTRA_STREAM)
                }
                stream?.let { return fromUri(it, container) }
                val text = intent.getStringExtra(Intent.EXTRA_TEXT)?.takeIf { it.isNotBlank() } ?: return null
                val trimmed = text.trim()
                if (!trimmed.contains('\n') && (trimmed.startsWith("http://") || trimmed.startsWith("https://")) && !trimmed.contains(' ')) {
                    DocumentSource.Remote(LinkResolver.toRawUrl(trimmed))
                } else {
                    val title = intent.getStringExtra(Intent.EXTRA_SUBJECT)
                        ?: trimmed.lineSequence().map { it.trim().trimStart('#').trim() }.firstOrNull { it.isNotEmpty() }?.take(60)
                        ?: "Shared text"
                    container.documents.putInline(title, text)
                }
            }
            else -> null
        }
    }

    private fun fromUri(uri: Uri, container: AppContainer): DocumentSource? = when (uri.scheme) {
        "http", "https" -> DocumentSource.Remote(LinkResolver.toRawUrl(uri.toString()))
        "content" -> {
            container.library.persistAccess(uri)
            DocumentSource.Local(uri)
        }
        "file" -> DocumentSource.Local(uri)
        else -> null
    }
}
