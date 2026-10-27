package dev.soto.panelgrove

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import androidx.exifinterface.media.ExifInterface
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import androidx.test.platform.app.InstrumentationRegistry
import dev.soto.panelgrove.core.*
import dev.soto.panelgrove.data.book
import dev.soto.panelgrove.ui.adjustDisplay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID

class CustomCoverInstrumentedTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val app get() = instrumentation.targetContext.applicationContext as PanelgroveApplication
    private fun document(id: String, bytes: ByteArray): Uri {
        app.contentResolver.call(Uri.parse("content://dev.soto.panelgrove.test.documents"), "fixture", null,
            Bundle().apply { putString("id", id); putByteArray("bytes", bytes) })
        return DocumentsContract.buildDocumentUri("dev.soto.panelgrove.test.documents", id)
    }
    private fun png(color: Int): ByteArray = ByteArrayOutputStream().use { out ->
        Bitmap.createBitmap(80, 120, Bitmap.Config.ARGB_8888).apply {
            eraseColor(color); compress(Bitmap.CompressFormat.PNG, 100, out); recycle()
        }
        out.toByteArray()
    }

    @Test fun coversSurviveRescanReplacementAndReopeningWithoutChangingTheBook() = runBlocking<Unit> {
        val file = File(app.sources.managedDirectory, "cover-${UUID.randomUUID()}.cbz")
        instrumentation.context.assets.open("fixtures/sample.cbz").use { input -> file.outputStream().use { input.copyTo(it) } }
        val original = file.readBytes()
        val uri = Uri.parse("panelgrove://managed/${file.name}")
        val book = app.library.importFile(uri)
        val id = "hardening/covers-${UUID.randomUUID()}"
        try {
            val image = document("$id/cover.png", png(Color.MAGENTA))
            app.library.setCustomCover(book.id, image)
            val selected = app.library.book(book.id)!!
            val first = app.library.cover(selected)
            assertNotNull(selected.customCover); assertEquals(Color.MAGENTA, BitmapFactory.decodeFile(first.path).getPixel(0, 0))
            app.library.change(book.id) { it.copy(position = ReadingPosition("test/2", 1), settings = ReaderOverrides(gamma = 1.4f)) }
            val refreshed = app.library.importFile(uri)
            assertEquals(selected.customCover, refreshed.customCover)
            assertEquals(first, app.library.cover(refreshed))
            app.library.openSession(book.id) {}.use { assertEquals(4, it.index.pages.size) }
            assertEquals(selected.customCover, app.library.book(book.id)!!.customCover)
            assertEquals(1.4f, app.library.book(book.id)!!.settings.gamma)
            assertArrayEquals(original, file.readBytes())
            val failed = runCatching { app.library.setCustomCover(book.id, document("$id/bad.png", byteArrayOf(1, 2, 3))) }
            assertTrue(failed.isFailure); assertTrue(first.exists())
            assertEquals(selected.customCover, app.library.book(book.id)!!.customCover)
            app.library.setCustomCover(book.id, document("$id/second.png", png(Color.CYAN)))
            assertFalse(first.exists())
            val second = app.library.cover(app.library.book(book.id)!!)
            assertEquals(Color.CYAN, BitmapFactory.decodeFile(second.path).getPixel(0, 0))
            app.library.setCustomCover(book.id, null)
            val removed = app.library.book(book.id)!!
            assertNull(removed.customCover); assertFalse(second.exists())
            assertTrue(app.library.cover(removed).exists())
            assertEquals(1, removed.position?.index)
            assertArrayEquals(original, file.readBytes())
        } finally {
            app.library.remove(book.id, false); file.delete()
            app.contentResolver.call(Uri.parse("content://dev.soto.panelgrove.test.documents"), "fixture", null,
                Bundle().apply { putString("id", id); putBoolean("reset", true) })
        }
    }

    @Test fun cameraOrientationAndLargeImagesProduceBoundedCovers() = runBlocking<Unit> {
        val store = dev.soto.panelgrove.data.CustomCoverStore(app)
        val id = "hardening/cover-orientation-${UUID.randomUUID()}"
        val jpeg = File(app.cacheDir, "exif-${UUID.randomUUID()}.jpg")
        val created = mutableListOf<File>()
        try {
            Bitmap.createBitmap(120, 80, Bitmap.Config.ARGB_8888).apply {
                eraseColor(Color.BLUE); jpeg.outputStream().use { compress(Bitmap.CompressFormat.JPEG, 90, it) }; recycle()
            }
            ExifInterface(jpeg.path).apply { setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_ROTATE_90.toString()); saveAttributes() }
            val key = store.import(document("$id/camera.jpg", jpeg.readBytes()))
            created += store.file(key)
            BitmapFactory.decodeFile(store.file(key).path).apply { assertEquals(80, width); assertEquals(120, height); recycle() }
            val bytes = ByteArrayOutputStream().use { output ->
                Bitmap.createBitmap(2048, 1024, Bitmap.Config.ARGB_8888).apply {
                    eraseColor(Color.GREEN); compress(Bitmap.CompressFormat.PNG, 100, output); recycle()
                }; output.toByteArray()
            }
            val largeKey = store.import(document("$id/large.png", bytes)); created += store.file(largeKey)
            BitmapFactory.decodeFile(store.file(largeKey).path).apply { assertTrue(width <= 720 && height <= 720); recycle() }
        } finally {
            created.forEach { it.delete() }; jpeg.delete()
            app.contentResolver.call(Uri.parse("content://dev.soto.panelgrove.test.documents"), "fixture", null,
                Bundle().apply { putString("id", id); putBoolean("reset", true) })
        }
    }

    @Test fun folderRescansKeepCoversAndRemovalLeavesBothOriginalFilesIntact() = runBlocking<Unit> {
        val id = "hardening/cover-rescan-${UUID.randomUUID()}"
        val imageBytes = png(Color.YELLOW)
        val image = document("$id/cover.png", imageBytes)
        val archiveBytes = instrumentation.context.assets.open("fixtures/sample.cbz").use { it.readBytes() }
        val archive = document("$id/books/book.cbz", archiveBytes)
        val root = app.library.addRoot(DocumentsContract.buildTreeDocumentUri("dev.soto.panelgrove.test.documents", "$id/books"))
        var imported: Book? = null
        try {
            assertTrue(app.library.scan(root.id).isEmpty())
            val book = app.library.dao.books().map { it.book() }.single { it.sourceId == root.id }
            imported = book
            app.library.setCustomCover(book.id, image)
            val selected = app.library.book(book.id)!!
            val ownedCover = app.library.cover(selected)
            app.library.change(book.id) { it.copy(settings = ReaderOverrides(contrast = 1.3f, gamma = 1.6f)) }
            assertTrue(app.library.scan(root.id).isEmpty())
            val rescanned = app.library.book(book.id)!!
            assertEquals(selected.customCover, rescanned.customCover)
            assertEquals(ownedCover, app.library.cover(rescanned))
            assertEquals(1.6f, rescanned.settings.gamma)
            app.library.remove(book.id, false); imported = null
            assertFalse(ownedCover.exists())
            assertArrayEquals(imageBytes, app.sources.input(image).use { it.readBytes() })
            assertArrayEquals(archiveBytes, app.sources.input(archive).use { it.readBytes() })
        } finally {
            imported?.let { app.library.remove(it.id, false) }; app.library.detachRoot(root.id)
            app.contentResolver.call(Uri.parse("content://dev.soto.panelgrove.test.documents"), "fixture", null,
                Bundle().apply { putString("id", id); putBoolean("reset", true) })
        }
    }

    @Test fun adjustedPixelsAndZoomCropsUseTheSameCurveAndPreserveOriginals() = runBlocking<Unit> {
        val source = Bitmap.createBitmap(4, 2, Bitmap.Config.ARGB_8888)
        val colors = intArrayOf(Color.BLACK, Color.rgb(64, 64, 64), Color.rgb(128, 128, 128), Color.WHITE,
            Color.RED, Color.GREEN, Color.BLUE, Color.argb(128, 128, 128, 128))
        source.setPixels(colors, 0, 4, 0, 0, 4, 2)
        val adjusted = adjustDisplay(source, 1f, 2f)
        val crop = Bitmap.createBitmap(source, 1, 0, 2, 2)
        val tile = adjustDisplay(crop, 1f, 2f)
        try {
            assertSame(source, adjustDisplay(source, 1f, 1f))
            assertEquals(Color.rgb(181, 181, 181), adjusted.getPixel(2, 0))
            assertEquals(128, Color.alpha(adjusted.getPixel(3, 1)))
            for (y in 0..1) for (x in 0..1) assertEquals(adjusted.getPixel(x + 1, y), tile.getPixel(x, y))
            val after = IntArray(8); source.getPixels(after, 0, 4, 0, 0, 4, 2)
            assertArrayEquals(colors, after)
        } finally { source.recycle(); adjusted.recycle(); crop.recycle(); tile.recycle() }
    }
}
