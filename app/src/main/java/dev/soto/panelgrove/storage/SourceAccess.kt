package dev.soto.panelgrove.storage

import dev.soto.panelgrove.R
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import android.provider.DocumentsContract
import android.system.Os
import android.system.OsConstants
import androidx.documentfile.provider.DocumentFile
import dev.soto.panelgrove.core.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.util.UUID

data class SourceDetails(val name: String, val size: Long, val modified: Long) {
    val revision get() = "$size:$modified"
}

class SourceAccess(private val context: Context, private val usableBytes: (File) -> Long = { it.usableSpace }) {
    private val strings get() = dev.soto.panelgrove.ui.StringDictionary(context.resources)
    val managedDirectory = File(context.filesDir, "books").apply { mkdirs() }
    private val copyStaging = File(context.cacheDir, "copy-imports").apply { mkdirs() }
    private val staging = File(context.cacheDir, "sources").apply { mkdirs() }
    private val stageMutex = Mutex()
    private val resolver get() = context.contentResolver
    val copies by lazy { CopyFolderStore(context, this) }
    fun format(uri: Uri): BookFormat? = input(uri).use { source ->
        val header = ByteArray(8)
        var count = 0
        while (count < header.size) { val read = source.read(header, count, header.size - count); if (read <= 0) break; count += read }
        ArchiveRules.detectFormat(header.copyOf(count))
    }

    fun identity(location: String): String {
        val uri = Uri.parse(location)
        if (uri.scheme != "content") return location
        return runCatching {
            val id = if (uri.pathSegments.contains("document")) DocumentsContract.getDocumentId(uri)
                else DocumentsContract.getTreeDocumentId(uri)
            "document:${uri.authority}:${id}"
        }.getOrDefault(location)
    }
    fun available(book: Book): Boolean = runCatching {
        if (book.format == BookFormat.FOLDER) TreeAccess(context).children(Uri.parse(book.uri))
        else open(Uri.parse(book.uri)).use { }
    }.isSuccess
    fun stagingBytes(): Long = staging.listFiles().orEmpty().sumOf { it.length() }
    private fun checkSpace(directory: File, expected: Long) {
        require(expected <= 4L * 1024 * 1024 * 1024) { strings[R.string.this_file_exceeds_the_supported_size_limit] }
        check(usableBytes(directory) >= expected.coerceAtLeast(0) + 8L * 1024 * 1024) { strings[R.string.not_enough_free_storage_for_a_local_copy_free] }
    }

