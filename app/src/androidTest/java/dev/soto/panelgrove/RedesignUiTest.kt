package dev.soto.panelgrove

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import dev.soto.panelgrove.core.*
import dev.soto.panelgrove.data.row
import dev.soto.panelgrove.ui.LibraryViewModel
import dev.soto.panelgrove.ui.LibraryScreen
import dev.soto.panelgrove.ui.PanelgroveTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.UUID

class RedesignUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val app get() = compose.activity.application as PanelgroveApplication
    private val model get() = ViewModelProvider(compose.activity)[LibraryViewModel::class.java]
    private fun snapshot(name: String) {
        compose.waitForIdle()
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        android.os.SystemClock.sleep(250) // Allow the Android window/sheet animation to reach the captured frame.
        val directory = File(app.filesDir, "design-validation").apply { mkdirs() }
        InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot().let { bitmap ->
            File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
    }
    private fun book(name: String, number: String): Book = runBlocking {
        val file = File(app.sources.managedDirectory, "redesign-$number.cbz")
        InstrumentationRegistry.getInstrumentation().context.assets.open("fixtures/sample.cbz").use { input -> file.outputStream().use { input.copyTo(it) } }
        val imported = app.library.importFile(Uri.parse("panelgrove://managed/${file.name}"))
        app.library.change(imported.id) { it.copy(title = name, series = "Panelgrove design fixtures", number = number, status = ReadingStatus.UNREAD,
            position = null, favorite = false, settings = ReaderOverrides(), metadataEdited = true) }
        app.library.dao.deleteBookmarks(imported.id)
        app.library.book(imported.id)!!
    }

    @Test fun librarySheetsContentsCompletionAndDailyHistory() {
        val previous = runBlocking { app.preferences.current() }
        val first = book("A Quiet Afternoon", "1")
        val second = book("The Next Afternoon", "2")
        try {
            runBlocking { app.preferences.update { it.copy(theme = AppTheme.DARK, trueBlack = false, reader = ReaderSettings()) } }
            compose.onNodeWithText("Library").assertExists()
            compose.onNodeWithContentDescription("Home").assertDoesNotExist()
            compose.runOnIdle { model.open(first.id) }
            compose.waitUntil(20_000) { compose.onAllNodesWithText("1 / 4").fetchSemanticsNodes().isNotEmpty() }
            compose.waitUntil(20_000) { compose.onNodeWithTag("rendered-page-1").isDisplayed() }
            snapshot("reader")
            compose.onNodeWithContentDescription("Bookmark page").performClick()
            compose.onNodeWithText("Contents").performClick()
            compose.onNodeWithText("Chapter 2").assertIsDisplayed()
            snapshot("contents-chapters")
            compose.onNodeWithText("Bookmarks").performClick()
            compose.onNodeWithText("Current page").assertIsDisplayed()
            snapshot("contents-bookmarks")
            compose.onNodeWithContentDescription("Remove bookmark on page 1").performClick()
            compose.onNodeWithText("Bookmark a page to find it here.").assertIsDisplayed()
            compose.onNodeWithText("Close").performClick()
            compose.onNodeWithContentDescription("Reading settings").performClick()
            snapshot("reader-settings")
            compose.onNodeWithText("Left to right").performClick()
            compose.onNodeWithText("Cancel").performClick()
            assertNull(runBlocking { app.library.book(first.id)!!.settings.direction })
            compose.onNodeWithContentDescription("Reading settings").performClick()
            compose.onNodeWithText("Left to right").performClick()
            compose.onNodeWithText("Save").performClick()
            compose.waitUntil(10_000) { runBlocking { app.library.book(first.id)!!.settings.direction == Direction.LTR } }
            compose.onNodeWithContentDescription("Finish book").performClick()
            compose.onNodeWithText("Read next").assertIsDisplayed()
            snapshot("finish-with-next")
            compose.onNodeWithText("Read next").performClick()
            compose.waitUntil(20_000) { compose.onAllNodesWithText("1 / 4").fetchSemanticsNodes().isNotEmpty() && runBlocking { app.library.book(second.id)!!.status == ReadingStatus.READING } }
            compose.onNodeWithContentDescription("Finish book").performClick()
            compose.onNodeWithText("Read next").assertDoesNotExist()
            snapshot("finish-without-next")
            compose.onNodeWithText("Read again").performClick()
            compose.waitUntil(10_000) { runBlocking { app.library.book(second.id)!!.status == ReadingStatus.READING } }
            compose.onNodeWithContentDescription("Back to library").performClick()
            compose.onNodeWithContentDescription("Search your shelf").performTextInput("A Quiet Afternoon")
            compose.onNodeWithTag("library-shelf").performScrollToNode(hasTestTag("book-${first.id}"))
            compose.onNodeWithTag("book-${first.id}").performTouchInput { longClick() }
            compose.onNodeWithText("Book details").assertIsDisplayed()
            snapshot("book-details")
            compose.onNodeWithText("Add to favorites").performClick()
            compose.waitUntil(10_000) { runBlocking { app.library.book(first.id)!!.favorite } }
            compose.onNodeWithText("Close").performClick()
            compose.onNodeWithContentDescription("Search your shelf").performTextClearance()
            snapshot("library")
            val now = System.currentTimeMillis()
            runBlocking {
                app.library.dao.deleteHistory(first.id)
                repeat(3) { app.library.dao.save(HistoryEntry(UUID.randomUUID().toString(), first.id, now - it * 1000).row()) }
            }
            compose.onNodeWithContentDescription("History").performClick()
            compose.onNodeWithTag("history-${first.id}-${java.time.LocalDate.now()}").assert(hasText("3 sessions", substring = true))
            snapshot("history")
            compose.onNodeWithContentDescription("Clear history").performClick()
            compose.onNodeWithText("Cancel").performClick()
        } finally {
            snapshot("flow-final")
            File(app.filesDir, "design-validation/flow-semantics.txt").writeText(compose.onRoot().printToString())
            compose.runOnIdle { model.open(null) }
            runBlocking { app.preferences.set(previous) }
        }
    }

    @Test fun appearancePersistenceAndSettingsLayout() {
        val previous = runBlocking { app.preferences.current() }
        try {
            runBlocking { app.preferences.update { it.copy(theme = AppTheme.DARK, trueBlack = false) } }
            compose.onNodeWithContentDescription("Settings").performClick()
            snapshot("settings-dark")
            compose.onNodeWithContentDescription("True black").performClick()
            compose.waitUntil(10_000) { runBlocking { app.preferences.current().trueBlack } }
            compose.activityRule.scenario.recreate()
            compose.onNodeWithContentDescription("True black").assertIsOn()
            snapshot("settings-black")
            compose.onNodeWithText("Light").performClick()
            compose.waitUntil(10_000) { runBlocking { app.preferences.current().theme == AppTheme.LIGHT } }
            snapshot("settings-light")
            compose.onNodeWithTag("settings-list").performScrollToNode(hasText("Open-source licenses"))
            snapshot("settings-scrolled")
        } finally { runBlocking { app.preferences.set(previous) } }
    }

    @Test fun emptyLibraryOffersSeparateFileAndFolderActions() {
        var filesChosen = false
        var folderChosen = false
        compose.runOnIdle {
            compose.activity.setContent {
                PanelgroveTheme(AppTheme.DARK) {
                    Surface(Modifier.fillMaxSize()) {
                        Box(Modifier.safeDrawingPadding()) {
                            LibraryScreen(emptyList(), true, model, {}, { filesChosen = true }, { folderChosen = true })
                        }
                    }
                }
            }
        }
        compose.onNodeWithText("Your shelf is empty").assertIsDisplayed()
        compose.onNodeWithText("Choose files").performClick()
        compose.onNodeWithText("Add a folder").performClick()
        assertTrue(filesChosen)
        assertTrue(folderChosen)
        snapshot("empty-library-content")
    }
}
