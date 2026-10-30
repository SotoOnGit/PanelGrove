package dev.soto.panelgrove.core

import kotlinx.serialization.Serializable
import java.time.*
import java.time.temporal.TemporalAdjusters
import java.util.UUID

/** Snapshots keep a removed book's reading log useful. editionId survives restore ID remapping. */
@Serializable data class ReadingSubject(
    val editionId: String, val bookId: String, val title: String, val series: String = "",
    val number: String = "", val kind: String = "volume", val language: String = "",
) {
    val workKey: String get() = if (series.isBlank() || number.isBlank()) editionId
        else listOf(series.trim().lowercase(java.util.Locale.ROOT), kind, number.trim()).joinToString("\u001f")
    companion object {
        fun from(book: Book) = ReadingSubject(book.statisticsId, book.id, book.title, book.series, book.number,
            if (book.metadata.kind.equals("chapter", true)) "chapter" else "volume", book.editionLanguage)
    }
}

/** One cumulative checkpoint per session, local day and chapter; never trimmed with opening history. */
@Serializable data class ReadingSpan(val startedAt: Long, val endedAt: Long, val activeMillis: Long)
@Serializable data class ReadingRecord(
    val id: String, val sessionId: String, val subject: ReadingSubject, val date: String,
    val chapter: String? = null, val startedAt: Long, val endedAt: Long, val activeMillis: Long,
    val pages: Map<String, Long> = emptyMap(),
    val spans: List<ReadingSpan> = emptyList(), val zoneId: String = "UTC",
)

/** Clip only the final comparison day, retaining actual pauses rather than spreading time over gaps. */
fun ReadingRecord.beforeLocalTime(time: LocalTime): ReadingRecord? {
    val cutoff = LocalDate.parse(date).atTime(time).atZone(ZoneId.of(zoneId)).toInstant().toEpochMilli()
    val intervals = spans.ifEmpty { listOf(ReadingSpan(startedAt, endedAt, activeMillis)) }
    val clipped = intervals.mapNotNull { span ->
        val end = minOf(span.endedAt, cutoff)
        if (end <= span.startedAt) null else {
            val millis = if (end == span.endedAt) span.activeMillis else span.activeMillis * (end - span.startedAt) / (span.endedAt - span.startedAt)
            if (millis == 0L) null else span.copy(endedAt = end, activeMillis = millis)
        }
    }
    return if (clipped.isEmpty()) null else copy(endedAt = minOf(endedAt, cutoff), activeMillis = clipped.sumOf { it.activeMillis },
        pages = pages.filterValues { it <= cutoff }, spans = clipped)
}
@Serializable data class ReadingCompletion(
    val id: String, val subject: ReadingSubject, val date: String, val completedAt: Long,
    val chapter: String? = null,
)
@Serializable data class ReadingStatistics(
    val trackingSince: Long? = null, val records: List<ReadingRecord> = emptyList(),
    val completions: List<ReadingCompletion> = emptyList(),
) {
    fun validate() {
        require(trackingSince == null || trackingSince > 0) { "Invalid statistics start date." }
        require(records.size <= 1_000_000 && completions.size <= 500_000) { "Statistics exceed backup limits." }
        val safe = Regex("[A-Za-z0-9_-]{1,100}")
        fun subject(s: ReadingSubject) = safe.matches(s.editionId) && safe.matches(s.bookId) &&
            s.kind in listOf("volume", "chapter") && listOf(s.title, s.series, s.number, s.language).all { it.length <= 4096 }
        fun date(s: String) = runCatching { LocalDate.parse(s) }.isSuccess
        require(records.map { it.id }.distinct().size == records.size && completions.map { it.id }.distinct().size == completions.size) { "Duplicate statistics IDs." }
        require(records.all { safe.matches(it.id) && safe.matches(it.sessionId) && subject(it.subject) && date(it.date) &&
            it.startedAt > 0 && it.endedAt >= it.startedAt && it.activeMillis in 1..93_600_000 &&
            it.activeMillis <= it.endedAt - it.startedAt && it.pages.size <= 20_000 &&
            it.pages.all { (page, time) -> page.isNotBlank() && page.length <= 4096 && time in it.startedAt..it.endedAt } &&
            runCatching { ZoneId.of(it.zoneId) }.isSuccess && it.spans.size <= 100_000 &&
            (it.spans.isEmpty() || (it.spans.sumOf { span -> span.activeMillis } == it.activeMillis &&
                it.spans.all { span -> span.startedAt >= it.startedAt && span.endedAt <= it.endedAt &&
                    span.activeMillis > 0 && span.activeMillis <= span.endedAt - span.startedAt } &&
                it.spans.zipWithNext().all { (a, b) -> a.endedAt <= b.startedAt })) &&
            (it.chapter?.length ?: 0) <= 4096 }) { "Invalid reading record." }
        require(completions.all { safe.matches(it.id) && subject(it.subject) && date(it.date) && it.completedAt > 0 &&
            (it.chapter?.length ?: 0) <= 4096 }) { "Invalid reading completion." }
    }
}

