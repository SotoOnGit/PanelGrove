package dev.soto.panelgrove.engine

import dev.soto.panelgrove.R
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.Rect
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.LruCache
import dev.soto.panelgrove.core.*
import dev.soto.panelgrove.storage.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.apache.commons.compress.archivers.zip.ZipFile
import java.io.File
import java.io.Closeable
import java.io.InputStream

data class PageRegion(val left: Float = 0f, val top: Float = 0f, val right: Float = 1f, val bottom: Float = 1f)
data class CacheUsage(val bitmapBytes: Int, val bitmapBudget: Int, val pageBytes: Long, val pageBudget: Long, val largestBitmapBytes: Int)

interface DocumentSession : Closeable {
    val book: Book
    val index: DocumentIndex
    suspend fun pageFile(index: Int): File
    suspend fun pdf(index: Int, width: Int, height: Int, region: PageRegion): File = error("Not a PDF")
}

class DocumentEngine(private val context: Context, val sources: SourceAccess) {
    private val strings get() = dev.soto.panelgrove.ui.StringDictionary(context.resources)
    private val decoder = DecoderClient(context)
    private val tree = TreeAccess(context)
    private val directory = File(context.cacheDir, "pages").apply { mkdirs() }
    private val lock = Mutex()
    private val cleanup = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    fun release(session: DocumentSession) { cleanup.launch { lock.withLock { session.close() } } }
    private val budget = minOf(Runtime.getRuntime().maxMemory() / 6, 48L * 1024 * 1024).toInt()
    private val memory = object : LruCache<String, Bitmap>(budget) { override fun sizeOf(key: String, value: Bitmap) = value.allocationByteCount }
    private var largestBitmap = 0
    fun cacheUsage() = CacheUsage(memory.size(), budget, directory.listFiles().orEmpty().sumOf { it.length() }, 512L * 1024 * 1024, largestBitmap)
    fun clearMemory() = memory.evictAll()

