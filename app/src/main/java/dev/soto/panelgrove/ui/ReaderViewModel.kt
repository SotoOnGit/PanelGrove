package dev.soto.panelgrove.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import android.net.Uri
import androidx.lifecycle.viewModelScope
import dev.soto.panelgrove.PanelgroveApplication
import dev.soto.panelgrove.R
import dev.soto.panelgrove.core.*
import dev.soto.panelgrove.engine.DocumentSession
import dev.soto.panelgrove.storage.ExportedPage
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow

class ReaderViewModel(application: Application, private val saved: SavedStateHandle) : AndroidViewModel(application) {
    val app = application as PanelgroveApplication
    private val strings get() = app.strings
    val session = MutableStateFlow<DocumentSession?>(null)
    val page = MutableStateFlow(0)
    val offset = MutableStateFlow(0f)
    val message = MutableStateFlow<String?>(null)
    val loading = MutableStateFlow<String?>(null)
    val end = MutableStateFlow(false)
    val dimensions = MutableStateFlow<Map<String, Pair<Int, Int>>>(emptyMap())
    val statisticsPaused = MutableStateFlow(false)
    val statisticsIdle = MutableStateFlow(false)
    private var readingTracker: ReadingTracker? = null
    private var statisticsJob: Job? = null
    private var foreground = false
    private var blocked = false
    private var idleMinutes = 5
    private var shownPages = emptyList<Int>()
    private val readyPages = mutableSetOf<Int>()
    private var completionId: String? = null
    private var completed = false

    fun readingVisibility(resumed: Boolean, covered: Boolean, timeout: Int) {
        if (foreground == resumed && blocked == covered && idleMinutes == timeout) return
        foreground = resumed; blocked = covered; idleMinutes = timeout
        updateStatistics()
    }
    fun readingInteraction() { updateStatistics(interaction = true) }
    fun pauseStatistics() { statisticsPaused.value = !statisticsPaused.value; updateStatistics(interaction = true) }
    fun visiblePages(indices: List<Int>) {
        if (shownPages == indices) return
        shownPages = indices; updateStatistics()
    }
    fun pageReady(opened: DocumentSession, index: Int, ready: Boolean) {
        if (session.value !== opened) return
        if (ready) readyPages += index else readyPages -= index
        updateStatistics()
    }
    fun checkpointStatistics() { updateStatistics(flush = true) }
    fun statisticsTick() { updateStatistics() }
    private fun updateStatistics(interaction: Boolean = false, flush: Boolean = false) {
        val tracker = readingTracker ?: return
        val visible = shownPages.filter { it in readyPages }
        tracker.update(android.os.SystemClock.elapsedRealtime(), System.currentTimeMillis(), visible,
            foreground && !blocked && !statisticsPaused.value && visible.isNotEmpty(), interaction, idleMinutes)
        statisticsIdle.value = tracker.isIdle
        if (flush) {
            val records = tracker.checkpoint()
            if (records.isNotEmpty()) {
                val opened = session.value ?: return
                val chapters = opened.index.pages.indices.groupBy { tracker.chapters[it] }.filterKeys { it != null }
                    .map { (chapter, indices) -> chapter!! to indices.map { opened.index.pages[it].id }.toSet() }.toMap()
                val previous = statisticsJob
                statisticsJob = app.applicationScope.launch {
                    previous?.join()
                    try { app.library.saveReading(records, chapters) }
                    catch (error: Exception) { if (error is CancellationException) throw error; message.value = strings[R.string.statistics_save_failed] }
                }
            }
        }
    }
    fun readAgain() {
        completed = false; completionId = java.util.UUID.randomUUID().toString()
        val opened = session.value ?: return
        app.applicationScope.launch { app.library.change(opened.book.id) { it.copy(status = ReadingStatus.READING) } }
        move(0)
    }
    val preparingExport = MutableStateFlow(false)
    val pendingExport = saved.getStateFlow<String?>("pageExport", null)
    val exportDispatched: Boolean get() = saved["pageExportDispatched"] ?: false
    fun exportDispatched() { saved["pageExportDispatched"] = true }
    fun prepareExport(index: Int, share: Boolean) {
        val opened = session.value ?: return
        if (preparingExport.value || pendingExport.value != null || index !in opened.index.pages.indices) return
        val pageId = opened.index.pages[index].id
        preparingExport.value = true
        viewModelScope.launch {
            try {
                val book = app.library.book(opened.book.id) ?: opened.book
                val exported = app.pageExports.prepare(book, pageId, share)
                saved["pageExportDispatched"] = false
                saved["pageExport"] = PanelgroveJson.encodeToString(exported)
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                message.value = dev.soto.panelgrove.FailureMessages.user(error, strings[R.string.page_export_failed])
            } finally { preparingExport.value = false }
        }
    }
    fun exportResult(uri: Uri?) {
        val exported = pendingExport.value?.let { PanelgroveJson.decodeFromString<ExportedPage>(it) } ?: return
        saved["pageExport"] = null; saved["pageExportDispatched"] = false
        viewModelScope.launch {
            try {
                if (!exported.share && uri != null) { app.pageExports.save(exported, uri); message.value = strings[R.string.page_saved] }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                message.value = dev.soto.panelgrove.FailureMessages.user(error, strings[R.string.page_export_failed])
            } finally { if (!exported.share) app.pageExports.discard(exported) }
        }
    }
    private var job: Job? = null
    private var id: String? = null
    private var positionJob: Job? = null
    private var generation = 0