fun mergeStatistics(incoming: ReadingStatistics, live: ReadingStatistics, bookIds: Map<String, String> = emptyMap(), editionIds: Map<String, String> = emptyMap()): ReadingStatistics {
    fun remap(s: ReadingSubject) = s.copy(bookId = bookIds[s.bookId] ?: s.bookId, editionId = editionIds[s.editionId] ?: s.editionId)
    val records = live.records.associateBy { it.id }.toMutableMap()
    incoming.records.forEach { incomingRecord ->
        val row = incomingRecord.copy(subject = remap(incomingRecord.subject))
        val old = records[row.id]
        if (old == null) records[row.id] = row
        else {
            require(old.sessionId == row.sessionId && old.subject.editionId == row.subject.editionId && old.date == row.date && old.chapter == row.chapter && old.zoneId == row.zoneId) { "Conflicting reading record identity." }
            val newest = if (row.activeMillis > old.activeMillis || (row.activeMillis == old.activeMillis && old.spans.isEmpty() && row.spans.isNotEmpty())) row else old
            records[row.id] = newest.copy(pages = (old.pages.keys + row.pages.keys).associateWith { maxOf(old.pages[it] ?: 0, row.pages[it] ?: 0) },
                startedAt = minOf(old.startedAt, row.startedAt), endedAt = maxOf(old.endedAt, row.endedAt))
        }
    }
    val completions = live.completions.associateBy { it.id }.toMutableMap()
    incoming.completions.forEach { completion ->
        val row = completion.copy(subject = remap(completion.subject))
        val old = completions[row.id]
        require(old == null || (old.subject.editionId == row.subject.editionId && old.chapter == row.chapter && old.completedAt == row.completedAt)) { "Conflicting completion identity." }
        if (old == null) completions[row.id] = row
    }
    return ReadingStatistics(listOfNotNull(incoming.trackingSince, live.trackingSince).minOrNull(), records.values.toList(), completions.values.toList())
}

