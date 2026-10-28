package dev.soto.panelgrove.ui

import dev.soto.panelgrove.R
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.soto.panelgrove.core.*
import dev.soto.panelgrove.engine.DocumentSession

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun ContentsSheet(session: DocumentSession, current: Int, marks: List<Bookmark>, model: ReaderViewModel, library: LibraryViewModel, dismiss: () -> Unit) {
    val strings = rememberStrings()

    var selected by remember { mutableIntStateOf(0) }
    var thumbnails by remember { mutableStateOf(false) }
    val chapters = remember(session) { session.index.pages.mapIndexedNotNull { i, page -> page.chapter?.let { it to i } }.distinctBy { it.first } }
    val chapterIndex = chapters.indexOfLast { it.second <= current }
    ModalBottomSheet(onDismissRequest = dismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
        dragHandle = { BottomSheetDefaults.DragHandle(width = 32.dp, height = 4.dp, color = MaterialTheme.colorScheme.outlineVariant) }) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(.8f)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { SheetTitle(strings[R.string.contents]) }
                IconButton({ thumbnails = !thumbnails }) { Icon(Icons.Default.PhotoLibrary, strings[R.string.page_thumbnails]) }
            }
            SecondaryTabRow(selectedTabIndex = selected, containerColor = MaterialTheme.colorScheme.surfaceContainerHigh) {
                Tab(selected == 0, { selected = 0 }, text = { Text(strings[R.string.chapters]) }, unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant)
                Tab(selected == 1, { selected = 1 }, text = { Text(strings[R.string.bookmarks]) }, unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (thumbnails) LazyRow(Modifier.height(120.dp), contentPadding = PaddingValues(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                itemsIndexed(session.index.pages, key = { _, page -> page.id }) { index, _ ->
                    Column(Modifier.width(60.dp).clickable { model.move(index); dismiss() }, horizontalAlignment = Alignment.CenterHorizontally) {
                        PageThumbnail(session, index, model, Modifier.width(60.dp).height(80.dp))
                        Text(strings[R.string.page_number, index + 1], style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(24.dp)) {
                if (selected == 0) {
                    if (chapters.isEmpty()) item { Text(strings[R.string.this_book_has_no_chapter_metadata_use_the_page]) }
                    itemsIndexed(chapters) { ordinal, (chapter, index) ->
                        Surface(color = if (ordinal == chapterIndex) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                            contentColor = if (ordinal == chapterIndex) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                            shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth().clickable { model.move(index); dismiss() }) {
                            Row(Modifier.heightIn(min = 56.dp).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(when (chapter) { "frontmatter" -> strings[R.string.front_matter]; "backmatter" -> strings[R.string.back_matter]; else -> if (chapter.firstOrNull()?.isDigit() == true) strings[R.string.chapter, chapter] else chapter }, Modifier.weight(1f))
                                Text(strings[R.string.page_number, index + 1])
                            }
                        }
                    }
                } else {
                    if (marks.isEmpty()) item { Text(strings[R.string.bookmark_a_page_to_find_it_here]) }
                    items(marks.sortedBy { it.pageIndex }, key = { it.id }) { mark ->
                        val index = session.index.pages.indexOfFirst { it.id == mark.pageId }.takeIf { it >= 0 } ?: mark.pageIndex.coerceIn(session.index.pages.indices)
                        val active = index == current
                        Surface(color = if (active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                            contentColor = if (active) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                            shape = RoundedCornerShape(12.dp)) {
                            Row(Modifier.fillMaxWidth().clickable { model.move(index); dismiss() }.padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                PageThumbnail(session, index, model, Modifier.width(40.dp).height(56.dp))
                                Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
                                    Text(mark.label.ifBlank { strings[R.string.page, index + 1] })
                                    if (mark.missing) Text(strings[R.string.original_page_removed_opens_nearest_remaining_page], style = MaterialTheme.typography.bodySmall)
                                    else if (active) Text(strings[R.string.current_page], style = MaterialTheme.typography.bodySmall)
                                }
                                IconButton({ library.action { library.library.dao.deleteBookmark(mark.id) } }) { Icon(Icons.Default.Close, strings[R.string.remove_bookmark_on_page, index + 1]) }
                            }
                        }
                    }
                    item { Text(strings[R.string.tap_the_bookmark_icon_in_the_reader_bar_to], Modifier.padding(top = 16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall) }
                }
            }
            TextButton(dismiss, Modifier.align(Alignment.End).padding(horizontal = 24.dp)) { Text(strings[R.string.close]) }
        }
    }
}

@Composable fun FinishBookScreen(book: Book, next: Book?, library: LibraryViewModel, close: () -> Unit, readNext: (Book) -> Unit, back: () -> Unit, again: () -> Unit) {
    val strings = rememberStrings()

    BackHandler(onBack = close)
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(Modifier.fillMaxSize().safeDrawingPadding()) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 72.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically)) {
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                    Icon(Icons.Default.Check, null, Modifier.padding(28.dp).size(32.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
                }
                Spacer(Modifier.height(8.dp))
                SectionLabel(strings[R.string.finished])
                Text(book.title, style = MaterialTheme.typography.displaySmall, textAlign = TextAlign.Center)
                Text(languageLabel(book.editionLanguage, strings), color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(listOfNotNull(book.series.takeIf { it.isNotBlank() }, book.number.takeIf { it.isNotBlank() }?.let { strings[R.string.vol, it] }, strings[R.string.page_count, book.pageCount]).joinToString(" · "),
                    textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                if (next != null) {
                    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            BookCover(next, library, Modifier.width(44.dp).aspectRatio(2f / 3f))
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) { SectionLabel(strings[R.string.up_next]); Text(next.title); Text(languageLabel(next.editionLanguage, strings), style = MaterialTheme.typography.bodySmall) }
                        }
                    }
                    Button({ readNext(next) }, Modifier.fillMaxWidth()) { Text(strings[R.string.read_next]) }
                }
                OutlinedButton(back, Modifier.fillMaxWidth(), colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface)) { Text(strings[R.string.back_to_library]) }
                TextButton(again) { Text(strings[R.string.read_again]) }
            }
            IconButton(close, Modifier.align(Alignment.TopStart).padding(8.dp)) { Icon(Icons.Default.Close, strings[R.string.close_finished_book]) }
        }
    }
}
