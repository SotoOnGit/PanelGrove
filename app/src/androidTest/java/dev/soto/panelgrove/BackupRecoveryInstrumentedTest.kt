package dev.soto.panelgrove

import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import androidx.lifecycle.SavedStateHandle
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.soto.panelgrove.core.*
import dev.soto.panelgrove.data.*
import dev.soto.panelgrove.storage.*
import dev.soto.panelgrove.ui.LibraryViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.security.MessageDigest

/** Generated books and an isolated DB; safe to run without clearing a user's library. */
@RunWith(AndroidJUnit4::class)
class BackupRecoveryInstrumentedTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val app get() = instrumentation.targetContext.applicationContext as PanelgroveApplication
    private val authority = "dev.soto.panelgrove.test.documents"
    private lateinit var db: LibraryDatabase
    private lateinit var library: LibraryRepository
    private lateinit var previous: AppPreferences
    private var previousPending: String? = null
    private fun uri(path: String) = DocumentsContract.buildDocumentUri(authority, "hardening/restore/$path")
    private fun tree(path: String) = DocumentsContract.buildTreeDocumentUri(authority, "hardening/restore/$path")
    private fun configure(path: String = "", block: Bundle.() -> Unit) {
        app.contentResolver.call(Uri.parse("content://$authority"), "fixture", null, Bundle().apply {
            putString("id", "hardening/restore" + if (path.isBlank()) "" else "/$path"); block()
        })
    }
    private fun bytes(name: String = "sample.cbz") = instrumentation.context.assets.open("fixtures/$name").use { it.readBytes() }
    private fun hash(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    private fun write(path: String, bytes: ByteArray = bytes()): Uri {
        if ('/' in path) configure(path.substringBeforeLast('/')) { putBoolean("directory", true) }
        return uri(path).also { app.contentResolver.openOutputStream(it, "wt")!!.use { out -> out.write(bytes) } }
    }
    private suspend fun restore(backup: LibraryBackup) {
        library.restoreBackup(write("backup.json", PanelgroveJson.encodeToString(backup).toByteArray()))
    }
    private fun model(state: SavedStateHandle = SavedStateHandle()): LibraryViewModel {
        lateinit var result: LibraryViewModel
        instrumentation.runOnMainSync { result = LibraryViewModel(app, state) }
        return result
    }
    @Before fun setup() = runBlocking {
        previous = app.preferences.current()
        previousPending = app.preferences.pendingImportFolderRestore.first()
        app.preferences.setRestored(AppPreferences())
        configure { putBoolean("reset", true); putBoolean("directory", true) }
        db = Room.inMemoryDatabaseBuilder(app, LibraryDatabase::class.java).build()
        library = LibraryRepository(app, db, app.preferences, app.sources, app.engine)
    }
    @After fun cleanup() = runBlocking {
        app.preferences.setRestored(AppPreferences())
        app.preferences.set(previous)
        previousPending?.let { app.preferences.requestImportFolderRestore(it) }
        library.dao.books().map { it.book() }.forEach { library.cover(it).delete() }
        db.close()
        configure { putBoolean("reset", true) }
    }
    @Test fun previousPreviewBackupRestoresAndRelinksWithoutLosingReadingData() = runBlocking {
        val incoming = Book("previous-preview", "readly://managed/old-volume.cbz", "Volume.cbz", BookFormat.ZIP,
            managed = true, favorite = true, contentHash = hash(bytes()), position = ReadingPosition("test/2", 1, .4f))
        val backup = LibraryBackup(books = listOf(incoming),
            bookmarks = listOf(Bookmark("old-mark", incoming.id, "test/2", 1)),
            history = listOf(HistoryEntry("old-visit", incoming.id, 123)))
        restore(backup)
        assertEquals("panelgrove://managed/old-volume.cbz", library.book(incoming.id)!!.uri)
        val selected = write("relocated-volume.cbz")
        val relinked = library.importFile(selected, replaceId = incoming.id, linkOnly = true)
        assertEquals(selected.toString(), relinked.uri)
        assertTrue(relinked.favorite)
        assertEquals(.4f, relinked.position!!.offset, .001f)
        restore(backup)
        assertEquals(1, library.dao.books().size)
        assertEquals(selected.toString(), library.book(incoming.id)!!.uri)
        assertEquals(backup.bookmarks, library.dao.bookmarks().map { it.bookmark() })
        assertEquals(backup.history, library.dao.history().map { it.entry() })
    }

    @Test fun restoreRequestsThePreviouslySelectedFolderEvenWhenCopyingWasDisabled() = runBlocking {
        val folder = tree("previous")
        restore(LibraryBackup(preferences = AppPreferences(copyImports = false, copyFolderUri = folder.toString(), copyFolderName = "Previous")))
        assertEquals(folder.toString(), app.preferences.pendingImportFolderRestore.first())
        val first = model()
        withTimeout(5000) { first.restoreFolderRequested.first { it } }
        first.selectRestoreFolder(null).join()
        assertEquals(folder.toString(), app.preferences.pendingImportFolderRestore.first())
        val afterRestart = model()
        withTimeout(5000) { afterRestart.restoreFolderRequested.first { it } }
        restore(LibraryBackup())
        assertNull(app.preferences.pendingImportFolderRestore.first())
    }
    @Test fun relocatedFolderRecoversMatchingCopiesAndDiscoversNewBooksWithoutWritingFiles() = runBlocking {
        val incoming = Book("restore-portable", uri("old/missing.cbz").toString(), "Original filename.cbz", BookFormat.ZIP,
            managed = true, contentHash = hash(bytes()), copyRootUri = tree("old").toString(), favorite = true,
            collections = listOf("Keep"), position = ReadingPosition("test/2", 1, .35f))
        restore(LibraryBackup(preferences = AppPreferences(copyImports = true, copyFolderUri = tree("old").toString()),
            books = listOf(incoming), bookmarks = listOf(Bookmark("restore-mark", incoming.id, "test/2", 1))))
        write("moved/Series/Renamed.cbz")
        write("moved/Other.pdf", bytes("sample.pdf"))
        write("moved/Corrupt.cbz", byteArrayOf(1, 2, 3))
        configure("moved") { putBoolean("grantTree", true) }
        val folder = tree("moved")
        assertTrue(app.sources.persist(folder)) // Reading is sufficient; no write grant is taken.
        val before = TreeAccess(app).children(TreeAccess(app).root(folder)).map { it.name }
        val result = library.recoverImportFolder(folder)
        assertEquals(FolderRecovery(2, 1), result)
        val recovered = library.book(incoming.id)!!
        assertEquals(2, library.dao.books().size)
        assertTrue(recovered.managed); assertTrue(recovered.favorite)
        assertEquals(folder.toString(), recovered.copyRootUri)
        assertEquals(listOf("Keep"), recovered.collections)
        assertEquals(.35f, recovered.position!!.offset, .001f)
        assertEquals(incoming.id, library.dao.bookmarks().single().bookId)
        assertEquals(AccessState.AVAILABLE, recovered.access)
        app.engine.open(recovered).use { assertTrue(app.engine.render(it, 0, 100, 150).height > 0) }
        assertEquals(result, library.recoverImportFolder(folder))
        assertEquals(2, library.dao.books().size)
        assertEquals(before, TreeAccess(app).children(TreeAccess(app).root(folder)).map { it.name })
        assertArrayEquals(bytes(), app.sources.input(uri("moved/Series/Renamed.cbz")).use { it.readBytes() })
    }
    @Test fun relinkingAMissingManagedCopyDoesNotRequireACopyDestinationOrRecopyTheOriginal() = runBlocking {
        val incoming = Book("relink-copy", "panelgrove://managed/unavailable.cbz", "Portable.cbz", BookFormat.ZIP,
            managed = true, contentHash = hash(bytes()), position = ReadingPosition("test/2", 1, .5f))
        restore(LibraryBackup(preferences = AppPreferences(copyImports = true), books = listOf(incoming)))
        val selected = write("selected.cbz")
        val relinked = library.importFile(selected, replaceId = incoming.id, linkOnly = true)
        assertEquals(selected.toString(), relinked.uri)
        assertFalse(relinked.managed); assertNull(relinked.copyRootUri)
        assertEquals(.5f, relinked.position!!.offset, .001f)
        assertEquals(1, library.dao.books().size)
    }
    @Test fun selectingAnUnrelatedExistingBookExplainsTheConflictAndKeepsBothBooks() = runBlocking {
        val other = library.importFile(write("other.cbz", bytes("mixed-images.cbz")))
        val incoming = Book("different-copy", "panelgrove://managed/missing.cbz", "Wanted.cbz", BookFormat.ZIP,
            managed = true, contentHash = hash(bytes()))
        restore(LibraryBackup(books = listOf(incoming)))
        val before = library.dao.books()
        val error = runCatching { library.importFile(Uri.parse(other.uri), replaceId = incoming.id, linkOnly = true) }.exceptionOrNull()
        assertTrue(error is RelinkConflictException)
        assertTrue(error!!.message!!.contains(other.title))
        assertEquals(before, library.dao.books())
    }
    @Test fun provenDuplicateRelinkCombinesReadingDataAndKeepsTheRestoredIdentity() = runBlocking {
        val selected = write("already-imported.cbz")
        val other = library.importFile(selected)
        library.change(other.id) { it.copy(favorite = true, collections = listOf("Existing")) }
        library.dao.save(Bookmark("existing-mark", other.id, "test/1", 0).row())
        library.dao.save(HistoryEntry("existing-visit", other.id, 123).row())
        val incoming = Book("duplicate-copy", "panelgrove://managed/missing.cbz", "Wanted.cbz", BookFormat.ZIP,
            managed = true, contentHash = hash(bytes()), collections = listOf("Restored"), position = ReadingPosition("test/2", 1, .2f))
        restore(LibraryBackup(books = listOf(incoming), bookmarks = listOf(Bookmark("restored-mark", incoming.id, "test/2", 1))))
        val result = library.importFile(selected, replaceId = incoming.id, linkOnly = true)
        assertEquals(incoming.id, result.id); assertEquals(1, library.dao.books().size)
        assertTrue(result.favorite); assertEquals(setOf("Existing", "Restored"), result.collections.toSet())
        assertEquals(.2f, result.position!!.offset, .001f)
        assertEquals(2, library.dao.bookmarks().size)
        assertTrue(library.dao.bookmarks().all { it.bookId == result.id })
        assertEquals(result.id, library.dao.history().single().bookId)
    }
    @Test fun relinkTargetsSurvivePickerRecreationAndCancellationClearsThem() = runBlocking {
        val state = SavedStateHandle()
        val first = model(state)
        first.beginRelinkBook("restored-target")
        val afterPicker = model(SavedStateHandle(state.keys().associateWith { state.get<Any?>(it) }))
        assertEquals("restored-target", afterPicker.replacingBook)
        afterPicker.relink(null).join()
        assertNull(afterPicker.replacingBook); assertNull(afterPicker.replacingRoot)
        first.beginRelinkRoot("restored-root")
        val afterTreePicker = model(SavedStateHandle(state.keys().associateWith { state.get<Any?>(it) }))
        assertEquals("restored-root", afterTreePicker.replacingRoot); assertNull(afterTreePicker.replacingBook)
        afterTreePicker.addFolder(null).join()
        assertNull(afterTreePicker.replacingRoot)
    }
}
