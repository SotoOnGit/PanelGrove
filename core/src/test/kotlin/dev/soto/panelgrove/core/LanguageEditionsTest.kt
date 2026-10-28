package dev.soto.panelgrove.core

import org.junit.Assert.*
import org.junit.Test

class LanguageEditionsTest {
    private fun book(id: String = "en1", language: String? = "en", number: String = "1") =
        Book(id, "content://books/$id", "$id.cbz", BookFormat.ZIP, series = "Shared Series", number = number, language = language)

    @Test fun metadataIsUsedUntilExplicitlyOverridden() {
        val source = book(language = null).copy(metadata = BookMetadata(language = "jpn"))
        assertEquals("ja", source.editionLanguage)
        assertEquals("en", source.copy(language = "EN").editionLanguage)
        assertEquals("", source.copy(language = "").editionLanguage)
        assertEquals("zh-hant-tw", normalizeLanguage(" ZH_Hant_TW "))
        assertEquals("fr", normalizeLanguage("fre"))
        assertEquals("en", normalizeLanguage("eng"))
    }

    @Test fun editionSelectionGroupsDifferentFormatsWithoutCombiningBooks() {
        val english = book().copy(position = ReadingPosition("p2", 1), favorite = true)
        val japanese = book("ja1", "ja").copy(format = BookFormat.RAR, pageCount = 10, metadata = BookMetadata(kind = "volume"))
        val chapter = book("chapter", "ja").copy(metadata = BookMetadata(kind = "chapter"))
        val current = english.copy(metadata = BookMetadata(kind = "volume"))
        val editions = languageEditions(current, listOf(current, japanese, chapter, book("en2", number = "2"), book("other").copy(series = "Other")))
        assertEquals(listOf(current, japanese), editions)
        assertNull(japanese.position)
        assertEquals(1, english.position!!.index)
    }

    @Test fun unnumberedOrUnsortedBooksAreNotAssumedToBeEditions() {
        val unnumbered = book(number = "")
        assertEquals(listOf(unnumbered), languageEditions(unnumbered, listOf(unnumbered, book("ja", "ja", ""))))
        val unsorted = book().copy(series = "")
        assertEquals(listOf(unsorted), languageEditions(unsorted, listOf(unsorted, book("other").copy(series = ""))))
    }

    @Test fun nextUnreadStaysInTheSameLanguageIncludingUnspecified() {
        val current = book()
        val english = book("en3", "eng", "3")
        val japanese = book("ja2", "ja", "2")
        val unknown = book("unknown", null, "2")
        assertEquals(english, nextUnread(current, listOf(japanese, unknown, english)))
        assertNull(nextUnread(current, listOf(japanese, unknown)))
        assertEquals(unknown, nextUnread(current.copy(language = ""), listOf(english, unknown)))
        assertNull(nextUnread(current.copy(language = "en-gb"), listOf(english)))
    }

    @Test fun backupAndMergePreserveAssignmentsAndIndependentReadingData() {
        val english = book().copy(position = ReadingPosition("en-page", 5), status = ReadingStatus.READING)
        val japanese = book("ja1", "ja").copy(format = BookFormat.PDF, position = ReadingPosition("pdf:2", 2))
        val backup = LibraryBackup(books = listOf(english, japanese),
            bookmarks = listOf(Bookmark("enmark", english.id, "en-page", 5), Bookmark("jamark", japanese.id, "pdf:2", 2)),
            history = listOf(HistoryEntry("envisit", english.id), HistoryEntry("javisit", japanese.id)))
        val decoded = PanelgroveJson.decodeFromString<LibraryBackup>(PanelgroveJson.encodeToString(backup))
        decoded.validate()
        val merged = mergeBackup(decoded, LibraryBackup())
        assertEquals(2, merged.books.size)
        assertEquals(listOf("en", "ja"), merged.books.map { it.editionLanguage })
        assertEquals(listOf(5, 2), merged.books.map { it.position!!.index })
        assertEquals(setOf("en1", "ja1"), merged.bookmarks.map { it.bookId }.toSet())
        assertEquals(2, merged.history.size)
        val old = PanelgroveJson.decodeFromString<Book>("""{"id":"old","uri":"content://old","displayName":"old.cbz","format":"ZIP","metadata":{"language":"ja"}}""")
        assertNull(old.language)
        assertEquals("ja", old.editionLanguage)
    }

    @Test fun manifestWithoutLanguageRetainsComicInfoLanguage() {
        val manifest = """{"pages":[{"path":"1.png"}]}"""
        val comic = "<ComicInfo><LanguageISO>ja</LanguageISO></ComicInfo>"
        assertEquals("ja", MetadataReader.index(listOf(PageRef("1", "1.png")), manifest, comic).metadata.language)
        assertEquals("en", MetadataReader.index(listOf(PageRef("1", "1.png")), manifest.replace("{\"pages\"", "{\"language\":\"en\",\"pages\""), comic).metadata.language)
    }

    @Test fun shelfGroupsOneNumberAndUsesTheMostRecentlyOpenedCoverWithAStableKey() {
        val english = book().copy(lastOpenedAt = 100)
        val japanese = book("ja1", "ja").copy(lastOpenedAt = 200)
        val next = book("en2", number = "2")
        val groups = groupLanguageEditions(listOf(english, japanese, next))
        assertEquals(2, groups.size)
        assertEquals(japanese, groups.first().representative)
        val changed = groupLanguageEditions(listOf(english.copy(lastOpenedAt = 300), japanese, next)).first()
        assertEquals(english.id, changed.representative.id)
        assertEquals(groups.first().key, changed.key)
        assertEquals(setOf("en1", "ja1"), changed.editions.map { it.id }.toSet())
    }

    @Test fun untypedEditionsJoinOneKnownSequenceButCannotBridgeChaptersAndVolumes() {
        val volume = book().copy(metadata = BookMetadata(kind = "volume"))
        val untyped = book("pdf", "ja").copy(format = BookFormat.PDF)
        assertEquals(1, groupLanguageEditions(listOf(volume, untyped)).size)
        assertEquals(setOf(volume, untyped), languageEditions(untyped, listOf(volume, untyped)).toSet())
        val chapter = book("chapter", "ja").copy(metadata = BookMetadata(kind = "chapter"))
        val groups = groupLanguageEditions(listOf(volume, untyped, chapter))
        assertEquals(3, groups.size)
        assertEquals(listOf(untyped), languageEditions(untyped, listOf(volume, untyped, chapter)))
        assertEquals(listOf(volume), languageEditions(volume, listOf(volume, untyped, chapter)))
        assertEquals(3, groups.flatMap { it.editions }.map { it.id }.distinct().size)
    }

    @Test fun unopenedRepresentativeAndGroupIdentityAreIndependentOfInputOrder() {
        val first = book().copy(addedAt = 10)
        val later = book("ja1", "ja").copy(addedAt = 20)
        val forward = groupLanguageEditions(listOf(first, later)).single()
        val reversed = groupLanguageEditions(listOf(later, first)).single()
        assertEquals(first, forward.representative)
        assertEquals(forward.representative, reversed.representative)
        assertEquals(forward.key, reversed.key)
        assertEquals(2, groupLanguageEditions(listOf(first.copy(number = ""), later.copy(number = ""))).size)
    }
}