    fun managedFile(uri: Uri): File {
        require(uri.scheme == "panelgrove" && uri.authority in setOf("managed", "staging")) { strings[R.string.this_source_is_not_managed_by_panelgrove] }
        val filename = uri.lastPathSegment ?: error(strings[R.string.invalid_managed_source])
        require(filename.matches(Regex("[a-zA-Z0-9._-]+"))) { strings[R.string.invalid_managed_filename] }
        val directory = if (uri.authority == "staging") copyStaging else managedDirectory
        return File(directory, filename).also { require(it.canonicalFile.parentFile == directory.canonicalFile) }
    }
    fun details(uri: Uri): SourceDetails {
        if (uri.scheme == "panelgrove") return managedFile(uri).let { SourceDetails(it.name, it.length(), it.lastModified()) }
        require(uri.scheme == "content") { strings[R.string.choose_a_document_using_android_s_file_picker] }
        var name = uri.lastPathSegment ?: strings[R.string.untitled]; var size = -1L
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val n = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME); val s = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (n >= 0) name = cursor.getString(n) ?: name
                if (s >= 0 && !cursor.isNull(s)) size = cursor.getLong(s)
            }
        }
        val modified = runCatching { DocumentFile.fromSingleUri(context, uri)?.lastModified() ?: 0 }.getOrDefault(0)
        return SourceDetails(name, size, modified)
    }
    fun persist(uri: Uri, write: Boolean = false): Boolean = runCatching {
        resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or if (write) Intent.FLAG_GRANT_WRITE_URI_PERMISSION else 0)
        true
    }.getOrDefault(false)
    fun deleteCopy(book: Book): Boolean {
        require(book.managed) { strings[R.string.this_file_is_an_external_original] }
        val uri = Uri.parse(book.uri)
        if (uri.scheme == "panelgrove") return managedFile(uri).let { !it.exists() || it.delete() }
        val root = Uri.parse(book.copyRootUri ?: error(strings[R.string.this_copy_has_no_destination_folder]))
        require(copies.contains(root, uri) && book.originUri?.let(::identity) != identity(book.uri)) { strings[R.string.this_file_is_outside_its_copy_folder] }
        return DocumentsContract.deleteDocument(resolver, uri)
    }
    fun open(uri: Uri): ParcelFileDescriptor = if (uri.scheme == "panelgrove")
        ParcelFileDescriptor.open(managedFile(uri), ParcelFileDescriptor.MODE_READ_ONLY)
    else { require(uri.scheme == "content"); resolver.openFileDescriptor(uri, "r") ?: error(strings[R.string.the_source_is_unavailable]) }
    fun input(uri: Uri): InputStream = if (uri.scheme == "panelgrove") managedFile(uri).inputStream()
    else { require(uri.scheme == "content"); resolver.openInputStream(uri) ?: error(strings[R.string.the_source_is_unavailable]) }
    suspend fun contentHash(uri: Uri): String = withContext(Dispatchers.IO) {
        val hash = MessageDigest.getInstance("SHA-256")
        input(uri).use { source -> copyBounded(source, object : OutputStream() {
            override fun write(value: Int) = Unit
            override fun write(bytes: ByteArray, offset: Int, length: Int) = Unit
        }, 4L * 1024 * 1024 * 1024, hash = hash) }
        hash.digest().joinToString("") { "%02x".format(it) }
    }

    suspend fun seekable(uri: Uri, revision: String, progress: suspend (String) -> Unit = {}): ParcelFileDescriptor = withContext(Dispatchers.IO) {
        val fd = open(uri)
        if (runCatching { Os.lseek(fd.fileDescriptor, 0, OsConstants.SEEK_SET) }.isSuccess) return@withContext fd
        fd.close()
        stageMutex.withLock {
            val file = File(staging, digest(uri.toString() + revision))
            if (!file.exists()) {
                trimStaging(null)
                checkSpace(staging, details(uri).size)
                val partial = File(staging, file.name + ".part")
                progress(strings[R.string.preparing_a_local_working_copy])
                try {
                    input(uri).use { source -> partial.outputStream().use { output -> copyBounded(source, output, 4L * 1024 * 1024 * 1024,
                        { bytes -> progress(strings[R.string.preparing_megabytes, bytes / (1024 * 1024)]) }) } }
                    check(partial.renameTo(file)) { strings[R.string.could_not_finish_the_working_copy] }
                } finally { partial.delete() }
            }
            trimStaging(file)
            file.setLastModified(System.currentTimeMillis())
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        }
    }
    suspend fun copyManaged(uri: Uri, name: String, progress: suspend (String) -> Unit): Pair<Uri, String> = copyLocally(uri, name, false, progress)
    suspend fun stageCopy(uri: Uri, name: String, progress: suspend (String) -> Unit): Pair<Uri, String> = copyLocally(uri, name, true, progress)
    private suspend fun copyLocally(uri: Uri, name: String, temporary: Boolean, progress: suspend (String) -> Unit): Pair<Uri, String> {
        var finished: File? = null
        try { return withContext(Dispatchers.IO) {
        val directory = if (temporary) copyStaging else managedDirectory
        checkSpace(directory, details(uri).size)
        val filename = "${UUID.randomUUID()}.${name.substringAfterLast('.', "cbz").lowercase().filter { it.isLetterOrDigit() }.take(8)}"
        val target = File(directory, filename); val partial = File(directory, "${filename}.part")
        try {
            val hash = MessageDigest.getInstance("SHA-256")
            input(uri).use { source -> partial.outputStream().use { output -> copyBounded(source, output, 4L * 1024 * 1024 * 1024,
                { bytes -> progress(strings[R.string.preparing_megabytes, bytes / (1024 * 1024)]) }, hash) } }
            currentCoroutineContext().ensureActive()
            check(partial.renameTo(target)) { strings[R.string.could_not_finish_the_managed_copy] }
            finished = target
            Uri.parse("panelgrove://${if (temporary) "staging" else "managed"}/$filename") to hash.digest().joinToString("") { "%02x".format(it) }
        } finally { partial.delete() }
        } } catch (error: Exception) { finished?.delete(); throw error }
    }
    private fun trimStaging(keep: File?) {
        val files = staging.listFiles()?.filter { it.isFile && !it.name.endsWith(".part") }?.sortedBy { it.lastModified() } ?: return
        var total = files.sumOf { it.length() }
        for (file in files) if (file != keep && total > 1024L * 1024 * 1024) { val bytes = file.length(); if (file.delete()) total -= bytes }
    }
}

suspend fun copyBounded(input: InputStream, output: OutputStream, limit: Long,
    progress: suspend (Long) -> Unit = {}, hash: MessageDigest? = null) {
    val buffer = ByteArray(64 * 1024); var total = 0L; var lastProgress = 0L
    while (true) {
        currentCoroutineContext().ensureActive()
        val read = input.read(buffer); if (read < 0) break
        total += read; require(total <= limit) { "This file exceeds the supported size limit." }
        try { output.write(buffer, 0, read) } catch (error: java.io.IOException) {
            throw java.io.IOException("Could not write the local copy. Check free storage and try again.", error)
        }
        hash?.update(buffer, 0, read)
        if (total - lastProgress > 8 * 1024 * 1024) { progress(total); lastProgress = total }
    }
}
fun digest(value: String): String = MessageDigest.getInstance("SHA-256").digest(value.toByteArray()).joinToString("") { "%02x".format(it) }
