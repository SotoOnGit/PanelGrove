package dev.soto.panelgrove.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dev.soto.panelgrove.R

@Composable fun PageExportDialog(current: Int, count: Int, dismiss: () -> Unit, export: (Int, Boolean) -> Unit) {
    val strings = rememberStrings()
    var number by rememberSaveable { mutableStateOf((current + 1).toString()) }
    val page = number.toIntOrNull()?.takeIf { it in 1..count }
    AlertDialog(onDismissRequest = dismiss, title = { Text(strings[R.string.export_page]) }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(strings[R.string.page_export_description])
            OutlinedTextField(number, { number = it }, Modifier.fillMaxWidth(), singleLine = true,
                label = { Text(strings[R.string.page_to_export]) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                isError = page == null, supportingText = { Text(strings[R.string.page_export_range, count]) })
        }
    }, confirmButton = {
        TextButton({ page?.let { export(it - 1, false); dismiss() } }, enabled = page != null) { Text(strings[R.string.save_image]) }
    }, dismissButton = {
        Row {
            TextButton(dismiss) { Text(strings[R.string.cancel]) }
            TextButton({ page?.let { export(it - 1, true); dismiss() } }, enabled = page != null) { Text(strings[R.string.share_page]) }
        }
    })
}
