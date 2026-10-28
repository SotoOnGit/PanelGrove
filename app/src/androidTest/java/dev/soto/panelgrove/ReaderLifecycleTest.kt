package dev.soto.panelgrove

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.soto.panelgrove.core.*
import dev.soto.panelgrove.data.*
import dev.soto.panelgrove.ui.ReaderViewModel
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class ReaderLifecycleTest {
    @Test fun rapidBookChangesKeepLatestSessionAndPersistPreviousPositionAndCompletion() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val app = instrumentation.targetContext.applicationContext as PanelgroveApplication
        suspend fun book(name: String): Book {
            val file = File(app.sources.managedDirectory, "lifecycle-$name.cbz")
            instrumentation.context.assets.open("fixtures/sample.cbz").use { input -> file.outputStream().use { input.copyTo(it) } }
            val book = app.library.importFile(Uri.parse("panelgrove://managed/${file.name}"))
            app.library.change(book.id) { it.copy(position = null, status = ReadingStatus.UNREAD) }
            app.library.dao.deleteBookmarks(book.id)
            return book
        }
        suspend fun await(condition: suspend () -> Boolean) = withTimeout(20_000) { while (!condition()) delay(10) }
        val first = book("first"); val second = book("second")
        lateinit var reader: ReaderViewModel
        instrumentation.runOnMainSync { reader = ReaderViewModel(app, androidx.lifecycle.SavedStateHandle()); reader.open(first.id) }
        await { reader.session.value?.book?.id == first.id }
        instrumentation.runOnMainSync { reader.move(2, .45f); reader.bookmark(); reader.finish(); reader.open(second.id) }
        await { reader.session.value?.book?.id == second.id && reader.loading.value == null }
        await { app.library.book(first.id)?.let { it.position == ReadingPosition("test/3", 2, .45f) && it.status == ReadingStatus.READ } == true }
        await { app.library.dao.bookmarks().map { it.bookmark() }.any { it.bookId == first.id && it.pageId == "test/3" } }
        instrumentation.runOnMainSync { repeat(20) { reader.open(first.id); reader.open(second.id) } }
        await { reader.session.value?.book?.id == second.id && reader.loading.value == null }
        instrumentation.runOnMainSync { reader.close(); reader.open("missing-generated-book") }
        await { reader.loading.value == null && reader.message.value != null }
        instrumentation.runOnMainSync { reader.open(first.id) }
        await { reader.session.value?.book?.id == first.id }
        assertEquals(2, reader.page.value); assertEquals(.45f, reader.offset.value, .001f)
        instrumentation.runOnMainSync { reader.close() }
    }
}
