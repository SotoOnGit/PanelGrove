package dev.soto.panelgrove.ui

import dev.soto.panelgrove.R
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import dev.soto.panelgrove.BuildConfig
import dev.soto.panelgrove.core.*
import dev.soto.panelgrove.storage.ImportWorker

@Composable fun SettingsScreen(preferences: AppPreferences, roots: List<LibrarySource>, books: List<Book>,
    model: LibraryViewModel, export: () -> Unit, restore: () -> Unit, relinkRoot: (String) -> Unit, relinkBook: (String) -> Unit) {
    val strings = rememberStrings()

    var options by remember { mutableStateOf(false) }
    var restoreConfirm by remember { mutableStateOf(false) }
    var licenses by remember { mutableStateOf(false) }
    LazyColumn(Modifier.fillMaxSize().testTag("settings-list"), contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SectionLabel(strings[R.string.appearance])
                ChoiceSegments(AppTheme.entries.map { it to strings.theme(it) }, preferences.theme) { theme -> model.updatePreferences { it.copy(theme = theme) } }
                SettingSwitch(strings[R.string.true_black], strings[R.string.pure_black_background_in_dark_mode], preferences.trueBlack) { value -> model.updatePreferences { it.copy(trueBlack = value) } }
                ActionRow(strings[R.string.default_reading_settings]) { options = true }
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SectionLabel(strings[R.string.files_storage])
                SettingSwitch(strings[R.string.copy_imported_files], strings[R.string.saved_in_your_folder_organized_by_series], preferences.copyImports, model::setCopyImports)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text(strings[R.string.import_folder])
                        Text(preferences.copyFolderUri?.let { model.app.sources.copies.displayLocation(it, preferences.copyFolderName) } ?: strings[R.string.no_import_folder_selected], style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Button({ model.requestCopyFolder() }, Modifier.semantics { contentDescription = if (preferences.copyFolderUri == null) strings[R.string.choose_import_folder] else strings[R.string.change_import_folder] }) { Text(if (preferences.copyFolderUri == null) strings[R.string.choose] else strings[R.string.change]) }
                }
                if (preferences.copyFolderUri != null) ActionRow(strings[R.string.find_existing_books], strings[R.string.find_existing_books_description]) { model.requestRestoreFolder() }
                if (books.any { it.managed }) ActionRow(strings[R.string.move_existing_copies_here], strings.quantity(R.plurals.managed_copy_count, books.count { it.managed }, books.count { it.managed })) { model.moveCopies() }
                Text(strings[R.string.copies_appear_in_your_file_manager_as_series_book], style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item { SectionLabel(strings[R.string.library_folders]) }
        if (roots.isEmpty()) item { Text(strings[R.string.no_folders_yet_add_one_from_library_to_enable], color = MaterialTheme.colorScheme.onSurfaceVariant) }
        items(roots, key = { it.id }) { root ->
            Column {
                Text(root.name)
                Text(if (root.access == AccessState.AVAILABLE) strings[R.string.linked_refreshed_when_you_return_to_panelgrove] else strings[R.string.folder_unavailable_grant_access_again], style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                FlowRow {
                    TextButton({ ImportWorker.scan(model.app, root.id) }) { Text(strings[R.string.rescan]) }
                    TextButton({ relinkRoot(root.id) }) { Text(strings[R.string.relink]) }
                    TextButton({ model.action { model.library.detachRoot(root.id) } }) { Text(strings[R.string.unlink]) }
                }
            }
        }
        val unavailable = books.filter { it.access != AccessState.AVAILABLE }
        if (unavailable.isNotEmpty()) {
            item { SectionLabel(strings[R.string.sources_to_relink]) }
            items(unavailable, key = { "missing:${it.id}" }) { book ->
                ActionRow(book.title, strings[R.string.reading_progress_is_safe]) { if (book.format == BookFormat.FOLDER && book.sourceId != null) relinkRoot(book.sourceId!!) else relinkBook(book.id) }
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SectionLabel(strings[R.string.statistics])
                Text(strings[R.string.statistics_idle_explanation], style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(strings[R.string.statistics_idle_timeout])
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(1, 3, 5, 10, 30).forEach { minutes ->
                        FilterChip(preferences.statisticsIdleMinutes == minutes,
                            { model.updatePreferences { it.copy(statisticsIdleMinutes = minutes) } },
                            { Text(strings[R.string.statistics_minutes, minutes]) })
                    }
                }
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SectionLabel(strings[R.string.backup_restore])
                Text(strings[R.string.saves_progress_bookmarks_collections_and_settings_manga_files_are], color = MaterialTheme.colorScheme.onSurfaceVariant)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(export) { Text(strings[R.string.export_backup]) }
                    OutlinedButton({ restoreConfirm = true }) { Text(strings[R.string.restore]) }
                }
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                SectionLabel(strings[R.string.about])
                Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(strings[R.string.app_name], style = MaterialTheme.typography.titleMedium)
                            Text(BuildConfig.VERSION_NAME, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(strings[R.string.free_and_open_source_gpl_3_0_or_later], color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(strings[R.string.about_panelgrove], style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                ActionRow(strings[R.string.open_source_licenses]) { licenses = true }
            }
        }
    }
    if (options) ReaderSettingsDialog(strings[R.string.default_reading_settings], preferences.reader, { options = false }, { settings -> model.updatePreferences { it.copy(reader = settings) } })
    if (restoreConfirm) AlertDialog(onDismissRequest = { restoreConfirm = false }, title = { Text(strings[R.string.restore_a_backup]) },
        text = { Text(strings[R.string.the_backup_will_be_merged_into_your_library_entries]) },
        confirmButton = { TextButton({ restoreConfirm = false; restore() }) { Text(strings[R.string.choose_backup]) } }, dismissButton = { TextButton({ restoreConfirm = false }) { Text(strings[R.string.cancel]) } })
    if (licenses) {
        val context = androidx.compose.ui.platform.LocalContext.current
        val text = remember { context.assets.open("THIRD_PARTY_NOTICES.txt").bufferedReader().use { it.readText() } }
        AlertDialog(onDismissRequest = { licenses = false }, title = { Text(strings[R.string.open_source_licenses]) }, text = {
            LazyColumn { item { Text(text, style = MaterialTheme.typography.bodySmall) } }
        }, confirmButton = { TextButton({ licenses = false }) { Text(strings[R.string.close]) } })
    }
}
