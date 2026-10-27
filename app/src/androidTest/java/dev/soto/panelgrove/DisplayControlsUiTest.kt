package dev.soto.panelgrove

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import dev.soto.panelgrove.core.*
import dev.soto.panelgrove.ui.LibraryViewModel
import dev.soto.panelgrove.ui.ReaderViewModel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.UUID

class DisplayControlsUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val app get() = compose.activity.application as PanelgroveApplication
    private fun snapshot(name: String) {
        compose.waitForIdle()
        val directory = File(app.filesDir, "display-validation").apply { mkdirs() }
        InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot().let { bitmap ->
            File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle()
        }
    }
    private fun adjust(name: String, value: Float) = compose.onNodeWithContentDescription(name)
        .performSemanticsAction(SemanticsActions.SetProgress) { it(value) }
    private fun showAdjustments() {
        compose.onNodeWithContentDescription("Reading settings").performClick()
        compose.onNodeWithText("Display adjustments").performClick()
        compose.waitUntil(15_000) { compose.onNodeWithTag("display-preview").isDisplayed() }
    }
    private fun previewPixels() = compose.onNodeWithTag("display-preview").capturePixels()
    private fun pagePixels() = compose.onNodeWithTag("rendered-page-1").captureToImage().toPixelMap()
    private fun changed(a: androidx.compose.ui.graphics.PixelMap, b: androidx.compose.ui.graphics.PixelMap): Boolean {
        if (a.width != b.width || a.height != b.height) return false
        return (1..9).any { x -> (1..9).any { y -> a[a.width * x / 10, a.height * y / 10] != b[b.width * x / 10, b.height * y / 10] } }
    }

    @Test fun previewCancelSaveRecreateAndResetRetainTheExpectedPixels() {
        val file = File(app.sources.managedDirectory, "display-${UUID.randomUUID()}.cbz")
        InstrumentationRegistry.getInstrumentation().context.assets.open("fixtures/sample.cbz").use { input -> file.outputStream().use { input.copyTo(it) } }
        val previous = runBlocking { app.preferences.current() }
        val book = runBlocking { app.library.importFile(Uri.parse("panelgrove://managed/${file.name}")) }
        try {
            runBlocking { app.preferences.update { it.copy(reader = ReaderSettings(), seriesSettings = emptyMap()) } }
            compose.runOnIdle { ViewModelProvider(compose.activity)[LibraryViewModel::class.java].open(book.id) }
            compose.waitUntil(30_000) { compose.onNodeWithTag("rendered-page-1").isDisplayed() }
            val original = pagePixels()
            showAdjustments()
            val neutral = previewPixels()
            adjust("Gamma", 1.6f); adjust("Contrast", 1.3f)
            compose.waitUntil(15_000) { changed(neutral, previewPixels()) }
            snapshot("preview-adjusted")
            compose.activityRule.scenario.recreate()
            compose.waitUntil(30_000) { compose.onNodeWithTag("display-preview").isDisplayed() }
            compose.onNodeWithText("Gamma · 1.60").assertIsDisplayed()
            compose.onNodeWithText("Contrast · 1.30").assertIsDisplayed()
            compose.onNodeWithText("Cancel").performClick()
            assertNull(runBlocking { app.library.book(book.id)!!.settings.gamma })
            assertFalse(changed(original, pagePixels()))
            showAdjustments(); adjust("Gamma", 1.6f); adjust("Contrast", 1.3f)
            compose.onNodeWithText("Save").performClick()
            compose.waitUntil(10_000) { runBlocking { app.library.book(book.id)?.settings?.gamma == 1.6f } }
            compose.waitUntil(15_000) { changed(original, pagePixels()) }
            snapshot("reader-adjusted")
            compose.activityRule.scenario.recreate()
            compose.waitUntil(30_000) { compose.onNodeWithTag("rendered-page-1").isDisplayed() }
            compose.waitUntil(15_000) { changed(original, pagePixels()) }
            showAdjustments()
            compose.onNodeWithText("Reset display adjustments").performScrollTo().performClick()
            snapshot("preview-reset")
            compose.onNodeWithText("Save").performClick()
            compose.waitUntil(10_000) { runBlocking { app.library.book(book.id)?.settings?.gamma == 1f && app.library.book(book.id)?.settings?.contrast == 1f } }
            compose.waitUntil(15_000) { !changed(original, pagePixels()) }
            compose.onNodeWithContentDescription("Reading settings").performClick()
            compose.onNodeWithText("Use defaults").performClick()
            compose.waitUntil(10_000) { runBlocking { app.library.book(book.id)?.settings == ReaderOverrides() } }
        } finally {
            compose.runOnIdle {
                ViewModelProvider(compose.activity)[ReaderViewModel::class.java].close()
                ViewModelProvider(compose.activity)[LibraryViewModel::class.java].open(null)
            }
            compose.waitForIdle()
            runBlocking { app.preferences.set(previous); app.library.remove(book.id, false) }; file.delete()
        }
    }
}
