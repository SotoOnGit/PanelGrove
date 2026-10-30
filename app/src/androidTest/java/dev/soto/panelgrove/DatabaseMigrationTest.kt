package dev.soto.panelgrove

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.soto.panelgrove.core.*
import dev.soto.panelgrove.data.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DatabaseMigrationTest {
    @Test fun versionOneLibraryMigratesWithoutLosingBookOrReadingData() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "hardening-migration.db"
        context.deleteDatabase(name)
        val book = Book("migration", "content://example/book", "book.cbz", BookFormat.ZIP,
            favorite = true, position = ReadingPosition("p2", 1, .5f), collections = listOf("Kept"))
        try {
            SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(name), null).use { db ->
                db.execSQL("CREATE TABLE books (id TEXT NOT NULL PRIMARY KEY, uri TEXT NOT NULL, payload TEXT NOT NULL)")
                db.execSQL("CREATE UNIQUE INDEX index_books_uri ON books (uri)")
                db.execSQL("CREATE TABLE sources (id TEXT NOT NULL PRIMARY KEY, payload TEXT NOT NULL)")
                db.execSQL("CREATE TABLE bookmarks (id TEXT NOT NULL PRIMARY KEY, bookId TEXT NOT NULL, payload TEXT NOT NULL)")
                db.execSQL("CREATE TABLE history (id TEXT NOT NULL PRIMARY KEY, bookId TEXT NOT NULL, openedAt INTEGER NOT NULL, payload TEXT NOT NULL)")
                db.execSQL("INSERT INTO books VALUES (?, ?, ?)", arrayOf(book.id, book.uri, PanelgroveJson.encodeToString(book)))
                val mark = Bookmark("migration-mark", book.id, "p2", 1)
                db.execSQL("INSERT INTO bookmarks VALUES (?, ?, ?)", arrayOf(mark.id, mark.bookId, PanelgroveJson.encodeToString(mark)))
                db.version = 1
            }
            val migrated = Room.databaseBuilder(context, LibraryDatabase::class.java, name).addMigrations(LibraryDatabase.MIGRATION_1_2, LibraryDatabase.MIGRATION_2_3).build()
            try {
                assertEquals(book, migrated.library().book(book.id)!!.book())
                assertEquals(1, migrated.library().bookmarks().size)
                assertNull(migrated.library().pendingRestore())
                assertTrue(migrated.library().readingRecords().isEmpty())
                migrated.library().startStatistics(StatisticsStart(startedAt = 123))
                assertEquals(123L, migrated.library().statisticsStart()!!.startedAt)
                migrated.library().save(RestoreJournal(preferences = PanelgroveJson.encodeToString(AppPreferences())))
                assertNotNull(migrated.library().pendingRestore())
            } finally { migrated.close() }
        } finally { context.deleteDatabase(name) }
    }

    @Test fun versionTwoMigratesWithItsPendingRestoreAndNewStatisticsTables() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "statistics-v2-migration.db"
        context.deleteDatabase(name)
        try {
            SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(name), null).use { db ->
                db.execSQL("CREATE TABLE books (id TEXT NOT NULL PRIMARY KEY, uri TEXT NOT NULL, payload TEXT NOT NULL)")
                db.execSQL("CREATE UNIQUE INDEX index_books_uri ON books (uri)")
                db.execSQL("CREATE TABLE sources (id TEXT NOT NULL PRIMARY KEY, payload TEXT NOT NULL)")
                db.execSQL("CREATE TABLE bookmarks (id TEXT NOT NULL PRIMARY KEY, bookId TEXT NOT NULL, payload TEXT NOT NULL)")
                db.execSQL("CREATE TABLE history (id TEXT NOT NULL PRIMARY KEY, bookId TEXT NOT NULL, openedAt INTEGER NOT NULL, payload TEXT NOT NULL)")
                db.execSQL("CREATE TABLE restore_journal (id INTEGER NOT NULL PRIMARY KEY, preferences TEXT NOT NULL)")
                db.execSQL("INSERT INTO restore_journal VALUES (1, ?)", arrayOf(PanelgroveJson.encodeToString(AppPreferences(trueBlack = true))))
                db.version = 2
            }
            val migrated = Room.databaseBuilder(context, LibraryDatabase::class.java, name).addMigrations(LibraryDatabase.MIGRATION_2_3).build()
            try {
                assertTrue(PanelgroveJson.decodeFromString<AppPreferences>(migrated.library().pendingRestore()!!.preferences).trueBlack)
                assertTrue(migrated.library().readingRecords().isEmpty())
                assertTrue(migrated.library().readingCompletions().isEmpty())
                assertNull(migrated.library().statisticsStart())
            } finally { migrated.close() }
        } finally { context.deleteDatabase(name) }
    }
}
