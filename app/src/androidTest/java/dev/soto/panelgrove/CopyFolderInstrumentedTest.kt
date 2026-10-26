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
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.File
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import java.util.zip.ZipEntry

@RunWith(AndroidJUnit4::class)
class CopyFolderInstrumentedTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val app get() = instrumentation.targetContext.applicationContext as PanelgroveApplication
    private val authority = "dev.soto.panelgrove.test.documents"
    private lateinit var database: LibraryDatabase
    private lateinit var library: LibraryRepository
    private lateinit var previous: AppPreferences
    private val privateFiles = mutableListOf<File>()
    private fun uri(path: String) = DocumentsContract.buildDocumentUri(authority, "hardening/$path")
    private fun tree(path: String) = DocumentsContract.buildTreeDocumentUri(authority, "hardening/$path")
    private fun configure(path: String = "", block: Bundle.() -> Unit) {
        app.contentResolver.call(Uri.parse("content://$authority"), "fixture", null, Bundle().apply {
            putString("id", "hardening" + if (path.isBlank()) "" else "/$path"); block()
        })
    }
    private fun bytes() = instrumentation.context.assets.open("fixtures/sample.cbz").use { it.readBytes() }
    private fun file(path: String, data: ByteArray = bytes()): Uri {
        if ('/' in path) configure(path.substringBeforeLast('/')) { putBoolean("directory", true) }
        return uri(path).also { location -> app.contentResolver.openOutputStream(location, "wt")!!.use { it.write(data) } }
    }
    private fun grantFolder(path: String): Uri {
        configure(path) { putBoolean("directory", true); putBoolean("grantTree", true) }
        return tree(path).also { assertTrue(app.sources.persist(it, write = true)) }
    }
    private suspend fun select(path: String = "copies"): Uri = grantFolder(path).also { folder ->
        app.preferences.update { it.copy(copyFolderUri = folder.toString(), copyFolderName = path) }
    }
    @Before fun setup() = runBlocking {
        configure { putBoolean("reset", true); putBoolean("directory", true) }
        previous = app.preferences.current()
        app.preferences.set(AppPreferences())
        database = Room.inMemoryDatabaseBuilder(app, LibraryDatabase::class.java).build()
        library = LibraryRepository(app, database, app.preferences, app.sources, app.engine)
    }
    @After fun cleanup() = runBlocking {
        app.preferences.set(previous)
        database.close()
        privateFiles.forEach { it.delete() }
        configure { putBoolean("reset", true) }
    }
    private fun children(folder: Uri) = TreeAccess(app).children(folder)
    private fun root(folder: Uri) = TreeAccess(app).root(folder)
    private fun copyBytes(book: Book) = app.sources.input(Uri.parse(book.uri)).use { it.readBytes() }

    @Test fun missingDestinationNeverSilentlyCreatesAPrivateCopy() = runBlocking {
        val original = file("source/Volume 1.cbz")
        val before = app.sources.managedDirectory.listFiles().orEmpty().toSet()
        assertTrue(runCatching { library.importFile(original, copy = true) }.exceptionOrNull() is CopyFolderRequiredException)
        assertEquals(before, app.sources.managedDirectory.listFiles().orEmpty().toSet())
        assertTrue(library.dao.books().isEmpty()); assertArrayEquals(bytes(), app.sources.input(original).use { it.readBytes() })
    }
    @Test fun copiesUseMetadataSeriesAndReadableNamesAndRepeatedImportsReuseThem() = runBlocking {
        val folder = select()
        val original = file("source/Volume 1.cbz")
        val before = app.sources.managedDirectory.listFiles().orEmpty().toSet()
        val book = library.importFile(original, copy = true)
        assertTrue(book.managed); assertEquals("content", Uri.parse(book.uri).scheme)
        assertEquals(folder.toString(), book.copyRootUri); assertEquals(original.toString(), book.originUri)
        val series = children(root(folder)).single()
        // This is embedded book metadata from the retained original fixture, not app branding.
        assertEquals("Readly Originals", series.name); assertTrue(series.directory)
        assertEquals(listOf("Volume 1.cbz"), children(series.uri).map { it.name })
        assertArrayEquals(bytes(), copyBytes(book)); assertEquals(before, app.sources.managedDirectory.listFiles().orEmpty().toSet())
        val again = library.importFile(original, copy = true)
        assertEquals(book.id, again.id); assertEquals(book.uri, again.uri)
        assertEquals(1, children(series.uri).size)
        app.engine.open(again).use { assertTrue(app.engine.render(it, 3, 300, 400).width > 0) }
    }
    @Test fun collisionsAndDeletionNeverOverwriteOrRemoveAnOriginal() = runBlocking {
        val folder = select()
        val untouched = file("copies/Readly Originals/Volume 1.cbz", "Unrelated file".toByteArray())
        val firstSource = file("source-a/Volume 1.cbz"); val secondSource = file("source-b/Volume 1.cbz")
        val first = library.importFile(firstSource, copy = true); val second = library.importFile(secondSource, copy = true)
        assertNotEquals(first.id, second.id); assertNotEquals(first.uri, second.uri)
        val series = children(root(folder)).single()
        assertEquals(setOf("Volume 1.cbz", "Volume 1 (2).cbz", "Volume 1 (3).cbz"), children(series.uri).map { it.name }.toSet())
        library.remove(first.id, deleteCopy = true)
        assertEquals("Unrelated file", app.sources.input(untouched).bufferedReader().use { it.readText() })
        assertArrayEquals(bytes(), app.sources.input(firstSource).use { it.readBytes() }); assertArrayEquals(bytes(), copyBytes(second))
        assertFalse(runCatching { app.sources.deleteCopy(second.copy(copyRootUri = tree("source-a").toString())) }.isSuccess)
        assertArrayEquals(bytes(), copyBytes(second))
    }
    @Test fun movingLegacyCopiesAndChangingSeriesPreservesReadingData() = runBlocking {
        val file = File(app.sources.managedDirectory, "copy-folder-legacy.cbz").also { privateFiles += it; it.writeBytes(bytes()) }
        val legacy = library.importFile(Uri.parse("panelgrove://managed/${file.name}"))
        library.change(legacy.id) { it.copy(favorite = true, collections = listOf("Keep"), status = ReadingStatus.READ,
            position = ReadingPosition("test/3", 2, .4f), settings = ReaderOverrides(direction = Direction.LTR)) }
        app.engine.open(legacy).use { library.toggleBookmark(legacy, it.index.pages[2], 2) }
        val folder = select(); library.moveCopies()
        val moved = library.book(legacy.id)!!
        assertEquals(folder.toString(), moved.copyRootUri); assertFalse(file.exists()); assertArrayEquals(bytes(), copyBytes(moved))
        assertTrue(moved.favorite); assertEquals(listOf("Keep"), moved.collections)
        assertEquals(ReadingPosition("test/3", 2, .4f), moved.position); assertEquals(ReadingStatus.READ, moved.status)
        assertEquals(Direction.LTR, moved.settings.direction)
        assertEquals("test/3", library.dao.bookmarksForBook(moved.id).single().bookmark().pageId)
        library.editMetadata(moved.id) { it.copy(series = "My edited series", metadataEdited = true) }
        val edited = library.book(moved.id)!!
        assertEquals("My edited series", edited.series); assertNotEquals(moved.uri, edited.uri)
        val newFolder = children(root(folder)).first { it.name == "My edited series" }
        assertEquals(1, children(newFolder.uri).size); assertEquals(moved.position, edited.position)
    }
    @Test fun changingTheDestinationLeavesPreviousCopiesUntilTheyAreMoved() = runBlocking {
        val firstFolder = select("first"); val first = library.importFile(file("source/One.cbz"), copy = true)
        val secondFolder = select("second"); val second = library.importFile(file("source/Two.cbz"), copy = true)
        assertEquals(firstFolder.toString(), library.book(first.id)!!.copyRootUri)
        assertEquals(secondFolder.toString(), second.copyRootUri); assertArrayEquals(bytes(), copyBytes(first))
        library.moveCopies()
        assertEquals(secondFolder.toString(), library.book(first.id)!!.copyRootUri)
        assertEquals(second.id, library.book(second.id)!!.id); assertArrayEquals(bytes(), copyBytes(library.book(first.id)!!))
    }
    @Test fun cancellingAnExternalWriteCleansTheCreatedDocumentAndStage() = runBlocking {
        val folder = select(); val original = file("source/Cancelled.cbz")
        val before = app.sources.managedDirectory.listFiles().orEmpty().toSet()
        val failure = runCatching { library.importFile(original, copy = true) { if (it.startsWith("Saving ")) throw CancellationException("Generated cancellation") } }.exceptionOrNull()
        assertTrue(failure is CancellationException); assertTrue(library.dao.books().isEmpty())
        assertEquals(before, app.sources.managedDirectory.listFiles().orEmpty().toSet())
        assertTrue(children(root(folder)).all { !it.directory || children(it.uri).isEmpty() })
        assertArrayEquals(bytes(), app.sources.input(original).use { it.readBytes() })
        assertArrayEquals(bytes(), copyBytes(library.importFile(original, copy = true)))
    }
    @Test fun recoveryDeletesOnlyUncommittedCopiesAndKeepsCommittedCopies() = runBlocking {
        val folder = select(); val source = file("source/Recovery.cbz")
        val abandoned = app.sources.copies.publish(source, folder, "Recovery", "Abandoned.cbz") {}
        app.sources.copies.recover(emptySet())
        assertFalse(abandoned.ticket.exists())
        assertTrue(children(children(root(folder)).single().uri).isEmpty())
        val committed = app.sources.copies.publish(source, folder, "Recovery", "Committed.cbz") {}
        app.sources.copies.recover(setOf(committed.uri.toString()))
        assertFalse(committed.ticket.exists()); assertArrayEquals(bytes(), app.sources.input(committed.uri).use { it.readBytes() })
    }
    @Test fun revokedDestinationDoesNotFallBackToPrivateStorage() = runBlocking {
        select(); val original = file("source/Denied.cbz")
        configure("copies") { putBoolean("denied", true) }
        assertFalse(app.sources.copies.available(app.preferences.current().copyFolderUri))
        val before = app.sources.managedDirectory.listFiles().orEmpty().toSet()
        assertTrue(runCatching { library.importFile(original, copy = true) }.isFailure)
        assertEquals(before, app.sources.managedDirectory.listFiles().orEmpty().toSet()); assertTrue(library.dao.books().isEmpty())
    }
    @Test fun togglePromptsBeforeEnablingAndSelectionPersistsReadWriteAccess() = runBlocking {
        val state = SavedStateHandle()
        lateinit var model: LibraryViewModel
        instrumentation.runOnMainSync { model = LibraryViewModel(app, state) }
        model.setCopyImports(true).join()
        assertTrue(model.copyFolderRequested.value); assertFalse(app.preferences.current().copyImports)
        model.selectCopyFolder(null).join(); assertFalse(app.preferences.current().copyImports)
        assertFalse(model.copyFolderRequested.value)
        val selected = grantFolder("selected")
        model.setCopyImports(true).join(); model.selectCopyFolder(selected).join()
        val preferences = app.preferences.current()
        assertTrue(preferences.copyImports); assertEquals(selected.toString(), preferences.copyFolderUri)
        assertTrue(app.contentResolver.persistedUriPermissions.any { it.uri == selected && it.isReadPermission && it.isWritePermission })
        model.setCopyImports(false).join(); assertEquals(selected.toString(), app.preferences.current().copyFolderUri)
        model.setCopyImports(true).join(); assertFalse(model.copyFolderRequested.value); assertTrue(app.preferences.current().copyImports)
    }
    @Test fun temporaryImportsWaitForDestinationAndCanBeCancelled() = runBlocking {
        val state = SavedStateHandle()
        lateinit var model: LibraryViewModel
        instrumentation.runOnMainSync { model = LibraryViewModel(app, state) }
        val source = file("source/Temporary.cbz")
        model.import(source, temporary = true).join()
        assertTrue(model.copyFolderRequested.value)
        assertTrue(state.get<String>("pendingCopyImports")!!.contains("Temporary.cbz"))
        model.selectCopyFolder(null).join()
        assertEquals("[]", state.get<String>("pendingCopyImports")); assertFalse(model.copyFolderRequested.value)
    }
    @Test fun selectedDestinationWinsOverAnInterruptedPreferenceRestore() = runBlocking {
        lateinit var model: LibraryViewModel
        instrumentation.runOnMainSync { model = LibraryViewModel(app, SavedStateHandle()) }
        val folder = grantFolder("selected-after-restore")
        model.setCopyImports(true).join()
        app.library.dao.save(RestoreJournal(preferences = PanelgroveJson.encodeToString(AppPreferences(copyImports = true))))
        try {
            model.selectCopyFolder(folder).join()
            assertEquals(folder.toString(), app.preferences.current().copyFolderUri)
            assertTrue(app.preferences.current().copyImports); assertNull(app.library.dao.pendingRestore())
        } finally { app.library.dao.clearRestore() }
    }
    @Test fun metadataFreeCopiesInferSeriesFromFilenamesAndUseUnsortedForUnknowns() = runBlocking {
        val stripped = ByteArrayOutputStream().also { output -> ZipOutputStream(output).use { zip ->
            ZipInputStream(ByteArrayInputStream(bytes())).use { input ->
                while (true) {
                    val entry = input.nextEntry ?: break
                    if (!entry.isDirectory && ArchiveRules.isImage(entry.name)) {
                        zip.putNextEntry(ZipEntry(entry.name)); input.copyTo(zip); zip.closeEntry()
                    }
                }
            }
        } }.toByteArray()
        val folder = select()
        val named = library.importFile(file("source/Example Series Volume 2.cbz", stripped), copy = true)
        val unknown = library.importFile(file("source/001.cbz", stripped), copy = true)
        assertEquals("Example Series", named.series); assertEquals("", unknown.series)
        assertEquals(setOf("Example Series", "Unsorted"), children(root(folder)).map { it.name }.toSet())
    }
    @Test fun externalPdfAndRarCopiesStillRenderThroughTheIsolatedDecoder() = runBlocking {
        select()
        for (name in listOf("sample.pdf", "rar5.cbr")) {
            val data = instrumentation.context.assets.open("fixtures/$name").use { it.readBytes() }
            val book = library.importFile(file("source/$name", data), copy = true)
            app.engine.open(book).use { session -> assertTrue(app.engine.render(session, 3, 300, 400).height > 0) }
        }
    }
    @Test fun externallyChangedCopyRefreshesOnOpenAndOriginalRescanRepairsIt() = runBlocking {
        select(); val original = file("source/Changed.cbz")
        val book = library.importFile(original, copy = true)
        library.openSession(book.id) {}.use { assertEquals(4, it.index.pages.size) }
        val changed = instrumentation.context.assets.open("fixtures/mixed-images.cbz").use { it.readBytes() }
        app.contentResolver.openOutputStream(Uri.parse(book.uri), "wt")!!.use { it.write(changed) }
        library.openSession(book.id) {}.use { assertEquals(3, it.index.pages.size) }
        val repaired = library.importFile(original, copy = true)
        assertArrayEquals(bytes(), copyBytes(repaired)); assertEquals(book.id, repaired.id)
        assertEquals(4, repaired.pageCount)
    }
}
