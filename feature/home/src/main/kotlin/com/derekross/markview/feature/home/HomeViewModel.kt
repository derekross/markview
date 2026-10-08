package com.derekross.markview.feature.home

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.derekross.markview.core.data.DocumentSource
import com.derekross.markview.core.data.FolderEntry
import com.derekross.markview.core.data.Library
import com.derekross.markview.core.data.LinkResolver
import com.derekross.markview.core.data.appContainer
import com.derekross.markview.core.designsystem.ReaderSettings
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HomeViewModel(application: Application) : AndroidViewModel(application) {
    private val container = application.appContainer

    val library: StateFlow<Library?> = container.library.library
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val settings: StateFlow<ReaderSettings> = container.settings.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReaderSettings())

    fun updateSettings(settings: ReaderSettings) {
        viewModelScope.launch { container.settings.update { settings } }
    }

    fun sourceForPickedFile(uri: Uri): DocumentSource {
        container.library.persistAccess(uri)
        return DocumentSource.Local(uri)
    }

    fun addFolder(uri: Uri, onAdded: (FolderEntry) -> Unit) {
        viewModelScope.launch {
            val name = Uri.decode(uri.lastPathSegment.orEmpty()).substringAfterLast(':').substringAfterLast('/').ifBlank { "Folder" }
            container.library.addFolder(uri, name)
            onAdded(FolderEntry(uri.toString(), name, System.currentTimeMillis()))
        }
    }

    fun removeFolder(folder: FolderEntry) {
        viewModelScope.launch { container.library.removeFolder(folder.treeUri) }
    }

    fun toggleFavorite(key: String, favorite: Boolean) {
        viewModelScope.launch { container.library.setFavorite(key, favorite) }
    }

    fun remove(key: String) {
        viewModelScope.launch { container.library.remove(key) }
    }

    /** Pasted text is either a URL to fetch or Markdown to show directly. */
    fun sourceForPastedText(text: String): DocumentSource {
        val trimmed = text.trim()
        return if (isUrl(trimmed)) {
            DocumentSource.Remote(LinkResolver.toRawUrl(trimmed))
        } else {
            container.documents.putInline(pastedTitle(trimmed), text)
        }
    }

    fun sourceForUrl(url: String): DocumentSource {
        val trimmed = url.trim().let { if (it.startsWith("http://") || it.startsWith("https://")) it else "https://$it" }
        return DocumentSource.Remote(LinkResolver.toRawUrl(trimmed))
    }

    companion object {
        fun isUrl(text: String) = !text.contains('\n') && Regex("""^https?://\S+$""").matches(text)

        fun pastedTitle(text: String): String =
            text.lineSequence().map { it.trim().trimStart('#').trim() }.firstOrNull { it.isNotEmpty() }?.take(60) ?: "Pasted text"
    }
}
