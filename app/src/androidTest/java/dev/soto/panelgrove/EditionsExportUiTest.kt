package dev.soto.panelgrove

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.core.app.ActivityOptionsCompat
import androidx.core.app.ActivityCompat
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import dev.soto.panelgrove.core.*
import dev.soto.panelgrove.storage.ExportedPage
import dev.soto.panelgrove.ui.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import java.io.File
import java.util.UUID

class EditionsExportUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val app get() = compose.activity.application as PanelgroveApplication
    private val libraryModel get() = ViewModelProvider(compose.activity)[LibraryViewModel::class.java]
    private val reader get() = ViewModelProvider(compose.activity)[ReaderViewModel::class.java]
    private val created = mutableListOf<Book>()
    private val files = mutableListOf<File>()
    private lateinit var previousPreferences: AppPreferences
    private val group = "Edition UI ${UUID.randomUUID()}"
    @Before fun setup() { previousPreferences = runBlocking { app.preferences.current() } }
    private fun snapshot(name: String) {
        compose.waitForIdle()
        // UiAutomation captures the native dialog window, whose fade follows Compose idleness.
        android.os.SystemClock.sleep(350)
        val directory = File(app.filesDir, "editions-export-validation").apply { mkdirs() }
        InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot().let { bitmap ->
            File(directory, "$name.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
    }
    private fun book(language: String, position: Int, number: String = "1"): Book = runBlocking {
        val file = File(app.sources.managedDirectory, "edition-ui-${UUID.randomUUID()}.cbz").also { files += it }
        InstrumentationRegistry.getInstrumentation().context.assets.open("fixtures/sample.cbz").use { input -> file.outputStream().use { input.copyTo(it) } }
        val imported = app.library.importFile(Uri.parse("panelgrove://managed/${file.name}"))
        app.library.change(imported.id) { it.copy(title = "Edition $language", series = group, number = number, language = language, metadataEdited = true,
            position = ReadingPosition("test/${position + 1}", position), settings = ReaderOverrides(direction = Direction.LTR, contrast = 1.7f, gamma = 1.5f)) }
        app.library.book(imported.id)!!.also { created += it }
    }
    private fun open(book: Book) {
        compose.runOnIdle { libraryModel.open(book.id) }
        compose.waitUntil(20_000) { compose.onAllNodesWithText("${book.position!!.index + 1} / 4").fetchSemanticsNodes().isNotEmpty() }
    }
    private fun exportDialog() {
        compose.onNodeWithContentDescription("Reader actions").performClick()
        compose.onNodeWithText("Export page").performClick()
        compose.onNodeWithText("Page to export").assertExists()
    }
    @After fun cleanup() {
        compose.runOnIdle { reader.close(); libraryModel.open(null) }
        compose.waitForIdle()
        runBlocking { created.forEach { app.library.remove(it.id, false) }; app.preferences.set(previousPreferences) }
        files.forEach { it.delete() }
    }

    @Test fun switchingEditionsRecreatesAtTheirOwnPositionsAndKeepsBookmarks() {
        val english = book("en", 1)
        val japanese = book("ja", 3)
        runBlocking { app.engine.open(english).use { app.library.toggleBookmark(english, it.index.pages[1], 1) } }
        open(english)
        compose.onNodeWithContentDescription("Reader actions").performClick()
        compose.onNodeWithText("Language editions").performClick()
        snapshot("language-editions")
        compose.onNodeWithText("Japanese (ja)").performClick()
        compose.waitUntil(20_000) { compose.onAllNodesWithText("4 / 4").fetchSemanticsNodes().isNotEmpty() }
        assertEquals(japanese.id, reader.session.value!!.book.id)
        compose.activityRule.scenario.recreate()
        compose.waitUntil(20_000) { compose.onAllNodesWithText("4 / 4").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("Reader actions").performClick()
        compose.onNodeWithText("Language editions").performClick()
        compose.onNodeWithText("English (en)").performClick()
        compose.waitUntil(20_000) { compose.onAllNodesWithText("2 / 4").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("Remove bookmark").assertExists()
        assertEquals(3, runBlocking { app.library.book(japanese.id)!!.position!!.index })
        assertEquals(1, runBlocking { app.library.dao.bookmarksForBook(english.id).size })
    }

    @Test fun pageSelectionValidationCancellationAndSaveUseSelectedOriginalImage() {
        val book = book("en", 0)
        val destinationId = "hardening/export-ui-${UUID.randomUUID()}.png"
        val destination = DocumentsContract.buildDocumentUri("dev.soto.panelgrove.test.documents", destinationId)
        app.contentResolver.call(Uri.parse("content://dev.soto.panelgrove.test.documents"), "fixture", null,
            Bundle().apply { putString("id", destinationId); putByteArray("bytes", ByteArray(0)) })
        var chosen: Uri? = null
        var launches = 0
        val registry = object : ActivityResultRegistry() {
            override fun <I, O> onLaunch(requestCode: Int, contract: ActivityResultContract<I, O>, input: I, options: ActivityOptionsCompat?) {
                val intent = input as Intent
                assertEquals(Intent.ACTION_CREATE_DOCUMENT, intent.action)
                assertEquals("image/png", intent.type)
                assertTrue(intent.getStringExtra(Intent.EXTRA_TITLE)!!.endsWith("page-003.png"))
                launches++
                dispatchResult(requestCode, if (chosen == null) Activity.RESULT_CANCELED else Activity.RESULT_OK, chosen?.let { Intent().setData(it) })
            }
        }
        val owner = object : ActivityResultRegistryOwner { override val activityResultRegistry = registry }
        compose.activity.setContent { CompositionLocalProvider(LocalActivityResultRegistryOwner provides owner) { PanelgroveApp(libraryModel) } }
        try {
            open(book)
            exportDialog()
            compose.onNodeWithText("Page to export").performTextReplacement("0")
            compose.onNodeWithText("Save image").assertIsNotEnabled()
            compose.onNodeWithText("Share page").assertIsNotEnabled()
            compose.onNodeWithText("Page to export").performTextReplacement("3")
            snapshot("export-page")
            compose.onNodeWithText("Save image").performClick()
            compose.waitUntil(20_000) { launches == 1 && reader.pendingExport.value == null && !reader.preparingExport.value }
            assertEquals(0, app.contentResolver.openInputStream(destination)!!.use { it.readBytes().size })
            chosen = destination
            exportDialog()
            compose.onNodeWithText("Page to export").performTextReplacement("3")
            compose.onNodeWithText("Save image").performClick()
            compose.waitUntil(20_000) { reader.message.value == "Page image saved." }
            val expected = runBlocking { app.engine.open(book).use { it.pageFile(2).readBytes() } }
            assertArrayEquals(expected, app.contentResolver.openInputStream(destination)!!.use { it.readBytes() })
            assertEquals(0, reader.page.value)
            assertEquals(2, launches)
        } finally {
            app.contentResolver.call(Uri.parse("content://dev.soto.panelgrove.test.documents"), "fixture", null,
                Bundle().apply { putString("id", destinationId); putBoolean("reset", true) })
        }
    }

    @Test fun stagedExportSurvivesRestoredReaderStateWithoutExportingAnotherBook() {
        val book = book("en", 0)
        val exported = runBlocking { app.pageExports.prepare(book, "test/2", false) }
        val state = SavedStateHandle(mapOf("pageExport" to PanelgroveJson.encodeToString(exported), "pageExportDispatched" to true))
        val restored = ReaderViewModel(app, state)
        assertTrue(restored.exportDispatched)
        assertEquals(exported, PanelgroveJson.decodeFromString<ExportedPage>(restored.pendingExport.value!!))
        compose.runOnIdle { restored.exportResult(null) }
        compose.waitUntil(10_000) { !app.pageExports.file(exported).exists() }
        assertNull(restored.pendingExport.value)
    }

    @Test fun androidDocumentPickerCancellationAfterRecreationKeepsTheSelectedSnapshot() {
        val book = book("en", 0)
        open(book)
        val originalActivity = compose.activity
        val originalReader = reader
        exportDialog()
        compose.onNodeWithText("Page to export").performTextReplacement("2")
        compose.onNodeWithText("Save image").performClick()
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        val previousFlags = automation.serviceInfo.flags
        automation.serviceInfo = automation.serviceInfo.apply {
            flags = flags or android.accessibilityservice.AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        }
        try {
            // The filename keyboard can be the active accessibility window on a fresh device.
            fun pickerVisible() = automation.windows.any { it.root?.packageName?.toString()?.endsWith("documentsui") == true }
            compose.waitUntil(20_000) { pickerVisible() }
            val pending = reader.pendingExport.value!!
            val exported = PanelgroveJson.decodeFromString<ExportedPage>(pending)
            assertTrue(app.pageExports.file(exported).isFile)
            assertTrue(reader.exportDispatched)
            // ActivityScenario.recreate first demands RESUMED, which a real external picker prevents.
            // AndroidX also handles the API 26/27 platform relaunch/stop ordering bugs.
            InstrumentationRegistry.getInstrumentation().runOnMainSync { ActivityCompat.recreate(originalActivity) }
            compose.waitUntil(10_000) { originalActivity.isDestroyed }
            assertTrue("The save picker must remain open while the reader is recreated", pickerVisible())
            assertEquals(pending, originalReader.pendingExport.value)
            assertTrue(originalReader.exportDispatched)
            repeat(3) {
                if (originalReader.pendingExport.value != null && pickerVisible()) {
                    android.os.ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand("input keyevent 4")).use { it.readBytes() }
                    android.os.SystemClock.sleep(350) // Back first dismisses the keyboard when present.
                }
            }
            compose.waitUntil(15_000) { reader.pendingExport.value == null && !app.pageExports.file(exported).exists() }
            assertEquals(book.id, libraryModel.activeBook.value)
            compose.waitUntil(20_000) { compose.onAllNodesWithText("1 / 4").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("1 / 4").assertExists()
        } finally {
            automation.serviceInfo = automation.serviceInfo.apply { flags = previousFlags }
        }
    }

    @Test fun manualLanguageAssignmentAndLibraryFilterKeepOnlyTheChosenEdition() {
        val english = book("en", 1)
        val japanese = book("en", 2)
        var dismissed = false
        compose.activity.setContent {
            val books by app.library.books.collectAsState(initial = emptyList())
            PanelgroveTheme(AppTheme.DARK) { books.find { it.id == japanese.id }?.let { BookDetails(it, libraryModel, { dismissed = true }, {}) } }
        }
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Language code").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Language code").performScrollTo().performTextReplacement("ja")
        compose.onNodeWithText("Save").performClick()
        compose.waitUntil(10_000) { dismissed && runBlocking { app.library.book(japanese.id)?.editionLanguage == "ja" } }
        assertEquals(2, runBlocking { app.library.book(japanese.id)!!.position!!.index })
        compose.activity.setContent { PanelgroveApp(libraryModel) }
        compose.onNodeWithContentDescription("Search your shelf").performTextInput(group)
        compose.onNodeWithContentDescription("Library options").performClick()
        compose.onNodeWithText("Filter by language").performClick()
        compose.onAllNodesWithText("Japanese (ja)").onLast().performClick()
        compose.onNodeWithTag("book-${japanese.id}").assertExists()
        compose.onNodeWithTag("book-${english.id}").assertDoesNotExist()
        compose.onNodeWithContentDescription("Clear library filters").performClick()
        val groupTag = "edition-group-${groupLanguageEditions(listOf(english, japanese)).single().key}"
        compose.onNodeWithTag(groupTag).assertExists()
        compose.onNodeWithTag("book-${english.id}").assertDoesNotExist()
    }

    @Test fun stackedGridAndListChooseVersionsWithProgressAndUpdateTheRepresentativeCover() {
        val english = book("en", 1)
        val japanese = book("ja", 3)
        val nextVolume = book("en", 0, "2")
        runBlocking {
            app.preferences.update { it.copy(grid = true) }
            app.library.change(english.id) { it.copy(status = ReadingStatus.READING, lastOpenedAt = 100) }
            app.library.change(japanese.id) { it.copy(status = ReadingStatus.READ, lastOpenedAt = 200) }
        }
        val groupTag = "edition-group-${groupLanguageEditions(listOf(english, japanese)).single().key}"
        compose.onNodeWithContentDescription("Search your shelf").performTextInput(group)
        compose.waitUntil(10_000) { compose.onAllNodesWithTag(groupTag).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("book-${english.id}").assertDoesNotExist()
        compose.onNodeWithTag("book-${japanese.id}").assertDoesNotExist()
        compose.onNodeWithTag("book-${nextVolume.id}").assertExists()
        val cover = hasContentDescription("Cover of Edition ja") and hasAnyAncestor(hasTestTag(groupTag))
        compose.waitUntil(10_000) { compose.onAllNodes(cover, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        snapshot("stacked-editions-grid")
        compose.onNodeWithTag(groupTag).performClick()
        compose.onNodeWithText("Choose version").assertIsDisplayed()
        compose.onNodeWithTag("edition-choice-${english.id}").assert(hasText("50% · Page 2 of 4"))
        compose.onNodeWithTag("edition-choice-${japanese.id}").assert(hasText("Finished"))
        snapshot("stacked-editions-picker")
        compose.activityRule.scenario.recreate()
        compose.waitUntil(15_000) { compose.onAllNodesWithText("Choose version").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("edition-choice-${english.id}").assert(hasText("50% · Page 2 of 4"))
        compose.onNode(hasContentDescription("Details for Edition en") and hasAnyAncestor(hasTestTag("edition-choice-${english.id}"))).performClick()
        compose.onNodeWithText("Book details").assertIsDisplayed()
        compose.onNodeWithText("Close").performClick()
        compose.onNodeWithTag(groupTag).performClick()
        compose.onNodeWithTag("edition-choice-${english.id}").performClick()
        compose.waitUntil(20_000) { compose.onAllNodesWithText("2 / 4").fetchSemanticsNodes().isNotEmpty() }
        assertEquals(english.id, reader.session.value!!.book.id)
        compose.onNodeWithContentDescription("Back to library").performClick()
        val englishCover = hasContentDescription("Cover of Edition en") and hasAnyAncestor(hasTestTag(groupTag))
        compose.waitUntil(10_000) { compose.onAllNodes(englishCover, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("Switch library layout").performClick()
        snapshot("stacked-editions-list")
        compose.onNodeWithTag(groupTag).performClick()
        compose.onNodeWithTag("edition-choice-${japanese.id}").performClick()
        compose.waitUntil(20_000) { compose.onAllNodesWithText("4 / 4").fetchSemanticsNodes().isNotEmpty() }
        assertEquals(japanese.id, reader.session.value!!.book.id)
        assertEquals(1, runBlocking { app.library.book(english.id)!!.position!!.index })
    }
}
