package dev.soto.panelgrove.core

import org.junit.Assert.*
import org.junit.Test

class DisplayAdjustmentsTest {
    @Test fun neutralIsAnExactIdentityAndGammaKeepsEndpoints() {
        assertArrayEquals(IntArray(256) { it }, displayLookup(1f, 1f))
        for (gamma in listOf(.5f, 1f, 2f)) {
            val lookup = displayLookup(1f, gamma)
            assertEquals(0, lookup[0]); assertEquals(255, lookup[255])
            assertTrue((1..255).all { lookup[it - 1] <= lookup[it] })
        }
        assertEquals(181, displayLookup(1f, 2f)[128])
        assertEquals(64, displayLookup(1f, .5f)[128])
    }

    @Test fun contrastChangesBothSidesOfMidGray() {
        val high = displayLookup(2f, 1f)
        assertEquals(0, high[64]); assertEquals(255, high[192])
        val low = displayLookup(.5f, 1f)
        assertEquals(64, low[0]); assertEquals(191, low[255])
    }

    @Test fun displaySettingsFollowExistingPrecedenceAndSurviveBackup() {
        val book = Book("book", "content://books/1", "1.cbz", BookFormat.ZIP, series = "A", settings = ReaderOverrides(gamma = 1.7f))
        val prefs = AppPreferences(reader = ReaderSettings(contrast = 1.1f, gamma = .8f),
            seriesSettings = mapOf("A" to ReaderOverrides(contrast = 1.4f)))
        val resolved = resolveSettings(prefs, book)
        assertEquals(1.4f, resolved.contrast); assertEquals(1.7f, resolved.gamma)
        assertEquals(resolved, ReaderOverrides.from(resolved).applyTo(ReaderSettings()))
        val backup = LibraryBackup(preferences = prefs, books = listOf(book))
        val restored = PanelgroveJson.decodeFromString<LibraryBackup>(PanelgroveJson.encodeToString(backup))
        restored.validate(); assertEquals(backup, restored)
        assertEquals(1f, PanelgroveJson.decodeFromString<ReaderSettings>("{}").gamma)
    }

    @Test fun invalidDisplaySettingsAndCoverPathsAreRejected() {
        for (value in listOf(.49f, 2.01f, Float.NaN, Float.POSITIVE_INFINITY)) {
            assertTrue(runCatching { displayLookup(1f, value) }.isFailure)
            assertTrue(runCatching { LibraryBackup(preferences = AppPreferences(reader = ReaderSettings(gamma = value))).validate() }.isFailure)
            assertTrue(runCatching { LibraryBackup(preferences = AppPreferences(seriesSettings = mapOf("A" to ReaderOverrides(contrast = value)))).validate() }.isFailure)
        }
        val book = Book("book", "content://books/1", "1.cbz", BookFormat.ZIP)
        assertTrue(runCatching { LibraryBackup(books = listOf(book.copy(customCover = "../escape"))).validate() }.isFailure)
    }

    @Test fun backupMergeKeepsLocalCoversWithoutAdoptingForeignFileReferences() {
        val book = Book("book", "content://books/1", "1.cbz", BookFormat.ZIP)
        val incoming = LibraryBackup(books = listOf(book.copy(customCover = "foreign-cover")))
        assertNull(mergeBackup(incoming, LibraryBackup()).books.single().customCover)
        val merged = mergeBackup(incoming, LibraryBackup(books = listOf(book.copy(customCover = "local-cover"))))
        assertEquals("local-cover", merged.books.single().customCover)
    }
}
