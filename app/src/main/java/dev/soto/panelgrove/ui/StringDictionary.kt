package dev.soto.panelgrove.ui

import android.content.res.Resources
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import dev.soto.panelgrove.R
import dev.soto.panelgrove.core.AppTheme
import dev.soto.panelgrove.core.BookFormat

/** All interface copy is keyed in res/values/strings.xml, ready for language dictionaries. */
class StringDictionary(private val resources: Resources) {
    operator fun get(@StringRes key: Int, vararg arguments: Any): String =
        if (arguments.isEmpty()) resources.getString(key) else resources.getString(key, *arguments)
    fun quantity(key: Int, count: Int, vararg arguments: Any): String = resources.getQuantityString(key, count, *arguments)
    fun theme(theme: AppTheme): String = get(when (theme) {
        AppTheme.SYSTEM -> R.string.theme_system
        AppTheme.LIGHT -> R.string.theme_light
        AppTheme.DARK -> R.string.theme_dark
    })
    fun format(format: BookFormat): String = get(when (format) {
        BookFormat.ZIP -> R.string.format_zip
        BookFormat.RAR -> R.string.format_rar
        BookFormat.SEVEN_Z -> R.string.format_seven_z
        BookFormat.PDF -> R.string.format_pdf
        BookFormat.FOLDER -> R.string.format_folder
    })
}

@Composable fun rememberStrings(): StringDictionary {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    return remember(context, configuration) { StringDictionary(context.resources) }
}
