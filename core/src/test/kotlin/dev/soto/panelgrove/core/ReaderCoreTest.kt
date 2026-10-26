package dev.soto.panelgrove.core

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.zip.ZipFile

class ReaderCoreTest {
    private fun pages(vararg names: String) = names.map { PageRef(it, it) }
    private fun manifest(vararg names: Pair<String, String>) = """{"series":"Sample","title":"Volume 1","reading_direction":"right-to-left","completeness":"partial","pages":[${names.joinToString { (name, id) -> """{"archive_name":"$name","source_id":"$id","chapter":"1"}""" }}]}"""
    private fun book() = Book("book-1", "content://books/1", "Volume 1.cbz", BookFormat.ZIP)

    @Test fun naturalOrderHandlesUnpaddedNumbersAndHugeValues() {
        assertEquals(listOf("1.png", "02.png", "10.png", "999999999999999999999999999.png"),
            listOf("10.png", "999999999999999999999999999.png", "02.png", "1.png").sortedWith(NaturalOrder))
    }
    @Test fun naturalOrderHasDeterministicTies() { assertEquals(listOf("01.png", "1.png"), listOf("1.png", "01.png").sortedWith(NaturalOrder)) }
    @Test fun archiveRulesIgnoreMacResources() { assertFalse(ArchiveRules.isImage("__MACOSX/001.png")); assertFalse(ArchiveRules.isImage(".hidden/1.jpg")); assertTrue(ArchiveRules.isImage("chapter/01.WEBP")) }
    @Test fun archiveRulesRejectTraversalAndAbsolutePaths() {
        listOf("../1.png", "a/../../1.png", "/1.png", "C:\\1.png", "a\u0000.png").forEach { assertTrue(runCatching { ArchiveRules.safePath(it) }.isFailure) }
        assertEquals("chapter/1.png", ArchiveRules.safePath("chapter\\1.png"))
    }
    @Test fun knownFormatsAreExplicit() { assertEquals(BookFormat.RAR, ArchiveRules.format("book.CBR")); assertNull(ArchiveRules.format("book.epub")) }
    @Test fun ordinaryArchivesUseNaturalOrder() { assertEquals(listOf("1.png", "2.png", "10.png"), MetadataReader.index(pages("10.png", "2.png", "1.png")).pages.map { it.locator }) }
    @Test fun validManifestControlsOrderAndStableIds() {
        val index = MetadataReader.index(pages("1.png", "2.png"), manifest("2.png" to "source/2", "1.png" to "source/1"))
        assertEquals(listOf("2.png", "1.png"), index.pages.map { it.locator }); assertEquals("source/2", index.pages[0].id)
        assertEquals(Direction.RTL, index.metadata.direction); assertEquals("partial", index.metadata.completeness)
    }
    @Test fun partialLabelDoesNotDiscardAnySuppliedPages() { assertEquals(2, MetadataReader.index(pages("1.png", "2.png"), manifest("1.png" to "1", "2.png" to "2")).pages.size) }
    @Test fun incompleteManifestFallsBackWithoutDroppingPages() {
        val index = MetadataReader.index(pages("1.png", "2.png"), manifest("1.png" to "1"))
        assertEquals(2, index.pages.size); assertEquals(1, index.warnings.size)
    }
    @Test fun duplicateManifestIdsFallBack() {
        val index = MetadataReader.index(pages("1.png", "2.png"), manifest("1.png" to "same", "2.png" to "same"))
        assertEquals(listOf("1.png", "2.png"), index.pages.map { it.id }); assertTrue(index.warnings.isNotEmpty())
    }
    @Test fun invalidManifestKeepsComicInfo() {
        val index = MetadataReader.index(pages("1.png"), "{broken", "<ComicInfo><Series>Library</Series><Manga>YesAndRightToLeft</Manga></ComicInfo>")
        assertEquals("Library", index.metadata.series); assertEquals(Direction.RTL, index.metadata.direction)
    }
    @Test fun xmlEntitiesAreRejectedWithoutBlockingPages() {
        val xml = "<!DOCTYPE ComicInfo [<!ENTITY xxe SYSTEM 'file:///private'>]><ComicInfo><Title>&xxe;</Title></ComicInfo>"
        val index = MetadataReader.index(pages("1.png"), comicInfo = xml)
        assertNull(index.metadata.title); assertTrue(index.warnings.isNotEmpty())
    }
    @Test fun comicInfoRecognizesCoversAndSpreads() {
        val index = MetadataReader.index(pages("2.png", "1.png"), comicInfo = "<ComicInfo><Pages><Page Image='1' Type='FrontCover' DoublePage='true'/></Pages></ComicInfo>")
        assertEquals(1, index.metadata.coverIndex); assertTrue(index.pages[1].doublePage)
    }
    @Test(expected = IllegalArgumentException::class) fun duplicateArchivePageNamesAreRejected() { MetadataReader.index(pages("1.png", "1.png")) }
    @Test fun positionFollowsStableIdAfterInsertion() {
        val position = remapPosition(ReadingPosition("p2", 1, .4f), listOf(PageRef("new", "0.png"), PageRef("p1", "1.png"), PageRef("p2", "2.png")))!!
        assertEquals(2, position.index); assertEquals(.4f, position.offset)
    }
    @Test fun removedPageUsesClampedIndexAndResetsOffset() { val p = remapPosition(ReadingPosition("gone", 50, .8f), pages("1.png"))!!; assertEquals(0, p.index); assertEquals(0f, p.offset) }
    @Test fun coverAndWidePagesStayAlone() {
        val entries = (0..5).map { PageRef("$it", "$it.png", width = if (it == 3) 2000 else 1000, height = 1600) }
        assertEquals(listOf(listOf(0), listOf(1, 2), listOf(3), listOf(4, 5)), pageGroups(entries, true))
    }
    @Test fun singlePageModeNeverGroups() { assertEquals(listOf(listOf(0), listOf(1)), pageGroups(pages("1.png", "2.png"), false)) }
    @Test fun overridesHaveBookThenSeriesThenMetadataPrecedence() {
        val preferences = AppPreferences(seriesSettings = mapOf("A" to ReaderOverrides(direction = Direction.LTR, mode = ReadingMode.VERTICAL)))
        val book = book().copy(series = "A", metadata = BookMetadata(direction = Direction.RTL), settings = ReaderOverrides(mode = ReadingMode.PAGED))
        val result = resolveSettings(preferences, book)
        assertEquals(Direction.LTR, result.direction); assertEquals(ReadingMode.PAGED, result.mode)
    }
    @Test fun backupRoundTripPreservesReaderState() {
        val book = book().copy(status = ReadingStatus.READING, position = ReadingPosition("p1", 0, .3f), collections = listOf("Favorites"))
        val backup = LibraryBackup(books = listOf(book), bookmarks = listOf(Bookmark("mark-1", book.id, "p1", 0)))
        val restored = PanelgroveJson.decodeFromString<LibraryBackup>(PanelgroveJson.encodeToString(backup)); restored.validate()
        assertEquals(backup, restored)
    }
    @Test fun unknownBackupVersionIsRejected() { assertTrue(runCatching { LibraryBackup(version = 99).validate() }.isFailure) }
    @Test fun orphanedBookmarksAreRejected() { assertTrue(runCatching { LibraryBackup(bookmarks = listOf(Bookmark("x", "absent", "p1", 0))).validate() }.isFailure) }
    @Test fun duplicateBackupLocationsAreRejected() { assertTrue(runCatching { LibraryBackup(books = listOf(book(), book().copy(id = "other"))).validate() }.isFailure) }
    @Test fun nextUnreadKeepsChapterAndVolumeSequencesSeparate() {
        val current = book().copy(series = "A", number = "12", metadata = BookMetadata(kind = "volume"))
        val volume = current.copy(id = "v13", uri = "content://books/13", number = "13")
        val chapter = current.copy(id = "c45", uri = "content://books/45", number = "45", metadata = BookMetadata(kind = "chapter"))
        assertEquals(volume, nextUnread(current, listOf(chapter, volume)))
        assertNull(nextUnread(current, listOf(chapter)))
    }
    @Test fun nextUnreadDoesNotJoinUnsortedBooks() {
        val current = book().copy(series = "", number = "1")
        assertNull(nextUnread(current, listOf(current.copy(id = "two", number = "2"))))
    }
    @Test fun appearancePreferencesRemainCompatibleWithOlderBackups() {
        val old = PanelgroveJson.decodeFromString<AppPreferences>("""{"theme":"SYSTEM","grid":false}""")
        assertEquals(AppTheme.SYSTEM, old.theme)
        assertFalse(old.trueBlack)
        assertEquals(AppTheme.DARK, AppPreferences().theme)
        assertEquals(0xFF0B0D10L, AppPreferences().reader.background)
    }
    @Test fun trueBlackSurvivesBackupRoundTrip() {
        val backup = LibraryBackup(preferences = AppPreferences(trueBlack = true))
        val restored = PanelgroveJson.decodeFromString<LibraryBackup>(PanelgroveJson.encodeToString(backup))
        assertTrue(restored.preferences.trueBlack)
        restored.validate()
    }
    @Test fun backupRejectsPathIdentifiersAndExternalFileUris() {
        assertTrue(runCatching { LibraryBackup(books = listOf(book().copy(id = "../outside"))).validate() }.isFailure)
        assertTrue(runCatching { LibraryBackup(books = listOf(book().copy(uri = "file:///private"))).validate() }.isFailure)
    }

    @Test fun suppliedTranslationExportsRemainCompatible() {
        val path = System.getProperty("panelgrove.samples").orEmpty()
        org.junit.Assume.assumeTrue("Local manga samples are optional and never bundled.", path.isNotBlank())
        val archives = File(path).walkTopDown().filter { it.extension.equals("cbz", true) && "work" !in it.toPath().map { segment -> segment.toString() } }.toList()
        assertTrue("Expected at least one supplied CBZ", archives.isNotEmpty())
        archives.forEach { file -> ZipFile(file).use { zip ->
            val entries = zip.entries().toList()
            val images = entries.filter { !it.isDirectory && ArchiveRules.isImage(it.name) }.map { PageRef(it.name, it.name) }
            val manifest = zip.getEntry("translation-manifest.json")?.let { zip.getInputStream(it).bufferedReader().use { reader -> reader.readText() } }
            val index = MetadataReader.index(images, manifest)
            assertEquals(file.name, images.size, index.pages.size)
            assertEquals(file.name, images.map { it.locator }.toSet(), index.pages.map { it.locator }.toSet())
            if (manifest != null) assertTrue("${file.name}: ${index.warnings}", index.warnings.isEmpty())
        } }
    }
}
