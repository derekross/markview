package com.derekross.markview.core.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class LibraryEntry(
    val key: String,
    val treeUri: String? = null,
    val title: String,
    val displayName: String,
    val lastOpened: Long,
    /** 0..1 fraction of the document read. */
    val progress: Float = 0f,
    val blockIndex: Int = 0,
    val blockOffset: Int = 0,
    val favorite: Boolean = false,
    val wordCount: Int = 0,
    val readingMinutes: Int = 0,
) {
    val source: DocumentSource get() = DocumentSource.fromKey(key, treeUri)
}

@Serializable
data class FolderEntry(val treeUri: String, val name: String, val addedAt: Long)

@Serializable
data class Library(
    val entries: List<LibraryEntry> = emptyList(),
    val folders: List<FolderEntry> = emptyList(),
) {
    val recents: List<LibraryEntry> get() = entries.sortedByDescending { it.lastOpened }
    val favorites: List<LibraryEntry> get() = entries.filter { it.favorite }.sortedByDescending { it.lastOpened }
    fun entry(key: String): LibraryEntry? = entries.firstOrNull { it.key == key }
}

/** Recents, favorites, reading positions and folder grants. Persisted as JSON in DataStore. */
class LibraryRepository(private val context: Context) {
    private val store get() = context.markviewDataStore
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }

    val library: Flow<Library> = store.data.map { it.library() }.distinctUntilChanged()

    private fun Preferences.library(): Library =
        this[LIBRARY]?.let { runCatching { json.decodeFromString(Library.serializer(), it) }.getOrNull() } ?: Library()

    private suspend fun update(transform: (Library) -> Library) {
        store.edit { prefs -> prefs[LIBRARY] = json.encodeToString(Library.serializer(), transform(prefs.library())) }
    }

    /** Records that a document was opened, keeping its saved position and favorite flag. */
    suspend fun recordOpened(
        source: DocumentSource,
        title: String,
        displayName: String,
        wordCount: Int,
        readingMinutes: Int,
    ) {
        if (source is DocumentSource.Inline) return
        val treeUri = (source as? DocumentSource.Local)?.treeUri?.toString()
        update { lib ->
            val existing = lib.entry(source.key)
            val entry = (existing ?: LibraryEntry(source.key, treeUri, title, displayName, 0L)).copy(
                title = title,
                displayName = displayName,
                treeUri = treeUri ?: existing?.treeUri,
                lastOpened = System.currentTimeMillis(),
                wordCount = wordCount,
                readingMinutes = readingMinutes,
            )
            val others = lib.entries.filter { it.key != source.key }
            // Keep favorites forever; cap non-favorite history.
            val trimmed = others.sortedByDescending { it.lastOpened }
                .filterIndexed { i, e -> e.favorite || i < MAX_RECENTS - 1 }
            lib.copy(entries = listOf(entry) + trimmed)
        }
    }

    suspend fun savePosition(key: String, blockIndex: Int, blockOffset: Int, progress: Float) = update { lib ->
        lib.copy(entries = lib.entries.map {
            if (it.key == key) it.copy(blockIndex = blockIndex, blockOffset = blockOffset, progress = progress.coerceIn(0f, 1f)) else it
        })
    }

    suspend fun setFavorite(key: String, favorite: Boolean) = update { lib ->
        lib.copy(entries = lib.entries.map { if (it.key == key) it.copy(favorite = favorite) else it })
    }

    suspend fun remove(key: String) = update { lib -> lib.copy(entries = lib.entries.filter { it.key != key }) }

    suspend fun clearHistory() = update { lib -> lib.copy(entries = lib.entries.filter { it.favorite }) }

    suspend fun addFolder(treeUri: Uri, name: String) {
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                treeUri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        }.recoverCatching {
            context.contentResolver.takePersistableUriPermission(treeUri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        update { lib ->
            val folders = lib.folders.filter { it.treeUri != treeUri.toString() } + FolderEntry(treeUri.toString(), name, System.currentTimeMillis())
            lib.copy(folders = folders)
        }
    }

    suspend fun removeFolder(treeUri: String) {
        runCatching {
            context.contentResolver.releasePersistableUriPermission(
                Uri.parse(treeUri),
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        }
        update { lib -> lib.copy(folders = lib.folders.filter { it.treeUri != treeUri }) }
    }

    /** Persists read (and, when granted, write) access to a single opened document. */
    fun persistAccess(uri: Uri) {
        if (uri.scheme != "content") return
        val resolver = context.contentResolver
        runCatching {
            resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        }.recoverCatching {
            resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    private companion object {
        val LIBRARY = stringPreferencesKey("library_json")
        const val MAX_RECENTS = 60
    }
}
