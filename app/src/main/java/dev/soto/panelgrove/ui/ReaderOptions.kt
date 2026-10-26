package dev.soto.panelgrove.ui

import dev.soto.panelgrove.R
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import dev.soto.panelgrove.core.*

@Composable fun ReaderOptions(settings: ReaderSettings, update: (ReaderSettings) -> Unit, modifier: Modifier = Modifier) {
    val strings = rememberStrings()

    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(strings[R.string.reading_direction], style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        ChoiceSegments(listOf(Direction.RTL to strings[R.string.right_to_left], Direction.LTR to strings[R.string.left_to_right]), settings.direction) { update(settings.copy(direction = it)) }
        Text(strings[R.string.reading_mode], style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        ChoiceSegments(listOf(ReadingMode.PAGED to strings[R.string.reading_mode_pages], ReadingMode.VERTICAL to strings[R.string.vertical_scroll]), settings.mode) { update(settings.copy(mode = it)) }
        Text(strings[R.string.page_sizing], style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        ChoiceSegments(listOf(FitMode.PAGE to strings[R.string.fit_page], FitMode.WIDTH to strings[R.string.fit_width]), settings.fit) { update(settings.copy(fit = it)) }
        Text(strings[R.string.reader_background], style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        // Keep previously saved backgrounds visible as the corresponding original preset.
        val background = when (settings.background) { 0xFF101210L -> 0xFF0B0D10L; 0xFFF5F1E8L -> 0xFFF4EFE6L; else -> settings.background }
        ChoiceSegments(listOf(0xFF0B0D10L to strings[R.string.ink], 0xFFF4EFE6L to strings[R.string.paper], 0xFF000000L to strings[R.string.black]), background) { update(settings.copy(background = it)) }
        SettingSwitch(strings[R.string.two_page_spreads], strings[R.string.on_wide_screens_cover_stays_alone], settings.spreads) { update(settings.copy(spreads = it)) }
        SettingSwitch(strings[R.string.use_system_brightness], "", settings.brightness < 0) { update(settings.copy(brightness = if (it) -1f else .5f)) }
        if (settings.brightness >= 0) Slider(settings.brightness, { update(settings.copy(brightness = it)) }, modifier = Modifier.semantics { contentDescription = strings[R.string.reader_brightness] }, valueRange = .03f..1f)
        SettingSwitch(strings[R.string.volume_key_navigation], strings[R.string.down_advances_up_goes_back], settings.volumeKeys) { update(settings.copy(volumeKeys = it)) }
        SettingSwitch(strings[R.string.keep_screen_awake], "", settings.keepAwake) { update(settings.copy(keepAwake = it)) }
        TextButton({ update(settings.copy(rotationLock = !settings.rotationLock)) }) { Text(if (settings.rotationLock) strings[R.string.orientation_locked] else strings[R.string.orientation_automatic]) }
    }
}

@Composable fun SettingSwitch(title: String, subtitle: String, checked: Boolean, change: (Boolean) -> Unit) {
    val strings = rememberStrings()

    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle.isNotBlank()) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked, change, Modifier.semantics { contentDescription = title })
    }
}

@Composable fun ReaderSettingsDialog(title: String, initial: ReaderSettings, dismiss: () -> Unit,
    save: (ReaderSettings) -> Unit, reset: (() -> Unit)? = null) {
    val strings = rememberStrings()

    var draft by rememberSaveable(initial, stateSaver = androidx.compose.runtime.saveable.Saver(
        save = { PanelgroveJson.encodeToString(it) }, restore = { PanelgroveJson.decodeFromString<ReaderSettings>(it) })) { mutableStateOf(initial) }
    PanelgroveSheet(dismiss, footer = {
        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            if (reset != null) TextButton({ reset(); dismiss() }) { Text(strings[R.string.use_defaults]) }
            TextButton(dismiss) { Text(strings[R.string.cancel]) }
            Button({ save(draft); dismiss() }) { Text(strings[R.string.save]) }
        }
    }) {
        SheetTitle(title)
        ReaderOptions(draft, { draft = it })
    }
}
