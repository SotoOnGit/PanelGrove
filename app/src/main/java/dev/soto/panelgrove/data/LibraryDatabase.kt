package dev.soto.panelgrove.data

import androidx.room.*
import dev.soto.panelgrove.core.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "books", indices = [Index(value = ["uri"], unique = true)])
data class BookRow(@PrimaryKey val id: String, val uri: String, val payload: String)
@Entity(tableName = "sources") data class SourceRow(@PrimaryKey val id: String, val payload: String)
@Entity(tableName = "bookmarks") data class BookmarkRow(@PrimaryKey val id: String, val bookId: String, val payload: String)
@Entity(tableName = "history") data class HistoryRow(@PrimaryKey val id: String, val bookId: String, val openedAt: Long, val payload: String)
@Entity(tableName = "restore_journal") data class RestoreJournal(@PrimaryKey val id: Int = 1, val preferences: String)
@Entity(tableName = "reading_records", indices = [Index("editionId")])
data class ReadingRecordRow(@PrimaryKey val id: String, val editionId: String, val payload: String)
@Entity(tableName = "reading_completions", indices = [Index("editionId")])
data class ReadingCompletionRow(@PrimaryKey val id: String, val editionId: String, val payload: String)
@Entity(tableName = "statistics_start") data class StatisticsStart(@PrimaryKey val id: Int = 1, val startedAt: Long)

@Dao interface LibraryDao {
    @Query("SELECT * FROM reading_records") fun observeReadingRecords(): Flow<List<ReadingRecordRow>>
    @Query("SELECT * FROM reading_completions") fun observeReadingCompletions(): Flow<List<ReadingCompletionRow>>
    @Query("SELECT * FROM statistics_start WHERE id = 1") fun observeStatisticsStart(): Flow<StatisticsStart?>
    @Query("SELECT * FROM reading_records") suspend fun readingRecords(): List<ReadingRecordRow>
    @Query("SELECT * FROM reading_records WHERE id = :id") suspend fun readingRecord(id: String): ReadingRecordRow?
    @Query("SELECT * FROM reading_records WHERE editionId = :id") suspend fun readingRecordsForEdition(id: String): List<ReadingRecordRow>
    @Query("SELECT * FROM reading_completions") suspend fun readingCompletions(): List<ReadingCompletionRow>
    @Query("SELECT * FROM reading_completions WHERE id = :id") suspend fun readingCompletion(id: String): ReadingCompletionRow?
    @Query("SELECT * FROM reading_completions WHERE editionId = :id") suspend fun readingCompletionsForEdition(id: String): List<ReadingCompletionRow>
    @Query("SELECT * FROM statistics_start WHERE id = 1") suspend fun statisticsStart(): StatisticsStart?
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun startStatistics(row: StatisticsStart)
    @Upsert suspend fun save(row: StatisticsStart)
    @Upsert suspend fun save(row: ReadingRecordRow)
    @Upsert suspend fun save(row: ReadingCompletionRow)
    @Query("SELECT * FROM books") fun observeBooks(): Flow<List<BookRow>>
    @Query("SELECT * FROM sources") fun observeSources(): Flow<List<SourceRow>>
    @Query("SELECT * FROM bookmarks") fun observeBookmarks(): Flow<List<BookmarkRow>>
    @Query("SELECT * FROM history ORDER BY openedAt DESC LIMIT 500") fun observeHistory(): Flow<List<HistoryRow>>
    @Query("SELECT * FROM books") suspend fun books(): List<BookRow>
    @Query("SELECT * FROM sources") suspend fun sources(): List<SourceRow>
    @Query("SELECT * FROM bookmarks") suspend fun bookmarks(): List<BookmarkRow>
    @Query("SELECT * FROM bookmarks WHERE bookId = :id") suspend fun bookmarksForBook(id: String): List<BookmarkRow>
    @Query("SELECT * FROM history ORDER BY openedAt DESC LIMIT 500") suspend fun history(): List<HistoryRow>
    @Query("SELECT * FROM books WHERE id = :id") suspend fun book(id: String): BookRow?
    @Query("SELECT * FROM books WHERE uri = :uri") suspend fun bookByUri(uri: String): BookRow?
    @Upsert suspend fun save(row: BookRow)
    @Upsert suspend fun save(row: SourceRow)
    @Upsert suspend fun save(row: BookmarkRow)
    @Upsert suspend fun save(row: HistoryRow)
    @Query("DELETE FROM books WHERE id = :id") suspend fun deleteBook(id: String)
    @Query("DELETE FROM bookmarks WHERE bookId = :id") suspend fun deleteBookmarks(id: String)
    @Query("DELETE FROM history WHERE bookId = :id") suspend fun deleteHistory(id: String)
    @Query("DELETE FROM history") suspend fun clearHistory()
    @Query("DELETE FROM bookmarks WHERE id = :id") suspend fun deleteBookmark(id: String)
    @Query("DELETE FROM history WHERE id NOT IN (SELECT id FROM history ORDER BY openedAt DESC LIMIT 500)") suspend fun trimHistory()
    @Query("DELETE FROM sources WHERE id = :id") suspend fun deleteSource(id: String)
    @Query("SELECT * FROM restore_journal WHERE id = 1") suspend fun pendingRestore(): RestoreJournal?
    @Upsert suspend fun save(row: RestoreJournal)
    @Query("DELETE FROM restore_journal WHERE id = 1") suspend fun clearRestore()
}

