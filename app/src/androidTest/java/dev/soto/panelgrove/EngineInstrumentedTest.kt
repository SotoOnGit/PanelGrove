package dev.soto.panelgrove

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.soto.panelgrove.core.*
import dev.soto.panelgrove.data.*
import dev.soto.panelgrove.engine.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class EngineInstrumentedTest {
    private val app get() = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as PanelgroveApplication
    private fun fixture(name: String): Uri {
        val destination = File(app.sources.managedDirectory, "test-$name")
        InstrumentationRegistry.getInstrumentation().context.assets.open("fixtures/$name").use { input -> destination.outputStream().use { input.copyTo(it) } }
        return Uri.parse("panelgrove://managed/${destination.name}")
    }
    private suspend fun imported(name: String) = app.library.importFile(fixture(name))

    @Test fun cbzManifestOrderRenderingAndStableProgress() = runBlocking {
        val book = imported("sample.cbz")
        // Retained original fixtures keep their embedded series metadata across the app rename.
        assertEquals(4, book.pageCount); assertEquals("Readly Originals", book.series)
        app.engine.open(book).use { session ->
            assertEquals(listOf("001.png", "002.png", "003.png", "010.png"), session.index.pages.map { it.locator })
            assertEquals("test/1", session.index.pages[0].id)
            assertEquals(listOf("1", "1", "2", "2"), session.index.pages.map { it.chapter })
            val bitmap = app.engine.render(session, 0, 600, 850)
            assertTrue(bitmap.width > 0 && bitmap.height > 0)
            val region = app.engine.render(session, 0, 600, 850, PageRegion(.2f, .2f, .6f, .6f))
            assertTrue(region.width > 0)
            app.library.position(book.id, session.index.pages[2], 2, .35f)
        }
        val again = app.library.importFile(fixture("sample.cbz"))
        assertEquals(book.id, again.id); assertEquals("test/3", again.position?.pageId)
        assertEquals(.35f, again.position!!.offset, .001f)
    }
    @Test fun rar4Rar5AndSolidRar5OpenAndRender() = runBlocking {
        for (name in listOf("rar4.cbr", "rar5.cbr", "solid-rar5.cbr")) {
            val book = imported(name)
            app.engine.open(book).use { session ->
                assertEquals(name, 4, session.index.pages.size)
                for (i in listOf(0, 3, 1)) assertTrue(app.engine.render(session, i, 400, 600).height > 0)
                assertEquals("Readly Originals", session.index.metadata.series)
            }
        }
    }
    @Test fun pdfEngineIndexesAndRendersZoomRegions() = runBlocking {
        val book = imported("sample.pdf")
        app.engine.open(book).use { session ->
            assertEquals(4, session.index.pages.size)
            assertTrue(app.engine.render(session, 2, 600, 850).width == 600)
            assertTrue(app.engine.render(session, 2, 400, 500, PageRegion(.1f, .1f, .5f, .6f)).height == 500)
        }
    }
    @Test fun encryptedAndUnsafeArchivesAreRejected() = runBlocking {
        assertTrue(runCatching { imported("encrypted.cbr") }.isFailure)
        assertTrue(runCatching { imported("unsafe.cbz") }.isFailure)
    }
    @Test fun thousandPageArchiveIsIndexedWithoutDecodingAllPages() = runBlocking {
        val book = imported("thousand.cbz")
        app.engine.open(book).use { session ->
            assertEquals(1000, session.index.pages.size)
            assertEquals("1.png", session.index.pages.first().locator)
            assertEquals("1000.png", session.index.pages.last().locator)
            assertTrue(app.engine.render(session, 999, 360, 520).width > 0)
        }
    }
    @Test fun bookmarksHistoryAndRoomSurviveDatabaseReopening() = runBlocking {
        val book = imported("sample.cbz")
        app.library.openSession(book.id) {}.use { session ->
            app.library.position(book.id, session.index.pages[1], 1, .2f)
            val mark = app.library.dao.bookmarks().map { it.bookmark() }.find { it.bookId == book.id }
            if (mark == null) app.library.toggleBookmark(book, session.index.pages[1], 1)
        }
        val other = androidx.room.Room.databaseBuilder(app, LibraryDatabase::class.java, "panelgrove.db").build()
        try {
            assertEquals("test/2", other.library().book(book.id)!!.book().position?.pageId)
            assertTrue(other.library().bookmarks().any { it.bookId == book.id })
            assertTrue(other.library().history().any { it.bookId == book.id })
        } finally { other.close() }
    }
    @Test fun managedLocationCannotEscapePrivateBookDirectory() {
        assertTrue(runCatching { app.sources.managedFile(Uri.parse("panelgrove://managed/..%2Foutside")) }.isFailure)
    }
    @Test fun jpegWebpAndPngBoundsKeepWidePagesOutOfSpreads() = runBlocking {
        val book = imported("mixed-images.cbz")
        app.engine.open(book).use { session ->
            assertEquals(3, session.index.pages.size)
            assertTrue(session.index.pages[1].width > session.index.pages[1].height)
            assertEquals(listOf(listOf(0), listOf(1), listOf(2)), pageGroups(session.index.pages, true))
            for (i in 0..2) assertTrue(app.engine.render(session, i, 360, 520).height > 0)
        }
    }
    @Test fun noNetworkPermissionIsRequested() {
        val permissions = app.packageManager.getPackageInfo(app.packageName, android.content.pm.PackageManager.GET_PERMISSIONS).requestedPermissions.orEmpty()
        assertFalse(permissions.contains("android.permission.INTERNET"))
        assertFalse(permissions.contains("android.permission.MANAGE_EXTERNAL_STORAGE"))
    }
}
