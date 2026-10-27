package dev.soto.panelgrove.ui

import dev.soto.panelgrove.R
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.soto.panelgrove.core.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Date

@Composable fun BookCover(book: Book, model: LibraryViewModel, modifier: Modifier = Modifier) {
    val strings = rememberStrings()

    val cover by produceState<android.graphics.Bitmap?>(null, book.id, book.revision, book.customCover) {
        value = withContext(Dispatchers.IO) { BitmapFactory.decodeFile(model.library.cover(book).path) }
    }
    Box(modifier.clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh)) {
        if (cover != null) Image(cover!!.asImageBitmap(), strings[R.string.cover_of, book.title], Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else Column(Modifier.fillMaxSize().padding(8.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Icon(Icons.Default.AutoStories, null, tint = MaterialTheme.colorScheme.primary)
            Text(book.title, style = MaterialTheme.typography.labelMedium, maxLines = 4, overflow = TextOverflow.Ellipsis)
        }
        if (book.favorite) Icon(Icons.Default.Favorite, strings[R.string.favorite], Modifier.align(Alignment.TopEnd).padding(6.dp).size(20.dp), tint = MaterialTheme.colorScheme.tertiary)
        if (book.status != ReadingStatus.UNREAD) LinearProgressIndicator(progress = { bookProgress(book) },
            trackColor = MaterialTheme.colorScheme.outlineVariant,
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(3.dp), gapSize = 0.dp, drawStopIndicator = {})
    }
}
private fun bookProgress(book: Book) = if (book.status == ReadingStatus.READ) 1f else
    (((book.position?.index ?: 0) + 1).toFloat() / book.pageCount.coerceAtLeast(1)).coerceIn(0f, 1f)

private val bookOrder = Comparator<Book> { a, b ->
    NaturalOrder.compare(a.series, b.series).takeIf { it != 0 }
        ?: NaturalOrder.compare(a.number, b.number).takeIf { it != 0 }
        ?: NaturalOrder.compare(a.title, b.title).takeIf { it != 0 } ?: a.id.compareTo(b.id)
}

private enum class LibraryFilter(val label: Int) { ALL(R.string.all), READING(R.string.reading), UNREAD(R.string.unread), FINISHED(R.string.finished), FAVORITES(R.string.favorites) }

@Composable fun LibraryScreen(books: List<Book>, grid: Boolean, model: LibraryViewModel, details: (String) -> Unit, chooseFiles: () -> Unit, addFolder: () -> Unit) {
    val strings = rememberStrings()

    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf(LibraryFilter.ALL) }
    var series by rememberSaveable { mutableStateOf("") }
    var collection by rememberSaveable { mutableStateOf("") }
    var overflow by remember { mutableStateOf(false) }
    var seriesMenu by remember { mutableStateOf(false) }
    var collectionMenu by remember { mutableStateOf(false) }
    val visible = books.filter { book ->
        (query.isBlank() || listOf(book.title, book.series, book.displayName).any { it.contains(query, true) }) &&
            (series.isBlank() || book.series == series) && (collection.isBlank() || collection in book.collections) &&
            when (filter) { LibraryFilter.FAVORITES -> book.favorite; LibraryFilter.READING -> book.status == ReadingStatus.READING; LibraryFilter.UNREAD -> book.status == ReadingStatus.UNREAD; LibraryFilter.FINISHED -> book.status == ReadingStatus.READ; else -> true }
    }.sortedWith(bookOrder)
    val continuing = books.filter { it.status == ReadingStatus.READING }.maxByOrNull { it.lastOpenedAt }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            BasicTextField(query, { query = it }, Modifier.weight(1f).heightIn(min = 52.dp)
                .clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .semantics { contentDescription = strings[R.string.search_your_shelf] }, singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary), decorationBox = { field ->
                    Row(Modifier.padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Search, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Box(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                            if (query.isEmpty()) Text(strings[R.string.search_your_shelf], color = MaterialTheme.colorScheme.onSurfaceVariant)
                            field()
                        }
                        if (books.isNotEmpty()) IconButton({ model.updatePreferences { it.copy(grid = !it.grid) } }) {
                            Icon(if (grid) Icons.AutoMirrored.Filled.List else Icons.Default.GridView, strings[R.string.switch_library_layout])
                        }
                    }
                })
            if (books.isNotEmpty()) Box {
                IconButton({ overflow = true }) { Icon(Icons.Default.MoreVert, strings[R.string.library_options]) }
                DropdownMenu(overflow, { overflow = false }) {
                    DropdownMenuItem(text = { Text(strings[R.string.rescan_library_folders]) }, onClick = { overflow = false; model.refresh(true) })
                    DropdownMenuItem(text = { Text(strings[R.string.filter_by_series]) }, onClick = { overflow = false; seriesMenu = true })
                    DropdownMenuItem(text = { Text(strings[R.string.filter_by_collection]) }, onClick = { overflow = false; collectionMenu = true })
                }
            }
        }
        if (books.isEmpty()) EmptyLibrary(chooseFiles, addFolder)
        else {
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LibraryFilter.entries.forEach { choice ->
                    val label = strings[choice.label]
                    FilterChip(filter == choice, { filter = choice }, { Text(label) }, shape = RoundedCornerShape(9.dp),
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer))
                }
            }
            if (series.isNotBlank() || collection.isNotBlank()) Row(Modifier.padding(horizontal = 16.dp)) {
                InputChip(series.isNotBlank(), { seriesMenu = true }, { Text(series.ifBlank { strings[R.string.all_series] }) })
                Spacer(Modifier.width(8.dp))
                InputChip(collection.isNotBlank(), { collectionMenu = true }, { Text(collection.ifBlank { strings[R.string.all_collections] }) })
                IconButton({ series = ""; collection = "" }) { Icon(Icons.Default.Close, strings[R.string.clear_series_and_collection_filters]) }
            }
            if (grid) LazyVerticalGrid(GridCells.Fixed(3), Modifier.weight(1f).testTag("library-shelf"),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 88.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                if (continuing != null && query.isBlank() && filter == LibraryFilter.ALL && series.isBlank() && collection.isBlank())
                    item(key = "continue", span = { GridItemSpan(maxLineSpan) }) { ContinueReading(continuing, model) }
                if (visible.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) { Text(strings[R.string.no_books_match_these_filters], Modifier.padding(vertical = 32.dp)) }
                visible.groupBy { it.series }.forEach { (name, grouped) ->
                    item(key = "series:${name}", span = { GridItemSpan(maxLineSpan) }) { Text(name.ifBlank { strings[R.string.unsorted] }, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 4.dp)) }
                    items(grouped, key = { it.id }) { BookCard(it, model, details) }
                }
            } else LazyColumn(Modifier.weight(1f).testTag("library-shelf"), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 88.dp)) {
                if (continuing != null && query.isBlank() && filter == LibraryFilter.ALL && series.isBlank() && collection.isBlank()) item { ContinueReading(continuing, model) }
                if (visible.isEmpty()) item { Text(strings[R.string.no_books_match_these_filters], Modifier.padding(vertical = 32.dp)) }
                visible.groupBy { it.series }.forEach { (name, grouped) ->
                    item(key = "series:${name}") { Text(name.ifBlank { strings[R.string.unsorted] }, Modifier.padding(vertical = 16.dp), style = MaterialTheme.typography.titleMedium) }
                    items(grouped, key = { it.id }) { BookListRow(it, model, details) }
                }
            }
        }
    }
    if (seriesMenu) ChoiceDialog(strings[R.string.series], listOf("") + books.map { it.series }.filter { it.isNotBlank() }.distinct().sortedWith(NaturalOrder), strings[R.string.all_series], { seriesMenu = false }) { series = it }
    if (collectionMenu) ChoiceDialog(strings[R.string.collections], listOf("") + books.flatMap { it.collections }.distinct().sortedWith(NaturalOrder), strings[R.string.all_collections], { collectionMenu = false }) { collection = it }
}

