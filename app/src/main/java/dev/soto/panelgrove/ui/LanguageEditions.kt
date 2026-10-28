package dev.soto.panelgrove.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.runtime.Composable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.soto.panelgrove.R
import dev.soto.panelgrove.core.*
import java.util.Locale

fun languageLabel(language: String, strings: StringDictionary): String {
    if (language.isBlank()) return strings[R.string.language_unspecified]
    val locale = Locale.forLanguageTag(language)
    val name = locale.getDisplayName(Locale.getDefault())
    return if (locale.language.isBlank() || name.equals(language, true)) language else "$name ($language)"
}

@Composable fun LanguageEditionsDialog(current: Book?, editions: List<Book>, model: LibraryViewModel,
    dismiss: () -> Unit, choose: (Book) -> Unit, details: ((String) -> Unit)? = null) {
    val strings = rememberStrings()
    val latest = editions.filter { it.lastOpenedAt > 0 }.maxByOrNull { it.lastOpenedAt }
    AlertDialog(onDismissRequest = dismiss, title = { Text(strings[if (current == null) R.string.choose_version else R.string.language_editions]) }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(strings[R.string.editions_keep_separate_progress], style = MaterialTheme.typography.bodySmall)
            LazyColumn(Modifier.weight(1f, fill = false), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(editions, key = { it.id }) { edition ->
                    Surface(shape = RoundedCornerShape(12.dp), color = if (edition.id == current?.id)
                        MaterialTheme.colorScheme.primaryContainer else Color.Transparent) {
                        Row(Modifier.fillMaxWidth().testTag("edition-choice-${edition.id}").clickable { choose(edition); dismiss() }.padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            BookCover(edition, model, Modifier.width(40.dp).aspectRatio(2f / 3f))
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(languageLabel(edition.editionLanguage, strings), style = MaterialTheme.typography.titleSmall)
                                if (edition.id == current?.id) Text(strings[R.string.selected_edition], style = MaterialTheme.typography.labelSmall)
                                else if (current == null && edition.id == latest?.id) Text(strings[R.string.last_read_edition], style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                Text(edition.title, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                                Text(strings[R.string.edition_file, strings.format(edition.format), edition.displayName], maxLines = 1,
                                    overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(when (edition.status) {
                                    ReadingStatus.UNREAD -> strings[R.string.unread]
                                    ReadingStatus.READ -> strings[R.string.finished]
                                    ReadingStatus.READING -> strings[R.string.edition_reading_progress, (bookProgress(edition) * 100).toInt(),
                                        ((edition.position?.index ?: 0) + 1).coerceAtMost(edition.pageCount), edition.pageCount]
                                }, style = MaterialTheme.typography.labelSmall)
                                LinearProgressIndicator(progress = { bookProgress(edition) }, modifier = Modifier.fillMaxWidth().height(4.dp),
                                    gapSize = 0.dp, drawStopIndicator = {})
                                if (edition.access != AccessState.AVAILABLE) Text(strings[R.string.relink_source], style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                            }
                            if (details != null) IconButton({ dismiss(); details(edition.id) }) {
                                Icon(Icons.Default.MoreVert, strings[R.string.details_for, edition.title])
                            }
                        }
                    }
                }
            }
        }
    }, confirmButton = { TextButton(dismiss) { Text(strings[R.string.close]) } })
}
