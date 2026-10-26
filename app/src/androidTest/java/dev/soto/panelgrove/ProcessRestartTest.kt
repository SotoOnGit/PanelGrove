package dev.soto.panelgrove

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.soto.panelgrove.core.*
import dev.soto.panelgrove.data.*
import dev.soto.panelgrove.ui.ReaderViewModel
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import android.os.Bundle
import android.provider.DocumentsContract

/** Invoked in two separate instrumentations by scripts/test-process-restart.ps1, with
 * an actual external force-stop between them. Ordinary suite execution skips both phases. */
@RunWith(AndroidJUnit4::class)
class ProcessRestartTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val app get() = instrumentation.targetContext.applicationContext as PanelgroveApplication
    private suspend fun await(condition: suspend () -> Boolean) = withTimeout(20_000) { while (!condition()) delay(20) }
    @Test fun prepare() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("panelgroveProcessPhase") == "prepare")
        File(app.filesDir, "hardening-process-preferences.json").writeText(PanelgroveJson.encodeToString(app.preferences.current()))
        val file = File(app.sources.managedDirectory, "hardening-process.cbz")
        instrumentation.context.assets.open("fixtures/sample.cbz").use { input -> file.outputStream().use { input.copyTo(it) } }
        val book = app.library.importFile(Uri.parse("panelgrove://managed/${file.name}"))
        app.library.dao.deleteBookmarks(book.id)
        lateinit var model: ReaderViewModel
        instrumentation.runOnMainSync { model = ReaderViewModel(app); model.open(book.id) }
        await { model.session.value != null }
        instrumentation.runOnMainSync { model.move(2, .375f); model.bookmark(); model.finish() }
        await { app.library.book(book.id)?.let { it.position == ReadingPosition("test/3", 2, .375f) && it.status == ReadingStatus.READ } == true && app.library.dao.bookmarks().any { it.bookId == book.id } }
        File(app.filesDir, "hardening-process-book.txt").writeText(book.id)
        val authority = "dev.soto.panelgrove.test.documents"
        app.contentResolver.call(Uri.parse("content://$authority"), "fixture", null, Bundle().apply {
            putString("id", "hardening/process-copies"); putBoolean("directory", true); putBoolean("grantTree", true)
        })
        val folder = DocumentsContract.buildTreeDocumentUri(authority, "hardening/process-copies")
        check(app.sources.persist(folder, write = true))
        val abandoned = app.sources.copies.publish(Uri.parse(book.uri), folder, "Interrupted", "Incomplete.cbz") {}
        File(app.filesDir, "hardening-process-abandoned.txt").writeText(abandoned.uri.toString())
        File(app.cacheDir, "copy-imports").apply { mkdirs() }.let { File(it, "interrupted-copy.cbz").writeText("Generated staging leftover") }
        // A committed DB restore with preferences still pending when the process is killed.
        app.library.dao.save(RestoreJournal(preferences = PanelgroveJson.encodeToString(AppPreferences(theme = AppTheme.DARK, reader = ReaderSettings(mode = ReadingMode.VERTICAL)))))
        listOf(File(app.filesDir, "books"), File(app.filesDir, "covers"), File(app.cacheDir, "sources"), File(app.cacheDir, "pages")).forEach {
            it.mkdirs(); File(it, "hardening-interrupted.part").writeText("Generated interrupted output")
        }
        val token = InstrumentationRegistry.getArguments().getString("panelgroveProcessToken") ?: error("The process-restart script must supply a token")
        File(app.filesDir, "hardening-process-ready.txt").writeText(token)
        // The external script terminates this still-running reader process at the saved boundary.
        delay(120_000)
    }
    @Test fun verify() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("panelgroveProcessPhase") == "verify")
        val id = File(app.filesDir, "hardening-process-book.txt").readText()
        await { app.library.dao.pendingRestore() == null }
        assertEquals(AppTheme.DARK, app.preferences.current().theme)
        assertEquals(ReadingMode.VERTICAL, app.preferences.current().reader.mode)
        assertFalse(File(app.cacheDir, "copy-imports/interrupted-copy.cbz").exists())
        val abandoned = Uri.parse(File(app.filesDir, "hardening-process-abandoned.txt").readText())
        // Preferences recovery finishes before provider cleanup in the startup coroutine.
        // Wait for the external file operation, rather than racing that background work.
        await { runCatching { app.sources.input(abandoned).use { } }.isFailure }
        listOf(File(app.filesDir, "books"), File(app.filesDir, "covers"), File(app.cacheDir, "sources"), File(app.cacheDir, "pages")).forEach {
            assertFalse(File(it, "hardening-interrupted.part").exists())
        }
        lateinit var model: ReaderViewModel
        instrumentation.runOnMainSync { model = ReaderViewModel(app); model.open(id) }
        await { model.session.value != null }
        assertEquals(2, model.page.value); assertEquals(.375f, model.offset.value, .001f)
        assertEquals("test/3", app.library.book(id)!!.position!!.pageId)
        assertEquals(ReadingStatus.READ, app.library.book(id)!!.status)
        assertTrue(app.library.dao.bookmarks().map { it.bookmark() }.any { it.bookId == id && it.pageId == "test/3" })
        instrumentation.runOnMainSync { model.close() }
        val original = File(app.filesDir, "hardening-process-preferences.json")
        app.preferences.set(PanelgroveJson.decodeFromString(original.readText()))
    }
}