@Composable private fun ContinueReading(book: Book, model: LibraryViewModel) {
    val strings = rememberStrings()

    Card(onClick = { model.open(book.id) }, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer), shape = RoundedCornerShape(16.dp)) {
        Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            BookCover(book, model, Modifier.width(56.dp).aspectRatio(2f / 3f))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                SectionLabel(strings[R.string.continue_reading])
                Text(book.title, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(strings[R.string.page_of, (book.position?.index ?: 0) + 1, book.pageCount], style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                LinearProgressIndicator(progress = { bookProgress(book) }, modifier = Modifier.fillMaxWidth().height(4.dp), trackColor = MaterialTheme.colorScheme.outlineVariant, gapSize = 0.dp, drawStopIndicator = {})
            }
        }
    }
}

@Composable private fun ChoiceDialog(title: String, choices: List<String>, empty: String, dismiss: () -> Unit, choose: (String) -> Unit) {
    val strings = rememberStrings()

    AlertDialog(onDismissRequest = dismiss, title = { Text(title) }, text = { LazyColumn { items(choices) { choice ->
        TextButton({ choose(choice); dismiss() }, Modifier.fillMaxWidth()) { Text(choice.ifBlank { empty }) }
    } } }, confirmButton = { TextButton(dismiss) { Text(strings[R.string.close]) } })
}

