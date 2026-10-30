package dev.soto.panelgrove.core

import org.junit.Assert.*
import org.junit.Test
import java.time.*

class ReadingStatisticsTest {
    private val subject = ReadingSubject("edition", "book", "Volume 1", "Series", "1", language = "ja")
    private val pages = listOf(PageRef("p1", "1", chapter = "1"), PageRef("p2", "2"), PageRef("p3", "3", chapter = "2"))
    private val origin = Instant.parse("2026-10-26T10:00:00Z").toEpochMilli()
    private fun tracker() = ReadingTracker(subject, pages, { ZoneOffset.UTC })
    private fun tick(t: ReadingTracker, at: Long, shown: List<Int> = listOf(0), active: Boolean = true, input: Boolean = false, idle: Int = 5) =
        t.update(at, origin + at, shown, active, input, idle)
    private fun record(id: String, day: String, millis: Long = 60_000, edition: ReadingSubject = subject, session: String = id,
        chapter: String? = "1") = ReadingRecord(id, session, edition, day, chapter, origin, origin + millis, millis, mapOf("p1" to origin + millis))

    @Test fun dwellCountsOnlyVisiblePagesAndSkippedPagesAreExcluded() {
        val t = tracker()
        tick(t, 0, input = true)
        tick(t, 1_000, shown = listOf(2), input = true)
        tick(t, 3_500, shown = listOf(2))
        val rows = t.checkpoint()
        assertEquals(3_500L, rows.sumOf { it.activeMillis })
        assertEquals(setOf("p3"), rows.flatMap { it.pages.keys }.toSet())
        assertEquals("2", rows.first { it.pages.isNotEmpty() }.chapter)
    }
    @Test fun spreadsCreditBothPagesWithoutDoublingTimeAcrossChapterBoundary() {
        val t = tracker()
        tick(t, 0, listOf(1, 2), input = true)
        tick(t, 4_001, listOf(1, 2))
        val rows = t.checkpoint()
        assertEquals(4_001L, rows.sumOf { it.activeMillis })
        assertEquals(setOf("p2", "p3"), rows.flatMap { it.pages.keys }.toSet())
        assertEquals(2, rows.size)
    }
    @Test fun backgroundMenusAndManualPauseDoNotAccrueTime() {
        val t = tracker()
        tick(t, 0, input = true); tick(t, 5_000, active = false)
        tick(t, 65_000, active = false); tick(t, 70_000, active = true)
        tick(t, 75_000, active = false)
        assertEquals(10_000L, t.checkpoint().sumOf { it.activeMillis })
    }
    @Test fun loadingDoesNotCountAndIdleCapsTimeUntilInteraction() {
        val t = tracker()
        tick(t, 0, emptyList(), active = false)
        tick(t, 20_000, active = true, input = true)
        tick(t, 620_000)
        assertTrue(t.isIdle)
        val before = t.checkpoint()
        assertEquals(300_000L, before.sumOf { it.activeMillis })
        tick(t, 625_000, input = true); tick(t, 630_000)
        val after = t.checkpoint()
        assertFalse(t.isIdle)
        assertEquals(5_000L, after.sumOf { it.activeMillis })
        assertNotEquals(before.single().sessionId, after.single().sessionId)
    }
    @Test fun checkpointsAreCumulativeAndRepeatedBackupImportDoesNotAddTime() {
        val t = tracker()
        tick(t, 0, input = true); tick(t, 15_000)
        val first = ReadingStatistics(origin, t.checkpoint())
        tick(t, 30_000)
        val second = ReadingStatistics(origin, t.checkpoint())
        assertEquals(first.records.single().id, second.records.single().id)
        val merged = mergeStatistics(first, second)
        assertEquals(30_000L, merged.records.single().activeMillis)
        assertEquals(merged, mergeStatistics(second, merged))
        merged.validate()
    }
    @Test fun midnightSplitsTimeAndDwellAcrossLocalDates() {
        val t = tracker()
        val start = Instant.parse("2026-10-26T23:59:58Z").toEpochMilli()
        t.update(0, start, listOf(0), true, true)
        t.update(5_000, start + 5_000)
        val rows = t.checkpoint().sortedBy { it.date }
        assertEquals(listOf("2026-10-26", "2026-10-27"), rows.map { it.date })
        assertEquals(listOf(2_000L, 3_000L), rows.map { it.activeMillis })
        assertEquals(1, rows.map { it.sessionId }.distinct().size)
        ReadingStatistics(start, rows).validate()
    }
    @Test fun timeUsesMonotonicClockRatherThanWallClockChanges() {
        val t = tracker()
        t.update(0, origin, listOf(0), true, true)
        t.update(3_000, origin + 3_600_000)
        assertEquals(3_000L, t.checkpoint().single().activeMillis)
        t.update(6_000, origin - 3_600_000)
        t.update(9_000, origin - 3_597_000)
        val saved = ReadingStatistics(origin, t.checkpoint())
        assertEquals(6_000L, saved.records.sumOf { it.activeMillis })
        saved.validate()
    }
    @Test fun summaryDeduplicatesPagesInPeriodButKeepsEditionsSeparate() {
        val other = subject.copy(editionId = "english", bookId = "english", language = "en")
        val stats = ReadingStatistics(origin, listOf(record("a", "2026-10-26"), record("b", "2026-10-27"), record("c", "2026-10-27", edition = other)))
        val week = StatisticsRange.of(LocalDate.parse("2026-10-28"), StatisticsPeriod.WEEK)
        assertEquals(2, stats.summary(week).pages)
        assertEquals(180_000L, stats.summary(week).millis)
        assertEquals(1, stats.summary(week, "ja").pages)
        assertEquals(120_000L, stats.summary(week, "ja").millis)
    }
    @Test fun sessionSummariesUseOnlyTheSelectedPeriodPortion() {
        val stats = ReadingStatistics(origin, listOf(record("a", "2026-10-25", session = "same"),
            record("b", "2026-10-26", session = "same"), record("c", "2026-10-26", 120_000, session = "other")))
        val summary = stats.summary(StatisticsRange.of(LocalDate.parse("2026-10-26"), StatisticsPeriod.WEEK))
        assertEquals(2, summary.sessions); assertEquals(90_000L, summary.averageSession); assertEquals(120_000L, summary.longestSession)
    }
    @Test fun completionsSeparateUniqueWorksEditionsAndRereadsAndRespectDates() {
        val english = subject.copy(editionId = "en", language = "en")
        val events = listOf(ReadingCompletion("first", subject, "2026-10-26", origin),
            ReadingCompletion("second", english, "2026-10-27", origin + 1),
            ReadingCompletion("reread", subject, "2026-10-28", origin + 2),
            ReadingCompletion("chapter", subject, "2026-10-28", origin + 3, "1"))
        val summary = ReadingStatistics(origin, completions = events).summary(StatisticsRange.of(LocalDate.parse("2026-10-26"), StatisticsPeriod.WEEK))
        assertEquals(1, summary.volumes); assertEquals(1, summary.chapters); assertEquals(2, summary.editions); assertEquals(1, summary.rereads)
    }
    @Test fun streakSurvivesUntilTodayEndsAndDoesNotBridgeGaps() {
        val stats = ReadingStatistics(origin, listOf(record("a", "2026-10-24"), record("b", "2026-10-25"), record("c", "2026-10-27")))
        assertEquals(ReadingStreaks(2, 2), stats.copy(records = stats.records.take(2)).streaks(LocalDate.parse("2026-10-26")))
        assertEquals(ReadingStreaks(1, 2), stats.streaks(LocalDate.parse("2026-10-28")))
        assertEquals(0, stats.streaks(LocalDate.parse("2026-10-29")).current)
    }
    @Test fun oldBackupHasNoInventedStatisticsAndNewDataSurvivesRoundTripWithoutBooks() {
        val old = PanelgroveJson.decodeFromString<LibraryBackup>("""{"version":1}""")
        old.validate(); assertEquals(ReadingStatistics(), old.statistics)
        val backup = LibraryBackup(statistics = ReadingStatistics(origin, listOf(record("row", "2026-10-26"))))
        backup.validate()
        assertEquals(backup, PanelgroveJson.decodeFromString<LibraryBackup>(PanelgroveJson.encodeToString(backup)))
    }
    @Test fun restoreRemapsLiveBookAndEditionIdentityAndKeepsRemovedBookRecords() {
        val book = Book("book", "content://docs/book", "book.cbz", BookFormat.ZIP, statisticsId = "edition")
        val liveBook = book.copy(id = "live", statisticsId = "live-edition")
        val incoming = LibraryBackup(books = listOf(book), statistics = ReadingStatistics(origin, listOf(record("row", "2026-10-26"))))
        val merged = mergeBackup(incoming, LibraryBackup(books = listOf(liveBook)))
        assertEquals("live", merged.statistics.records.single().subject.bookId)
        assertEquals("live-edition", merged.statistics.records.single().subject.editionId)
        assertEquals(merged.statistics, mergeBackup(incoming, merged).statistics)
        assertEquals(1, mergeBackup(incoming.copy(books = emptyList()), LibraryBackup()).statistics.records.size)
    }
    @Test fun invalidImportedStatisticsAndTimeoutsAreRejected() {
        val valid = record("row", "2026-10-26")
        for (bad in listOf(valid.copy(activeMillis = -1), valid.copy(date = "invalid"), valid.copy(endedAt = origin - 1), valid.copy(pages = mapOf("p1" to 1)))) {
            assertTrue(runCatching { LibraryBackup(statistics = ReadingStatistics(origin, listOf(bad))).validate() }.isFailure)
        }
        assertTrue(runCatching { LibraryBackup(preferences = AppPreferences(statisticsIdleMinutes = 0)).validate() }.isFailure)
    }
    @Test fun weekAndMonthRangesIncludeLeapDaysAndStartWeeksOnMonday() {
        assertEquals(LocalDate.parse("2026-10-26"), StatisticsRange.of(LocalDate.parse("2026-11-01"), StatisticsPeriod.WEEK).start)
        assertEquals(29, StatisticsRange.of(LocalDate.parse("2024-02-10"), StatisticsPeriod.MONTH).dates.size)
    }
    @Test fun partialDayComparisonClipsActualReadingIntervalsWithoutCountingBreaks() {
        val day = LocalDate.parse("2026-10-26")
        fun time(hour: Int) = day.atTime(hour, 0).toInstant(ZoneOffset.UTC).toEpochMilli()
        val row = ReadingRecord("clipped", "session", subject, day.toString(), "1", time(9), time(18), 7_200_000,
            mapOf("early" to time(9) + 30_000, "late" to time(17) + 30_000),
            listOf(ReadingSpan(time(9), time(10), 3_600_000), ReadingSpan(time(17), time(18), 3_600_000)))
        assertNull(row.beforeLocalTime(LocalTime.of(8, 0)))
        assertEquals(3_600_000L, row.beforeLocalTime(LocalTime.of(16, 0))!!.activeMillis)
        val partial = row.beforeLocalTime(LocalTime.of(17, 30))!!
        assertEquals(5_400_000L, partial.activeMillis)
        assertEquals(setOf("early", "late"), partial.pages.keys)
        ReadingStatistics(records = listOf(partial)).validate()
    }
    @Test fun activeSpansCoalesceButPreservePausesAndUseRecordedTimeZone() {
        val t = ReadingTracker(subject, pages, { ZoneId.of("Europe/Bucharest") })
        tick(t, 0, input = true); tick(t, 1_000); tick(t, 2_000, active = false)
        tick(t, 12_000); tick(t, 15_000)
        val row = t.checkpoint().single()
        assertEquals(2, row.spans.size)
        assertEquals(5_000L, row.activeMillis)
        assertEquals("Europe/Bucharest", row.zoneId)
        ReadingStatistics(records = listOf(row)).validate()
    }
    @Test fun smallWallClockCorrectionsDoNotOverlapIntervalsOrInvalidateBackups() {
        val t = tracker()
        t.update(0, origin, listOf(0), true, true)
        t.update(1_000, origin + 999)
        t.update(2_000, origin + 1_500)
        t.update(3_000, origin + 2_502)
        val row = t.checkpoint().single()
        assertEquals(3_000L, row.activeMillis)
        assertEquals(1, row.spans.size)
        ReadingStatistics(records = listOf(row)).validate()
    }
}