@Database(entities = [BookRow::class, SourceRow::class, BookmarkRow::class, HistoryRow::class, RestoreJournal::class,
    ReadingRecordRow::class, ReadingCompletionRow::class, StatisticsStart::class], version = 3, exportSchema = true)
abstract class LibraryDatabase : RoomDatabase() {
    abstract fun library(): LibraryDao
    companion object {
        val MIGRATION_2_3 = object : androidx.room.migration.Migration(2, 3) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `reading_records` (`id` TEXT NOT NULL, `editionId` TEXT NOT NULL, `payload` TEXT NOT NULL, PRIMARY KEY(`id`))")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_reading_records_editionId` ON `reading_records` (`editionId`)")
                db.execSQL("CREATE TABLE IF NOT EXISTS `reading_completions` (`id` TEXT NOT NULL, `editionId` TEXT NOT NULL, `payload` TEXT NOT NULL, PRIMARY KEY(`id`))")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_reading_completions_editionId` ON `reading_completions` (`editionId`)")
                db.execSQL("CREATE TABLE IF NOT EXISTS `statistics_start` (`id` INTEGER NOT NULL, `startedAt` INTEGER NOT NULL, PRIMARY KEY(`id`))")
            }
        }
        val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `restore_journal` (`id` INTEGER NOT NULL, `preferences` TEXT NOT NULL, PRIMARY KEY(`id`))")
            }
        }
    }
}

fun Book.row() = BookRow(id, uri, PanelgroveJson.encodeToString(this))
fun BookRow.book(): Book = PanelgroveJson.decodeFromString(payload)
fun LibrarySource.row() = SourceRow(id, PanelgroveJson.encodeToString(this))
fun SourceRow.source(): LibrarySource = PanelgroveJson.decodeFromString(payload)
fun Bookmark.row() = BookmarkRow(id, bookId, PanelgroveJson.encodeToString(this))
fun BookmarkRow.bookmark(): Bookmark = PanelgroveJson.decodeFromString(payload)
fun HistoryEntry.row() = HistoryRow(id, bookId, openedAt, PanelgroveJson.encodeToString(this))
fun HistoryRow.entry(): HistoryEntry = PanelgroveJson.decodeFromString(payload)
fun ReadingRecord.row() = ReadingRecordRow(id, subject.editionId, PanelgroveJson.encodeToString(this))
fun ReadingRecordRow.record(): ReadingRecord = PanelgroveJson.decodeFromString(payload)
fun ReadingCompletion.row() = ReadingCompletionRow(id, subject.editionId, PanelgroveJson.encodeToString(this))
fun ReadingCompletionRow.completion(): ReadingCompletion = PanelgroveJson.decodeFromString(payload)
