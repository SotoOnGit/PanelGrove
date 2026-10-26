package dev.soto.panelgrove.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.work.WorkManager
import dev.soto.panelgrove.PanelgroveApplication
import dev.soto.panelgrove.FailureMessages
import dev.soto.panelgrove.BuildConfig
import dev.soto.panelgrove.R
import dev.soto.panelgrove.core.*
import dev.soto.panelgrove.data.book
import dev.soto.panelgrove.storage.ImportWorker
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID
import kotlinx.serialization.Serializable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Serializable private data class PendingImport(val uri: String, val copy: Boolean, val replace: String?, val openAfter: Boolean)

class LibraryViewModel(application: Application, private val saved: SavedStateHandle) : AndroidViewModel(application) {
    val app = application as PanelgroveApplication
    private val strings get() = app.strings
    val library = app.library
    val books = library.books.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val sources = library.roots.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val bookmarks = library.bookmarks.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val history = library.history.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val preferences = app.preferences.flow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppPreferences())
    val work = WorkManager.getInstance(app).getWorkInfosByTagFlow("library").stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    private val sessionStartedAt = System.currentTimeMillis()
    private val observedRunning = mutableSetOf<UUID>()
    val acknowledgedFailures = app.preferences.acknowledgedFailures.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val importFailures = work.map { jobs ->
        observedRunning += jobs.filter { !it.state.isFinished }.map { it.id }
        jobs.filter { it.state == androidx.work.WorkInfo.State.FAILED &&
            (BuildConfig.DEBUG || (!it.outputData.getBoolean("background", false) &&
                (it.id in observedRunning || it.outputData.getLong("requestedAt", 0) >= sessionStartedAt))) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    fun dismissFailure(id: UUID) = action { app.preferences.acknowledgeFailure(id.toString()) }
    val activeBook = saved.getStateFlow<String?>("activeBook", null)
    val message = app.restoreIssue
    val operation = MutableStateFlow<String?>(null)
    var replacingBook: String?
        get() = saved["replacingBook"]
        set(value) { saved["replacingBook"] = value }
    var replacingRoot: String?
        get() = saved["replacingRoot"]
        set(value) { saved["replacingRoot"] = value }
    val copyFolderRequested = saved.getStateFlow("copyFolderRequested", false)
    val pendingFolderRestore = app.preferences.pendingImportFolderRestore.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val restoreFolderRequested = combine(pendingFolderRestore, saved.getStateFlow("restoreFolderDeferred", false),
        saved.getStateFlow("restoreFolderPickerOpen", false)) { pending, deferred, picker -> pending != null && !deferred && !picker }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    private var pendingImports: List<PendingImport>
        get() = saved.get<String>("pendingCopyImports")?.let { PanelgroveJson.decodeFromString(it) } ?: emptyList()
        set(value) { saved["pendingCopyImports"] = PanelgroveJson.encodeToString(value) }
    private var lastScan = 0L
    fun open(id: String?) { saved["activeBook"] = id }
    fun notify(text: String) { message.value = text }
    fun action(block: suspend () -> Unit) = viewModelScope.launch {
        try { block() } catch (error: Exception) { if (error is CancellationException) throw error; notify(FailureMessages.user(error, strings[R.string.the_operation_could_not_be_completed_check_file_access])) }
    }
    init {
        action {
            val current = app.preferences.current()
            if (app.preferences.pendingImportFolderRestore.first() == null && current.copyImports &&
                !withContext(Dispatchers.IO) {
                    current.copyFolderUri?.let { uri -> runCatching {
                        val tree = dev.soto.panelgrove.storage.TreeAccess(app)
                        tree.children(tree.root(Uri.parse(uri)))
                    }.isSuccess } == true
                }) {
                // Also repair libraries restored by older versions that did not request grants.
                if (current.copyFolderUri != null && library.dao.books().any { it.book().managed }) requestRestoreFolder()
                else requestCopyFolder(enableImports = true)
            }
        }
    }
    fun beginRelinkBook(id: String) { replacingRoot = null; replacingBook = id }
    fun beginRelinkRoot(id: String?) { replacingBook = null; replacingRoot = id }
    fun relink(uri: Uri?) = action {
        val id = replacingBook
        replacingBook = null; replacingRoot = null
        if (uri == null || id == null) return@action
        check(app.sources.persist(uri)) { strings[R.string.android_did_not_grant_persistent_file_access] }
        watchImport(ImportWorker.file(app, uri, copy = false, replace = id, relink = true), openAfter = false)
    }
    fun requestRestoreFolder() = action {
        app.preferences.requestImportFolderRestore(app.preferences.current().copyFolderUri)
        saved["restoreFolderDeferred"] = false
        saved["copyFolderRequested"] = false
    }
    fun restoreFolderPickerOpened() { saved["restoreFolderPickerOpen"] = true }
    fun selectRestoreFolder(uri: Uri?) = action {
        saved["restoreFolderPickerOpen"] = false
        saved["restoreFolderDeferred"] = true
        if (uri == null) return@action
        withContext(Dispatchers.IO) {
            // Reading existing manga also works with a provider that grants read-only access.
            if (!app.sources.persist(uri, write = true)) check(app.sources.persist(uri)) { strings[R.string.android_did_not_grant_persistent_access_to_this_folder] }
            dev.soto.panelgrove.storage.TreeAccess(app).children(dev.soto.panelgrove.storage.TreeAccess(app).root(uri))
        }
        library.recoverRestore()
        val name = withContext(Dispatchers.IO) { app.sources.details(dev.soto.panelgrove.storage.TreeAccess(app).root(uri)).name }
        app.preferences.update { it.copy(copyFolderUri = uri.toString(), copyFolderName = name) }
        app.preferences.requestImportFolderRestore(uri.toString())
        watchImport(ImportWorker.restoreFolder(app, uri), openAfter = false)
    }
    fun import(uri: Uri, temporary: Boolean = false, openAfter: Boolean = false, forceCopy: Boolean = false, replace: String? = null) = action {
        val persisted = app.sources.persist(uri)
        val settings = app.preferences.current()
        val needsCopy = forceCopy || temporary || !persisted || settings.copyImports || library.retainsCopyFor(uri, replace)
        val request = PendingImport(uri.toString(), needsCopy, replace, openAfter)
        if (needsCopy && !withContext(Dispatchers.IO) { app.sources.copies.available(settings.copyFolderUri) }) {
            pendingImports = pendingImports + request
            requestCopyFolder()
        } else enqueue(request)
    }
    private fun enqueue(request: PendingImport) {
        val id = ImportWorker.file(app, Uri.parse(request.uri), request.copy, request.replace)
        if (request.openAfter) watchImport(id)
    }
    fun requestCopyFolder(enableImports: Boolean = false) {
        if (enableImports) saved["enableCopiesAfterSelection"] = true
        saved["copyFolderRequested"] = true
    }
    fun copyFolderPickerOpened() { saved["copyFolderRequested"] = false }
    fun setCopyImports(enabled: Boolean) = action {
        if (!enabled) app.preferences.update { it.copy(copyImports = false) }
        else {
            val current = app.preferences.current()
            if (withContext(Dispatchers.IO) { app.sources.copies.available(current.copyFolderUri) }) app.preferences.update { it.copy(copyImports = true) }
            else requestCopyFolder(enableImports = true)
        }
    }
    fun selectCopyFolder(uri: Uri?) = action {
        saved["copyFolderRequested"] = false
        val enable = saved.get<Boolean>("enableCopiesAfterSelection") == true
        saved["enableCopiesAfterSelection"] = false
        if (uri == null) {
            if (enable) app.preferences.update { it.copy(copyImports = false) }
            if (pendingImports.isNotEmpty()) notify(strings[R.string.import_cancelled_choose_a_copy_folder_before_importing_these])
            pendingImports = emptyList(); saved["moveCopiesAfterSelection"] = false
            return@action
        }
        try {
            val name = withContext(Dispatchers.IO) {
                check(app.sources.persist(uri, write = true)) { strings[R.string.android_did_not_grant_lasting_read_and_write_access] }
                app.sources.copies.checkWritable(uri)
            }
            library.recoverRestore()
            app.preferences.update { it.copy(copyFolderUri = uri.toString(), copyFolderName = name, copyImports = it.copyImports || enable) }
            val queued = pendingImports; pendingImports = emptyList(); queued.forEach(::enqueue)
            if (saved.get<Boolean>("moveCopiesAfterSelection") == true) ImportWorker.moveCopies(app)
            saved["moveCopiesAfterSelection"] = false
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            if (enable) saved["enableCopiesAfterSelection"] = true
            saved["copyFolderRequested"] = true
            throw error
        }
    }
    fun moveCopies() = action {
        if (withContext(Dispatchers.IO) { app.sources.copies.available(app.preferences.current().copyFolderUri) }) ImportWorker.moveCopies(app)
        else { saved["moveCopiesAfterSelection"] = true; requestCopyFolder() }
    }
    private fun watchImport(id: UUID, openAfter: Boolean = true) = viewModelScope.launch {
        val info = WorkManager.getInstance(app).getWorkInfoByIdFlow(id).filterNotNull().first { it.state.isFinished }
        if (openAfter) info.outputData.getString("bookId")?.let(::open)
        info.outputData.getString("message")?.let(::notify)
    }
    fun addFolder(uri: Uri?) = action {
        if (uri == null) { replacingRoot = null; replacingBook = null; return@action }
        check(app.sources.persist(uri)) { strings[R.string.android_did_not_grant_persistent_access_to_this_folder] }
        val root = library.addRoot(uri, replacingRoot); replacingRoot = null
        replacingBook?.let { id ->
            if (library.book(id)?.format == BookFormat.FOLDER)
                library.importFile(dev.soto.panelgrove.storage.TreeAccess(app).root(uri), root = root, folder = true, replaceId = id)
        }
        replacingBook = null
        ImportWorker.scan(app, root.id)
    }
    fun refresh(force: Boolean = false) = action {
        if (force || System.currentTimeMillis() - lastScan > 60_000) {
            lastScan = System.currentTimeMillis()
            library.dao.sources().forEach { ImportWorker.scan(app, it.id) }
        }
    }
    fun updatePreferences(transform: (AppPreferences) -> AppPreferences) = action { app.preferences.update(transform) }
    fun change(book: Book, transform: (Book) -> Book) = action { library.change(book.id, transform) }
    fun editMetadata(book: Book, transform: (Book) -> Book) = action { library.editMetadata(book.id, transform) }
    fun backup(uri: Uri, restore: Boolean) = action {
        operation.value = if (restore) strings[R.string.restoring_library] else strings[R.string.creating_backup]
        try {
            if (restore) {
                library.restoreBackup(uri)
                saved["restoreFolderDeferred"] = false
                saved["restoreFolderPickerOpen"] = false
                saved["copyFolderRequested"] = false
            } else library.exportBackup(uri)
            notify(if (restore) strings[R.string.backup_restored_relink_unavailable_folders_or_books_in_settings] else strings[R.string.backup_saved_manga_files_were_left_in_their_folders])
        } finally { operation.value = null }
    }
}
