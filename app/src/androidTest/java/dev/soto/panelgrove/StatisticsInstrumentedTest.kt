package dev.soto.panelgrove

import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import dev.soto.panelgrove.core.*
import dev.soto.panelgrove.data.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import java.time.LocalDate

class StatisticsInstrumentedTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val app get() = instrumentation.targetContext.applicationContext as PanelgroveApplication
    private lateinit var db: LibraryDatabase
    private lateinit var library: LibraryRepository
    private val book = Book("stats-book", "content://example/stats", "Statistics.cbz", BookFormat.ZIP, series = "Series", number = "1", language = "ja")
    private val subject = ReadingSubject.from(book)
    private val now = System.currentTimeMillis()
    private val date = LocalDate.now().toString()
    private fun record(id: String = "stats-row", pages: Map<String, Long> = mapOf("p1" to now)) =
        ReadingRecord(id, "stats-session", subject, date, "1", now - 10_000, now, 10_000, pages)
    private val backupUri get() = DocumentsContract.buildDocumentUri("dev.soto.panelgrove.test.documents", "hardening/statistics/backup.json")

    @Before fun setup() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(app, LibraryDatabase::class.java).build()
        library = LibraryRepository(app, db, app.preferences, app.sources, app.engine)
        library.dao.save(book.row())
        library.dao.startStatistics(StatisticsStart(startedAt = now - 20_000))
        app.contentResolver.call(Uri.parse("content://dev.soto.panelgrove.test.documents"), "fixture", null,
            Bundle().apply { putString("id", "hardening/statistics"); putBoolean("directory", true) })
        Unit
    }
    @After fun cleanup() {
        db.close()
        app.contentResolver.call(Uri.parse("content://dev.soto.panelgrove.test.documents"), "fixture", null,
            Bundle().apply { putString("id", "hardening/statistics"); putBoolean("reset", true) })
    }
    @Test fun checkpointsUpsertAndChaptersFinishOnlyOnceAfterEveryPageWasViewed() = runBlocking {
        val chapters = mapOf("1" to setOf("p1", "p2"))
        library.saveReading(listOf(record()), chapters)
        assertTrue(library.dao.readingCompletions().isEmpty())
        val full = record(pages = mapOf("p1" to now - 2_000, "p2" to now))
        library.saveReading(listOf(full), chapters)
        library.saveReading(listOf(full), chapters)
        assertEquals(1, library.dao.readingRecords().size)
        assertEquals(1, library.dao.readingCompletions().size)
        assertEquals("1", library.dao.readingCompletions().single().completion().chapter)
        library.saveReading(listOf(record("reread", mapOf("p1" to now + 5_000, "p2" to now + 6_000)).copy(endedAt = now + 10_000)), chapters)
        assertEquals(2, library.dao.readingCompletions().size)
    }
    @Test fun removalAndHistoryClearingRetainStatisticsAndBackupImportsAreIdempotent() = runBlocking {
        library.saveReading(listOf(record()), emptyMap())
        library.finishReading(book.id, "stats-finish")
        library.dao.clearHistory()
        library.remove(book.id, false)
        assertEquals(1, library.dao.readingRecords().size)
        assertEquals(1, library.dao.readingCompletions().size)
        library.exportBackup(backupUri)
        val backup = app.contentResolver.openInputStream(backupUri)!!.bufferedReader().use { PanelgroveJson.decodeFromString<LibraryBackup>(it.readText()) }
        backup.validate()
        assertTrue(backup.books.isEmpty())
        assertEquals("Statistics", backup.statistics.records.single().subject.title)
        library.restoreBackup(backupUri); library.restoreBackup(backupUri)
        assertEquals(1, library.dao.readingRecords().size)
        assertEquals(1, library.dao.readingCompletions().size)
        assertEquals(now - 20_000, library.dao.statisticsStart()!!.startedAt)
        val fresh = Room.inMemoryDatabaseBuilder(app, LibraryDatabase::class.java).build()
        try {
            val destination = LibraryRepository(app, fresh, app.preferences, app.sources, app.engine)
            destination.restoreBackup(backupUri); destination.restoreBackup(backupUri)
            assertEquals(1, destination.dao.readingRecords().size)
            assertEquals(1, destination.dao.readingCompletions().size)
            assertEquals(10_000L, destination.dao.readingRecords().single().record().activeMillis)
            assertEquals(now - 20_000, destination.dao.statisticsStart()!!.startedAt)
        } finally { fresh.close() }
    }
    @Test fun manuallyMarkingReadDoesNotCreateACompletionAndFinishesAreStableById() = runBlocking {
        library.change(book.id) { it.copy(status = ReadingStatus.READ) }
        assertTrue(library.dao.readingCompletions().isEmpty())
        library.finishReading(book.id, "finish-once")
        library.finishReading(book.id, "finish-once")
        assertEquals(1, library.dao.readingCompletions().size)
    }
}
