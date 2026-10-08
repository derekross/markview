package com.derekross.markview.core.data

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class FolderItem(
    val name: String,
    val documentUri: Uri,
    val treeUri: Uri,
    val isDirectory: Boolean,
    val lastModified: Long,
    val size: Long,
)

/** Browses folders granted through the Storage Access Framework. */
class FolderRepository(private val context: Context) {

    /** Lists sub-folders and Markdown files directly inside [parentDocumentId] (root when null). */
    suspend fun list(treeUri: Uri, parentDocumentId: String? = null): List<FolderItem> = withContext(Dispatchers.IO) {
        val parentId = parentDocumentId ?: DocumentsContract.getTreeDocumentId(treeUri)
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentId)
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED,
            DocumentsContract.Document.COLUMN_SIZE,
        )
        val items = mutableListOf<FolderItem>()
        runCatching {
            context.contentResolver.query(childrenUri, projection, null, null, null)?.use { c ->
                while (c.moveToNext()) {
                    val id = c.getString(0)
                    val name = c.getString(1) ?: continue
                    val mime = c.getString(2).orEmpty()
                    if (name.startsWith(".")) continue
                    val isDir = mime == DocumentsContract.Document.MIME_TYPE_DIR
                    if (!isDir && !isMarkdownName(name)) continue
                    items += FolderItem(
                        name = name,
                        documentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, id),
                        treeUri = treeUri,
                        isDirectory = isDir,
                        lastModified = if (c.isNull(3)) 0L else c.getLong(3),
                        size = if (c.isNull(4)) 0L else c.getLong(4),
                    )
                }
            }
        }
        items.sortedWith(compareByDescending<FolderItem> { it.isDirectory }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.name })
    }

    fun documentId(uri: Uri): String = DocumentsContract.getDocumentId(uri)

    companion object {
        private val EXTENSIONS = setOf("md", "markdown", "mdown", "mkd", "mkdn", "mdwn", "mdx", "txt", "text")

        fun isMarkdownName(name: String): Boolean = name.substringAfterLast('.', "").lowercase() in EXTENSIONS
    }
}
