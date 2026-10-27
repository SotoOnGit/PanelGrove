package dev.soto.panelgrove

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.soto.panelgrove.core.*
import dev.soto.panelgrove.data.*
import dev.soto.panelgrove.engine.PageRegion
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID
import java.util.zip.ZipInputStream

@RunWith(AndroidJUnit4::class)
class SevenZipInstrumentedTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val app get() = instrumentation.targetContext.applicationContext as PanelgroveApplication
    private val authority = "dev.soto.panelgrove.test.documents"
    private val files = mutableListOf<File>()
    private lateinit var database: LibraryDatabase
    private lateinit var library: LibraryRepository
    private lateinit var preferences: AppPreferences
    private fun uri(id: String) = DocumentsContract.buildDocumentUri(authority, "hardening/seven/$id")
    private fun tree(id: String = "") = DocumentsContract.buildTreeDocumentUri(authority, "hardening/seven" + if (id.isEmpty()) "" else "/$id")
    private fun configure(id: String = "", block: Bundle.() -> Unit) {
        app.contentResolver.call(Uri.parse("content://$authority"), "fixture", null, Bundle().apply {
            putString("id", "hardening/seven" + if (id.isEmpty()) "" else "/$id"); block()
        })
    }
    private fun write(name: String, fixture: String) = configure(name) {
        putByteArray("bytes", instrumentation.context.assets.open("fixtures/$fixture").use { it.readBytes() })
    }
    private fun fixture(name: String): Book {
        val file = File(app.sources.managedDirectory, "seven-${UUID.randomUUID()}-$name").also { files += it }
        instrumentation.context.assets.open("fixtures/$name").use { input -> file.outputStream().use { input.copyTo(it) } }
        return Book(UUID.randomUUID().toString(), "panelgrove://managed/${file.name}", name, BookFormat.SEVEN_Z,
            revision = UUID.randomUUID().toString())
    }
    @Before fun setup() = runBlocking {
        configure { putBoolean("reset", true); putBoolean("directory", true) }
        database = Room.inMemoryDatabaseBuilder(app, LibraryDatabase::class.java).build()
        library = LibraryRepository(app, database, app.preferences, app.sources, app.engine)
        preferences = app.preferences.current()
    }
    @After fun cleanup() = runBlocking {
        database.library().books().forEach { library.remove(it.id, false) }
        database.close()
        app.preferences.update { preferences }
        files.forEach { it.delete() }
        configure { putBoolean("reset", true) }
        app.engine.clearMemory()
    }

    @Test fun solidLzma2LzmaStoredDeflateAndPpmdReadInAnyOrder() = runBlocking {
        val expected = mutableMapOf<String, ByteArray>()
        ZipInputStream(instrumentation.context.assets.open("fixtures/sample.cbz")).use { zip ->
            while (true) { val entry = zip.nextEntry ?: break; if (ArchiveRules.isImage(entry.name)) expected[entry.name] = zip.readBytes() }
        }
        for (name in listOf("solid-lzma2.cb7", "lzma.7z", "stored.7z", "deflate.7z", "ppmd.7z")) {
            val book = fixture(name)
            assertEquals(BookFormat.SEVEN_Z, app.sources.format(Uri.parse(book.uri)))
            app.engine.open(book).use { session ->
                assertEquals(name, listOf("001.png", "002.png", "003.png", "010.png"), session.index.pages.map { it.locator })
                assertEquals("Readly Originals", session.index.metadata.series)
                assertEquals("test/1", session.index.pages.first().id)
                assertEquals(listOf("1", "1", "2", "2"), session.index.pages.map { it.chapter })
                for (page in listOf(3, 0, 2, 1)) {
                    assertArrayEquals(name, expected[session.index.pages[page].locator], session.pageFile(page).readBytes())
                    assertTrue(name, app.engine.render(session, page, 360, 520).height > 0)
                }
                assertTrue(app.engine.render(session, 0, 400, 600, PageRegion(.2f, .2f, .7f, .7f)).width > 0)
            }
            app.engine.open(book).use { assertEquals("test/3", it.index.pages[2].id) }
        }
    }

    @Test fun unicodeNaturalOrderComicInfoAndInvalidOptionalMetadata() = runBlocking {
        app.engine.open(fixture("unicode.7z")).use { session ->
            assertEquals(listOf("日本語🌙/1.png", "日本語🌙/2.png", "日本語🌙/10.png"), session.index.pages.map { it.locator })
            assertEquals("Seven Zip", session.index.metadata.series)
            assertEquals(Direction.RTL, session.index.metadata.direction)
            assertTrue(app.engine.render(session, 2, 360, 520).height > 0)
        }
        app.engine.open(fixture("invalid-metadata.cb7")).use { session ->
            assertEquals(listOf("001.png", "002.png", "010.png"), session.index.pages.map { it.locator })
            assertTrue(app.engine.render(session, 1, 360, 520).height > 0)
        }
    }

    @Test fun encryptedCorruptUnsafeOversizedAndEmptyArchivesFailWithoutBreakingDecoder() = runBlocking {
        for (name in listOf("encrypted-header.7z", "encrypted-data.cb7", "corrupt.7z", "unsafe.7z", "oversized.7z", "empty.7z")) {
            val book = fixture(name)
            val error = runCatching { library.importFile(Uri.parse(book.uri)) }.exceptionOrNull()
            assertNotNull(name, error)
            assertFalse(name, error is NullPointerException)
            assertTrue(name, database.library().books().isEmpty())
        }
        app.engine.open(fixture("solid-lzma2.cb7")).use { assertTrue(app.engine.render(it, 3, 360, 520).height > 0) }
    }

    @Test fun pageCrcAndUnsupportedCodecFailuresAreExplicit() = runBlocking {
        for (name in listOf("bad-crc.7z", "bzip2.7z")) {
            val error = runCatching {
                app.engine.open(fixture(name)).use { session -> session.index.pages.indices.forEach { session.pageFile(it) } }
            }.exceptionOrNull()
            assertNotNull(name, error)
            assertFalse(name, error is NullPointerException)
        }
        assertFalse(File(app.cacheDir, "pages").listFiles().orEmpty().any { it.name.endsWith(".part") })
    }

    @Test fun folderScanAndReimportPreserveReadingDataAndDetectActualContainer() = runBlocking {
        write("Book.CB7", "solid-lzma2.cb7")
        write("Other.7Z", "lzma.7z")
        val root = library.addRoot(tree())
        assertTrue(library.scan(root.id).isEmpty())
        val books = database.library().books().map { it.book() }
        assertEquals(2, books.size)
        assertTrue(books.all { it.format == BookFormat.SEVEN_Z })
        val book = books.first { it.displayName == "Book.CB7" }
        library.openSession(book.id) {}.use { session ->
            library.position(book.id, session.index.pages[2], 2, .35f)
            library.toggleBookmark(book, session.index.pages[2], 2)
        }
        library.change(book.id) { it.copy(favorite = true, title = "My title", metadataEdited = true) }
        assertTrue(library.scan(root.id).isEmpty())
        val again = library.importFile(uri("Book.CB7"))
        assertEquals(book.id, again.id)
        assertEquals(ReadingPosition("test/3", 2, .35f), again.position)
        assertEquals("My title", again.title)
        assertTrue(again.favorite)
        assertEquals("test/3", database.library().bookmarks().single().bookmark().pageId)
        assertTrue(database.library().history().any { it.bookId == book.id })
        write("Renamed.cbr", "solid-lzma2.cb7")
        assertEquals(BookFormat.SEVEN_Z, library.importFile(uri("Renamed.cbr")).format)
        write("Zip.cb7", "sample.cbz")
        assertEquals(BookFormat.ZIP, library.importFile(uri("Zip.cb7")).format)
        write("Split.7z.001", "solid-lzma2.cb7")
        assertTrue(runCatching { library.importFile(uri("Split.7z.001")) }.isFailure)
    }

    @Test fun nonSeekableProviderAndManagedCopiesRetainExtensionAndReadingData() = runBlocking {
        write("Book.cb7", "solid-lzma2.cb7")
        val pipe = DocumentsContract.buildDocumentUri(authority, "pipe:hardening/seven/Book.cb7")
        val book = library.importFile(pipe)
        app.engine.open(book).use { assertTrue(app.engine.render(it, 3, 360, 520).height > 0) }
        configure("copies") { putBoolean("directory", true); putBoolean("grantTree", true) }
        val destination = tree("copies")
        check(app.sources.persist(destination, write = true))
        app.preferences.update { it.copy(copyFolderUri = destination.toString(), copyFolderName = "7Z test copies") }
        app.engine.open(book).use { library.position(book.id, it.index.pages[1], 1, .2f) }
        val copied = library.importFile(pipe, copy = true, replaceId = book.id)
        assertEquals(book.id, copied.id)
        assertEquals(BookFormat.SEVEN_Z, copied.format)
        assertEquals(book.displayName, copied.displayName)
        assertTrue(app.sources.details(Uri.parse(copied.uri)).name.endsWith(".cb7"))
        assertEquals("test/2", copied.position?.pageId)
        app.engine.open(copied).use { assertTrue(app.engine.render(it, 2, 360, 520).height > 0) }
    }

    @Test fun androidOffersPanelgroveForSevenZipMimeTypes() {
        for (mime in listOf("application/x-7z-compressed", "application/x-cb7")) {
            val intent = Intent(Intent.ACTION_VIEW).setDataAndType(uri("Book.cb7"), mime).setPackage(app.packageName)
            assertTrue(mime, app.packageManager.queryIntentActivities(intent, android.content.pm.PackageManager.MATCH_DEFAULT_ONLY)
                .any { it.activityInfo.name == MainActivity::class.java.name })
        }
    }
}