/** Pure monotonic-clock tracker. Call before changing visibility; checkpoints are safe to upsert. */
class ReadingTracker(
    private val subject: ReadingSubject, private val pages: List<PageRef>,
    private val zone: () -> ZoneId = { ZoneId.systemDefault() },
    private val newId: () -> String = { UUID.randomUUID().toString() },
) {
    private var sessionId = newId()
    private var lastElapsed: Long? = null
    private var lastWall = 0L
    private var lastZone: ZoneId? = null
    private var lastInteraction = 0L
    private var lastActive = 0L
    private var active = false
    private var visible = emptyList<Int>()
    private var timeout = 300_000L
    private val buckets = linkedMapOf<Pair<String, String?>, ReadingRecord>()
    private val dwell = mutableMapOf<Pair<String, Int>, Long>()
    private val qualified = mutableSetOf<Pair<String, Int>>()
    private val dirty = mutableSetOf<Pair<String, String?>>()
    private val pending = mutableListOf<ReadingRecord>()
    val chapters: Map<Int, String?> = buildMap {
        var current: String? = null
        pages.forEachIndexed { i, page -> if (page.chapter != null) current = page.chapter; put(i, current) }
    }
    var isIdle: Boolean = false; private set

    fun update(elapsed: Long, wall: Long, shown: List<Int> = visible, reading: Boolean = active,
        interaction: Boolean = false, idleMinutes: Int = (timeout / 60_000).toInt()) {
        val previous = lastElapsed
        val projectedWall = if (previous == null) wall else lastWall + (elapsed - previous).coerceAtLeast(0)
        val clockChanged = kotlin.math.abs(wall - projectedWall) > 2_000
        if (previous != null && active && visible.isNotEmpty()) {
            val until = minOf(elapsed, lastInteraction + timeout)
            val duration = (until - previous).coerceAtLeast(0)
            if (duration > 0) {
                credit(lastWall, duration, lastZone ?: zone())
                lastActive = previous + duration
            }
        }
        if (previous != null && (lastZone != zone() || clockChanged)) {
            pending += dirty.mapNotNull(buckets::get); dirty.clear(); buckets.clear(); dwell.clear(); qualified.clear()
        }
        if (interaction || (reading && !active)) {
            if (previous != null && ((active && elapsed >= lastInteraction + timeout) || elapsed - lastActive >= timeout)) {
                pending += dirty.mapNotNull(buckets::get); dirty.clear()
                sessionId = newId(); buckets.clear(); dwell.clear(); qualified.clear()
            }
            lastInteraction = elapsed
        }
        if (shown != visible) {
            val removed = visible - shown.toSet()
            dwell.keys.removeAll { it.second in removed }; qualified.removeAll { it.second in removed }
        }
        timeout = idleMinutes.coerceIn(1, 30) * 60_000L
        lastElapsed = elapsed; lastWall = if (clockChanged) wall else projectedWall; lastZone = zone(); visible = shown.filter { it in pages.indices }.distinct(); active = reading
        isIdle = active && elapsed >= lastInteraction + timeout
    }

    private fun credit(wall: Long, duration: Long, timeZone: ZoneId) {
        var cursor = wall; var remaining = duration
        while (remaining > 0) {
            val day = Instant.ofEpochMilli(cursor).atZone(timeZone).toLocalDate()
            val midnight = day.plusDays(1).atStartOfDay(timeZone).toInstant().toEpochMilli()
            val chunk = minOf(remaining, (midnight - cursor).coerceAtLeast(1))
            val byChapter = visible.groupBy { chapters[it] }
            var allocated = 0L
            byChapter.entries.forEachIndexed { ordinal, (chapter, indices) ->
                val share = if (ordinal == byChapter.size - 1) chunk - allocated else chunk * indices.size / visible.size
                allocated += share
                if (share == 0L) return@forEachIndexed
                val key = day.toString() to chapter
                val old = buckets[key] ?: ReadingRecord(newId(), sessionId, subject, key.first, chapter, cursor, cursor, 0, zoneId = timeZone.id)
                val seen = old.pages.toMutableMap()
                indices.forEach { index ->
                    val pageKey = key.first to index
                    val spent = (dwell[pageKey] ?: 0) + chunk
                    dwell[pageKey] = spent
                    if (spent >= 2_000 && qualified.add(pageKey)) seen[pages[index].id] = cursor + chunk
                }
                val span = ReadingSpan(cursor, cursor + chunk, share)
                val last = old.spans.lastOrNull()
                val intervals = if (last != null && last.endedAt == cursor && last.activeMillis * chunk == share * (last.endedAt - last.startedAt))
                    old.spans.dropLast(1) + last.copy(endedAt = span.endedAt, activeMillis = last.activeMillis + share)
                    else old.spans + span
                buckets[key] = old.copy(endedAt = cursor + chunk, activeMillis = old.activeMillis + share, pages = seen, spans = intervals)
                dirty += key
            }
            cursor += chunk; remaining -= chunk
        }
    }

    fun checkpoint(): List<ReadingRecord> = (pending + dirty.mapNotNull(buckets::get)).also { dirty.clear(); pending.clear() }
}

