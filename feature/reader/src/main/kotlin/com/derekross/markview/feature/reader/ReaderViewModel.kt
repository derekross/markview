package com.derekross.markview.feature.reader

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.derekross.markview.core.data.DocumentSource
import com.derekross.markview.core.data.DocumentTooLargeException
import com.derekross.markview.core.data.LibraryEntry
import com.derekross.markview.core.data.appContainer
import com.derekross.markview.core.designsystem.ReaderSettings
import com.derekross.markview.core.markdown.MarkdownParser
import com.derekross.markview.core.markdown.MdDocument
import com.derekross.markview.core.markdown.plainText
import com.derekross.markview.core.render.findOccurrences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class SavedPosition(val blockIndex: Int, val offset: Int)

sealed interface ReaderUiState {
    data object Loading : ReaderUiState
    data class Error(val message: String) : ReaderUiState
    data class Ready(
        val document: MdDocument,
        val displayName: String,
        val rawText: String,
        val writable: Boolean,
        /** Position to restore once, on first display. */
        val restore: SavedPosition?,
        /** Incremented on live reload so the UI can announce it. */
        val revision: Int = 0,
    ) : ReaderUiState {
        val title: String get() = document.title ?: displayName.substringBeforeLast('.')
    }
}

data class SearchMatch(val blockIndex: Int, val occurrence: Int)

data class SearchState(
    val active: Boolean = false,
    val query: String = "",
    val matches: List<SearchMatch> = emptyList(),
    val current: Int = -1,
) {
    val currentMatch: SearchMatch? get() = matches.getOrNull(current)
}

class ReaderViewModel(application: Application, val source: DocumentSource) : AndroidViewModel(application) {
    private val container = application.appContainer

    private val _state = MutableStateFlow<ReaderUiState>(ReaderUiState.Loading)
    val state: StateFlow<ReaderUiState> = _state.asStateFlow()

    private val _search = MutableStateFlow(SearchState())
    val search: StateFlow<SearchState> = _search.asStateFlow()

    val settings: StateFlow<ReaderSettings> = container.settings.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, ReaderSettings())

    val favorite: StateFlow<Boolean> = container.library.library
        .map { it.entry(source.key)?.favorite == true }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    private var lastModified: Long? = null
    private var watchJob: Job? = null

    init {
        load()
    }

    fun load() {
        _state.value = ReaderUiState.Loading
        viewModelScope.launch {
            runCatching {
                val loaded = container.documents.load(source)
                val document = withContext(Dispatchers.Default) { parser.parse(loaded.text) }
                val entry: LibraryEntry? = container.library.library.first().entry(source.key)
                lastModified = loaded.lastModified
                val title = document.title ?: loaded.displayName.substringBeforeLast('.')
                container.library.recordOpened(source, title, loaded.displayName, document.stats.wordCount, document.stats.readingMinutes)
                ReaderUiState.Ready(
                    document = document,
                    displayName = loaded.displayName,
                    rawText = loaded.text,
                    writable = loaded.writable,
                    restore = entry?.takeIf { it.blockIndex > 0 || it.blockOffset > 0 }?.let { SavedPosition(it.blockIndex, it.blockOffset) },
                )
            }.onSuccess {
                _state.value = it
                startWatching()
            }.onFailure { error ->
                _state.value = ReaderUiState.Error(
                    when (error) {
                        is DocumentTooLargeException -> "This file is too large to open (limit is 8 MB)."
                        is SecurityException -> "Markview no longer has permission to open this file. Open it again from your files app."
                        is java.io.FileNotFoundException -> "This file couldn't be found. It may have been moved or deleted."
                        is java.net.UnknownHostException -> "You appear to be offline."
                        else -> error.message ?: "Something went wrong while opening this document."
                    },
                )
            }
        }
    }

    /** Live reload: poll the provider's last-modified time while the document is open. */
    private fun startWatching() {
        if (source !is DocumentSource.Local || lastModified == null) return
        watchJob?.cancel()
        watchJob = viewModelScope.launch {
            while (isActive) {
                delay(WATCH_INTERVAL_MS)
                val modified = container.documents.lastModified(source) ?: continue
                if (modified != lastModified) {
                    lastModified = modified
                    reloadInPlace()
                }
            }
        }
    }

    private suspend fun reloadInPlace() {
        val current = _state.value as? ReaderUiState.Ready ?: return
        runCatching {
            val loaded = container.documents.load(source)
            if (loaded.text == current.rawText) return
            val document = withContext(Dispatchers.Default) { parser.parse(loaded.text) }
            _state.value = current.copy(document = document, rawText = loaded.text, restore = null, revision = current.revision + 1)
            if (_search.value.active) setQuery(_search.value.query)
        }
    }

    fun savePosition(blockIndex: Int, offset: Int, progress: Float) {
        if (source is DocumentSource.Inline) return
        viewModelScope.launch { container.library.savePosition(source.key, blockIndex, offset, progress) }
    }

    fun toggleFavorite() {
        viewModelScope.launch { container.library.setFavorite(source.key, !favorite.value) }
    }

    fun updateSettings(settings: ReaderSettings) {
        viewModelScope.launch { container.settings.update { settings } }
    }

    // region Find in document

    fun openSearch() = _search.update { it.copy(active = true) }

    fun closeSearch() {
        _search.value = SearchState()
    }

    fun setQuery(query: String) {
        val document = (_state.value as? ReaderUiState.Ready)?.document
        val matches = if (document == null || query.isBlank()) emptyList() else computeMatches(document, query)
        _search.update { it.copy(query = query, matches = matches, current = if (matches.isEmpty()) -1 else 0) }
    }

    fun nextMatch() = _search.update { s ->
        if (s.matches.isEmpty()) s else s.copy(current = (s.current + 1) % s.matches.size)
    }

    fun previousMatch() = _search.update { s ->
        if (s.matches.isEmpty()) s else s.copy(current = (s.current - 1 + s.matches.size) % s.matches.size)
    }

    // endregion

    companion object {
        private const val WATCH_INTERVAL_MS = 2_000L
        private val parser = MarkdownParser()

        fun computeMatches(document: MdDocument, query: String): List<SearchMatch> =
            document.blocks.flatMapIndexed { index, block ->
                findOccurrences(block.plainText(), query).indices.map { SearchMatch(index, it) }
            }
    }
}
