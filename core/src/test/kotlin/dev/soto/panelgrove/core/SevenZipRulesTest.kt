package dev.soto.panelgrove.core

import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import org.junit.Assert.*
import org.junit.Test

class SevenZipRulesTest {
    @Test fun sevenZipIsDetectedBySignatureAndExtension() {
        for (name in listOf("volume.cb7", "volume.CB7", "volume.7z", "volume.7Z"))
            assertEquals(BookFormat.SEVEN_Z, ArchiveRules.format(name))
        val signature = byteArrayOf(0x37, 0x7a, 0xbc.toByte(), 0xaf.toByte(), 0x27, 0x1c)
        assertEquals(BookFormat.SEVEN_Z, ArchiveRules.detectFormat(signature))
        assertEquals(BookFormat.SEVEN_Z, ArchiveRules.detectFormat(signature + byteArrayOf(0, 4)))
        for (length in 0 until signature.size) assertNull(ArchiveRules.detectFormat(signature.copyOf(length)))
        assertNull(ArchiveRules.format("volume.7z.001"))
        assertNull(ArchiveRules.format("volume.cb7.002"))
    }

    @Test fun sevenZipBackupRetainsReadingDataAndManagedCopyNames() {
        val book = Book("seven", "content://docs/volume", "Volume.CB7", BookFormat.SEVEN_Z,
            position = ReadingPosition("page-2", 1, .25f), favorite = true)
        val backup = LibraryBackup(books = listOf(book), bookmarks = listOf(Bookmark("mark", book.id, "page-2", 1)))
        val restored = PanelgroveJson.decodeFromString<LibraryBackup>(PanelgroveJson.encodeToString(backup))
        restored.validate()
        assertEquals(backup, restored)
        val merged = mergeBackup(restored, LibraryBackup())
        assertEquals(BookFormat.SEVEN_Z, merged.books.single().format)
        assertEquals(book.position, merged.books.single().position)
        assertEquals(backup.bookmarks, merged.bookmarks)
        assertEquals("Volume.cb7", CopyNaming.filename(book.displayName))
        assertEquals("Volume.7z", CopyNaming.filename("Volume.7Z"))
    }
}
