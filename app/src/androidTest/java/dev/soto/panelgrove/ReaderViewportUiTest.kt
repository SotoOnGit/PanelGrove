package dev.soto.panelgrove

import android.content.pm.ActivityInfo
import android.net.Uri
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.soto.panelgrove.core.*
import dev.soto.panelgrove.ui.LibraryViewModel
import dev.soto.panelgrove.ui.ReaderViewModel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class ReaderViewportUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val app get() = compose.activity.application as PanelgroveApplication
    private fun page(number: Int) = compose.onNodeWithContentDescription("Page $number. Pinch or double tap to zoom.")
    private fun zoom(number: Int, percent: Int) = page(number).assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "$percent% zoom"))
    private fun withBook(settings: ReaderOverrides, block: (Book) -> Unit) {
        val file = File(app.sources.managedDirectory, "viewport-${UUID.randomUUID()}.cbz")
        InstrumentationRegistry.getInstrumentation().context.assets.open("fixtures/sample.cbz").use { source -> file.outputStream().use { source.copyTo(it) } }
        val book = runBlocking {
            val book = app.library.importFile(Uri.parse("panelgrove://managed/${file.name}"))
            app.library.change(book.id) { it.copy(settings = ReaderOverrides.from(settings.applyTo(ReaderSettings()))) }
            app.library.book(book.id)!!
        }
        try {
            runBlocking {
                androidx.work.WorkManager.getInstance(app).getWorkInfosByTag("library").get()
                    .filter { it.state == androidx.work.WorkInfo.State.FAILED }
                    .forEach { app.preferences.acknowledgeFailure(it.id.toString()) }
            }
            compose.waitUntil(10_000) { compose.onAllNodesWithText("Import needs attention").fetchSemanticsNodes().isEmpty() }
            compose.runOnIdle { ViewModelProvider(compose.activity)[LibraryViewModel::class.java].open(book.id) }
            compose.waitUntil(30_000) { compose.onNodeWithTag("rendered-page-1").isDisplayed() }
            block(book)
        } finally {
            compose.runOnIdle {
                ViewModelProvider(compose.activity)[ReaderViewModel::class.java].close()
                ViewModelProvider(compose.activity)[LibraryViewModel::class.java].open(null)
            }
            compose.waitForIdle()
            runBlocking { app.library.remove(book.id, false) }
            file.delete()
            compose.runOnIdle { compose.activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED }
        }
    }
    @Test fun doubleTapPinchAndPanUseOneCameraWithoutTurningThePage() = withBook(ReaderOverrides(spreads = false)) { book ->
        page(1).performTouchInput { doubleClick(Offset(width * .35f, height * .4f)) }
        zoom(1, 250)
        compose.onNodeWithTag("reader-viewport").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "250% zoom"))
        page(1).performTouchInput { swipeLeft() }
        assertEquals(0, runBlocking { app.library.book(book.id)?.position?.index ?: 0 })
        page(1).performTouchInput { doubleClick() }
        zoom(1, 100)
        page(1).performTouchInput { pinch(Offset(width * .4f, height * .5f), Offset(width * .6f, height * .5f),
            Offset(width * .2f, height * .5f), Offset(width * .8f, height * .5f)) }
        val state = compose.onNodeWithTag("reader-viewport").fetchSemanticsNode().config[SemanticsProperties.StateDescription]
        assertTrue(state, state.substringBefore('%').toInt() > 150)
        assertEquals(0, runBlocking { app.library.book(book.id)?.position?.index ?: 0 })
    }
    @Test fun spreadPagesMagnifyTogether() = withBook(ReaderOverrides(spreads = true)) { _ ->
        compose.runOnIdle { compose.activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
        compose.waitUntil(20_000) { compose.activity.resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE }
        compose.waitUntil(20_000) { page(1).isDisplayed() }
        compose.runOnIdle { ViewModelProvider(compose.activity)[ReaderViewModel::class.java].move(1) }
        compose.waitUntil(20_000) { page(2).isDisplayed() && page(3).isDisplayed() }
        page(2).performTouchInput { doubleClick() }
        zoom(2, 250); zoom(3, 250)
    }
    @Test fun verticalZoomDoesNotScrollTheUnderlyingPages() = withBook(ReaderOverrides(mode = ReadingMode.VERTICAL)) { book ->
        page(1).performTouchInput { doubleClick() }
        zoom(1, 250)
        val before = runBlocking { app.library.book(book.id)?.position }
        compose.onNodeWithTag("reader-viewport").performTouchInput { swipeUp() }
        compose.waitForIdle()
        val after = runBlocking { app.library.book(book.id)?.position }
        assertEquals(before?.index ?: 0, after?.index ?: 0)
        assertEquals(before?.offset ?: 0f, after?.offset ?: 0f, .01f)
    }
}
