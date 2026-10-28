package dev.soto.panelgrove

import android.net.Uri
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import dev.soto.panelgrove.core.*
import dev.soto.panelgrove.data.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.UUID

class LanguageEditionsInstrumentedTest {
    @Test fun assigningRescanningAndRestoringEditionsKeepsSeparateReadingData(): Unit = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val app = instrumentation.targetContext.applicationContext as PanelgroveApplication
        val database = Room.inMemoryDatabaseBuilder(app, LibraryDatabase::class.java).build()
        val restoredDatabase = Room.inMemoryDatabaseBuilder(app, LibraryDatabase::class.java).build()
        val library = LibraryRepository(app, database, app.preferences, app.sources, app.engine)
        val restored = LibraryRepository(app, restoredDatabase, app.preferences, app.sources, app.engine)
        val files = mutableListOf<File>()
        try {
            val editions = listOf("sample.cbz" to "en", "rar5.cbr" to "ja", "sample.pdf" to "fr").mapIndexed { index, (fixture, language) ->
                val file = File(app.sources.managedDirectory, "edition-${UUID.randomUUID()}-$fixture").also { files += it }
                instrumentation.context.assets.open("fixtures/$fixture").use { input -> file.outputStream().use { input.copyTo(it) } }
                val imported = library.importFile(Uri.parse("panelgrove://managed/${file.name}"))
                library.editMetadata(imported.id) { it.copy(series = "Edition Acceptance", number = "1", language = language, metadataEdited = true) }
                val book = library.book(imported.id)!!
                app.engine.open(book).use { session ->
                    val pageIndex = index.coerceAtMost(session.index.pages.lastIndex)
                    library.position(book.id, session.index.pages[pageIndex], pageIndex, .25f)
                    library.toggleBookmark(book, session.index.pages[pageIndex], pageIndex)
                }
                library.openSession(book.id) {}.close()
                book
            }
            val before = database.library().books().map { it.book() }.associateBy { it.id }
            val marks = database.library().bookmarks().map { it.bookmark() }
            val history = database.library().history().map { it.entry() }
            editions.forEach { book ->
                library.importFile(Uri.parse(book.uri))
                val expected = before.getValue(book.id)
                assertEquals(expected, library.book(book.id)!!.copy(revision = expected.revision, copyRevision = expected.copyRevision))
            }
            assertEquals(3, database.library().books().size)
            assertEquals(marks.toSet(), database.library().bookmarks().map { it.bookmark() }.toSet())
            assertEquals(history.toSet(), database.library().history().map { it.entry() }.toSet())
            val backup = LibraryBackup(books = database.library().books().map { it.book() }, bookmarks = marks, history = history)
            val decoded = PanelgroveJson.decodeFromString<LibraryBackup>(PanelgroveJson.encodeToString(backup))
            val merged = mergeBackup(decoded, LibraryBackup())
            merged.books.forEach { restoredDatabase.library().save(it.row()) }
            merged.bookmarks.forEach { restoredDatabase.library().save(it.row()) }
            merged.history.forEach { restoredDatabase.library().save(it.row()) }
            val current = restored.book(editions.first().id)!!
            assertEquals(3, languageEditions(current, restoredDatabase.library().books().map { it.book() }).size)
            editions.forEach { book ->
                val reopened = restored.book(book.id)!!
                assertEquals(before.getValue(book.id).editionLanguage, reopened.editionLanguage)
                assertEquals(before.getValue(book.id).position, reopened.position)
            }
            assertEquals(3, restoredDatabase.library().bookmarks().size)
            assertEquals(3, restoredDatabase.library().history().size)
        } finally {
            database.library().books().forEach { library.remove(it.id, false) }
            restoredDatabase.library().books().forEach { restored.remove(it.id, false) }
            database.close(); restoredDatabase.close(); files.forEach { it.delete() }
        }
    }
}
