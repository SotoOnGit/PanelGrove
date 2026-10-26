package dev.soto.panelgrove

import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.soto.panelgrove.core.*
import dev.soto.panelgrove.data.*
import dev.soto.panelgrove.engine.*
import dev.soto.panelgrove.storage.*
import kotlinx.coroutines.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.*
import java.util.zip.*

@RunWith(AndroidJUnit4::class)
class HardeningInstrumentedTest {
    private val app get() = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as PanelgroveApplication
    private val authority = "dev.soto.panelgrove.test.documents"
    private lateinit var database: LibraryDatabase
    private lateinit var library: LibraryRepository
    private lateinit var originalPreferences: AppPreferences
    private val copies = mutableListOf<File>()
    private fun uri(id: String) = DocumentsContract.buildDocumentUri(authority, "hardening/$id")
    private fun tree(id: String = "") = DocumentsContract.buildTreeDocumentUri(authority, "hardening" + if (id.isEmpty()) "" else "/$id")
    private fun configure(id: String = "", block: Bundle.() -> Unit) {
        app.contentResolver.call(Uri.parse("content://$authority"), "fixture", null, Bundle().apply {
            putString("id", "hardening" + if (id.isEmpty()) "" else "/$id"); block()
        })
    }
    private fun write(id: String, bytes: ByteArray, fixed: Boolean = false, missingTime: Boolean = false) {
        if ('/' in id) configure(id.substringBeforeLast('/')) { putBoolean("directory", true) }
        configure(id) { putBoolean("fixedTime", fixed); putBoolean("noTime", missingTime) }
        app.contentResolver.openOutputStream(uri(id), "wt")!!.use { it.write(bytes) }
    }
    @Before fun setup() = runBlocking {
        configure { putBoolean("reset", true); putBoolean("directory", true) }
        database = Room.inMemoryDatabaseBuilder(app, LibraryDatabase::class.java).build()
        library = LibraryRepository(app, database, app.preferences, app.sources, app.engine)
        originalPreferences = app.preferences.current()
        configure("copies") { putBoolean("directory", true); putBoolean("grantTree", true) }
        check(app.sources.persist(tree("copies"), write = true))
        app.preferences.update { it.copy(copyFolderUri = tree("copies").toString(), copyFolderName = "copies") }
    }
    @After fun cleanup() = runBlocking {
        app.preferences.set(originalPreferences)
        database.close()
        copies.forEach { it.delete() }
        configure { putBoolean("reset", true) }
    }
    private fun png(color: Int, width: Int = 64, height: Int = 96): ByteArray {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        return try { bitmap.eraseColor(color); ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray() }
        finally { bitmap.recycle() }
    }
    private fun archive(color: Int, ids: List<String> = listOf("a", "b", "c"), chapter: String = "1", corrupt: Boolean = false): ByteArray {
        val entries = ids.map { "$it.png" to if (corrupt && it == ids[0]) byteArrayOf(1, 2, 3) else png(color) } +
            ("translation-manifest.json" to """{"title":"Generated","pages":[${ids.joinToString { """{"archive_name":"$it.png","source_id":"stable/$it","chapter":"$chapter"}""" }}]}""".toByteArray())
        // Fixed-size STORED entries and padding force equal-length exports despite changed pixels/metadata.
        return ByteArrayOutputStream().also { bytes -> ZipOutputStream(bytes).use { zip -> entries.forEach { (name, raw) ->
            val data = raw.copyOf(16_384)
            // Metadata cannot be zero-padded JSON; use spaces instead.
            if (name.endsWith("json")) java.util.Arrays.fill(data, raw.size, data.size, 32.toByte())
            zip.putNextEntry(ZipEntry(name).apply { method = ZipEntry.STORED; size = data.size.toLong(); compressedSize = size; crc = CRC32().apply { update(data) }.value; time = 0 })
            zip.write(data); zip.closeEntry()
        } } }.toByteArray()
    }
    private suspend fun pixel(book: Book, index: Int = 0) = app.engine.open(book).use { app.engine.render(it, index, 64, 96).getPixel(10, 10) }

