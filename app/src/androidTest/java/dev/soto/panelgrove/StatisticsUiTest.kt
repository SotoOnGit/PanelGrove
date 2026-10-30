package dev.soto.panelgrove

import android.net.Uri
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import dev.soto.panelgrove.core.*
import dev.soto.panelgrove.data.*
import dev.soto.panelgrove.ui.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import java.io.File
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

class StatisticsUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val app get() = compose.activity.application as PanelgroveApplication
    private val model get() = ViewModelProvider(compose.activity)[LibraryViewModel::class.java]
    private val reader get() = ViewModelProvider(compose.activity)[ReaderViewModel::class.java]
    private val prefix = "statistics-ui-${UUID.randomUUID()}"
    private lateinit var previous: AppPreferences
    private val files = mutableListOf<File>()
    private val created = mutableListOf<Book>()

    @Before fun setup() { previous = runBlocking { app.preferences.current() }; compose.runOnIdle { model.open(null) } }
    @After fun cleanup() {
        compose.runOnIdle { reader.close(); model.open(null) }
        compose.waitForIdle()
        runBlocking {
            created.forEach { app.library.remove(it.id, false) }
            app.preferences.set(previous)
            app.database.openHelper.writableDatabase.execSQL("DELETE FROM reading_records WHERE id LIKE ? OR editionId IN (${created.joinToString(",") { "?" }.ifBlank { "NULL" }})", arrayOf("$prefix%", *created.map { it.statisticsId }.toTypedArray()))
            app.database.openHelper.writableDatabase.execSQL("DELETE FROM reading_completions WHERE id LIKE ? OR editionId IN (${created.joinToString(",") { "?" }.ifBlank { "NULL" }})", arrayOf("$prefix%", *created.map { it.statisticsId }.toTypedArray()))
        }
        files.forEach { it.delete() }
    }
    private fun snapshot(name: String) {
        compose.waitForIdle()
        Thread.sleep(350)
        val directory = File(app.filesDir, "statistics-validation").apply { mkdirs() }
        InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot().let { bitmap ->
            File(directory, "$name.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
    }
    private fun navigate() {
        compose.onNodeWithContentDescription("Statistics").performClick()
        compose.onNodeWithTag("statistics-list").assertExists()
    }
    @Test fun graphComparisonDrilldownLanguagesCalendarAndRecreationWork() {
        val today = LocalDate.now()
        val start = today.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() + 1_000
        val subject = ReadingSubject(prefix, prefix, "Statistics test volume", "Statistics test series", "1", language = "ja")
        val row = ReadingRecord("$prefix-row", "$prefix-session", subject, today.toString(), "Chapter 1", start, start + 120_000, 120_000,
            mapOf("one" to start + 30_000, "two" to start + 60_000))
        runBlocking {
            app.library.dao.save(row.row())
            app.library.dao.save(row.copy(id = "$prefix-previous", sessionId = "$prefix-previous-session", date = today.minusWeeks(1).toString(),
                startedAt = start - 604_800_000, endedAt = start - 604_800_000 + 60_000, activeMillis = 60_000,
                pages = mapOf("one" to start - 604_800_000 + 30_000)).row())
            app.library.dao.save(ReadingCompletion("$prefix-finish", subject, today.toString(), start + 120_000).row())
        }
        navigate()
        compose.waitUntil(10_000) { model.statistics.value.records.any { it.id == "$prefix-row" } }
        snapshot("statistics-week-dark")
        // Earlier tests leave reading records that can wrap the comparison text and
        // push the chart below the viewport on the smaller CI emulator.
        compose.onNode(hasContentDescription("$today:", substring = true)).performScrollTo().assertIsDisplayed().performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("statistics-detail").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("statistics-detail").assertIsDisplayed()
        compose.onNodeWithText("Close").performClick()
        compose.onNodeWithContentDescription("Compare with an older period").performScrollTo().assertIsDisplayed().performClick()
        compose.onNodeWithContentDescription("Compare with a newer period").performScrollTo().assertIsDisplayed().performClick()
        compose.onNodeWithText("Month").performScrollTo().assertIsDisplayed().performClick()
        compose.onNode(hasContentDescription("${today.withDayOfMonth(1)}:", substring = true)).performScrollTo().assertIsDisplayed()
        compose.onNode(hasContentDescription("${today.withDayOfMonth(today.lengthOfMonth())}:", substring = true)).assertIsDisplayed()
        snapshot("statistics-month-dark")
        compose.onNodeWithText("All languages").performScrollTo().assertIsDisplayed().performClick()
        compose.onNodeWithText("Japanese (ja)").performClick()
        compose.onNodeWithContentDescription("Previous period").performScrollTo().assertIsDisplayed().performClick()
        compose.onNodeWithText("Today").performScrollTo().assertIsDisplayed().performClick()
        compose.activityRule.scenario.recreate()
        compose.onNodeWithTag("statistics-list").assertExists()
        compose.onNodeWithTag("statistics-list").performScrollToNode(hasText("Statistics test volume · Japanese (ja)"))
        compose.onNodeWithText("Statistics test volume · Japanese (ja)").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("statistics-detail").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("statistics-detail").assertIsDisplayed()
        compose.onNode(hasText("Statistics test volume") and hasClickAction()).performClick()
        compose.onNodeWithText("Chapter 1").performClick()
        compose.onAllNodesWithText("pages viewed", substring = true).onFirst().assertExists()
        snapshot("statistics-drilldown")
        compose.onNodeWithText("Close").performClick()
        compose.onNodeWithTag("statistics-list").performScrollToNode(hasTestTag("statistics-calendar"))
        snapshot("statistics-calendar")
        compose.onNodeWithTag("statistics-calendar").assertExists()
        compose.runOnIdle { model.updatePreferences { it.copy(theme = AppTheme.LIGHT) } }
        compose.waitUntil(10_000) { model.preferences.value.theme == AppTheme.LIGHT }
        compose.onNodeWithTag("statistics-list").performScrollToIndex(0)
        snapshot("statistics-week-light")
        val libraryModel = model
        val density = app.resources.displayMetrics.density
        compose.runOnIdle {
            compose.activity.setContent {
                CompositionLocalProvider(LocalDensity provides Density(density, 1.5f)) {
                    PanelgroveApp(libraryModel)
                }
            }
        }
        compose.onNodeWithContentDescription("Statistics").performClick()
        compose.onNodeWithTag("statistics-list").assertExists()
        snapshot("statistics-large-font")
        compose.onNodeWithTag("statistics-list").performScrollToNode(hasTestTag("statistics-calendar"))
        snapshot("statistics-calendar-large-font")
    }
    @Test fun timerTracksRenderedPagesPausesForMenusAndBackgroundAndSurvivesRotation() {
        val book = runBlocking {
            val file = File(app.sources.managedDirectory, "$prefix.cbz").also { files += it }
            InstrumentationRegistry.getInstrumentation().context.assets.open("fixtures/sample.cbz").use { input -> file.outputStream().use { input.copyTo(it) } }
            app.library.importFile(Uri.parse("panelgrove://managed/${file.name}")).also { created += it }
        }
        compose.runOnIdle { model.open(book.id) }
        compose.waitUntil(20_000) { compose.onAllNodesWithTag("rendered-page-1").fetchSemanticsNodes().isNotEmpty() }
        Thread.sleep(2_500)
        compose.runOnIdle { reader.checkpointStatistics() }
        var before = runBlocking {
            repeat(50) { if (app.library.dao.readingRecordsForEdition(book.statisticsId).isNotEmpty()) return@repeat; delay(20) }
            app.library.dao.readingRecordsForEdition(book.statisticsId).map { it.record() }
        }
        assertTrue(before.isNotEmpty())
        assertEquals(setOf("test/1"), before.flatMap { it.pages.keys }.toSet())
        compose.onNodeWithContentDescription("Pause reading timer").performClick()
        compose.runOnIdle { reader.checkpointStatistics() }
        Thread.sleep(300)
        before = runBlocking { app.library.dao.readingRecordsForEdition(book.statisticsId).map { it.record() } }
        Thread.sleep(2_000)
        compose.runOnIdle { reader.checkpointStatistics() }
        Thread.sleep(200)
        assertEquals(before.sumOf { it.activeMillis }, runBlocking { app.library.dao.readingRecordsForEdition(book.statisticsId).sumOf { it.record().activeMillis } })
        compose.onNodeWithContentDescription("Resume reading timer").performClick()
        compose.onNodeWithContentDescription("Reader actions").performClick()
        compose.runOnIdle { reader.checkpointStatistics() }
        Thread.sleep(300)
        val menuStart = runBlocking { app.library.dao.readingRecordsForEdition(book.statisticsId).sumOf { it.record().activeMillis } }
        Thread.sleep(2_000)
        compose.runOnIdle { reader.checkpointStatistics() }
        Thread.sleep(200)
        assertEquals(menuStart, runBlocking { app.library.dao.readingRecordsForEdition(book.statisticsId).sumOf { it.record().activeMillis } })
        compose.onNodeWithText("Export page").performClick()
        compose.onNodeWithText("Cancel").performClick()
        compose.activityRule.scenario.moveToState(androidx.lifecycle.Lifecycle.State.CREATED)
        Thread.sleep(300)
        val background = runBlocking { app.library.dao.readingRecordsForEdition(book.statisticsId).sumOf { it.record().activeMillis } }
        Thread.sleep(1_000)
        compose.activityRule.scenario.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED)
        compose.activityRule.scenario.recreate()
        compose.waitUntil(20_000) { compose.onAllNodesWithTag("rendered-page-1").fetchSemanticsNodes().isNotEmpty() }
        compose.runOnIdle { reader.checkpointStatistics() }
        Thread.sleep(300)
        val after = runBlocking { app.library.dao.readingRecordsForEdition(book.statisticsId).map { it.record() } }
        ReadingStatistics(records = after).validate()
        assertTrue(after.sumOf { it.activeMillis } >= background)
        assertEquals(1, after.map { it.sessionId }.distinct().size)
    }
    @Test fun spreadsAndVerticalScrollingCountVisiblePagesWithoutCountingPrefetchOrDoublingTime() {
        val start = android.os.SystemClock.elapsedRealtime()
        val book = runBlocking {
            val file = File(app.sources.managedDirectory, "$prefix.cbz").also { files += it }
            InstrumentationRegistry.getInstrumentation().context.assets.open("fixtures/sample.cbz").use { input -> file.outputStream().use { input.copyTo(it) } }
            val imported = app.library.importFile(Uri.parse("panelgrove://managed/${file.name}"))
            app.library.change(imported.id) { it.copy(position = ReadingPosition("test/2", 1),
                settings = ReaderOverrides(mode = ReadingMode.PAGED, direction = Direction.LTR, spreads = true)) }
            app.library.book(imported.id)!!.also { created += it }
        }
        try {
            compose.runOnIdle { compose.activity.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE; model.open(book.id) }
            compose.waitUntil(20_000) {
                compose.onAllNodesWithTag("rendered-page-2").fetchSemanticsNodes().isNotEmpty() && compose.onAllNodesWithTag("rendered-page-3").fetchSemanticsNodes().isNotEmpty()
            }
            Thread.sleep(2_500)
            compose.runOnIdle { reader.checkpointStatistics() }
            compose.waitUntil(10_000) { runBlocking { app.library.dao.readingRecordsForEdition(book.statisticsId).flatMap { it.record().pages.keys }.containsAll(listOf("test/2", "test/3")) } }
            runBlocking { app.library.change(book.id) { it.copy(settings = it.settings.copy(mode = ReadingMode.VERTICAL)) } }
            compose.runOnIdle { reader.move(3) }
            compose.waitUntil(20_000) { compose.onAllNodesWithTag("rendered-page-4").fetchSemanticsNodes().isNotEmpty() }
            Thread.sleep(2_500)
            compose.runOnIdle { reader.checkpointStatistics() }
            compose.waitUntil(10_000) { runBlocking { app.library.dao.readingRecordsForEdition(book.statisticsId).any { "test/4" in it.record().pages } } }
            val rows = runBlocking { app.library.dao.readingRecordsForEdition(book.statisticsId).map { it.record() } }
            ReadingStatistics(records = rows).validate()
            assertFalse(rows.any { "test/1" in it.pages })
            assertTrue(rows.sumOf { it.activeMillis } <= android.os.SystemClock.elapsedRealtime() - start + 500)
        } finally {
            compose.runOnIdle { compose.activity.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED }
        }
    }
}
