package dev.soto.panelgrove

import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.soto.panelgrove.core.*
import dev.soto.panelgrove.data.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import android.os.Bundle

@RunWith(AndroidJUnit4::class)
class StorageInstrumentedTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val app get() = instrumentation.targetContext.applicationContext as PanelgroveApplication
    // The separate test APK exposes only generated fixtures; no protected user document is involved.
    private fun grant(uri: Uri): Uri = uri
    @Test fun nonSeekableSharedDocumentIsStagedAndReadable() = runBlocking {
        val uri = grant(DocumentsContract.buildDocumentUri("dev.soto.panelgrove.test.documents", "pipe:sample.cbz"))
        val book = app.library.importFile(uri)
        app.engine.open(book).use { session ->
            assertEquals(4, session.index.pages.size)
            assertTrue(app.engine.render(session, 3, 360, 520).width > 0)
        }
    }
    @Test fun safFoldersRescanWithoutDuplicatesAndPreserveManualMetadata() = runBlocking {
        val tree = grant(DocumentsContract.buildTreeDocumentUri("dev.soto.panelgrove.test.documents", "root"))
        val root = app.library.addRoot(tree)
        assertTrue(app.library.scan(root.id).isEmpty())
        val before = app.library.dao.books().map { it.book() }.filter { it.sourceId == root.id }
        assertEquals(2, before.size)
        val folder = before.first { it.format == BookFormat.FOLDER }
        app.engine.open(folder).use { session -> assertEquals(4, session.index.pages.size); assertTrue(app.engine.render(session, 1, 360, 520).height > 0) }
        app.library.change(folder.id) { it.copy(title = "My edited title", metadataEdited = true, collections = listOf("Study")) }
        assertTrue(app.library.scan(root.id).isEmpty())
        val after = app.library.dao.books().map { it.book() }.filter { it.sourceId == root.id }
        assertEquals(before.map { it.id }.toSet(), after.map { it.id }.toSet())
        assertEquals("My edited title", after.first { it.id == folder.id }.title)
        assertEquals(listOf("Study"), after.first { it.id == folder.id }.collections)
    }
    @Test fun backupRoundTripMergesAndRestoresReadingState() = runBlocking {
        val tree = grant(DocumentsContract.buildTreeDocumentUri("dev.soto.panelgrove.test.documents", "root"))
        val uri = grant(DocumentsContract.buildDocumentUri("dev.soto.panelgrove.test.documents", "backup.json"))
        val bookUri = grant(DocumentsContract.buildDocumentUri("dev.soto.panelgrove.test.documents", "sample.cbz"))
        val book = app.library.importFile(bookUri)
        app.library.change(book.id) { it.copy(favorite = true, collections = listOf("Backup test"), position = ReadingPosition("test/2", 1, .25f)) }
        app.library.exportBackup(uri)
        app.library.change(book.id) { it.copy(favorite = false, collections = emptyList()) }
        app.library.restoreBackup(uri)
        val restored = app.library.book(book.id)!!
        assertTrue(restored.favorite); assertEquals(listOf("Backup test"), restored.collections)
        assertEquals("test/2", restored.position?.pageId)
        assertEquals(.25f, restored.position!!.offset, .001f)
    }
    @Test fun repeatedSharedImportsReuseTheManagedCopy() = runBlocking {
        val previous = app.preferences.current()
        val destination = DocumentsContract.buildTreeDocumentUri("dev.soto.panelgrove.test.documents", "hardening/storage-copies")
        app.contentResolver.call(Uri.parse("content://dev.soto.panelgrove.test.documents"), "fixture", null, Bundle().apply {
            putString("id", "hardening/storage-copies"); putBoolean("directory", true); putBoolean("grantTree", true)
        })
        check(app.sources.persist(destination, write = true))
        app.preferences.update { it.copy(copyFolderUri = destination.toString(), copyFolderName = "storage-copies") }
        var imported: Book? = null
        try {
        val uri = grant(DocumentsContract.buildDocumentUri("dev.soto.panelgrove.test.documents", "sample.cbz"))
        val first = app.library.importFile(uri, copy = true)
        imported = first
        val files = app.sources.managedDirectory.listFiles().orEmpty().map { it.name }.toSet()
        val second = app.library.importFile(uri, copy = true)
        assertEquals(first.id, second.id)
        assertEquals(first.uri, second.uri)
        assertEquals(files, app.sources.managedDirectory.listFiles().orEmpty().map { it.name }.toSet())
        val rescanned = app.library.importFile(uri, copy = false)
        assertTrue(rescanned.managed)
        assertEquals(first.uri, rescanned.uri)
        } finally {
            imported?.let { app.library.remove(it.id, deleteCopy = true) }
            app.preferences.set(previous)
            app.contentResolver.call(Uri.parse("content://dev.soto.panelgrove.test.documents"), "fixture", null, Bundle().apply {
                putString("id", "hardening/storage-copies"); putBoolean("reset", true)
            })
        }
    }
}
