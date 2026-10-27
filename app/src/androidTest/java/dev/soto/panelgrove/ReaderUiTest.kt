package dev.soto.panelgrove

import android.net.Uri
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.soto.panelgrove.core.*
import dev.soto.panelgrove.data.*
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.junit.rules.RuleChain
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import java.io.File
import android.os.ParcelFileDescriptor
import androidx.lifecycle.ViewModelProvider
import dev.soto.panelgrove.ui.LibraryViewModel
import androidx.compose.ui.semantics.SemanticsActions

@RunWith(AndroidJUnit4::class)
class ReaderUiTest {
    val compose = createAndroidComposeRule<MainActivity>()
    private var originalScale: String? = null
    @get:Rule val rules = RuleChain.outerRule(object : TestWatcher() {
        override fun starting(description: Description) {
            if (description.methodName == "largeTextLabeledCommandsWorkWithoutPageGesturesAndResumeAfterBackgrounding") {
                originalScale = shell("settings get system font_scale")
                shell("settings put system font_scale 2.0")
                awaitFontScale(2f)
            }
        }
        override fun finished(description: Description) {
            originalScale?.let { scale ->
                if (scale.toFloatOrNull() != null) shell("settings put system font_scale $scale")
                else shell("settings delete system font_scale")
                awaitFontScale(scale.toFloatOrNull() ?: 1f)
            }
        }
    }).around(compose)
    private fun shell(command: String): String = ParcelFileDescriptor.AutoCloseInputStream(
        InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command)
    ).bufferedReader().use { it.readText().trim() }
    private fun awaitFontScale(scale: Float) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val deadline = android.os.SystemClock.uptimeMillis() + 10_000
        while (kotlin.math.abs(instrumentation.targetContext.resources.configuration.fontScale - scale) > .01f &&
            android.os.SystemClock.uptimeMillis() < deadline) android.os.SystemClock.sleep(20)
        instrumentation.waitForIdleSync()
        assertEquals("Font scale must settle before launching or after closing the activity", scale,
            instrumentation.targetContext.resources.configuration.fontScale, .01f)
    }
    @Test fun openNavigateBookmarkRecreateAndReturnToLibrary() {
        val app = compose.activity.application as PanelgroveApplication
        val file = File(app.sources.managedDirectory, "ui-sample.cbz")
        InstrumentationRegistry.getInstrumentation().context.assets.open("fixtures/sample.cbz").use { input -> file.outputStream().use { input.copyTo(it) } }
        val book = runBlocking {
            val imported = app.library.importFile(Uri.parse("panelgrove://managed/${file.name}"))
            app.library.change(imported.id) { it.copy(title = "UI Reading Test", metadataEdited = true, position = null, status = ReadingStatus.UNREAD, addedAt = System.currentTimeMillis()) }
            app.library.dao.deleteBookmarks(imported.id)
            app.library.book(imported.id)!!
        }
        compose.waitUntil(20_000) { ViewModelProvider(compose.activity)[LibraryViewModel::class.java].books.value.any { it.id == book.id && it.title == "UI Reading Test" } }
        compose.onNodeWithContentDescription("Search your shelf").performTextInput("UI Reading Test")
        compose.onNodeWithTag("library-shelf").performScrollToNode(hasText("UI Reading Test"))
        compose.onNodeWithContentDescription("Cover of UI Reading Test").performSemanticsAction(SemanticsActions.OnClick) { it() }
        compose.waitUntil(20_000) { compose.onAllNodesWithText("Contents").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Contents").performClick()
        compose.onNodeWithText("Chapter 2").performClick()
        compose.waitUntil(10_000) { runBlocking { app.library.book(book.id)?.position?.index == 2 } }
        compose.onNodeWithContentDescription("Bookmark page").performClick()
        compose.waitUntil(10_000) { runBlocking { app.library.dao.bookmarks().any { it.bookId == book.id } } }
        compose.activityRule.scenario.recreate()
        compose.waitUntil(20_000) { compose.onAllNodesWithText("3 / 4").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("Back to library").performClick()
        compose.onNodeWithText("Library").performClick()
        compose.onNodeWithContentDescription("Search your shelf").assertExists()
        assertEquals(2, runBlocking { app.library.book(book.id)!!.position!!.index })
    }
    @Test fun largeTextLabeledCommandsWorkWithoutPageGesturesAndResumeAfterBackgrounding() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val app = compose.activity.application as PanelgroveApplication
        val file = File(app.sources.managedDirectory, "large-text.cbz")
        instrumentation.context.assets.open("fixtures/sample.cbz").use { input -> file.outputStream().use { input.copyTo(it) } }
        val book = runBlocking {
            val imported = app.library.importFile(Uri.parse("panelgrove://managed/${file.name}"))
            app.library.change(imported.id) { it.copy(position = null, settings = ReaderOverrides()) }; imported
        }
        compose.runOnIdle { ViewModelProvider(compose.activity)[LibraryViewModel::class.java].open(book.id) }
        compose.waitUntil(20_000) { compose.onAllNodesWithText("1 / 4").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("Next page").assertIsDisplayed().performClick()
        compose.waitUntil(10_000) { runBlocking { app.library.book(book.id)?.position?.index == 1 } }
        compose.onNodeWithContentDescription("Previous page").assertIsDisplayed().performClick()
        compose.waitUntil(10_000) { runBlocking { app.library.book(book.id)?.position?.index == 0 } }
        compose.onNodeWithContentDescription("Reading page").assertExists()
        compose.onNodeWithContentDescription("Reading settings").performClick()
        compose.onNodeWithText("Right to left").assertIsDisplayed()
        compose.onNodeWithText("Save").assertIsDisplayed().performClick()
        compose.activityRule.scenario.moveToState(androidx.lifecycle.Lifecycle.State.CREATED)
        compose.activityRule.scenario.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED)
        compose.waitUntil(10_000) { compose.onAllNodesWithText("1 / 4").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("Back to library").performClick()
    }
}
