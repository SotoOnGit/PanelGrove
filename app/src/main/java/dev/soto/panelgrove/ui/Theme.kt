package dev.soto.panelgrove.ui

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import dev.soto.panelgrove.core.AppTheme

internal val PanelgroveDarkColors = darkColorScheme(
    primary = Color(0xFFB4C5FF), onPrimary = Color(0xFF1B2B5E),
    primaryContainer = Color(0xFF2F4280), onPrimaryContainer = Color(0xFFDCE2FF),
    background = Color(0xFF0F1115), onBackground = Color(0xFFE6E8EC),
    surface = Color(0xFF0F1115), onSurface = Color(0xFFE6E8EC),
    surfaceContainerLowest = Color(0xFF0B0D10), surfaceContainerLow = Color(0xFF171A20),
    surfaceContainer = Color(0xFF171A20), surfaceContainerHigh = Color(0xFF1E222A),
    surfaceContainerHighest = Color(0xFF262B34), surfaceVariant = Color(0xFF1E222A),
    onSurfaceVariant = Color(0xFFA3AAB6), outlineVariant = Color(0xFF3A414D), outline = Color(0xFF6B7280),
    secondary = Color(0xFFB4C5FF), onSecondary = Color(0xFF1B2B5E),
    secondaryContainer = Color(0xFF2F4280), onSecondaryContainer = Color(0xFFDCE2FF),
    tertiary = Color(0xFFF2B8A2), onTertiary = Color(0xFF4C2618),
    tertiaryContainer = Color(0xFF663C2B), onTertiaryContainer = Color(0xFFFFDBCD),
    error = Color(0xFFFFB4AB), onError = Color(0xFF690005), scrim = Color.Black,
)
internal val PanelgroveLightColors = lightColorScheme(
    primary = Color(0xFF4458A7), onPrimary = Color.White,
    primaryContainer = Color(0xFFDCE2FF), onPrimaryContainer = Color(0xFF152450),
    background = Color(0xFFFAF9FD), onBackground = Color(0xFF1A1B20),
    surface = Color(0xFFFAF9FD), onSurface = Color(0xFF1A1B20),
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFF3F3F9),
    surfaceContainer = Color(0xFFEDEEF4), surfaceContainerHigh = Color(0xFFE6E7EF),
    surfaceContainerHighest = Color(0xFFE0E1E9), surfaceVariant = Color(0xFFE6E7EF),
    onSurfaceVariant = Color(0xFF5A5E69), outlineVariant = Color(0xFFC5C7D0), outline = Color(0xFF747783),
    secondary = Color(0xFF4458A7), onSecondary = Color.White,
    secondaryContainer = Color(0xFFDCE2FF), onSecondaryContainer = Color(0xFF152450),
    tertiary = Color(0xFF8B4F3A), onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFDBCD), onTertiaryContainer = Color(0xFF351004),
)

@Composable fun PanelgroveTheme(theme: AppTheme, trueBlack: Boolean = false, content: @Composable () -> Unit) {
    val darkMode = theme == AppTheme.DARK || (theme == AppTheme.SYSTEM && isSystemInDarkTheme())
    val activity = LocalActivity.current
    DisposableEffect(activity, darkMode) {
        activity?.window?.let { window ->
            val bars = WindowCompat.getInsetsController(window, window.decorView)
            bars.isAppearanceLightStatusBars = !darkMode
            bars.isAppearanceLightNavigationBars = !darkMode
        }
        onDispose { }
    }
    val colors = if (!darkMode) PanelgroveLightColors else if (trueBlack)
        PanelgroveDarkColors.copy(background = Color.Black, surface = Color.Black) else PanelgroveDarkColors
    val type = Typography().let { it.copy(
        displaySmall = it.displaySmall.copy(fontFamily = FontFamily.SansSerif, fontSize = 26.sp, fontWeight = FontWeight.Normal),
        headlineMedium = it.headlineMedium.copy(fontFamily = FontFamily.SansSerif, fontSize = 26.sp),
        titleLarge = it.titleLarge.copy(fontFamily = FontFamily.SansSerif, fontSize = 22.sp, fontWeight = FontWeight.Normal),
        titleMedium = it.titleMedium.copy(fontSize = 16.sp, fontWeight = FontWeight.Normal),
        labelSmall = it.labelSmall.copy(fontSize = 12.sp),
    ) }
    MaterialTheme(colorScheme = colors, typography = type,
        shapes = Shapes(small = RoundedCornerShape(12.dp), medium = RoundedCornerShape(16.dp), large = RoundedCornerShape(16.dp), extraLarge = RoundedCornerShape(28.dp)),
        content = content)
}
