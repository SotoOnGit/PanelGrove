package dev.soto.panelgrove.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.soto.panelgrove.R
import dev.soto.panelgrove.core.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.time.*
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.temporal.TemporalAdjusters
import java.util.Locale

fun readingDuration(millis: Long, strings: StringDictionary): String {
    val seconds = millis / 1_000
    return when {
        seconds < 60 -> strings[R.string.statistics_seconds, seconds]
        seconds < 3_600 -> strings[R.string.statistics_minutes, seconds / 60]
        else -> strings[R.string.statistics_hours_minutes, seconds / 3_600, seconds / 60 % 60]
    }
}
private fun rangeLabel(range: StatisticsRange): String {
    val format = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
    return "${range.start.format(format)} – ${range.endExclusive.minusDays(1).format(format)}"
}
private data class StatisticsDetail(val title: String, val records: List<ReadingRecord>, val completions: List<ReadingCompletion>)

@Composable fun StatisticsScreen(model: LibraryViewModel) {
    val strings = rememberStrings()
    val statistics by model.statistics.collectAsStateWithLifecycle()
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    val today = now.toLocalDate()
    LaunchedEffect(Unit) { while (true) { delay(30_000); now = LocalDateTime.now() } }
    var period by rememberSaveable { mutableStateOf(StatisticsPeriod.WEEK) }
    var anchor by rememberSaveable { mutableStateOf(today.toString()) }
    var comparisonOffset by rememberSaveable { mutableIntStateOf(-1) }
    var language by rememberSaveable { mutableStateOf<String?>(null) }
    var languageMenu by remember { mutableStateOf(false) }
    var detail by remember { mutableStateOf<StatisticsDetail?>(null) }
    var calendarYear by rememberSaveable { mutableIntStateOf(today.year) }
    val range = remember(anchor, period) { StatisticsRange.of(LocalDate.parse(anchor), period) }
    fun shifted(amount: Int): LocalDate = if (period == StatisticsPeriod.WEEK) range.start.plusWeeks(amount.toLong()) else range.start.plusMonths(amount.toLong())
    val comparison = StatisticsRange.of(shifted(comparisonOffset), period)
    val elapsedDays = if (today >= range.start && today < range.endExclusive) java.time.temporal.ChronoUnit.DAYS.between(range.start, today) + 1 else range.dates.size.toLong()
    val comparisonThrough = minOf(comparison.endExclusive, comparison.start.plusDays(elapsedDays))
    val comparedRange = StatisticsRange(comparison.start, comparisonThrough)
    val comparisonTime = if (today >= range.start && today < range.endExclusive && elapsedDays <= comparison.dates.size) now.toLocalTime() else null
    val comparisonRecords = remember(statistics, comparedRange, comparisonTime, language) {
        statistics.records.filter { it.date in comparedRange && (language == null || it.subject.language == language) }.mapNotNull { row ->
            if (comparisonTime != null && row.date == comparisonThrough.minusDays(1).toString()) row.beforeLocalTime(comparisonTime) else row
        }
    }
    val selectedRecords = remember(statistics, range, language) { statistics.records.filter { it.date in range && (language == null || it.subject.language == language) } }
    val selectedCompletions = remember(statistics, range, language) { statistics.completions.filter { it.date in range && (language == null || it.subject.language == language) } }
    val summary by produceState<StatisticsSummary?>(null, statistics, range, language) {
        value = withContext(Dispatchers.Default) { statistics.summary(range, language) }
    }
    val compared by produceState<StatisticsSummary?>(null, comparisonRecords, comparedRange) {
        value = withContext(Dispatchers.Default) { ReadingStatistics(records = comparisonRecords).summary(comparedRange) }
    }
    val streaks = remember(statistics, language, today) { statistics.streaks(today, language) }
    val daily = remember(selectedRecords) { selectedRecords.groupBy { it.date }.mapValues { (_, rows) -> rows.sumOf { it.activeMillis } } }
    val previousDaily = remember(comparisonRecords) {
        comparisonRecords.groupBy { it.date }.mapValues { (_, rows) -> rows.sumOf { it.activeMillis } }
    }
    fun showDay(date: String) {
        detail = StatisticsDetail(LocalDate.parse(date).format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL)),
            statistics.records.filter { it.date == date && (language == null || it.subject.language == language) },
            statistics.completions.filter { it.date == date && (language == null || it.subject.language == language) })
    }
    LazyColumn(Modifier.fillMaxSize().testTag("statistics-list"), contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ChoiceSegments(listOf(StatisticsPeriod.WEEK to strings[R.string.statistics_week], StatisticsPeriod.MONTH to strings[R.string.statistics_month]), period) {
                    period = it; comparisonOffset = -1
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton({ anchor = shifted(-1).toString(); comparisonOffset = -1 }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, strings[R.string.statistics_previous_period]) }
                    Text(rangeLabel(range), Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                    IconButton({ anchor = shifted(1).toString(); comparisonOffset = -1 }, enabled = range.endExclusive <= today) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, strings[R.string.statistics_next_period])
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.weight(1f)) {
                        OutlinedButton({ languageMenu = true }) { Text(language?.let { languageLabel(it, strings) } ?: strings[R.string.statistics_all_languages]) }
                        DropdownMenu(languageMenu, { languageMenu = false }) {
                            DropdownMenuItem({ Text(strings[R.string.statistics_all_languages]) }, { language = null; languageMenu = false })
                            (statistics.records.map { it.subject.language } + statistics.completions.map { it.subject.language }).distinct().sorted().forEach { code ->
                                DropdownMenuItem({ Text(languageLabel(code, strings)) }, { language = code; languageMenu = false })
                            }
                        }
                    }
                    TextButton({ anchor = today.toString(); comparisonOffset = -1 }) { Text(strings[R.string.today]) }
                }
                Text(readingDuration(summary?.millis ?: 0, strings), style = MaterialTheme.typography.headlineLarge, modifier = Modifier.testTag("statistics-total-time"))
                val difference = (summary?.millis ?: 0) - (compared?.millis ?: 0)
                val previous = compared?.millis ?: 0
                val comparisonText = if (previous == 0L) strings[R.string.statistics_no_comparison] else
                    strings[R.string.statistics_comparison, if (difference >= 0) "+" else "−", readingDuration(kotlin.math.abs(difference), strings),
                        String.format(Locale.getDefault(), "%+.0f%%", difference.toDouble() / previous * 100)]
                Text(comparisonText, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton({ comparisonOffset-- }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, strings[R.string.statistics_older_comparison]) }
                    Text(strings[R.string.statistics_compare_with, rangeLabel(comparedRange)], Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                    IconButton({ comparisonOffset++ }, enabled = comparisonOffset < -1) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, strings[R.string.statistics_newer_comparison])
                    }
                }
                if (comparisonTime != null) Text(strings[R.string.statistics_comparison_through, comparisonTime.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)), elapsedDays.toInt()], style = MaterialTheme.typography.bodySmall)
                StatisticsBars(range, daily, comparedRange, previousDaily, ::showDay)
                Text(strings[R.string.statistics_chart_legend], style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (selectedRecords.isEmpty() && selectedCompletions.isEmpty()) item {
            Text(strings[R.string.statistics_empty_period], color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item {
            val s = summary
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatisticsTile(strings[R.string.statistics_pages], "${s?.pages ?: 0}", Modifier.weight(1f))
                    StatisticsTile(strings[R.string.statistics_reading_days], "${s?.readingDays ?: 0}", Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatisticsTile(strings[R.string.statistics_volumes], "${s?.volumes ?: 0}", Modifier.weight(1f))
                    StatisticsTile(strings[R.string.statistics_chapters], "${s?.chapters ?: 0}", Modifier.weight(1f))
                }
                Text(strings[R.string.statistics_editions_rereads,
                    strings.quantity(R.plurals.statistics_edition_finishes, s?.editions ?: 0, s?.editions ?: 0),
                    strings.quantity(R.plurals.statistics_reread_count, s?.rereads ?: 0, s?.rereads ?: 0)], style = MaterialTheme.typography.bodySmall)
                Text(strings[R.string.statistics_page_definition], style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionLabel(strings[R.string.statistics_streaks])
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatisticsTile(strings[R.string.statistics_current_streak], strings.quantity(R.plurals.statistics_day_count, streaks.current, streaks.current), Modifier.weight(1f))
                    StatisticsTile(strings[R.string.statistics_longest_streak], strings.quantity(R.plurals.statistics_day_count, streaks.longest, streaks.longest), Modifier.weight(1f))
                }
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionLabel(strings[R.string.statistics_sessions])
                StatisticsRow(strings[R.string.statistics_session_count], "${summary?.sessions ?: 0}") {
                    detail = StatisticsDetail(strings[R.string.statistics_sessions], selectedRecords, selectedCompletions)
                }
                StatisticsRow(strings[R.string.statistics_average_session], readingDuration(summary?.averageSession ?: 0, strings))
                StatisticsRow(strings[R.string.statistics_longest_session], readingDuration(summary?.longestSession ?: 0, strings))
            }
        }
        item { SectionLabel(strings[R.string.statistics_most_read_series]) }
        val series = selectedRecords.groupBy { it.subject.series }.entries.sortedByDescending { (_, rows) -> rows.sumOf { it.activeMillis } }
        items(series, key = { "series:${it.key}" }) { (name, rows) ->
            StatisticsRow(name.ifBlank { strings[R.string.statistics_no_series] }, readingDuration(rows.sumOf { it.activeMillis }, strings)) {
                detail = StatisticsDetail(name.ifBlank { strings[R.string.statistics_no_series] }, rows, selectedCompletions.filter { it.subject.series == name })
            }
        }
        item { SectionLabel(strings[R.string.statistics_most_read_books]) }
        val books = selectedRecords.groupBy { it.subject.editionId }.entries.sortedByDescending { (_, rows) -> rows.sumOf { it.activeMillis } }
        items(books, key = { "book:${it.key}" }) { (edition, rows) ->
            val subject = rows.maxBy { it.endedAt }.subject
            StatisticsRow("${subject.title} · ${languageLabel(subject.language, strings)}", readingDuration(rows.sumOf { it.activeMillis }, strings)) {
                detail = StatisticsDetail(subject.title, rows, selectedCompletions.filter { it.subject.editionId == edition })
            }
        }
        item { SectionLabel(strings[R.string.statistics_languages]) }
        val languages = statistics.records.filter { it.date in range }.groupBy { it.subject.language }.entries.sortedByDescending { (_, rows) -> rows.sumOf { it.activeMillis } }
        items(languages, key = { "language:${it.key}" }) { (code, rows) ->
            StatisticsRow(languageLabel(code, strings), readingDuration(rows.sumOf { it.activeMillis }, strings)) { language = code }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) { SectionLabel(strings[R.string.statistics_activity_calendar]) }
                    IconButton({ calendarYear-- }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, strings[R.string.statistics_previous_year]) }
                    Text(calendarYear.toString())
                    IconButton({ calendarYear++ }, enabled = calendarYear < today.year) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, strings[R.string.statistics_next_year]) }
                }
                StatisticsCalendar(statistics, calendarYear, language, ::showDay)
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                statistics.trackingSince?.let { time ->
                    Text(strings[R.string.statistics_tracking_since, Instant.ofEpochMilli(time).atZone(ZoneId.systemDefault()).toLocalDate().format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))],
                        style = MaterialTheme.typography.bodySmall)
                }
                Text(strings[R.string.statistics_local_note], style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    detail?.let { StatisticsDetailSheet(it) { detail = null } }
}

