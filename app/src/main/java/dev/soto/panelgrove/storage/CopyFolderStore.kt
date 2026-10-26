package dev.soto.panelgrove.storage

import dev.soto.panelgrove.R
import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import dev.soto.panelgrove.core.*
import kotlinx.coroutines.*
import kotlinx.serialization.Serializable
import java.io.File
import java.util.UUID

class CopyFolderRequiredException : IllegalStateException("Choose an import folder in Settings to save manga copies.")
data class PublishedCopy(val uri: Uri, val root: Uri, val ticket: File)

@Serializable private data class CopyWrite(
    val root: String, val location: String,
)

/** Writes only newly-created provider documents. Existing documents are never overwritten.
 * The small private journal lets startup remove an interrupted provider write after process death. */
class CopyFolderStore(private val context: Context, private val sources: SourceAccess) {
    private val strings get() = dev.soto.panelgrove.ui.StringDictionary(context.resources)
    private val resolver get() = context.contentResolver
    private val journals = File(context.filesDir, "copy-writes").apply { mkdirs() }
    fun root(uri: Uri): Uri {
        require(uri.scheme == "content" && DocumentsContract.isTreeUri(uri)) { strings[R.string.choose_a_folder_using_android_s_folder_picker] }
        return TreeAccess(context).root(uri)
    }
    private fun withTreeGrant(tree: Uri, document: Uri): Uri = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getDocumentId(document))
    fun checkWritable(uri: Uri): String {
        val document = root(uri)
        check(resolver.persistedUriPermissions.any {
            it.isReadPermission && it.isWritePermission && sources.identity(it.uri.toString()) == sources.identity(uri.toString())
        }) { strings[R.string.the_import_folder_needs_read_and_write_access_choose] }
        resolver.query(document, arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_FLAGS, DocumentsContract.Document.COLUMN_MIME_TYPE), null, null, null)?.use { cursor ->
            check(cursor.moveToFirst()) { strings[R.string.the_import_folder_is_unavailable_choose_it_again_in] }
            check(cursor.getString(2) == DocumentsContract.Document.MIME_TYPE_DIR && cursor.getInt(1) and DocumentsContract.Document.FLAG_DIR_SUPPORTS_CREATE != 0) {
                strings[R.string.this_folder_does_not_allow_new_files_choose_a]
            }
            return cursor.getString(0) ?: strings[R.string.import_folder]
        }
        error(strings[R.string.the_import_folder_is_unavailable_choose_it_again_in])
    }
    fun available(uri: String?): Boolean = uri != null && runCatching { checkWritable(Uri.parse(uri)) }.isSuccess
    fun displayLocation(uri: String, name: String?): String = runCatching {
        val tree = Uri.parse(uri)
        if (tree.authority == "com.android.externalstorage.documents") {
            val id = DocumentsContract.getTreeDocumentId(tree)
            val volume = id.substringBefore(':')
            val storage = if (volume == "primary") strings[R.string.internal_storage] else strings[R.string.storage, volume]
            listOf(storage, id.substringAfter(':', "")).filter { it.isNotBlank() }.joinToString(" / ")
        } else name ?: strings[R.string.selected_folder]
    }.getOrDefault(name ?: strings[R.string.selected_folder])
    fun contains(tree: Uri, child: Uri): Boolean = runCatching {
        if (tree.authority != child.authority) return@runCatching false
        if (android.os.Build.VERSION.SDK_INT >= 29 && runCatching { DocumentsContract.isChildDocument(resolver, root(tree), child) }.getOrDefault(false)) return@runCatching true
        // API 26–28 and providers without isChildDocument support: prove membership by
        // traversing granted children, never by assuming an opaque document ID is a path.
        val pending = java.util.ArrayDeque<Pair<Uri, Int>>()
        val seen = mutableSetOf<String>(); var visited = 0
        pending.add(root(tree) to 0)
        val target = sources.identity(child.toString())
        while (pending.isNotEmpty()) {
            val (folder, depth) = pending.removeFirst()
            if (depth > 40 || ++visited > 10_000) return@runCatching false
            if (!seen.add(sources.identity(folder.toString()))) continue
            for (entry in TreeAccess(context).children(folder)) {
                if (sources.identity(entry.uri.toString()) == target) return@runCatching true
                if (entry.directory) pending.add(entry.uri to depth + 1)
            }
        }
        false
    }.getOrDefault(false)
    fun organized(book: Book, folder: Uri): Boolean = runCatching {
        if (book.copyRootUri?.let(sources::identity) != sources.identity(folder.toString())) return@runCatching false
        val tree = TreeAccess(context)
        val series = tree.children(root(folder)).find { it.directory && it.name == CopyNaming.seriesFolder(book.series) } ?: return@runCatching false
        tree.children(series.uri).any { sources.identity(it.uri.toString()) == sources.identity(book.uri) }
    }.getOrDefault(false)
    private fun save(ticket: File, write: CopyWrite) {
        val part = File(journals, ticket.name + ".part")
        try {
            part.outputStream().use { output -> output.write(PanelgroveJson.encodeToString(write).toByteArray()); output.fdSyncIfFile() }
            check(part.renameTo(ticket)) { strings[R.string.could_not_save_import_recovery_information_check_free_storage] }
        } finally { part.delete() }
    }
    private fun java.io.OutputStream.fdSyncIfFile() { (this as? java.io.FileOutputStream)?.fd?.sync() }
    private fun cleanup(ticket: File, write: CopyWrite, live: Set<String>) {
        val uri = Uri.parse(write.location)
        if (sources.identity(write.location) in live) { ticket.delete(); return }
        if (!contains(Uri.parse(write.root), uri)) return
        val exists = runCatching { resolver.query(uri, arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID), null, null, null)?.use { it.moveToFirst() } == true }.getOrDefault(false)
        if (!exists || DocumentsContract.deleteDocument(resolver, uri)) ticket.delete()
    }
    fun recover(liveLocations: Set<String>) {
        val live = liveLocations.map(sources::identity).toSet()
        journals.listFiles().orEmpty().filter { it.extension == "json" }.forEach { ticket ->
            runCatching { cleanup(ticket, PanelgroveJson.decodeFromString<CopyWrite>(ticket.readText()), live) }
        }
    }
    fun committed(copy: PublishedCopy) { copy.ticket.delete() }
    fun discard(copy: PublishedCopy) {
        runCatching { cleanup(copy.ticket, PanelgroveJson.decodeFromString<CopyWrite>(copy.ticket.readText()), emptySet()) }
    }
    suspend fun publish(source: Uri, folder: Uri, series: String, filename: String, progress: suspend (String) -> Unit): PublishedCopy {
        var result: PublishedCopy? = null
        try { return withContext(Dispatchers.IO) {
            checkWritable(folder)
            val tree = TreeAccess(context)
            val seriesName = CopyNaming.seriesFolder(series)
            val parent = tree.children(root(folder)).find { it.name == seriesName }?.also {
                check(it.directory) { strings[R.string.a_file_named_already_exists_where_the_series_folder, seriesName] }
            }?.uri ?: DocumentsContract.createDocument(resolver, root(folder), DocumentsContract.Document.MIME_TYPE_DIR, seriesName)?.let { withTreeGrant(folder, it) }
                ?: error(strings[R.string.could_not_create_the_series_folder_check_folder_access])
            val names = tree.children(parent).map { it.name }.toSet()
            val safe = CopyNaming.filename(filename)
            val stem = safe.substringBeforeLast('.'); val ext = safe.substringAfterLast('.')
            val finalName = sequenceOf(safe).plus((2..10_000).asSequence().map { "$stem ($it).$ext" }).firstOrNull { it !in names }
                ?: error(strings[R.string.too_many_files_have_this_name_in_the_series])
            val ticket = File(journals, "${UUID.randomUUID()}.json")
            var created: Uri? = null
            try {
                // Create a new document; never open an existing original for writing. Providers
                // without atomic rename are supported. Library publication happens only after close.
                val uri = DocumentsContract.createDocument(resolver, parent, "application/octet-stream", finalName)?.let { withTreeGrant(folder, it) }
                    ?: error(strings[R.string.could_not_create_the_manga_copy])
                created = uri
                val write = CopyWrite(folder.toString(), uri.toString())
                save(ticket, write)
                progress(strings[R.string.saving_in, filename, seriesName])
                sources.input(source).use { input ->
                    (resolver.openOutputStream(uri, "wt") ?: error(strings[R.string.cannot_write_to_the_import_folder])).use { output ->
                        copyBounded(input, output, 4L * 1024 * 1024 * 1024, { bytes -> progress(strings[R.string.preparing_megabytes, bytes / (1024 * 1024)]) })
                    }
                }
                currentCoroutineContext().ensureActive()
                PublishedCopy(uri, folder, ticket).also { result = it }
            } catch (error: Exception) {
                created?.let { uri -> runCatching { if (DocumentsContract.deleteDocument(resolver, uri)) ticket.delete() } }
                if (error is CancellationException) throw error
                throw java.io.IOException(strings[R.string.could_not_save_the_manga_copy_check_the_import], error)
            }
        } } catch (error: Exception) { result?.let(::discard); throw error }
    }
}
