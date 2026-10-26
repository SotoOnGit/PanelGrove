package dev.soto.panelgrove.core

import org.junit.Assert.*
import org.junit.Test

class CopyNamingTest {
    @Test fun readableSeriesAndFilesKeepTheirNames() {
        assertEquals("The Moon on a Rainy Night", CopyNaming.seriesFolder("The Moon on a Rainy Night"))
        assertEquals("Volume 12.cbz", CopyNaming.filename("Volume 12.CBZ"))
        assertEquals("Unsorted", CopyNaming.seriesFolder(""))
    }
    @Test fun traversalControlCharactersAndProviderReservedNamesAreSanitized() {
        for (input in listOf("..", "../outside", "C:\\outside", "A/B", "A:B", "bad\nname", "x".repeat(500))) {
            val result = CopyNaming.seriesFolder(input)
            assertFalse(result.contains('/')); assertFalse(result.contains('\\'))
            assertFalse(result == "." || result == "..")
            assertTrue(result.length <= 110)
        }
        assertNotEquals(CopyNaming.seriesFolder("A/B"), CopyNaming.seriesFolder("A:B"))
    }
    @Test fun unicodeIsPreservedWithoutCuttingSurrogatePairs() {
        assertEquals("雨夜の月", CopyNaming.seriesFolder("雨夜の月"))
        val result = CopyNaming.seriesFolder("😀".repeat(130))
        assertFalse(result.contains('\uFFFD'))
        assertTrue(result.toByteArray(Charsets.UTF_8).size < 200)
        assertTrue(result.codePointCount(0, result.length) < 130)
    }
    @Test fun filenamesInferSeriesAndKeepNumericUnknownsUnsorted() {
        assertEquals("My Series", CopyNaming.inferredSeries("My Series - Volume 12.cbz"))
        assertEquals("My Series", CopyNaming.inferredSeries("My Series Ch. 45.cbr"))
        assertEquals("", CopyNaming.inferredSeries("001.cbz"))
    }
    @Test fun backupsAcceptOwnedSafCopiesButRejectMissingOwnership() {
        val book = Book("owned", "content://docs/tree/root/document/copy", "Book.cbz", BookFormat.ZIP, managed = true, copyRootUri = "content://docs/tree/root")
        LibraryBackup(books = listOf(book), preferences = AppPreferences(copyImports = true, copyFolderUri = book.copyRootUri)).validate()
        assertTrue(runCatching { LibraryBackup(books = listOf(book.copy(copyRootUri = null))).validate() }.isFailure)
        assertTrue(runCatching { LibraryBackup(books = listOf(book.copy(managed = false))).validate() }.isFailure)
        assertTrue(runCatching { LibraryBackup(preferences = AppPreferences(copyFolderUri = "file:///private")).validate() }.isFailure)
    }
    @Test fun backupMergeRetainsLiveCopyOwnershipAndLocationAfterRelocation() {
        val old = Book("owned", "content://docs/tree/old/document/copy", "Book.cbz", BookFormat.ZIP, managed = true, copyRootUri = "content://docs/tree/old")
        val restored = mergeBackup(LibraryBackup(books = listOf(old)), LibraryBackup())
        val live = restored.copy(books = listOf(restored.books.single().copy(uri = "content://docs/tree/new/document/copy", copyRootUri = "content://docs/tree/new")))
        val again = mergeBackup(LibraryBackup(books = listOf(old)), live)
        assertEquals(1, again.books.size); assertEquals(live.books.single().copyRootUri, again.books.single().copyRootUri)
        again.validate()
    }
}
