package dev.soto.panelgrove.storage

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.core.content.FileProvider
import dev.soto.panelgrove.R
import dev.soto.panelgrove.core.*
import dev.soto.panelgrove.engine.DocumentEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import java.io.File
import java.util.UUID

@Serializable data class ExportedPage(val id: String, val filename: String, val mimeType: String, val share: Boolean)

/** Only completed export snapshots are exposed to other apps, with temporary read grants. */
class PageExportStore(private val context: Context, private val engine: DocumentEngine) {
    private val directory = File(context.cacheDir, "exports").apply { mkdirs() }
    private val strings get() = dev.soto.panelgrove.ui.StringDictionary(context.resources)
    fun file(page: ExportedPage): File {
        require(Regex("[A-Za-z0-9_-]{1,100}").matches(page.id))
        require(page.filename.isNotBlank() && page.filename != "." && page.filename != ".." &&
            page.filename.none { it == '/' || it == '\\' || it == ':' || it.isISOControl() })
        return File(File(directory, page.id), page.filename)
    }

    suspend fun prepare(book: Book, pageId: String, share: Boolean): ExportedPage = withContext(Dispatchers.IO) {
        engine.open(book).use { session ->
            val index = session.index.pages.indexOfFirst { it.id == pageId }
            require(index >= 0) { strings[R.string.this_page_is_no_longer_in_the_book] }
            val folder = File(directory, UUID.randomUUID().toString()).apply { check(mkdirs()) }
            val partial = File(folder, "page.part")
            try {
                engine.exportPage(session, index, partial)
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(partial.path, bounds)
                val mime = bounds.outMimeType
                val extension = when (mime) {
                    "image/png" -> "png"; "image/jpeg" -> "jpg"; "image/webp" -> "webp"
                    else -> error(strings[R.string.this_image_is_corrupt_or_unsupported])
                }
                require(bounds.outWidth > 0 && bounds.outHeight > 0) { strings[R.string.this_image_is_corrupt_or_unsupported] }
                val language = book.editionLanguage.takeIf { it.isNotBlank() }?.let { "-$it" }.orEmpty()
                val name = CopyNaming.filename("${book.title}$language-page-${(index + 1).toString().padStart(3, '0')}.$extension")
                val exported = ExportedPage(folder.name, name, mime, share)
                currentCoroutineContext().ensureActive()
                check(partial.renameTo(file(exported))) { strings[R.string.page_export_failed] }
                exported
            } finally {
                partial.delete()
                if (folder.listFiles().isNullOrEmpty()) folder.delete()
            }
        }
    }

    fun intent(page: ExportedPage): Intent = if (page.share) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.pageexports", file(page))
        Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
            type = page.mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newRawUri(page.filename, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }, strings[R.string.share_page])
    } else Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
        addCategory(Intent.CATEGORY_OPENABLE)
        type = page.mimeType
        putExtra(Intent.EXTRA_TITLE, page.filename)
    }

    suspend fun save(page: ExportedPage, uri: Uri) = withContext(Dispatchers.IO) {
        file(page).inputStream().use { input ->
            context.contentResolver.openOutputStream(uri, "wt")?.use { output ->
                copyBounded(input, output, ArchiveRules.MAX_PAGE_BYTES)
            } ?: error(strings[R.string.page_export_failed])
        }
    }

    fun discard(page: ExportedPage) { file(page).delete(); file(page).parentFile?.delete() }

    /** Run at startup only, so a foreground share or document picker keeps its snapshot. */
    fun cleanExpired() {
        val cutoff = System.currentTimeMillis() - 24 * 60 * 60 * 1000L
        directory.listFiles().orEmpty().filter { it.isDirectory }.forEach { folder ->
            folder.listFiles().orEmpty().filter { it.name.endsWith(".part") || it.lastModified() < cutoff }.forEach { it.delete() }
            if (folder.listFiles().isNullOrEmpty()) folder.delete()
        }
    }
}
