package dev.soto.panelgrove.ui

import dev.soto.panelgrove.R
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.work.WorkManager
import dev.soto.panelgrove.core.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun PanelgroveApp(model: LibraryViewModel) {
    val strings = rememberStrings()

    val preferences by model.preferences.collectAsStateWithLifecycle()
    val active by model.activeBook.collectAsStateWithLifecycle()
    PanelgroveTheme(preferences.theme, preferences.trueBlack) {
        if (active != null) {
            val reader: ReaderViewModel = viewModel()
            ReaderScreen(active!!, reader, model) { model.open(null) }
            return@PanelgroveTheme
        }
        val books by model.books.collectAsStateWithLifecycle()
        val roots by model.sources.collectAsStateWithLifecycle()
        val history by model.history.collectAsStateWithLifecycle()
        val work by model.work.collectAsStateWithLifecycle()
        val operation by model.operation.collectAsStateWithLifecycle()
        val message by model.message.collectAsStateWithLifecycle()
        val copyFolderRequested by model.copyFolderRequested.collectAsStateWithLifecycle()
        val restoreFolderRequested by model.restoreFolderRequested.collectAsStateWithLifecycle()
        val context = LocalContext.current
        var tab by rememberSaveable { mutableIntStateOf(0) }
        var add by remember { mutableStateOf(false) }
        var details by rememberSaveable { mutableStateOf<String?>(null) }
        val snackbar = remember { SnackbarHostState() }
        val pickFiles = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris -> uris.forEach { model.import(it) } }
        val pickRelink = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { model.relink(it) }
        val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { model.addFolder(it) }
        val pickCopyFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { model.selectCopyFolder(it) }
        val pickRestoreFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { model.selectRestoreFolder(it) }
        val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { it?.let { model.backup(it, false) } }
        val restore = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let { model.backup(it, true) } }
        LaunchedEffect(message) { message?.let { snackbar.showSnackbar(it); model.message.value = null } }
        val icons = listOf(Icons.Default.LibraryBooks, Icons.Default.History, Icons.Default.LightMode)
        val labels = listOf(strings[R.string.library], strings[R.string.history], strings[R.string.settings])
        var clearHistory by remember { mutableStateOf(false) }
        val tasks = work.filter { !it.state.isFinished }
        val failed by model.importFailures.collectAsStateWithLifecycle()
        val acknowledged by model.acknowledgedFailures.collectAsStateWithLifecycle()
        var dismissedErrors by rememberSaveable { mutableStateOf(listOf<String>()) }
        val error = if (acknowledged == null) null else failed.firstOrNull { it.id.toString() !in dismissedErrors && it.id.toString() !in acknowledged!! }
        BoxWithConstraints {
            val wide = maxWidth >= 840.dp
            Row {
                if (wide) NavigationRail(Modifier.fillMaxHeight().padding(top = 24.dp)) {
                    Icon(Icons.Default.AutoStories, strings[R.string.app_name], Modifier.padding(16.dp))
                    Spacer(Modifier.height(24.dp))
                    labels.forEachIndexed { i, label -> NavigationRailItem(tab == i, { tab = i }, { Icon(icons[i], label) }, label = { Text(label) }) }
                }
                Scaffold(Modifier.weight(1f), snackbarHost = { SnackbarHost(snackbar) },
                    topBar = { if (tab != 0) Surface {
                        Row(Modifier.statusBarsPadding().fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 24.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            Text(labels[tab], Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                            if (tab == 1) IconButton({ clearHistory = true }, enabled = history.isNotEmpty()) { Icon(Icons.Default.DeleteOutline, strings[R.string.clear_history]) }
                        }
                    } },
                    bottomBar = { if (!wide) Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
                        Row(Modifier.fillMaxWidth().navigationBarsPadding().height(if (androidx.compose.ui.platform.LocalDensity.current.fontScale > 1.3f) 80.dp else 64.dp)) {
                            labels.forEachIndexed { i, label -> NavigationBarItem(tab == i, { tab = i },
                                { Icon(icons[i], label) }, label = { Text(label) }, alwaysShowLabel = false,
                                modifier = Modifier.heightIn(min = 64.dp),
                                colors = NavigationBarItemDefaults.colors(indicatorColor = MaterialTheme.colorScheme.primaryContainer)) }
                        }
                    } },
                    floatingActionButton = { if (tab == 0 && books.isNotEmpty()) FloatingActionButton(onClick = { add = true },
                        modifier = Modifier.size(56.dp), shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
                        containerColor = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer) {
                        Icon(Icons.Default.Add, strings[R.string.add_manga])
                    } },
                ) { padding ->
                    Column(Modifier.fillMaxSize().padding(padding)) {
                        if (operation != null || tasks.isNotEmpty()) {
                            Surface(color = MaterialTheme.colorScheme.primaryContainer) {
                                Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp)) {
                                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                        Text(operation ?: tasks.first().progress.getString("message") ?: strings[R.string.preparing_your_library], Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                                        if (tasks.isNotEmpty()) TextButton({ tasks.forEach { WorkManager.getInstance(context).cancelWorkById(it.id) } }) { Text(strings[R.string.cancel]) }
                                    }
                                    LinearProgressIndicator(Modifier.fillMaxWidth())
                                }
                            }
                        }
                        when (tab) {
                            0 -> LibraryScreen(books, preferences.grid, model, { details = it },
                                { pickFiles.launch(arrayOf("*/*")) }, { model.beginRelinkRoot(null); pickFolder.launch(null) })
                            1 -> HistoryScreen(history, books, model, { details = it })
                            2 -> SettingsScreen(preferences, roots, books, model,
                                { export.launch(strings[R.string.backup_filename]) }, { restore.launch(arrayOf("application/json", "application/octet-stream")) },
                                { id -> model.beginRelinkRoot(id); pickFolder.launch(null) },
                                { id -> model.beginRelinkBook(id)
                                    if (books.find { it.id == id }?.format == BookFormat.FOLDER) pickFolder.launch(null)
                                    else pickRelink.launch(arrayOf("*/*")) })
                        }
                    }
                }
            }
        }
        if (add) ModalBottomSheet(onDismissRequest = { add = false }) {
            Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SheetTitle(strings[R.string.add_manga])
                Text(strings[R.string.cbz_cbr_pdf_image_folders], color = MaterialTheme.colorScheme.onSurfaceVariant)
                Button({ add = false; pickFiles.launch(arrayOf("*/*")) }, Modifier.fillMaxWidth()) { Icon(Icons.Default.InsertDriveFile, null); Spacer(Modifier.width(8.dp)); Text(strings[R.string.choose_files]) }
                OutlinedButton({ add = false; model.beginRelinkRoot(null); pickFolder.launch(null) }, Modifier.fillMaxWidth()) { Icon(Icons.Default.FolderOpen, null); Spacer(Modifier.width(8.dp)); Text(strings[R.string.add_a_library_folder]) }
                Text(strings[R.string.files_stay_in_their_folders_unless_you_choose_to], style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(20.dp))
            }
        }
        if (clearHistory) AlertDialog(onDismissRequest = { clearHistory = false }, title = { Text(strings[R.string.clear_reading_history]) },
            text = { Text(strings[R.string.reading_progress_and_bookmarks_will_be_kept]) },
            confirmButton = { TextButton({ model.action { model.library.dao.clearHistory() }; clearHistory = false }) { Text(strings[R.string.clear_history]) } },
            dismissButton = { TextButton({ clearHistory = false }) { Text(strings[R.string.cancel]) } })
        if (restoreFolderRequested) AlertDialog(onDismissRequest = { model.selectRestoreFolder(null) },
            title = { Text(strings[R.string.restore_import_folder]) },
            text = { Text(strings[R.string.restore_import_folder_explanation,
                preferences.copyFolderName ?: strings[R.string.import_folder]]) },
            confirmButton = { TextButton({ model.restoreFolderPickerOpened(); pickRestoreFolder.launch(preferences.copyFolderUri?.let(android.net.Uri::parse)) }) { Text(strings[R.string.choose_folder]) } },
            dismissButton = { TextButton({ model.selectRestoreFolder(null) }) { Text(strings[R.string.later]) } })
        if (copyFolderRequested && !restoreFolderRequested) AlertDialog(onDismissRequest = { model.selectCopyFolder(null) },
            title = { Text(strings[R.string.choose_an_import_folder]) },
            text = { Text(strings[R.string.choose_a_folder_on_your_device_for_manga_copies]) },
            confirmButton = { TextButton({ model.copyFolderPickerOpened(); pickCopyFolder.launch(preferences.copyFolderUri?.let(android.net.Uri::parse)) }) { Text(strings[R.string.choose_folder]) } },
            dismissButton = { TextButton({ model.selectCopyFolder(null) }) { Text(strings[R.string.cancel]) } })
        books.find { it.id == details }?.let { book -> BookDetails(book, model, { details = null },
            { if (book.format == BookFormat.FOLDER) { if (book.sourceId != null) model.beginRelinkRoot(book.sourceId) else model.beginRelinkBook(book.id); pickFolder.launch(null) }
                else { model.beginRelinkBook(book.id); pickRelink.launch(arrayOf("*/*")) }; details = null }) }
        fun dismissError() { error?.let { dismissedErrors += it.id.toString(); model.dismissFailure(it.id) } }
        if (error != null) AlertDialog(onDismissRequest = { dismissError() }, title = { Text(strings[R.string.import_needs_attention]) },
            text = { Text(error.outputData.getString("message") ?: strings[R.string.the_file_could_not_be_imported]) },
            confirmButton = { TextButton({ dismissError() }) { Text(strings[R.string.close]) } })
    }
}
