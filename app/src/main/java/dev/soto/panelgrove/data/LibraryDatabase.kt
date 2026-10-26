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

@Dao interface LibraryDao {
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

@Database(entities = [BookRow::class, SourceRow::class, BookmarkRow::class, HistoryRow::class, RestoreJournal::class], version = 2, exportSchema = true)
abstract class LibraryDatabase : RoomDatabase() {
    abstract fun library(): LibraryDao
    companion object {
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
