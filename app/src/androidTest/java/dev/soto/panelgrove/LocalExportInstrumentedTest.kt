package dev.soto.panelgrove

import android.net.Uri
import android.os.SystemClock
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Optional private acceptance sample, deliberately absent from public test assets. */
@RunWith(AndroidJUnit4::class)
class LocalExportInstrumentedTest {
    @Test fun existingTranslationVolumeRendersFirstChapterAndLastPages(): Unit = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("localExport") == "true")
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as PanelgroveApplication
        assumeTrue(File(app.sources.managedDirectory, "qa-volume.cbz").exists())
        val started = SystemClock.elapsedRealtime()
        val book = app.library.importFile(Uri.parse("panelgrove://managed/qa-volume.cbz"))
        val importMs = SystemClock.elapsedRealtime() - started
        app.engine.open(book).use { session ->
            assertEquals(157, session.index.pages.size)
            assertEquals("vol12/001", session.index.pages.first().id)
            assertEquals("vol12/157", session.index.pages.last().id)
            assertEquals(listOf("frontmatter", "45", "46", "47", "48", "backmatter"), session.index.pages.mapNotNull { it.chapter }.distinct())
            val first = SystemClock.elapsedRealtime()
            for (index in listOf(0, 38, 72, 110, 156)) assertTrue(app.engine.render(session, index, 1080, 1600).width > 0)
            val renderMs = SystemClock.elapsedRealtime() - first
            val warm = SystemClock.elapsedRealtime()
            repeat(20) { app.engine.render(session, 156, 1080, 1600) }
            val warmMs = SystemClock.elapsedRealtime() - warm
            Log.i("PanelgroveBenchmark", "Volume12 pages=157 importMs=$importMs fivePageRenderMs=$renderMs twentyCachedRendersMs=$warmMs")
            Unit
        }
    }
}
