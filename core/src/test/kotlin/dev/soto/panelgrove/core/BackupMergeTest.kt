package dev.soto.panelgrove.core

import org.junit.Assert.*
import org.junit.Test

class BackupMergeTest {
    private fun book(id: String = "b", uri: String = "content://docs/book") = Book(id, uri, "book.cbz", BookFormat.ZIP)
    private fun backup(book: Book = book()) = LibraryBackup(books = listOf(book),
        bookmarks = listOf(Bookmark("mark", book.id, "p2", 1, createdAt = 123)),
        history = listOf(HistoryEntry("visit", book.id, 456)))

    @Test fun matchingLocationsKeepLiveIdsAndRemapAllReferences() {
        val incoming = backup(book().copy(favorite = true, position = ReadingPosition("p2", 1, .4f)))
        val merged = mergeBackup(incoming, backup(book("live")))
        assertEquals(listOf("live"), merged.books.map { it.id })
        assertTrue(merged.books.single().favorite)
        assertEquals("live", merged.bookmarks.single().bookId)
        assertEquals("live", merged.history.single().bookId)
        assertEquals(.4f, merged.books.single().position!!.offset)
    }
    @Test fun matchingIdsAtDifferentLocationsProtectUnrelatedLiveDataAndAreRepeatable() {
        val live = backup(book(uri = "content://docs/unrelated"))
        val incoming = backup()
        val first = mergeBackup(incoming, live)
        val second = mergeBackup(incoming, first)
        assertEquals(2, first.books.size)
        assertEquals(live.books.single(), first.books.first { it.uri.endsWith("unrelated") })
        assertEquals(first.books, second.books)
        assertEquals(first.bookmarks, second.bookmarks)
        assertEquals(first.history, second.history)
        assertEquals(2, second.bookmarks.size)
    }
    @Test fun sourceIdCollisionsAreRemappedWithBookReferences() {
        val incoming = backup(book().copy(sourceId = "root")).copy(sources = listOf(LibrarySource("root", "content://docs/tree/new", "New")))
        val live = LibraryBackup(sources = listOf(LibrarySource("root", "content://docs/tree/old", "Old")))
        val merged = mergeBackup(incoming, live)
        assertEquals(2, merged.sources.size)
        assertNotEquals("root", merged.books.single().sourceId)
        assertEquals(merged.sources.first { it.name == "New" }.id, merged.books.single().sourceId)
        assertEquals(merged.sources, mergeBackup(incoming, merged).sources)
    }
    @Test fun matchingSourceLocationsReuseLiveRootId() {
        val incoming = backup(book().copy(sourceId = "root")).copy(sources = listOf(LibrarySource("root", "content://docs/tree/root", "Root")))
        val live = LibraryBackup(sources = listOf(LibrarySource("other", "content://docs/tree/root", "Root")))
        val merged = mergeBackup(incoming, live)
        assertEquals(1, merged.sources.size); assertEquals("other", merged.books.single().sourceId)
    }
    @Test fun freshInstallRelinkAndRetryDoNotRecreateOldLocations() {
        val incoming = backup(book().copy(managed = true, uri = "panelgrove://managed/absent.cbz", originUri = "content://docs/old"))
        val restored = mergeBackup(incoming, LibraryBackup())
        val relocated = restored.copy(books = listOf(restored.books.single().copy(uri = "content://docs/relocated", originUri = null, managed = false)))
        val retry = mergeBackup(incoming, relocated)
        assertEquals(1, retry.books.size); assertEquals("content://docs/relocated", retry.books.single().uri)
        assertEquals(restored.bookmarks, retry.bookmarks)
    }
    @Test fun managedFilenameCollisionDoesNotAliasDifferentBytes() {
        val incoming = backup(book().copy(uri = "panelgrove://managed/same.cbz", managed = true, contentHash = "incoming"))
        val live = backup(incoming.books.single().copy(id = "live", contentHash = "other"))
        val merged = mergeBackup(incoming, live)
        assertEquals(2, merged.books.size)
        assertEquals(2, merged.books.map { it.uri }.distinct().size)
        assertTrue(merged.books.first { it.id != "live" }.uri.contains("restore-"))
        assertEquals(merged.books, mergeBackup(incoming, merged).books)
    }
    @Test fun providerAliasesMergeButOverlappingPagesDoNot() {
        val incoming = backup(book(uri = "content://docs/tree/a/document/b"))
        val live = backup(book("live", "content://docs/document/b"))
        val merged = mergeBackup(incoming, live) { if (it.endsWith("/b")) "document:b" else it }
        assertEquals(1, merged.books.size)
        assertEquals(2, mergeBackup(backup(book(uri = "content://docs/chapter")), live).books.size)
    }
    @Test fun correctedManagedCopyKeepsItsLiveBytesWhenRestoringOldBackup() {
        val incoming = backup(book().copy(managed = true, uri = "panelgrove://managed/old.cbz", originUri = "content://docs/book", contentHash = "old"))
        val live = backup(incoming.books.single().copy(uri = "panelgrove://managed/new.cbz", contentHash = "corrected"))
        val merged = mergeBackup(incoming, live)
        assertEquals(1, merged.books.size)
        assertEquals("panelgrove://managed/new.cbz", merged.books.single().uri)
        assertEquals("corrected", merged.books.single().contentHash)
    }
    @Test fun incomingReferencesManagedFlagsOffsetsAndOverridesAreValidated() {
        val invalid = listOf(
            backup(book().copy(sourceId = "absent")),
            backup(book().copy(managed = true)),
            backup(book().copy(uri = "panelgrove://managed/../outside", managed = true)),
            backup(book().copy(position = ReadingPosition("p", -1, 0f))),
            backup(book().copy(position = ReadingPosition("p", 0, Float.NaN))),
            backup(book().copy(settings = ReaderOverrides(brightness = 2f))),
            backup().copy(history = listOf(HistoryEntry("visit", "b"), HistoryEntry("visit", "b"))),
            backup().copy(bookmarks = listOf(Bookmark("../unsafe", "b", "p", 0))),
        )
        invalid.forEachIndexed { index, backup -> assertTrue("Invalid backup $index accepted", runCatching { backup.validate() }.isFailure) }
    }
}
