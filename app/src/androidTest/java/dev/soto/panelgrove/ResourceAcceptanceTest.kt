package dev.soto.panelgrove

import android.content.ContextWrapper
import android.net.Uri
import android.os.Debug
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.soto.panelgrove.core.*
import dev.soto.panelgrove.engine.*
import dev.soto.panelgrove.storage.SourceAccess
import kotlinx.coroutines.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.RandomAccessFile
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class ResourceAcceptanceTest {
    private val app get() = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as PanelgroveApplication
    // A cold cache makes the traversal comparable regardless of test order or previous runs.
    private val testDirectory = File(app.cacheDir, "resource-test-${UUID.randomUUID()}")
    private val context = object : ContextWrapper(app) {
        override fun getFilesDir() = File(testDirectory, "files").apply { mkdirs() }
        override fun getCacheDir() = File(testDirectory, "cache").apply { mkdirs() }
    }
    private val sources = SourceAccess(context)
    private val engine = DocumentEngine(context, sources)
    @After fun cleanUp() {
        engine.clearMemory()
        assertTrue("Could not remove generated resource fixtures and cache", testDirectory.deleteRecursively())
    }
    private fun fixture(name: String): Book {
        val file = File(sources.managedDirectory, name)
        InstrumentationRegistry.getInstrumentation().context.assets.open("fixtures/$name").use { input -> file.outputStream().use { input.copyTo(it) } }
        val uri = Uri.parse("panelgrove://managed/${file.name}")
        return Book(UUID.randomUUID().toString(), uri.toString(), name, sources.format(uri)!!, revision = sources.details(uri).revision)
    }
    @Test fun thousandPageTraversalRapidRegionsAndHugePageStayWithinCacheBudgets() = runBlocking {
        val book = fixture("thousand.cbz")
        var peakHeap = 0L; var peakPssKiB = 0; var bitmapPeak = 0; var diskPeak = 0L
        fun measure() {
            val stats = engine.cacheUsage()
            assertTrue("Bitmap cache ${stats.bitmapBytes} exceeds ${stats.bitmapBudget}", stats.bitmapBytes <= stats.bitmapBudget)
            assertTrue("Page cache ${stats.pageBytes} exceeds ${stats.pageBudget}", stats.pageBytes <= stats.pageBudget)
            bitmapPeak = maxOf(bitmapPeak, stats.bitmapBytes); diskPeak = maxOf(diskPeak, stats.pageBytes)
            val runtime = Runtime.getRuntime()
            peakHeap = maxOf(peakHeap, runtime.totalMemory() - runtime.freeMemory())
            val info = Debug.MemoryInfo(); Debug.getMemoryInfo(info); peakPssKiB = maxOf(peakPssKiB, info.totalPss)
        }
        val started = System.currentTimeMillis()
        engine.open(book).use { session ->
            assertEquals(1000, session.index.pages.size)
            for (page in 0 until 1000) {
                engine.render(session, page, 360, 520)
                if (page % 25 == 0) measure()
                if (page % 250 == 249) android.util.Log.i("PanelgroveResources", "Traversed ${page + 1} pages in ${System.currentTimeMillis() - started} ms")
            }
            // Cancel queued zoom/scroll work and check that the final visible request still completes.
            coroutineScope {
                val requests = (0 until 50).map { i -> launch { engine.render(session, i, 1080, 1600, PageRegion(.1f, .1f, .8f, .9f)) } }
                requests.take(45).forEach { it.cancel() }; requests.joinAll()
            }
            measure()
        }
        val huge = fixture("huge-page.cbz")
        engine.open(huge).use { session ->
            assertEquals(20_000, session.index.pages.single().width)
            assertEquals(20_000, session.index.pages.single().height)
            engine.render(session, 0, 2048, 3072)
            engine.render(session, 0, 2048, 3072, PageRegion(.2f, .2f, .25f, .25f))
            measure()
        }
        val stats = engine.cacheUsage()
        assertTrue(stats.largestBitmapBytes <= 32 * 1024 * 1024)
        val result = "1000 pages, 50 rapid requests, 20000x20000 page; elapsedMs=${System.currentTimeMillis() - started}; " +
            "peakObservedHeapBytes=$peakHeap; peakObservedPssKiB=$peakPssKiB; bitmapCachePeak=$bitmapPeak; pageCachePeak=$diskPeak; stats=$stats; stagingBytes=${sources.stagingBytes()}"
        android.util.Log.i("PanelgroveResources", result)
        File(app.getExternalFilesDir(null), "hardening-resources.txt").writeText(result)
    }
    @Test fun pageCacheEvictsOldFilesBeforeExceedingItsBudget() = runBlocking {
        val directory = File(context.cacheDir, "pages")
        val old = listOf(File(directory, "resource-eviction-a"), File(directory, "resource-eviction-b"))
        try {
            old.forEach { RandomAccessFile(it, "rw").use { out -> out.setLength(270L * 1024 * 1024) }; it.setLastModified(1) }
            val book = fixture("sample.cbz")
            engine.open(book).use { engine.render(it, 2, 361, 521) }
            assertTrue(engine.cacheUsage().pageBytes <= 512L * 1024 * 1024)
            assertFalse(old.all { it.exists() })
        } finally { old.forEach { it.delete() } }
    }
}