    @Test fun sameSizeCorrectionsRefreshStagingPixelsAndChapterWithUnreliableTimestamps() = runBlocking {
        for ((name, pipe) in listOf("fixed.cbz" to false, "missing.cbz" to true)) {
            val before = archive(Color.RED); val after = archive(Color.BLUE, chapter = "2")
            assertEquals(before.size, after.size)
            write(name, before, fixed = !pipe, missingTime = pipe)
            val source = if (pipe) DocumentsContract.buildDocumentUri(authority, "pipe:hardening/$name") else uri(name)
            val book = library.importFile(source)
            assertEquals(Color.RED, pixel(book))
            library.change(book.id) { it.copy(title = "Manual", metadataEdited = true, favorite = true, collections = listOf("Keep"), status = ReadingStatus.READ, settings = ReaderOverrides(direction = Direction.LTR)) }
            write(name, after, fixed = !pipe, missingTime = pipe)
            val refreshed = library.importFile(source)
            assertEquals(book.id, refreshed.id); assertNotEquals(book.revision, refreshed.revision)
            assertEquals("Manual", refreshed.title); assertTrue(refreshed.favorite)
            assertEquals(listOf("Keep"), refreshed.collections); assertEquals(ReadingStatus.READ, refreshed.status)
            assertEquals(Direction.LTR, refreshed.settings.direction); assertEquals(Color.BLUE, pixel(refreshed))
            app.engine.open(refreshed).use { assertEquals("2", it.index.pages[0].chapter) }
        }
    }
    @Test fun survivingStableIdsRemapAndRemovedBookmarksStayExplicit() = runBlocking {
        write("replace.cbz", archive(Color.RED))
        val before = library.importFile(uri("replace.cbz"))
        app.engine.open(before).use { session ->
            library.position(before.id, session.index.pages[1], 1, .4f)
            library.toggleBookmark(before, session.index.pages[1], 1)
            library.toggleBookmark(before, session.index.pages[2], 2)
        }
        write("replace.cbz", archive(Color.BLUE, listOf("new", "a", "b")))
        val after = library.importFile(uri("replace.cbz"))
        assertEquals(ReadingPosition("stable/b", 2, .4f), after.position)
        val marks = library.dao.bookmarks().map { it.bookmark() }
        assertEquals(2, marks.first { it.pageId == "stable/b" }.pageIndex)
        assertFalse(marks.first { it.pageId == "stable/b" }.missing)
        assertTrue(marks.first { it.pageId == "stable/c" }.missing)
        assertEquals(2, marks.first { it.pageId == "stable/c" }.pageIndex)
    }
    @Test fun aliasesOverlappingRootsAndSameSizeImageFolderChangesKeepOneEntry() = runBlocking {
        write("nested/page.png", png(Color.RED).copyOf(16_384), missingTime = true)
        write("chapter.cbz", archive(Color.GREEN))
        val direct = library.importFile(uri("chapter.cbz"))
        val root = library.addRoot(tree())
        assertTrue(library.scan(root.id).isEmpty())
        assertEquals(direct.id, library.dao.books().map { it.book() }.single { it.format == BookFormat.ZIP }.id)
        val nested = library.addRoot(tree("nested"))
        assertTrue(library.scan(nested.id).isEmpty())
        val folders = library.dao.books().map { it.book() }.filter { it.format == BookFormat.FOLDER }
        assertEquals(1, folders.size); assertEquals(Color.RED, pixel(folders.single()))
        write("nested/page.png", png(Color.BLUE).copyOf(16_384), missingTime = true)
        assertTrue(library.scan(root.id).isEmpty())
        assertEquals(Color.BLUE, pixel(library.book(folders.single().id)!!))
        assertEquals(2, library.dao.books().size)
        write("volume.cbz", archive(Color.GREEN))
        library.scan(root.id)
        assertEquals(3, library.dao.books().size) // Same page bytes do not merge chapter and volume documents.
    }
    @Test fun disconnectedAndRemovedOriginalDoesNotDisableManagedCopy() = runBlocking {
        write("root/book.cbz", archive(Color.RED))
        val root = library.addRoot(tree("root"))
        library.scan(root.id)
        val book = library.importFile(uri("root/book.cbz"), copy = true, root = root, relativePath = "book.cbz")
        configure("root") { putBoolean("denied", true) }
        assertTrue(runCatching { library.scan(root.id) }.isFailure)
        assertEquals(AccessState.AVAILABLE, library.book(book.id)!!.access)
        assertEquals(Color.RED, pixel(library.book(book.id)!!))
        configure("root") { putBoolean("denied", false) }
        configure("root/book.cbz") { putBoolean("reset", true) }
        library.scan(root.id)
        assertEquals(AccessState.AVAILABLE, library.book(book.id)!!.access)
    }
    @Test fun freshRestoreUnavailableCopyRelinkRepeatAndMalformedBackupAreSafe() = runBlocking {
        val incoming = Book("portable", "panelgrove://managed/unavailable.cbz", "book.cbz", BookFormat.ZIP, managed = true,
            favorite = true, collections = listOf("Portable"), position = ReadingPosition("stable/b", 1, .3f))
        val backup = LibraryBackup(books = listOf(incoming), bookmarks = listOf(Bookmark("portable-mark", incoming.id, "stable/b", 1)))
        write("backup.json", PanelgroveJson.encodeToString(backup).toByteArray())
        library.restoreBackup(uri("backup.json"))
        assertEquals(AccessState.NEEDS_PERMISSION, library.book(incoming.id)!!.access)
        app.preferences.update { it.copy(copyFolderUri = tree("copies").toString(), copyFolderName = "copies") }
        write("moved.cbz", archive(Color.BLUE))
        val relinked = library.importFile(uri("moved.cbz"), replaceId = incoming.id)
        assertEquals(.3f, relinked.position!!.offset, .001f)
        library.restoreBackup(uri("backup.json")); library.restoreBackup(uri("backup.json"))
        assertEquals(1, library.dao.books().size); assertEquals(1, library.dao.bookmarks().size)
        assertEquals(relinked.uri, library.book(incoming.id)!!.uri)
        assertEquals(AccessState.AVAILABLE, library.book(incoming.id)!!.access)
        val saved = library.dao.books()
        for (bad in listOf("{broken", PanelgroveJson.encodeToString(backup.copy(version = 99)))) {
            write("bad.json", bad.toByteArray())
            assertTrue(runCatching { library.restoreBackup(uri("bad.json")) }.isFailure)
            assertEquals(saved, library.dao.books()); assertNull(library.dao.pendingRestore())
        }
    }
    @Test fun interruptedPreferencesRestoreIsRecoveredFromDurableJournal() = runBlocking {
        val desired = AppPreferences(theme = AppTheme.DARK, reader = ReaderSettings(mode = ReadingMode.VERTICAL))
        library.dao.save(RestoreJournal(preferences = PanelgroveJson.encodeToString(desired)))
        val reopenedRepository = LibraryRepository(app, database, app.preferences, app.sources, app.engine)
        reopenedRepository.recoverRestore()
        assertEquals(desired, app.preferences.current()); assertNull(library.dao.pendingRestore())
        reopenedRepository.recoverRestore(); assertEquals(desired, app.preferences.current())
    }
    @Test fun populatedRestoreRemapsSourceBookBookmarkAndHistoryCollisions() = runBlocking {
        write("live/book.cbz", archive(Color.RED))
        val liveRoot = library.addRoot(tree("live")); library.scan(liveRoot.id)
        val live = library.dao.books().single().book()
        library.dao.save(Bookmark("collision-mark", live.id, "stable/a", 0).row())
        library.dao.save(HistoryEntry("collision-history", live.id, 1).row())
        write("incoming/book.cbz", archive(Color.BLUE))
        val incoming = live.copy(uri = uri("incoming/book.cbz").toString(), favorite = true)
        val backup = LibraryBackup(sources = listOf(liveRoot.copy(uri = tree("incoming").toString())), books = listOf(incoming),
            bookmarks = listOf(Bookmark("collision-mark", incoming.id, "stable/b", 1)),
            history = listOf(HistoryEntry("collision-history", incoming.id, 2)))
        write("collision.json", PanelgroveJson.encodeToString(backup).toByteArray())
        library.restoreBackup(uri("collision.json")); library.restoreBackup(uri("collision.json"))
        assertEquals(2, library.dao.books().size); assertEquals(2, library.dao.sources().size)
        assertEquals(2, library.dao.bookmarks().size); assertEquals(2, library.dao.history().size)
        assertEquals(live.uri, library.book(live.id)!!.uri); assertFalse(library.book(live.id)!!.favorite)
        val restored = library.dao.books().map { it.book() }.single { it.id != live.id }
        assertNotEquals(live.sourceId, restored.sourceId); assertTrue(restored.favorite)
        assertTrue(library.dao.bookmarks().map { it.bookmark() }.any { it.bookId == restored.id && it.pageId == "stable/b" })
        assertEquals(Color.BLUE, pixel(restored))
    }
    @Test fun folderRestoreRelinksRelocatedRootWithoutLosingReadingData() = runBlocking {
        val source = LibrarySource("portable-root", tree("old-root").toString(), "Portable folder")
        val book = Book("rooted-book", uri("old-root/book.cbz").toString(), "book.cbz", BookFormat.ZIP,
            sourceId = source.id, relativePath = "book.cbz", collections = listOf("Portable"), position = ReadingPosition("stable/b", 1, .6f))
        val backup = LibraryBackup(sources = listOf(source), books = listOf(book), bookmarks = listOf(Bookmark("rooted-mark", book.id, "stable/b", 1)))
        write("rooted.json", PanelgroveJson.encodeToString(backup).toByteArray())
        library.restoreBackup(uri("rooted.json"))
        write("moved-root/book.cbz", archive(Color.BLUE))
        val relocated = library.addRoot(tree("moved-root"), replaceId = source.id)
        library.scan(relocated.id)
        assertEquals(1, library.dao.books().size)
        assertEquals(ReadingPosition("stable/b", 1, .6f), library.book(book.id)!!.position)
        assertEquals(listOf("Portable"), library.book(book.id)!!.collections)
        library.restoreBackup(uri("rooted.json"))
        assertEquals(1, library.dao.sources().size); assertEquals(1, library.dao.books().size)
        assertEquals(AccessState.AVAILABLE, library.book(book.id)!!.access)
    }
    @Test fun stagingBudgetKeepsOneOversizeActiveFileAndEvictsOtherCopies() = runBlocking {
        write("staging.cbz", archive(Color.RED))
        val pipe = DocumentsContract.buildDocumentUri(authority, "pipe:hardening/staging.cbz")
        val directory = File(app.cacheDir, "sources")
        val active = File(directory, digest(pipe.toString() + "oversize-policy"))
        val obsolete = File(directory, "hardening-obsolete-stage")
        try {
            java.io.RandomAccessFile(active, "rw").use { it.setLength(1024L * 1024 * 1024 + 1) }
            java.io.RandomAccessFile(obsolete, "rw").use { it.setLength(600L * 1024 * 1024) }
            obsolete.setLastModified(1)
            app.sources.seekable(pipe, "oversize-policy").use { assertEquals(active.length(), it.statSize) }
            assertFalse(obsolete.exists())
            assertEquals(active.length(), app.sources.stagingBytes())
            assertTrue(active.length() > 1024L * 1024 * 1024)
        } finally { active.delete(); obsolete.delete() }
    }
    @Test fun cancelledManagedAndStagedCopiesCleanPartialsAndCanRetry() = runBlocking {
        val large = archive(Color.RED) + ByteArray(9 * 1024 * 1024)
        write("large.cbz", large)
        val before = app.sources.managedDirectory.listFiles().orEmpty().toSet()
        val cancelled = runCatching { library.importFile(uri("large.cbz"), copy = true) { if (it.startsWith("Preparing ")) throw CancellationException("Generated cancellation") } }.exceptionOrNull()
        assertTrue("Expected cancellation; received $cancelled", cancelled is CancellationException)
        assertEquals(before, app.sources.managedDirectory.listFiles().orEmpty().toSet())
        assertTrue(library.dao.books().isEmpty())
        val pipe = DocumentsContract.buildDocumentUri(authority, "pipe:hardening/large.cbz")
        assertTrue(runCatching { app.sources.seekable(pipe, "cancel-case") { if (it.startsWith("Preparing 8") || it.startsWith("Preparing 9")) throw CancellationException("Generated cancellation") }.close() }.exceptionOrNull() is CancellationException)
        assertFalse(File(app.cacheDir, "sources").listFiles().orEmpty().any { it.name.endsWith(".part") })
        write("large.cbz", archive(Color.BLUE))
        val retried = library.importFile(uri("large.cbz"), copy = true)
        assertEquals(Color.BLUE, pixel(retried))
    }
    @Test fun lowStorageAndWriteFailuresReturnActionableErrors() = runBlocking {
        write("space.cbz", archive(Color.RED))
        val constrained = SourceAccess(app) { 0L }
        val error = runCatching { constrained.copyManaged(uri("space.cbz"), "space.cbz") {} }.exceptionOrNull()
        assertTrue(error!!.message!!.contains("free storage"))
        assertFalse(constrained.managedDirectory.listFiles().orEmpty().any { it.name.endsWith(".part") })
        val failedWrite = runCatching { copyBounded(ByteArrayInputStream(byteArrayOf(1)), object : OutputStream() {
            override fun write(value: Int) { throw IOException("ENOSPC injected") }
        }, 10) }.exceptionOrNull()
        assertTrue(failedWrite!!.message!!.contains("free storage"))
    }
    @Test fun corruptPageDoesNotPoisonNextPageOrNextBook() = runBlocking {
        write("corrupt.cbz", archive(Color.RED, corrupt = true))
        val corrupt = library.importFile(uri("corrupt.cbz"))
        assertTrue(runCatching { pixel(corrupt) }.isFailure)
        assertEquals(Color.RED, pixel(corrupt, 1))
        write("good.cbz", archive(Color.GREEN))
        assertEquals(Color.GREEN, pixel(library.importFile(uri("good.cbz"))))
        assertFalse(File(app.cacheDir, "pages").listFiles().orEmpty().any { it.name.endsWith(".part") })
    }
    @Test fun isolatedDecoderDeathAndCancellationAllowRebinding() = runBlocking {
        val testContext = InstrumentationRegistry.getInstrumentation().context
        val file = File(app.sources.managedDirectory, "hardening-decoder.pdf")
        testContext.assets.open("fixtures/sample.pdf").use { input -> file.outputStream().use { input.copyTo(it) } }
        copies += file
        val book = library.importFile(Uri.parse("panelgrove://managed/${file.name}"))
        val client = DecoderClient(app)
        val old = client.get().asBinder()
        client.get().abort()
        withTimeout(10_000) { while (old.isBinderAlive) delay(10) }
        app.engine.open(book).use { assertTrue(app.engine.render(it, 1, 300, 400).width > 0) }
        val started = CompletableDeferred<Unit>()
        val cancelled = launch(Dispatchers.IO) { client.call { started.complete(Unit); Thread.sleep(1000); 1 } }
        started.await(); cancelled.cancelAndJoin()
        // A cancelled client call terminates its isolated service, then a real PDF can reopen.
        withTimeout(10_000) { delay(100); app.engine.open(book).use { assertTrue(app.engine.render(it, 2, 301, 401).height > 0) } }
    }
}