    suspend fun open(book: Book, progress: suspend (String) -> Unit = {}): DocumentSession {
        var opened: DocumentSession? = null
        try { return withContext(Dispatchers.IO) { when (book.format) {
            BookFormat.ZIP -> openZip(book, progress)
            BookFormat.RAR, BookFormat.SEVEN_Z -> openNativeArchive(book, progress)
            BookFormat.PDF -> openPdf(book, progress)
            BookFormat.FOLDER -> openFolder(book)
        }.also { opened = it; currentCoroutineContext().ensureActive() } }
        } catch (error: Exception) { opened?.close(); throw error }
    }
    private fun cachedFile(book: Book, page: String, suffix: String = "image") = File(directory, digest("${book.id}:${book.revision}:$page:$suffix"))
    private suspend fun publish(file: File, write: suspend (File) -> Unit): File {
        if (!file.exists()) {
            val partial = File(directory, file.name + ".${java.util.UUID.randomUUID()}.part")
            try { write(partial); currentCoroutineContext().ensureActive(); check(partial.renameTo(file) || file.exists()) { strings[R.string.could_not_cache_the_page] } }
            finally { partial.delete() }
        }
        file.setLastModified(System.currentTimeMillis())
        trimDisk(file)
        return file
    }
    private suspend fun readMetadata(input: InputStream): String = input.use {
        val output = java.io.ByteArrayOutputStream()
        copyBounded(it, output, ArchiveRules.MAX_METADATA_BYTES)
        output.toString("UTF-8")
    }
    private suspend fun <T> optional(block: suspend () -> T): T? = try { block() }
        catch (error: Exception) { if (error is kotlinx.coroutines.CancellationException) throw error; null }
    private fun imageBounds(input: InputStream): Pair<Int, Int> = input.use {
        val header = ByteArray(128 * 1024); var bytes = 0
        while (bytes < header.size) { val count = it.read(header, bytes, header.size - bytes); if (count <= 0) break; bytes += count }
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(header, 0, bytes, options)
        options.outWidth.coerceAtLeast(0) to options.outHeight.coerceAtLeast(0)
    }
    private suspend fun openZip(book: Book, progress: suspend (String) -> Unit): DocumentSession {
        val stream = ParcelFileDescriptor.AutoCloseInputStream(sources.seekable(Uri.parse(book.uri), book.revision, progress))
        val zip = try { ZipFile.builder().setSeekableByteChannel(stream.channel).get() } catch (e: Exception) { stream.close(); throw e }
        try {
            val entries = zip.entries.toList()
            require(entries.size <= ArchiveRules.MAX_ENTRIES) { strings[R.string.this_archive_contains_too_many_entries] }
            var expanded = 0L
            val regular = entries.filter { !it.isDirectory }.associateBy { entry ->
                val name = ArchiveRules.safePath(entry.name)
                require(!entry.generalPurposeBit.usesEncryption()) { strings[R.string.encrypted_archives_are_not_supported] }
                require(!entry.isUnixSymlink) { strings[R.string.archive_links_are_not_supported] }
                require(entry.size in 0..ArchiveRules.MAX_PAGE_BYTES) { strings[R.string.an_archive_entry_exceeds_the_supported_size_limit] }
                expanded += entry.size; require(expanded <= ArchiveRules.MAX_TOTAL_EXPANDED) { strings[R.string.archive_exceeds_extraction_limits] }
                name
            }
            require(regular.size == entries.count { !it.isDirectory }) { strings[R.string.archive_contains_duplicate_filenames] }
            val pages = regular.filterKeys(ArchiveRules::isImage).map { (name, entry) ->
                currentCoroutineContext().ensureActive()
                val dimensions = runCatching { imageBounds(zip.getInputStream(entry)) }.getOrDefault(0 to 0)
                PageRef(name, name, dimensions.first, dimensions.second)
            }
            val translation = regular["translation-manifest.json"]?.let { optional { readMetadata(zip.getInputStream(it)) } }
            val comic = regular.entries.firstOrNull { it.key.equals("ComicInfo.xml", true) }?.value?.let { optional { readMetadata(zip.getInputStream(it)) } }
            val index = MetadataReader.index(pages, translation, comic)
            return object : DocumentSession {
                override val book = book
                override val index = index
                override suspend fun pageFile(index: Int): File = publish(cachedFile(book, this.index.pages[index].locator)) { file ->
                    val entry = regular[this.index.pages[index].locator] ?: error(strings[R.string.this_page_is_missing])
                    val crc = java.util.zip.CRC32()
                    java.util.zip.CheckedInputStream(zip.getInputStream(entry), crc).use { input ->
                        file.outputStream().use { output -> copyBounded(input, output, ArchiveRules.MAX_PAGE_BYTES) }
                    }
                    require(entry.crc < 0 || crc.value == entry.crc) { strings[R.string.this_archive_page_failed_its_integrity_check] }
                }
                override fun close() { zip.close(); stream.close() }
            }
        } catch (e: Exception) { zip.close(); stream.close(); throw e }
    }
    private suspend fun openNativeArchive(book: Book, progress: suspend (String) -> Unit): DocumentSession {
        progress(strings[R.string.preparing_the_archive_page_index])
        val names = sources.seekable(Uri.parse(book.uri), book.revision, progress).use { input ->
            decoder.request { it.index(input, book.format.name) }.getStringArray("entries")?.toList()
                ?: error(strings[R.string.the_archive_page_index_could_not_be_read])
        }
        names.forEach(ArchiveRules::safePath)
        require(names.size == names.distinct().size) { strings[R.string.archive_contains_duplicate_filenames] }
        suspend fun extract(name: String, limit: Long): File = publish(cachedFile(book, name)) { target ->
            sources.seekable(Uri.parse(book.uri), book.revision, progress).use { input ->
                ParcelFileDescriptor.open(target, ParcelFileDescriptor.MODE_CREATE or ParcelFileDescriptor.MODE_TRUNCATE or ParcelFileDescriptor.MODE_WRITE_ONLY).use { output ->
                    decoder.request { it.extract(input, name, output, limit) }
                }
            }
        }
        val translation = names.find { it == "translation-manifest.json" }?.let { optional { extract(it, ArchiveRules.MAX_METADATA_BYTES).readText() } }
        val comic = names.find { it.equals("ComicInfo.xml", true) }?.let { optional { extract(it, ArchiveRules.MAX_METADATA_BYTES).readText() } }
        val index = MetadataReader.index(names.filter(ArchiveRules::isImage).map { PageRef(it, it) }, translation, comic)
        return object : DocumentSession {
            override val book = book
            override val index = index
            override suspend fun pageFile(index: Int) = extract(this.index.pages[index].locator, ArchiveRules.MAX_PAGE_BYTES)
            override fun close() = Unit
        }
    }
    private suspend fun openPdf(book: Book, progress: suspend (String) -> Unit): DocumentSession {
        val result = sources.seekable(Uri.parse(book.uri), book.revision, progress).use { input -> decoder.request { it.index(input, "PDF") } }
        val widths = result.getIntArray("widths") ?: error(strings[R.string.could_not_read_pdf_pages])
        val heights = result.getIntArray("heights") ?: error(strings[R.string.could_not_read_pdf_pages])
        val index = DocumentIndex(widths.indices.map { PageRef("pdf:$it", it.toString(), widths[it], heights[it]) })
        return object : DocumentSession {
            override val book = book
            override val index = index
            override suspend fun pageFile(index: Int) = pdf(index, 1080, 1600, PageRegion())
            override suspend fun pdf(index: Int, width: Int, height: Int, region: PageRegion): File =
                publish(cachedFile(book, index.toString(), "$width:$height:$region")) { target ->
                    sources.seekable(Uri.parse(book.uri), book.revision, progress).use { input ->
                        ParcelFileDescriptor.open(target, ParcelFileDescriptor.MODE_CREATE or ParcelFileDescriptor.MODE_TRUNCATE or ParcelFileDescriptor.MODE_WRITE_ONLY).use { output ->
                            decoder.request { it.renderPdf(input, index, width, height, region.left, region.top, region.right, region.bottom, output) }
                        }
                    }
                }
            override fun close() = Unit
        }
    }
    private suspend fun openFolder(book: Book): DocumentSession {
        val files = tree.children(Uri.parse(book.uri)).filter { !it.directory }.associateBy { it.name }
        val pages = files.filterKeys(ArchiveRules::isImage).map { (name, source) ->
            currentCoroutineContext().ensureActive()
            val dimensions = runCatching { imageBounds(sources.input(source.uri)) }.getOrDefault(0 to 0)
            PageRef(name, name, dimensions.first, dimensions.second)
        }
        val translation = files["translation-manifest.json"]?.let { optional { readMetadata(sources.input(it.uri)) } }
        val comic = files.entries.find { it.key.equals("ComicInfo.xml", true) }?.value?.let { optional { readMetadata(sources.input(it.uri)) } }
        val index = MetadataReader.index(pages, translation, comic)
        return object : DocumentSession {
            override val book = book
            override val index = index
            override suspend fun pageFile(index: Int): File = publish(cachedFile(book, this.index.pages[index].locator)) { target ->
                val source = files[this.index.pages[index].locator] ?: error(strings[R.string.page_is_missing_from_the_folder])
                sources.input(source.uri).use { input -> target.outputStream().use { copyBounded(input, it, ArchiveRules.MAX_PAGE_BYTES) } }
            }
            override fun close() = Unit
        }
    }
    suspend fun render(session: DocumentSession, page: Int, width: Int, height: Int,
        region: PageRegion = PageRegion()): Bitmap = withContext(Dispatchers.IO) {
        lock.withLock {
            currentCoroutineContext().ensureActive()
            require(page in session.index.pages.indices) { strings[R.string.this_page_is_no_longer_in_the_book] }
            require(listOf(region.left, region.top, region.right, region.bottom).all { it.isFinite() && it in 0f..1f } && region.left < region.right && region.top < region.bottom) { strings[R.string.invalid_page_region] }
            val requestedWidth = width.coerceIn(1, 2048); val requestedHeight = height.coerceIn(1, 3072)
            val shrink = kotlin.math.sqrt((budget / 8.0 / (requestedWidth.toLong() * requestedHeight)).coerceAtMost(1.0))
            val w = (requestedWidth * shrink).toInt().coerceAtLeast(1); val h = (requestedHeight * shrink).toInt().coerceAtLeast(1)
            val key = "${session.book.id}:${session.book.revision}:$page:$w:$h:$region"
            memory.get(key)?.let { return@withLock it }
            val file = if (session.book.format == BookFormat.PDF) session.pdf(page, w, h, region) else session.pageFile(page)
            val bitmap = try {
                if (session.book.format == BookFormat.PDF) BitmapFactory.decodeFile(file.path) ?: error(strings[R.string.could_not_render_this_pdf_page])
                else decode(file, w, h, region)
            } catch (error: Exception) { file.delete(); throw error }
            try { currentCoroutineContext().ensureActive() } catch (error: Exception) { bitmap.recycle(); throw error }
            largestBitmap = maxOf(largestBitmap, bitmap.allocationByteCount)
            memory.put(key, bitmap)
            trimDisk(file)
            bitmap
        }
    }
    /** Copy the full source image; PDFs render at 2x points within the decoder's size limits. */
    suspend fun exportPage(session: DocumentSession, page: Int, destination: File) = withContext(Dispatchers.IO) {
        lock.withLock {
            require(page in session.index.pages.indices) { strings[R.string.this_page_is_no_longer_in_the_book] }
            val ref = session.index.pages[page]
            val source = if (session.book.format == BookFormat.PDF) {
                val scale = minOf(2.0, 2048.0 / ref.width.coerceAtLeast(1), 3072.0 / ref.height.coerceAtLeast(1))
                session.pdf(page, (ref.width * scale).toInt().coerceAtLeast(1), (ref.height * scale).toInt().coerceAtLeast(1), PageRegion())
            } else session.pageFile(page)
            source.inputStream().use { input -> destination.outputStream().use { copyBounded(input, it, ArchiveRules.MAX_PAGE_BYTES) } }
        }
    }
    @Suppress("DEPRECATION")
    private fun decode(file: File, width: Int, height: Int, region: PageRegion): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        require(bounds.outWidth in 1..100_000 && bounds.outHeight in 1..100_000) { strings[R.string.this_page_is_corrupt_or_has_unsupported_dimensions] }
        val rect = Rect((region.left * bounds.outWidth).toInt().coerceIn(0, bounds.outWidth - 1),
            (region.top * bounds.outHeight).toInt().coerceIn(0, bounds.outHeight - 1),
            (region.right * bounds.outWidth).toInt().coerceIn(1, bounds.outWidth),
            (region.bottom * bounds.outHeight).toInt().coerceIn(1, bounds.outHeight))
        var sample = 1
        while ((rect.width() / sample > width * 2 || rect.height() / sample > height * 2 ||
                rect.width().toLong() / sample * rect.height() / sample > minOf(8_000_000, budget / 8))) sample *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        if (region == PageRegion()) return BitmapFactory.decodeFile(file.path, options) ?: error(strings[R.string.this_image_is_corrupt_or_unsupported])
        val decoder = BitmapRegionDecoder.newInstance(file.path, false) ?: error(strings[R.string.cannot_zoom_this_image])
        return try { decoder.decodeRegion(rect, options) ?: error(strings[R.string.cannot_render_this_region]) } finally { decoder.recycle() }
    }
    private fun trimDisk(keep: File) {
        val files = directory.listFiles()?.filter { it.isFile && !it.name.endsWith(".part") }?.sortedBy { it.lastModified() } ?: return
        var bytes = files.sumOf { it.length() }
        for (file in files) if (file != keep && bytes > 512L * 1024 * 1024) { val size = file.length(); if (file.delete()) bytes -= size }
    }
}
