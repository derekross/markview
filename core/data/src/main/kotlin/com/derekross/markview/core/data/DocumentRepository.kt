package com.derekross.markview.core.data

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap

data class LoadedDocument(
    val source: DocumentSource,
    val displayName: String,
    val text: String,
    /** Last-modified millis when the provider reports it; used for live reload. */
    val lastModified: Long?,
    val writable: Boolean,
)

class DocumentTooLargeException(val bytes: Long) : IOException("Document is too large to open (${bytes / 1024} KB)")

/** Loads Markdown text from local providers, the network, or in-memory pastes. */
class DocumentRepository(private val context: Context) {
    private val resolver: ContentResolver get() = context.contentResolver
    private val inline = ConcurrentHashMap<String, Pair<String, String>>()

    /** Stores pasted/shared text and returns a source that can be loaded like any other. */
    fun putInline(title: String, text: String): DocumentSource.Inline {
        val id = Integer.toHexString(text.hashCode()) + "-" + System.currentTimeMillis().toString(36)
        inline[id] = title to text
        return DocumentSource.Inline(id)
    }

    suspend fun load(source: DocumentSource): LoadedDocument = withContext(Dispatchers.IO) {
        when (source) {
            is DocumentSource.Local -> loadLocal(source)
            is DocumentSource.Remote -> loadRemote(source)
            is DocumentSource.Inline -> {
                val (title, text) = inline[source.id] ?: throw IOException("This pasted document is no longer available")
                LoadedDocument(source, title, text, null, writable = false)
            }
        }
    }

    /** Cheap change probe for live reload; null when unsupported. */
    suspend fun lastModified(source: DocumentSource): Long? = withContext(Dispatchers.IO) {
        if (source !is DocumentSource.Local) return@withContext null
        if (source.uri.scheme == "file") return@withContext source.uri.path?.let { java.io.File(it).lastModified() }
        queryLong(source.uri, DocumentsContract.Document.COLUMN_LAST_MODIFIED)
    }

    suspend fun save(source: DocumentSource.Local, text: String) = withContext(Dispatchers.IO) {
        // "wt" truncates; some providers only support "w", which may leave trailing bytes, so prefer "wt".
        val stream = runCatching { resolver.openOutputStream(source.uri, "wt") }.getOrNull()
            ?: resolver.openOutputStream(source.uri, "w")
            ?: throw IOException("Can't write to this document")
        stream.use { it.write(text.toByteArray(Charsets.UTF_8)) }
    }

    private fun loadLocal(source: DocumentSource.Local): LoadedDocument {
        val uri = source.uri
        val size = queryLong(uri, OpenableColumns.SIZE)
        if (size != null && size > MAX_BYTES) throw DocumentTooLargeException(size)
        val bytes = resolver.openInputStream(uri)?.use { it.readBounded() } ?: throw IOException("Can't open this document")
        val name = queryString(uri, OpenableColumns.DISPLAY_NAME) ?: uri.lastPathSegment?.substringAfterLast('/') ?: "Untitled"
        val flags = queryLong(uri, DocumentsContract.Document.COLUMN_FLAGS) ?: 0L
        val writable = uri.scheme == "file" || flags and DocumentsContract.Document.FLAG_SUPPORTS_WRITE.toLong() != 0L
        return LoadedDocument(source, name, decode(bytes), lastModified(uri), writable)
    }

    private fun lastModified(uri: Uri): Long? =
        if (uri.scheme == "file") uri.path?.let { java.io.File(it).lastModified() } else queryLong(uri, DocumentsContract.Document.COLUMN_LAST_MODIFIED)

    private fun loadRemote(source: DocumentSource.Remote): LoadedDocument {
        val connection = (URL(source.url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 20_000
            instanceFollowRedirects = true
            setRequestProperty("Accept", "text/markdown, text/plain;q=0.9, */*;q=0.5")
            setRequestProperty("User-Agent", "Markview/1.0 (Android)")
        }
        try {
            val code = connection.responseCode
            if (code !in 200..299) throw IOException("Server responded with HTTP $code")
            val length = connection.contentLengthLong
            if (length > MAX_BYTES) throw DocumentTooLargeException(length)
            val bytes = connection.inputStream.use { it.readBounded() }
            val name = Uri.decode(source.url.substringBefore('?').trimEnd('/').substringAfterLast('/')).ifBlank { source.url }
            return LoadedDocument(source, name, decode(bytes), connection.lastModified.takeIf { it > 0 }, writable = false)
        } finally {
            connection.disconnect()
        }
    }

    private fun java.io.InputStream.readBounded(): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(16 * 1024)
        var total = 0L
        while (true) {
            val read = read(buffer)
            if (read < 0) break
            total += read
            if (total > MAX_BYTES) throw DocumentTooLargeException(total)
            out.write(buffer, 0, read)
        }
        return out.toByteArray()
    }

    private fun decode(bytes: ByteArray): String {
        // Strip a UTF-8 BOM; fall back to Latin-1 when the bytes aren't valid UTF-8.
        val start = if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()) 3 else 0
        val decoder = Charsets.UTF_8.newDecoder()
        return runCatching { decoder.decode(java.nio.ByteBuffer.wrap(bytes, start, bytes.size - start)).toString() }
            .getOrElse { String(bytes, start, bytes.size - start, Charsets.ISO_8859_1) }
    }

    private fun queryString(uri: Uri, column: String): String? = runCatching {
        resolver.query(uri, arrayOf(column), null, null, null)?.use { c ->
            if (c.moveToFirst() && !c.isNull(0)) c.getString(0) else null
        }
    }.getOrNull()

    private fun queryLong(uri: Uri, column: String): Long? = runCatching {
        resolver.query(uri, arrayOf(column), null, null, null)?.use { c ->
            if (c.moveToFirst() && !c.isNull(0)) c.getLong(0) else null
        }
    }.getOrNull()

    companion object {
        const val MAX_BYTES = 8L * 1024 * 1024
    }
}
