package dev.soto.panelgrove

import android.net.Uri
import android.os.Debug
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.soto.panelgrove.core.*
import dev.soto.panelgrove.engine.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.RandomAccessFile

@RunWith(AndroidJUnit4::class)
class ResourceAcceptanceTest {
    private val app get() = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as PanelgroveApplication
    private suspend fun fixture(name: String): Book {
        val file = File(app.sources.managedDirectory, "resource-$name")
        InstrumentationRegistry.getInstrumentation().context.assets.open("fixtures/$name").use { input -> file.outputStream().use { input.copyTo(it) } }
        return app.library.importFile(Uri.parse("panelgrove://managed/${file.name}"))
    }
    @Test fun thousandPageTraversalRapidRegionsAndHugePageStayWithinCacheBudgets() = runBlocking {
        val book = fixture("thousand.cbz")
        var peakHeap = 0L; var peakPssKiB = 0; var bitmapPeak = 0; var diskPeak = 0L
        fun measure() {
            val stats = app.engine.cacheUsage()
            assertTrue("Bitmap cache ${stats.bitmapBytes} exceeds ${stats.bitmapBudget}", stats.bitmapBytes <= stats.bitmapBudget)
            assertTrue("Page cache ${stats.pageBytes} exceeds ${stats.pageBudget}", stats.pageBytes <= stats.pageBudget)
            bitmapPeak = maxOf(bitmapPeak, stats.bitmapBytes); diskPeak = maxOf(diskPeak, stats.pageBytes)
            val runtime = Runtime.getRuntime()
            peakHeap = maxOf(peakHeap, runtime.totalMemory() - runtime.freeMemory())
            val info = Debug.MemoryInfo(); Debug.getMemoryInfo(info); peakPssKiB = maxOf(peakPssKiB, info.totalPss)
        }
        val started = System.currentTimeMillis()
        app.engine.open(book).use { session ->
            for (page in 0 until 1000) {
                app.engine.render(session, page, 360, 520)
                if (page % 25 == 0) measure()
            }
            // Cancel queued zoom/scroll work and check that the final visible request still completes.
            coroutineScope {
                val requests = (0 until 50).map { i -> launch { app.engine.render(session, i, 1080, 1600, PageRegion(.1f, .1f, .8f, .9f)) } }
                requests.take(45).forEach { it.cancel() }; requests.joinAll()
            }
            measure()
        }
        val huge = fixture("huge-page.cbz")
        app.engine.open(huge).use { session ->
            assertEquals(20_000, session.index.pages.single().width)
            app.engine.render(session, 0, 2048, 3072)
            app.engine.render(session, 0, 2048, 3072, PageRegion(.2f, .2f, .25f, .25f))
            measure()
        }
        val stats = app.engine.cacheUsage()
        assertTrue(stats.largestBitmapBytes <= 32 * 1024 * 1024)
        val result = "1000 pages, 50 rapid requests, 20000x20000 page; elapsedMs=${System.currentTimeMillis() - started}; " +
            "peakObservedHeapBytes=$peakHeap; peakObservedPssKiB=$peakPssKiB; bitmapCachePeak=$bitmapPeak; pageCachePeak=$diskPeak; stats=$stats; stagingBytes=${app.sources.stagingBytes()}"
        android.util.Log.i("PanelgroveResources", result)
        File(app.getExternalFilesDir(null), "hardening-resources.txt").writeText(result)
    }
    @Test fun pageCacheEvictsOldFilesBeforeExceedingItsBudget() = runBlocking {
        val directory = File(app.cacheDir, "pages")
        val old = listOf(File(directory, "resource-eviction-a"), File(directory, "resource-eviction-b"))
        try {
            old.forEach { RandomAccessFile(it, "rw").use { out -> out.setLength(270L * 1024 * 1024) }; it.setLastModified(1) }
            val book = fixture("sample.cbz")
            app.engine.open(book).use { app.engine.render(it, 2, 361, 521) }
            assertTrue(app.engine.cacheUsage().pageBytes <= 512L * 1024 * 1024)
            assertFalse(old.all { it.exists() })
        } finally { old.forEach { it.delete() } }
    }
}
