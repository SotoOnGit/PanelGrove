package dev.soto.panelgrove

import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.soto.panelgrove.core.*
import dev.soto.panelgrove.engine.DecoderClient
import dev.soto.panelgrove.engine.PageRegion
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

/** Safe on an existing library: only creates and removes uniquely named test documents. */
@RunWith(AndroidJUnit4::class)
class ArchiveCompatibilityTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val app get() = instrumentation.targetContext.applicationContext as PanelgroveApplication
    private suspend fun fixture(name: String, block: suspend (Book) -> Unit) {
        val file = File(app.sources.managedDirectory, "compat-${UUID.randomUUID()}-${name}")
        instrumentation.context.assets.open("fixtures/$name").use { source -> file.outputStream().use { source.copyTo(it) } }
        try {
            val uri = Uri.parse("panelgrove://managed/${file.name}")
            block(Book(UUID.randomUUID().toString(), uri.toString(), name, app.sources.format(uri) ?: BookFormat.RAR))
        } finally { file.delete() }
    }
    @Test fun rar4Rar5SolidUnicodeAndRarExtensionRender() = runBlocking {
        for (name in listOf("rar4.cbr", "rar5.cbr", "solid-rar5.cbr", "unicode.rar")) fixture(name) { book ->
            assertEquals(BookFormat.RAR, book.format)
            app.engine.open(book).use { session ->
                assertEquals(4, session.index.pages.size)
                for (page in listOf(3, 0, 2, 1)) assertTrue(app.engine.render(session, page, 400, 600).height > 0)
                assertTrue(app.engine.render(session, 0, 400, 600, PageRegion(.2f, .2f, .7f, .7f)).width > 0)
                if (name == "unicode.rar") assertTrue(session.index.pages.any { "日本語" in it.locator && "🌙" in it.locator })
            }
        }
    }
    @Test fun zipWithCbrExtensionImportsUsingItsContainerAndCleansUp() = runBlocking {
        fixture("zip-as-cbr.cbr") { shell ->
            assertEquals(BookFormat.ZIP, shell.format)
            val book = app.library.importFile(Uri.parse(shell.uri))
            try {
                assertEquals(BookFormat.ZIP, book.format)
                assertEquals(4, book.pageCount)
                app.engine.open(book).use { assertTrue(app.engine.render(it, 3, 400, 600).height > 0) }
            } finally { app.library.remove(book.id, false) }
        }
    }
    @Test fun decoderFailuresAreExplicitAndTheNextDocumentStillOpens() = runBlocking {
        for (name in listOf("encrypted.cbr", "corrupt.cbr")) fixture(name) { book ->
            val failure = runCatching { app.engine.open(book).close() }.exceptionOrNull()
            assertNotNull(name, failure)
            assertFalse(name, failure is NullPointerException)
            assertFalse(name, failure!!.message.orEmpty().contains("BaseBundle"))
        }
        fixture("rar5.cbr") { book ->
            val client = DecoderClient(app)
            val destination = File(app.cacheDir, "missing-${UUID.randomUUID()}")
            try {
                app.sources.open(Uri.parse(book.uri)).use { source ->
                    ParcelFileDescriptor.open(destination, ParcelFileDescriptor.MODE_CREATE or ParcelFileDescriptor.MODE_WRITE_ONLY).use { output ->
                        val failure = runCatching { client.request { it.extract(source, "missing.png", output, 1024) } }.exceptionOrNull()
                        assertTrue(failure is java.io.IOException)
                    }
                }
                app.engine.open(book).use { assertTrue(app.engine.render(it, 0, 400, 600).width > 0) }
            } finally { destination.delete() }
        }
    }
    @Test fun samsungPrivateArchiveIsReadOrReportedAsDamaged(): Unit = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("privateRar") == "true")
        val file = File(app.filesDir, "qa/compatibility-sample.rar")
        assertTrue("Push the private RAR to files/qa/compatibility-sample.rar", file.exists())
        val target = File(app.sources.managedDirectory, "compat-private-${UUID.randomUUID()}.rar")
        file.copyTo(target)
        try {
            val book = Book(UUID.randomUUID().toString(), "panelgrove://managed/${target.name}", "Private RAR acceptance", BookFormat.RAR)
            if (InstrumentationRegistry.getArguments().getString("privateRarCorrupt") == "true") {
                val failure = runCatching { app.engine.open(book).close() }.exceptionOrNull()
                assertTrue("Damaged archive must return a decoding error", failure is java.io.IOException)
                assertFalse(failure is NullPointerException)
                assertFalse(failure!!.message.orEmpty().contains("BaseBundle"))
                android.util.Log.i("PanelgroveAcceptance", "Damaged private RAR5 returned an explicit decode error; no null Bundle")
                return@runBlocking
            }
            app.engine.open(book).use { session ->
                assertTrue(session.index.pages.size > 100)
                for (index in listOf(0, session.index.pages.size / 2, session.index.pages.lastIndex))
                    assertTrue(app.engine.render(session, index, 600, 900).height > 0)
                android.util.Log.i("PanelgroveAcceptance", "Samsung RAR5: ${session.index.pages.size} pages; first/middle/last rendered")
            }
        } finally { target.delete() }
    }
    @Test fun previouslyFailedPhoneArchivesNeverSurfaceNullBundleErrors() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("existingFailures") == "true")
        val database = listOf(File(app.noBackupFilesDir, "androidx.work.workdb"), app.getDatabasePath("androidx.work.workdb")).first { it.exists() }
        val uris = mutableSetOf<String>()
        android.database.sqlite.SQLiteDatabase.openDatabase(database.path, null, android.database.sqlite.SQLiteDatabase.OPEN_READONLY).use { db ->
            db.rawQuery("SELECT input FROM workspec WHERE state = 3", null).use { rows ->
                while (rows.moveToNext()) {
                    androidx.work.Data.fromByteArray(rows.getBlob(0)).getString("uri")?.let { uris += it }
                }
            }
        }
        File(app.filesDir, "qa/previous-failed-uris.json").writeText(org.json.JSONArray(uris.toList()).toString())
        var tested = 0
        for (location in uris.take(10)) {
            val uri = Uri.parse(location)
            try {
                val details = app.sources.details(uri)
                if (ArchiveRules.format(details.name) != BookFormat.RAR) continue
                val format = app.sources.format(uri) ?: BookFormat.RAR
                val book = Book(UUID.randomUUID().toString(), location, details.name, format)
                app.engine.open(book).use { session ->
                    for (index in listOf(0, session.index.pages.lastIndex)) assertTrue(app.engine.render(session, index, 600, 900).height > 0)
                    android.util.Log.i("PanelgroveAcceptance", "Previously failed archive opened: format=$format pages=${session.index.pages.size}")
                }
            } catch (error: java.io.IOException) {
                assertFalse(error.message.orEmpty().contains("BaseBundle"))
                android.util.Log.i("PanelgroveAcceptance", "Previously failed source returned an explicit missing/damaged-file error: ${error.javaClass.simpleName}")
            }
            tested++
        }
        assertTrue("No previous failed RAR/CBR imports found", tested > 0)
    }
}