@Composable fun EmptyLibrary(chooseFiles: () -> Unit, addFolder: () -> Unit) {
    val strings = rememberStrings()

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 32.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically)) {
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
            Icon(Icons.Default.LibraryBooks, null, Modifier.padding(28.dp).size(40.dp), tint = MaterialTheme.colorScheme.primary)
        }
        Spacer(Modifier.height(8.dp))
        Text(strings[R.string.your_shelf_is_empty], style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
        Text(strings[R.string.add_manga_files_or_a_whole_folder_your_files], color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        Spacer(Modifier.height(4.dp))
        Button(chooseFiles, Modifier.fillMaxWidth()) { Text(strings[R.string.choose_files]) }
        OutlinedButton(addFolder, Modifier.fillMaxWidth()) { Text(strings[R.string.add_a_folder]) }
        Text(strings[R.string.cbz_cbr_pdf_and_image_folders], style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable private fun BookCard(book: Book, model: LibraryViewModel, details: (String) -> Unit) {
    val strings = rememberStrings()

    Column(Modifier.testTag(strings[R.string.book, book.id]).combinedClickable(onClick = { model.open(book.id) }, onLongClickLabel = strings[R.string.book_details], onLongClick = { details(book.id) })) {
        BookCover(book, model, Modifier.fillMaxWidth().aspectRatio(2f / 3f))
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f).padding(top = 8.dp)) {
                Text(book.title, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(if (book.access != AccessState.AVAILABLE) strings[R.string.relink_source] else strings[R.string.book_page_format, book.pageCount, strings.format(book.format)], style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
@Composable fun BookListRow(book: Book, model: LibraryViewModel, details: (String) -> Unit) {
    val strings = rememberStrings()

    Row(Modifier.fillMaxWidth().clickable { model.open(book.id) }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        BookCover(book, model, Modifier.width(52.dp).aspectRatio(2f / 3f))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(book.title, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(if (book.access != AccessState.AVAILABLE) strings[R.string.source_unavailable_relink_in_details] else strings[R.string.book_page_format, book.pageCount, strings.format(book.format)], style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        IconButton({ details(book.id) }) { Icon(Icons.Default.MoreVert, strings[R.string.details_for, book.title]) }
    }
}

@Composable fun HistoryScreen(history: List<HistoryEntry>, books: List<Book>, model: LibraryViewModel, details: (String) -> Unit) {
    val strings = rememberStrings()

    val zone = ZoneId.systemDefault()
    val today = LocalDate.now(zone)
    val days = history.sortedByDescending { it.openedAt }.groupBy { Instant.ofEpochMilli(it.openedAt).atZone(zone).toLocalDate() }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 24.dp, end = 24.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (history.isEmpty()) item { Text(strings[R.string.the_books_you_open_will_appear_here], Modifier.padding(vertical = 24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
        days.forEach { (day, entries) ->
            item(key = "day:${day}") { SectionLabel(when (day) { today -> strings[R.string.today]; today.minusDays(1) -> strings[R.string.yesterday]; else -> day.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)) }) }
            entries.groupBy { it.bookId }.forEach { (id, sessions) -> books.find { it.id == id }?.let { book ->
                item(key = "$day:$id") {
                    Row(Modifier.fillMaxWidth().testTag("history-${book.id}-${day}").clickable { model.open(book.id) }, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        BookCover(book, model, Modifier.width(44.dp).aspectRatio(2f / 3f))
                        Column(Modifier.weight(1f)) {
                            Text(book.title, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(strings.quantity(R.plurals.reading_session_count, sessions.size, DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(sessions.maxOf { it.openedAt })), sessions.size), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton({ details(book.id) }) { Icon(Icons.Default.MoreVert, strings[R.string.details_for, book.title]) }
                    }
                }
            } }
        }
    }
}

@Composable fun BookDetails(book: Book, model: LibraryViewModel, dismiss: () -> Unit, relink: () -> Unit) {
    val strings = rememberStrings()

    var title by rememberSaveable(book.id) { mutableStateOf(book.title) }
    var series by rememberSaveable(book.id) { mutableStateOf(book.series) }
    var number by rememberSaveable(book.id) { mutableStateOf(book.number) }
    var collections by rememberSaveable(book.id) { mutableStateOf(book.collections.joinToString(", ")) }
    var deleting by remember { mutableStateOf(false) }
    var deleteCopy by remember { mutableStateOf(false) }
    var seriesOptions by remember { mutableStateOf(false) }
    val chooseCover = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) model.customCover(book, uri)
    }
    val preferences by model.preferences.collectAsStateWithLifecycle()
    PanelgroveSheet(dismiss, footer = {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
            TextButton(dismiss) { Text(strings[R.string.close]) }
            Spacer(Modifier.width(16.dp))
            Button({ model.editMetadata(book) { it.copy(title = title.trim().ifBlank { book.title }, series = series.trim(), number = number.trim(), collections = collections.split(',').map(String::trim).filter(String::isNotEmpty).distinct(), metadataEdited = true) }; dismiss() }) { Text(strings[R.string.save]) }
        }
    }) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            BookCover(book, model, Modifier.width(56.dp).aspectRatio(2f / 3f))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SheetTitle(strings[R.string.book_details])
                Text(strings[R.string.book_file_summary, book.pageCount, strings.format(book.format), book.displayName], style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton({ chooseCover.launch(arrayOf("image/*")) }) { Text(strings[R.string.choose_custom_cover]) }
            if (book.customCover != null) TextButton({ model.customCover(book, null) }) { Text(strings[R.string.remove_custom_cover]) }
        }
        OutlinedTextField(title, { title = it }, Modifier.fillMaxWidth(), label = { Text(strings[R.string.title]) }, singleLine = true, shape = RoundedCornerShape(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(series, { series = it }, Modifier.weight(1.7f), label = { Text(strings[R.string.series]) }, singleLine = true, shape = RoundedCornerShape(12.dp))
            OutlinedTextField(number, { number = it }, Modifier.weight(1f), label = { Text(strings[R.string.vol_ch]) }, singleLine = true, shape = RoundedCornerShape(12.dp))
        }
        OutlinedTextField(collections, { collections = it }, Modifier.fillMaxWidth(), label = { Text(strings[R.string.collections]) }, placeholder = { Text(strings[R.string.separate_with_commas]) }, shape = RoundedCornerShape(12.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button({ model.change(book) { it.copy(favorite = !it.favorite) } }) { Text(if (book.favorite) strings[R.string.remove_favorite] else strings[R.string.add_to_favorites]) }
            OutlinedButton({ model.change(book) { it.copy(status = ReadingStatus.UNREAD) } }, colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface)) { Text(strings[R.string.mark_unread]) }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        if (book.series.isNotBlank()) ActionRow(strings[R.string.series_reading_settings]) { seriesOptions = true }
        ActionRow(strings[R.string.relink_source_file], book.metadata.completeness?.let { strings[R.string.export, it] }, click = relink)
        book.metadata.missingMaterial.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
        if (!book.managed && book.format != BookFormat.FOLDER) ActionRow(strings[R.string.make_a_managed_copy]) { model.import(android.net.Uri.parse(book.uri), forceCopy = true, replace = book.id); dismiss() }
        TextButton({ deleting = true }) { Text(strings[R.string.remove_from_library], color = MaterialTheme.colorScheme.error) }
    }
    if (deleting) AlertDialog(onDismissRequest = { deleting = false }, title = { Text(strings[R.string.remove_this_book]) },
        text = { Column { Text(strings[R.string.your_original_file_stays_in_its_folder]); if (book.managed) SettingSwitch(strings[R.string.delete_managed_copy], strings[R.string.also_delete_the_copied_manga_file_from_its_storage], deleteCopy) { deleteCopy = it } } },
        confirmButton = { TextButton({ model.action { model.library.remove(book.id, deleteCopy) }; dismiss() }) { Text(strings[R.string.remove]) } }, dismissButton = { TextButton({ deleting = false }) { Text(strings[R.string.cancel]) } })
    if (seriesOptions) ReaderSettingsDialog(strings[R.string.series_settings, book.series], preferences.seriesSettings[book.series]?.applyTo(preferences.reader) ?: preferences.reader,
        { seriesOptions = false }, { settings -> model.updatePreferences { it.copy(seriesSettings = it.seriesSettings + (book.series to ReaderOverrides.from(settings))) } },
        { model.updatePreferences { it.copy(seriesSettings = it.seriesSettings - book.series) } })
}
