package dev.soto.panelgrove

import android.content.pm.ActivityInfo
import android.net.Uri
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.soto.panelgrove.core.*
import dev.soto.panelgrove.ui.LibraryViewModel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class ReaderModesUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val app get() = compose.activity.application as PanelgroveApplication
    private fun open(name: String): Book {
        val file = File(app.sources.managedDirectory, name)
        InstrumentationRegistry.getInstrumentation().context.assets.open("fixtures/sample.cbz").use { input -> file.outputStream().use { input.copyTo(it) } }
        val book = runBlocking {
            val imported = app.library.importFile(Uri.parse("panelgrove://managed/${file.name}"))
            app.library.change(imported.id) { it.copy(position = null, settings = ReaderOverrides(), status = ReadingStatus.UNREAD) }
            app.library.book(imported.id)!!
        }
        compose.runOnIdle { ViewModelProvider(compose.activity)[LibraryViewModel::class.java].open(book.id) }
        compose.waitUntil(20_000) { compose.onAllNodesWithText("1 / 4").fetchSemanticsNodes().isNotEmpty() }
        compose.waitUntil(20_000) { compose.onNodeWithTag("rendered-page-1").isDisplayed() }
        return book
    }
    private fun page(index: Int) = compose.onNodeWithContentDescription("Page $index. Pinch or double tap to zoom.")
    private fun confirmSavedSelection(label: String) {
        compose.onNodeWithContentDescription("Reading settings").performClick()
        compose.waitUntil(10_000) { compose.onAllNodes(hasText(label) and
            SemanticsMatcher.expectValue(SemanticsProperties.Selected, true)).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Cancel").performClick()
    }
    @Test fun rtlLtrZoomAndVerticalResume() {
        val book = open("mode-test.cbz")
        page(1).performTouchInput { swipeRight() }
        compose.waitUntil(10_000) { runBlocking { app.library.book(book.id)?.position?.index == 1 } }
        page(2).performTouchInput { doubleClick() }
        page(2).assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "250% zoom"))
        page(2).performTouchInput { doubleClick() }
        page(2).assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "100% zoom"))
        compose.onNodeWithContentDescription("Reading settings").performClick()
        compose.onNodeWithText("Left to right").performClick()
        compose.onNodeWithText("Save").performClick()
        compose.waitUntil(10_000) { runBlocking { app.library.book(book.id)?.settings?.direction == Direction.LTR } }
        confirmSavedSelection("Left to right")
        page(2).performTouchInput { swipeLeft() }
        compose.waitUntil(10_000) { runBlocking { app.library.book(book.id)?.position?.index == 2 } }
        compose.onNodeWithContentDescription("Reading settings").performClick()
        compose.onNodeWithText("Vertical scroll").performClick()
        compose.onNodeWithText("Save").performClick()
        compose.waitUntil(10_000) { runBlocking { app.library.book(book.id)?.settings?.mode == ReadingMode.VERTICAL } }
        confirmSavedSelection("Vertical scroll")
        compose.onRoot().performTouchInput { swipeUp(startY = height * .7f, endY = height * .5f, durationMillis = 500) }
        compose.waitUntil(10_000) { runBlocking { (app.library.book(book.id)?.position?.offset ?: 0f) > .05f } }
        // A fling and its queued database writes must settle before recording the recreation baseline.
        compose.waitForIdle()
        var before: ReadingPosition? = null
        var unchangedSince = System.nanoTime()
        compose.waitUntil(10_000) {
            val position = runBlocking { app.library.book(book.id)?.position }
            if (position != before) { before = position; unchangedSince = System.nanoTime() }
            position != null && position.offset > .05f && System.nanoTime() - unchangedSince >= 300_000_000L
        }
        val savedPosition = checkNotNull(before)
        compose.activityRule.scenario.recreate()
        compose.waitUntil(20_000) {
            val position = runBlocking { app.library.book(book.id)?.position }
            position != null && position.pageId == savedPosition.pageId && kotlin.math.abs(position.offset - savedPosition.offset) < .04f
        }
        compose.onNodeWithContentDescription("Back to library").performClick()
    }
    @Test fun landscapeSpreadsKeepCoverAlone() {
        val book = open("spreads-test.cbz")
        runBlocking { app.library.change(book.id) { it.copy(settings = ReaderOverrides(spreads = true)) } }
        compose.runOnIdle { compose.activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
        compose.waitUntil(20_000) { compose.activity.resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE }
        compose.waitUntil(20_000) { page(1).isDisplayed() }
        page(1).assertIsDisplayed()
        page(1).performTouchInput { swipeRight() }
        compose.waitUntil(10_000) { runBlocking { app.library.book(book.id)?.position?.index == 1 } }
        page(2).assertIsDisplayed(); page(3).assertIsDisplayed()
        compose.onNodeWithContentDescription("Back to library").performClick()
        compose.runOnIdle { compose.activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED }
    }
}
