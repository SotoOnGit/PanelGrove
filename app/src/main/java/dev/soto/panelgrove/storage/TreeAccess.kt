package dev.soto.panelgrove.storage

import dev.soto.panelgrove.R
import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract

data class TreeChild(val uri: Uri, val name: String, val directory: Boolean, val size: Long, val modified: Long)
class TreeAccess(private val context: Context) {
    private val strings get() = dev.soto.panelgrove.ui.StringDictionary(context.resources)
    fun root(tree: Uri): Uri = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
    fun children(folder: Uri): List<TreeChild> {
        val id = DocumentsContract.getDocumentId(folder)
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(folder, id)
        val columns = arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE, DocumentsContract.Document.COLUMN_SIZE, DocumentsContract.Document.COLUMN_LAST_MODIFIED)
        val result = mutableListOf<TreeChild>()
        context.contentResolver.query(children, columns, null, null, null)?.use { cursor ->
            while (cursor.moveToNext()) {
                require(result.size < 20_000) { strings[R.string.this_folder_contains_too_many_files] }
                result += TreeChild(DocumentsContract.buildDocumentUriUsingTree(folder, cursor.getString(0)), cursor.getString(1) ?: strings[R.string.untitled],
                    cursor.getString(2) == DocumentsContract.Document.MIME_TYPE_DIR,
                    if (cursor.isNull(3)) 0 else cursor.getLong(3), if (cursor.isNull(4)) 0 else cursor.getLong(4))
            }
        } ?: error(strings[R.string.this_folder_is_unavailable_grant_access_again])
        return result
    }
}
