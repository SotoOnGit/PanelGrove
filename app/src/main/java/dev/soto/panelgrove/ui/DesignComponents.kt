package dev.soto.panelgrove.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun PanelgroveSheet(dismiss: () -> Unit, footer: @Composable () -> Unit = {}, content: @Composable ColumnScope.() -> Unit) {
    ModalBottomSheet(onDismissRequest = dismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = { BottomSheetDefaults.DragHandle(width = 32.dp, height = 4.dp, color = MaterialTheme.colorScheme.outlineVariant) }) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(.92f).imePadding()) {
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp), content = content)
            Box(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp)) { footer() }
        }
    }
}

@Composable fun SheetTitle(text: String) { Text(text, style = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp)) }

@Composable fun SectionLabel(text: String) {
    Text(text.uppercase(), color = MaterialTheme.colorScheme.primary,
        style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp, fontWeight = FontWeight.Medium, letterSpacing = .72.sp))
}

@Composable fun ActionRow(title: String, subtitle: String? = null, enabled: Boolean = true, click: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(enabled = enabled, onClick = click).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f)) {
            Text(title, color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun <T> ChoiceSegments(choices: List<Pair<T, String>>, selected: T, choose: (T) -> Unit) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        choices.forEachIndexed { index, (value, label) ->
            SegmentedButton(selected = selected == value, onClick = { choose(value) },
                shape = SegmentedButtonDefaults.itemShape(index, choices.size), icon = {},
                colors = SegmentedButtonDefaults.colors(activeContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    activeContentColor = MaterialTheme.colorScheme.onPrimaryContainer, inactiveContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                    inactiveBorderColor = MaterialTheme.colorScheme.outlineVariant, activeBorderColor = MaterialTheme.colorScheme.outlineVariant)) {
                Text(label, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}
