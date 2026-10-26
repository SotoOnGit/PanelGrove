package dev.soto.panelgrove.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.soto.panelgrove.PanelgroveApplication
import dev.soto.panelgrove.R
import dev.soto.panelgrove.core.*
import dev.soto.panelgrove.engine.DocumentSession
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow

class ReaderViewModel(application: Application) : AndroidViewModel(application) {
    val app = application as PanelgroveApplication
    private val strings get() = app.strings
    val session = MutableStateFlow<DocumentSession?>(null)
    val page = MutableStateFlow(0)
    val offset = MutableStateFlow(0f)
    val message = MutableStateFlow<String?>(null)
    val loading = MutableStateFlow<String?>(null)
    val end = MutableStateFlow(false)
    val dimensions = MutableStateFlow<Map<String, Pair<Int, Int>>>(emptyMap())
    private var job: Job? = null
    private var id: String? = null
    private var positionJob: Job? = null
    private var generation = 0

    fun open(bookId: String, force: Boolean = false) {
        if (!force && id == bookId && (session.value != null || job?.isActive == true)) return
        val request = ++generation
        save()
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
        app.applicationScope.launch {
            try { app.library.change(s.book.id) { it.copy(status = ReadingStatus.READ) } }
            catch (error: Exception) { if (error is CancellationException) throw error; message.value = strings[R.string.completion_could_not_be_saved_check_free_storage] }
        }
        end.value = true
    }
    fun save() { val s = session.value ?: return; persist(s, page.value, offset.value) }
    fun close() { save(); ++generation; job?.cancel(); job = null; id = null; loading.value = null; session.value?.let(app.engine::release); session.value = null }
    override fun onCleared() { save(); ++generation; job?.cancel(); session.value?.let(app.engine::release) }
}