@Composable private fun StatisticsTile(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(modifier, shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(value, style = MaterialTheme.typography.titleLarge)
            Text(label, style = MaterialTheme.typography.bodySmall)
        }
    }
}
@Composable private fun StatisticsRow(label: String, value: String, click: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().then(if (click != null) Modifier.clickable(onClick = click) else Modifier).padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Text(value, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
    }
}

@Composable private fun StatisticsBars(range: StatisticsRange, daily: Map<String, Long>, comparison: StatisticsRange,
    previous: Map<String, Long>, click: (String) -> Unit) {
    val strings = rememberStrings()
    val maximum = maxOf(daily.values.maxOrNull() ?: 0, previous.values.maxOrNull() ?: 0).coerceAtLeast(1)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(strings[R.string.statistics_daily_maximum, readingDuration(maximum, strings)], style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val columnWidth = maxWidth / range.dates.size
            val barWidth = minOf(12.dp, (columnWidth - 3.dp) / 2)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                range.dates.forEachIndexed { index, date ->
                    val duration = daily[date.toString()] ?: 0
                    val oldDate = comparison.start.plusDays(index.toLong())
                    val oldDuration = if (oldDate < comparison.endExclusive) previous[oldDate.toString()] ?: 0 else 0
                    Column(Modifier.weight(1f).clickable { click(date.toString()) }.semantics(mergeDescendants = true) {
                        contentDescription = strings[R.string.statistics_day_bar, date.toString(), readingDuration(duration, strings), readingDuration(oldDuration, strings)]
                    }.padding(top = 8.dp, bottom = 4.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(Modifier.height(120.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                            Box(Modifier.width(barWidth).height((120f * duration / maximum).coerceAtLeast(2f).dp).background(MaterialTheme.colorScheme.primary, RoundedCornerShape(4.dp)))
                            Box(Modifier.width(barWidth).height((120f * oldDuration / maximum).coerceAtLeast(2f).dp).background(MaterialTheme.colorScheme.primary.copy(alpha = .25f), RoundedCornerShape(4.dp)))
                        }
                        val label = if (range.dates.size == 7) date.dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, Locale.getDefault()).take(2)
                            else if (index == 0 || index == range.dates.lastIndex || (date.dayOfMonth % 5 == 0 && range.dates.size - date.dayOfMonth >= 3)) date.dayOfMonth.toString() else " "
                        Text(label, Modifier.wrapContentWidth(unbounded = true).heightIn(min = 24.dp), maxLines = 1, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

@Composable private fun StatisticsCalendar(statistics: ReadingStatistics, year: Int, language: String?, click: (String) -> Unit) {
    val strings = rememberStrings()
    val daily = remember(statistics, year, language) { statistics.records.filter { it.date.startsWith("$year-") && (language == null || it.subject.language == language) }
        .groupBy { it.date }.mapValues { (_, rows) -> rows.sumOf { it.activeMillis } } }
    val max = (daily.values.maxOrNull() ?: 0).coerceAtLeast(1)
    val start = LocalDate.of(year, 1, 1).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    val end = LocalDate.of(year + 1, 1, 1)
    val scroll = rememberScrollState()
    val weekWidth = with(LocalDensity.current) { 36.dp.roundToPx() }
    LaunchedEffect(year, language) {
        val focus = if (year == LocalDate.now().year) LocalDate.now() else daily.keys.maxOrNull()?.let(LocalDate::parse) ?: start
        val weeks = java.time.temporal.ChronoUnit.WEEKS.between(start, focus).toInt()
        scroll.scrollTo(((weeks - 3).coerceAtLeast(0) * weekWidth).coerceAtMost(scroll.maxValue))
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth().horizontalScroll(scroll).testTag("statistics-calendar"), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            generateSequence(start) { it.plusWeeks(1) }.takeWhile { it < end }.forEach { week ->
                Column(verticalArrangement = Arrangement.spacedBy(4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(if (week.dayOfMonth <= 7) week.month.getDisplayName(java.time.format.TextStyle.SHORT, Locale.getDefault()) else "", style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.width(32.dp).wrapContentWidth(unbounded = true), maxLines = 1)
                    repeat(7) { day ->
                        val date = week.plusDays(day.toLong())
                        val duration = daily[date.toString()] ?: 0
                        val intensity = if (duration == 0L) .06f else .25f + .75f * duration / max
                        Box(Modifier.size(32.dp).background(MaterialTheme.colorScheme.primary.copy(alpha = if (date.year == year) intensity else 0f), RoundedCornerShape(5.dp))
                            .then(if (date.year == year) Modifier.clickable { click(date.toString()) }.semantics {
                                contentDescription = strings[R.string.statistics_calendar_day, date.toString(), readingDuration(duration, strings)]
                            } else Modifier))
                    }
                }
            }
        }
        Text(strings[R.string.statistics_calendar_hint], style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun StatisticsDetailSheet(detail: StatisticsDetail, dismiss: () -> Unit) {
    val strings = rememberStrings()
    var expanded by remember(detail) { mutableStateOf(emptySet<String>()) }
    fun toggle(key: String) { expanded = if (key in expanded) expanded - key else expanded + key }
    ModalBottomSheet(onDismissRequest = dismiss) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(detail.title, Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
            TextButton(dismiss) { Text(strings[R.string.close]) }
        }
        LazyColumn(Modifier.fillMaxWidth().testTag("statistics-detail").heightIn(max = 640.dp), contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Text(readingDuration(detail.records.sumOf { it.activeMillis }, strings), style = MaterialTheme.typography.headlineMedium)
            }
            if (detail.records.isEmpty()) item { Text(strings[R.string.statistics_empty_period]) }
            detail.records.groupBy { it.subject.series }.entries.sortedByDescending { (_, rows) -> rows.sumOf { it.activeMillis } }.forEach { (series, seriesRows) ->
                item { SectionLabel(series.ifBlank { strings[R.string.statistics_no_series] }) }
                seriesRows.groupBy { it.subject.editionId }.entries.sortedByDescending { (_, rows) -> rows.sumOf { it.activeMillis } }.forEach { (edition, rows) ->
                    val subject = rows.maxBy { it.endedAt }.subject
                    item {
                        Row(Modifier.fillMaxWidth().clickable { toggle(edition) }, verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(subject.title, style = MaterialTheme.typography.titleSmall)
                                Text(languageLabel(subject.language, strings), style = MaterialTheme.typography.bodySmall)
                            }
                            Text(readingDuration(rows.sumOf { it.activeMillis }, strings))
                            Icon(if (edition in expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, strings[R.string.statistics_chapter_details])
                        }
                    }
                    if (edition in expanded) {
                        rows.groupBy { it.chapter }.forEach { (chapter, chapterRows) ->
                            val key = "$edition:$chapter"
                            item {
                                StatisticsRow(chapter ?: strings[R.string.statistics_unassigned_chapter], readingDuration(chapterRows.sumOf { it.activeMillis }, strings)) { toggle(key) }
                            }
                            if (key in expanded) {
                                chapterRows.groupBy { it.sessionId }.values.sortedByDescending { session -> session.maxOf { it.endedAt } }.forEach { session ->
                                    item {
                                        val time = Instant.ofEpochMilli(session.minOf { it.startedAt }).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT))
                                        val count = session.flatMap { it.pages.keys }.distinct().size
                                        Text(strings[R.string.statistics_session_detail, time, readingDuration(session.sumOf { it.activeMillis }, strings), strings.quantity(R.plurals.statistics_page_count, count, count)],
                                            Modifier.padding(start = 16.dp), style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }
                        }
                    }
                }
            }
            if (detail.completions.isNotEmpty()) {
                item { SectionLabel(strings[R.string.statistics_finished_in_period]) }
                items(detail.completions.sortedByDescending { it.completedAt }) { completion ->
                    Column {
                        Text(completion.subject.title + (completion.chapter?.let { " · $it" } ?: ""), style = MaterialTheme.typography.bodySmall)
                        Text(LocalDate.parse(completion.date).format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)) + " · " + languageLabel(completion.subject.language, strings),
                            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            if (detail.records.isNotEmpty()) {
                item { SectionLabel(strings[R.string.statistics_sessions]) }
                detail.records.groupBy { it.sessionId }.values.sortedByDescending { rows -> rows.maxOf { it.endedAt } }.forEach { rows ->
                    item {
                        val time = Instant.ofEpochMilli(rows.minOf { it.startedAt }).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT))
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(rows.last().subject.title, style = MaterialTheme.typography.titleSmall)
                            val count = rows.flatMap { it.pages.keys }.distinct().size
                            Text(strings[R.string.statistics_session_detail, time, readingDuration(rows.sumOf { it.activeMillis }, strings), strings.quantity(R.plurals.statistics_page_count, count, count)], style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}
