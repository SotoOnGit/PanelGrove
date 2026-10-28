package dev.soto.panelgrove

import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.soto.panelgrove.core.*
import dev.soto.panelgrove.storage.ExportedPage
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class PageExportInstrumentedTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val app get() = instrumentation.targetContext.applicationContext as PanelgroveApplication
    private val files = mutableListOf<File>()
    private val exports = mutableListOf<ExportedPage>()
    private val authority = "dev.soto.panelgrove.test.documents"
    private val root = "hardening/page-export"
    private fun document(id: String) = DocumentsContract.buildDocumentUri(authority, "$root/$id")
    private fun configure(id: String = "", block: Bundle.() -> Unit) {
        app.contentResolver.call(Uri.parse("content://$authority"), "fixture", null, Bundle().apply {
            putString("id", root + if (id.isEmpty()) "" else "/$id"); block()
        })
    }
    private fun fixture(name: String, format: BookFormat): Book {
        val file = File(app.sources.managedDirectory, "export-${UUID.randomUUID()}-$name").also { files += it }
        instrumentation.context.assets.open("fixtures/$name").use { input -> file.outputStream().use { input.copyTo(it) } }
        return Book(UUID.randomUUID().toString(), "panelgrove://managed/${file.name}", name, format,
            title = "Shared / title", language = "en", revision = UUID.randomUUID().toString())
    }
    private suspend fun prepare(book: Book, index: Int = 1, share: Boolean = false): ExportedPage {
        val id = app.engine.open(book).use { it.index.pages[index].id }
        return app.pageExports.prepare(book, id, share).also { exports += it }
    }
    @Before fun setup() { configure { putBoolean("reset", true); putBoolean("directory", true) } }
    @After fun cleanup() { exports.forEach(app.pageExports::discard); files.forEach { it.delete() }; configure { putBoolean("reset", true) } }

    @Test fun archivesAndImageFoldersExportOriginalBytesAtFullDimensions(): Unit = runBlocking {
        val books = listOf(fixture("sample.cbz", BookFormat.ZIP), fixture("rar5.cbr", BookFormat.RAR), fixture("solid-lzma2.cb7", BookFormat.SEVEN_Z),
            Book("export-folder", DocumentsContract.buildDocumentUriUsingTree(DocumentsContract.buildTreeDocumentUri(authority, "images"), "images").toString(), "images", BookFormat.FOLDER, title = "Folder"))
        for (book in books) {
            val expected = app.engine.open(book).use { it.pageFile(1).readBytes() }
            val page = prepare(book)
            assertArrayEquals(book.format.name, expected, app.pageExports.file(page).readBytes())
            assertEquals("image/png", page.mimeType)
            assertFalse(page.filename.contains('/'))
            val bitmap = BitmapFactory.decodeFile(app.pageExports.file(page).path)
            assertNotNull(bitmap); bitmap.recycle()
            configure("out.png") { putByteArray("bytes", ByteArray(0)) }
            app.pageExports.save(page, document("out.png"))
            assertArrayEquals(expected, app.contentResolver.openInputStream(document("out.png"))!!.use { it.readBytes() })
        }
    }

    @Test fun pdfExportPreservesWholePageAspectRatioAndUsesPng(): Unit = runBlocking {
        val book = fixture("sample.pdf", BookFormat.PDF)
        val page = prepare(book, 0)
        assertEquals("image/png", page.mimeType)
        app.engine.open(book).use { session ->
            val ref = session.index.pages[0]
            val image = BitmapFactory.decodeFile(app.pageExports.file(page).path)
            assertEquals(ref.width.toDouble() / ref.height, image.width.toDouble() / image.height, .003)
            assertEquals(minOf(2.0, 2048.0 / ref.width, 3072.0 / ref.height) * ref.width, image.width.toDouble(), 1.0)
            assertTrue(image.width <= 2048 && image.height <= 3072)
            image.recycle()
        }
    }

    @Test fun jpegWebpAndPngKeepTheirOriginalEncodingAndMatchingMimeTypes(): Unit = runBlocking {
        val book = fixture("mixed-images.cbz", BookFormat.ZIP)
        for ((index, mime) in listOf("image/jpeg", "image/webp", "image/png").withIndex()) {
            val original = app.engine.open(book).use { it.pageFile(index).readBytes() }
            val page = prepare(book, index)
            assertEquals(mime, page.mimeType)
            assertArrayEquals(original, app.pageExports.file(page).readBytes())
        }
    }

    @Test fun shareIntentUsesReadableContentUriAndOnlyTemporaryReadAccess(): Unit = runBlocking {
        val page = prepare(fixture("sample.cbz", BookFormat.ZIP), share = true)
        val chooser = app.pageExports.intent(page)
        assertEquals(Intent.ACTION_CHOOSER, chooser.action)
        @Suppress("DEPRECATION") val send = chooser.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)!!
        assertEquals(Intent.ACTION_SEND, send.action)
        assertEquals(page.mimeType, send.type)
        assertEquals(Intent.FLAG_GRANT_READ_URI_PERMISSION, send.flags and (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION))
        val uri = send.clipData!!.getItemAt(0).uri
        assertEquals("content", uri.scheme)
        assertEquals("${app.packageName}.pageexports", uri.authority)
        assertArrayEquals(app.pageExports.file(page).readBytes(), app.contentResolver.openInputStream(uri)!!.use { it.readBytes() })
        val create = app.pageExports.intent(page.copy(share = false))
        assertEquals(Intent.ACTION_CREATE_DOCUMENT, create.action)
        assertEquals(page.mimeType, create.type)
        assertEquals(page.filename, create.getStringExtra(Intent.EXTRA_TITLE))
    }

    @Test fun missingPageAndRevokedDestinationFailWithoutChangingSource(): Unit = runBlocking {
        val book = fixture("sample.cbz", BookFormat.ZIP)
        val original = files.last().readBytes()
        assertTrue(runCatching { app.pageExports.prepare(book, "no-longer-present", false) }.isFailure)
        val page = prepare(book)
        configure("blocked.png") { putByteArray("bytes", ByteArray(0)); putBoolean("denied", true) }
        assertTrue(runCatching { app.pageExports.save(page, document("blocked.png")) }.isFailure)
        assertArrayEquals(original, files.last().readBytes())
        assertTrue(app.pageExports.file(page).isFile)
    }

    @Test fun oldSharesExpireAndCurrentSnapshotsRemain(): Unit = runBlocking {
        val book = fixture("sample.cbz", BookFormat.ZIP)
        val old = prepare(book, share = true)
        val current = prepare(book, share = true)
        app.pageExports.file(old).setLastModified(System.currentTimeMillis() - 25 * 60 * 60 * 1000L)
        app.pageExports.cleanExpired()
        assertFalse(app.pageExports.file(old).exists())
        assertTrue(app.pageExports.file(current).exists())
    }
}
