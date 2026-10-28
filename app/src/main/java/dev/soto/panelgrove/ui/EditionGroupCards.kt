package dev.soto.panelgrove.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.soto.panelgrove.R
import dev.soto.panelgrove.core.*
import java.util.Locale

private fun groupSummary(group: EditionGroup, strings: StringDictionary): String {
    val book = group.representative
    val number = when (book.metadata.kind?.lowercase(Locale.ROOT)) {
        "volume" -> strings[R.string.vol, book.number]
        "chapter" -> strings[R.string.chapter, book.number]
        else -> strings[R.string.volume_chapter_number, book.number]
    }
    return "$number · ${strings.quantity(R.plurals.edition_version_count, group.editions.size, group.editions.size)}"
}

/** Up to two rear cover edges make a single displayed cover read as a stack of editions. */
@Composable private fun EditionStackCover(group: EditionGroup, model: LibraryViewModel, modifier: Modifier = Modifier) {
    val strings = rememberStrings()
    val shape = RoundedCornerShape(10.dp)
    Box(modifier) {
        Box(Modifier.fillMaxSize().padding(start = 10.dp, bottom = 10.dp).clip(shape)
            .background(MaterialTheme.colorScheme.tertiaryContainer).border(1.dp, MaterialTheme.colorScheme.outline, shape))
        if (group.editions.size > 2) Box(Modifier.fillMaxSize().padding(start = 5.dp, end = 5.dp, top = 5.dp, bottom = 5.dp).clip(shape)
            .background(MaterialTheme.colorScheme.primaryContainer).border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape))
        BookCover(group.representative, model, Modifier.fillMaxSize().padding(top = 10.dp, end = 10.dp))
        Surface(Modifier.align(Alignment.TopEnd).semantics {
            contentDescription = strings.quantity(R.plurals.edition_version_count, group.editions.size, group.editions.size)
        }, color = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            shape = RoundedCornerShape(8.dp), shadowElevation = 2.dp) {
            Text(group.editions.size.toString(), Modifier.padding(horizontal = 7.dp, vertical = 3.dp), style = MaterialTheme.typography.labelSmall)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable fun EditionGroupCard(group: EditionGroup, model: LibraryViewModel, choose: () -> Unit) {
    val strings = rememberStrings()
    val book = group.representative
    Column(Modifier.testTag("edition-group-${group.key}").semantics {
        contentDescription = strings.quantity(R.plurals.choose_group_version, group.editions.size, book.title, group.editions.size)
    }.combinedClickable(onClick = choose, onLongClick = choose, onLongClickLabel = strings[R.string.choose_version])) {
        EditionStackCover(group, model, Modifier.fillMaxWidth().aspectRatio(2f / 3f))
        Text(book.title, Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(groupSummary(group, strings), style = MaterialTheme.typography.labelSmall, maxLines = 2,
            overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable fun EditionGroupRow(group: EditionGroup, model: LibraryViewModel, choose: () -> Unit) {
    val strings = rememberStrings()
    val book = group.representative
    Row(Modifier.fillMaxWidth().testTag("edition-group-${group.key}").clickable(onClick = choose).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        EditionStackCover(group, model, Modifier.width(62.dp).aspectRatio(2f / 3f))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(book.title, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(groupSummary(group, strings), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        IconButton(choose) { Icon(Icons.Default.MoreVert, strings.quantity(R.plurals.choose_group_version, group.editions.size, book.title, group.editions.size)) }
    }
}