enum class StatisticsPeriod { WEEK, MONTH }
data class StatisticsRange(val start: LocalDate, val endExclusive: LocalDate) {
    val dates: List<LocalDate> get() = generateSequence(start) { it.plusDays(1) }.takeWhile { it < endExclusive }.toList()
    operator fun contains(date: String): Boolean = date >= start.toString() && date < endExclusive.toString()
    companion object {
        fun of(date: LocalDate, period: StatisticsPeriod): StatisticsRange {
            val start = if (period == StatisticsPeriod.WEEK) date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)) else date.withDayOfMonth(1)
            return StatisticsRange(start, if (period == StatisticsPeriod.WEEK) start.plusWeeks(1) else start.plusMonths(1))
        }
    }
}
data class StatisticsSummary(
    val millis: Long, val pages: Int, val readingDays: Int, val sessions: Int, val averageSession: Long,
    val longestSession: Long, val volumes: Int, val chapters: Int, val editions: Int, val rereads: Int,
)
fun ReadingStatistics.summary(range: StatisticsRange, language: String? = null): StatisticsSummary {
    val selected = records.filter { it.date in range && (language == null || it.subject.language == language) }
    val sessions = selected.groupBy { it.sessionId }.values.map { rows -> rows.sumOf { it.activeMillis } }
    val finishes = completions.filter { it.date in range && (language == null || it.subject.language == language) }
    val history = completions.filter { language == null || it.subject.language == language }
    val firstEditions = history.groupBy { it.subject.editionId to it.chapter }.mapValues { (_, rows) -> rows.minWith(compareBy({ it.completedAt }, { it.id })).id }
    val firstWorks = history.groupBy { it.subject.workKey to it.chapter }.mapValues { (_, rows) -> rows.minWith(compareBy({ it.completedAt }, { it.id })).id }
    val millis = selected.sumOf { it.activeMillis }
    return StatisticsSummary(millis, selected.flatMap { r -> r.pages.keys.map { r.subject.editionId to it } }.distinct().size,
        selected.map { it.date }.distinct().size, sessions.size, if (sessions.isEmpty()) 0 else millis / sessions.size,
        sessions.maxOrNull() ?: 0,
        finishes.count { it.chapter == null && it.subject.kind == "volume" && firstWorks[it.subject.workKey to null] == it.id },
        finishes.count { (it.chapter != null || it.subject.kind == "chapter") && firstWorks[it.subject.workKey to it.chapter] == it.id },
        finishes.count { it.chapter == null && firstEditions[it.subject.editionId to null] == it.id },
        finishes.count { firstEditions[it.subject.editionId to it.chapter] != it.id })
}
data class ReadingStreaks(val current: Int, val longest: Int)
fun ReadingStatistics.streaks(today: LocalDate, language: String? = null): ReadingStreaks {
    val days = records.filter { language == null || it.subject.language == language }.map { LocalDate.parse(it.date) }.filter { it <= today }.toSortedSet()
    var longest = 0; var run = 0; var previous: LocalDate? = null
    days.forEach { day -> run = if (previous?.plusDays(1) == day) run + 1 else 1; longest = maxOf(longest, run); previous = day }
    var current = 0; var cursor = if (today in days) today else today.minusDays(1)
    while (cursor in days) { current++; cursor = cursor.minusDays(1) }
    return ReadingStreaks(current, longest)
}
