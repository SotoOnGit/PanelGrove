package dev.soto.panelgrove.core

import org.junit.Assert.*
import org.junit.Test

class BackupBrandMigrationTest {
    private fun legacyBackup(): LibraryBackup = PanelgroveJson.decodeFromString("""
        {"version":1,"books":[{"id":"old-book","uri":"readly://managed/volume.cbz",
        "displayName":"Volume.cbz","format":"ZIP","managed":true,"favorite":true,"addedAt":123,
        "position":{"pageId":"page-2","index":1,"offset":0.4}}],
        "bookmarks":[{"id":"mark","bookId":"old-book","pageId":"page-2","pageIndex":1,"createdAt":123}],
        "history":[{"id":"visit","bookId":"old-book","openedAt":123}]}
    """.trimIndent())

    @Test fun previousPreviewBackupKeepsReadingDataAndUsesTheCurrentScheme() {
        val original = legacyBackup()
        val migrated = original.withCurrentLocations()
        migrated.validate()
        assertEquals(original.books.single().copy(uri = "panelgrove://managed/volume.cbz"), migrated.books.single())
        assertEquals(original.bookmarks, migrated.bookmarks)
        assertEquals(original.history, migrated.history)
        assertEquals(migrated, migrated.withCurrentLocations())
    }

    @Test fun externalDocumentsAndTheirCopyOwnershipArePreserved() {
        val book = legacyBackup().books.single().copy(uri = "content://docs/volume", originUri = "content://docs/original",
            copyRootUri = "content://docs/tree/copies")
        val backup = legacyBackup().copy(books = listOf(book), preferences = AppPreferences(copyFolderUri = book.copyRootUri))
        backup.withCurrentLocations().validate()
        assertEquals(backup, backup.withCurrentLocations())
    }

    @Test fun repeatingAnOldBackupAfterRelinkingDoesNotDuplicateReadingData() {
        val incoming = legacyBackup().withCurrentLocations()
        val first = mergeBackup(incoming, LibraryBackup())
        val relinked = first.copy(books = listOf(first.books.single().copy(uri = "content://docs/relocated", managed = false)))
        val repeated = mergeBackup(legacyBackup().withCurrentLocations(), relinked)
        assertEquals(relinked.books, repeated.books)
        assertEquals(relinked.bookmarks, repeated.bookmarks)
        assertEquals(relinked.history, repeated.history)
    }

    @Test fun migrationDoesNotBypassManagedPathOrDuplicateLocationValidation() {
        val original = legacyBackup()
        for (uri in listOf("readly://managed/../outside.cbz", "readly://managed/folder/file.cbz", "readly://staging/file.cbz")) {
            val migrated = original.copy(books = listOf(original.books.single().copy(uri = uri))).withCurrentLocations()
            assertTrue("Unsafe legacy location accepted: $uri", runCatching { migrated.validate() }.isFailure)
        }
        val aliases = original.copy(books = original.books + original.books.single().copy(id = "new-book", uri = "panelgrove://managed/volume.cbz"))
        assertTrue(runCatching { aliases.withCurrentLocations().validate() }.isFailure)
    }
}