    fun open(bookId: String, force: Boolean = false) {
        if (!force && id == bookId && (session.value != null || job?.isActive == true)) return
        val request = ++generation
        save()
        readingTracker = null; readyPages.clear(); shownPages = emptyList(); completed = false
        completionId = java.util.UUID.randomUUID().toString(); statisticsIdle.value = false
        job?.cancel(); session.value?.let(app.engine::release); session.value = null
        id = bookId; end.value = false; message.value = null; dimensions.value = emptyMap()
        job = viewModelScope.launch {
            loading.value = strings[R.string.opening_your_book]
            var pending: DocumentSession? = null
            try {
                val opened = app.library.openSession(bookId) { if (request == generation) loading.value = it }
                pending = opened
                val position = app.library.book(bookId)?.position
                currentCoroutineContext().ensureActive()
                if (request != generation) return@launch
                page.value = position?.index?.coerceIn(opened.index.pages.indices) ?: 0
                offset.value = position?.offset ?: 0f
                session.value = opened
                readingTracker = ReadingTracker(ReadingSubject.from(opened.book), opened.index.pages)
                shownPages = listOf(page.value)
                updateStatistics(interaction = true)
                pending = null
                opened.index.warnings.forEach { android.util.Log.w("PanelgroveMetadata", it) }
                message.value = if (dev.soto.panelgrove.BuildConfig.DEBUG) opened.index.warnings.firstOrNull() else null
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                message.value = dev.soto.panelgrove.FailureMessages.user(error, strings[R.string.this_book_could_not_be_opened_check_the_file])
            } finally { pending?.let(app.engine::release); if (request == generation) loading.value = null }
        }
    }
    fun move(index: Int, fraction: Float = 0f) {
        val opened = session.value ?: return
        if (index >= opened.index.pages.size) { end.value = true; return }
        if (index < 0) return
        readingInteraction()
        page.value = index; offset.value = fraction.coerceIn(0f, 1f); end.value = false
        persist(opened, index, fraction)
    }
    fun measured(opened: DocumentSession, index: Int, width: Int, height: Int) {
        if (session.value !== opened) return
        val page = opened.index.pages[index]
        if ((page.width <= 0 || page.height <= 0) && dimensions.value[page.id] != (width to height))
            dimensions.value = dimensions.value + (page.id to (width to height))
    }
    fun bookmark() {
        val s = session.value ?: return; val p = page.value
        app.applicationScope.launch {
            try { app.library.toggleBookmark(s.book, s.index.pages[p], p) }
            catch (error: Exception) { if (error is CancellationException) throw error; message.value = strings[R.string.the_bookmark_could_not_be_saved_check_free_storage] }
        }
    }
    private fun persist(opened: DocumentSession, index: Int, fraction: Float) {
        val previous = positionJob
        positionJob = app.applicationScope.launch {
            previous?.join()
            try { app.library.position(opened.book.id, opened.index.pages[index], index, fraction) }
            catch (error: Exception) { if (error is CancellationException) throw error; message.value = strings[R.string.your_place_could_not_be_saved_check_free_storage] }
        }
    }
    fun finish() {
        val s = session.value ?: return
        if (completed) return
        completed = true
        checkpointStatistics()
        val previousStatistics = statisticsJob
        val finishId = completionId ?: java.util.UUID.randomUUID().toString()
        app.applicationScope.launch {
            previousStatistics?.join()
            try { app.library.finishReading(s.book.id, finishId) }
            catch (error: Exception) { if (error is CancellationException) throw error; message.value = strings[R.string.completion_could_not_be_saved_check_free_storage] }
        }
        end.value = true
    }
    fun save() { checkpointStatistics(); val s = session.value ?: return; persist(s, page.value, offset.value) }
    fun close() { save(); readingTracker = null; readyPages.clear(); ++generation; job?.cancel(); job = null; id = null; loading.value = null; session.value?.let(app.engine::release); session.value = null }
    override fun onCleared() { close() }
}
