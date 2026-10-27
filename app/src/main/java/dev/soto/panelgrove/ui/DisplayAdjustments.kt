package dev.soto.panelgrove.ui

import android.graphics.Bitmap
import androidx.compose.runtime.*
import dev.soto.panelgrove.core.ReaderSettings
import dev.soto.panelgrove.core.displayLookup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** The decoder's cached bitmap and source bytes are never modified. Also used for zoom tiles. */
suspend fun adjustDisplay(source: Bitmap, contrast: Float, gamma: Float): Bitmap = withContext(Dispatchers.Default) {
    if (contrast == 1f && gamma == 1f) return@withContext source
    val lookup = displayLookup(contrast, gamma)
    val result = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
    val rows = 32
    val pixels = IntArray(source.width * minOf(rows, source.height))
    try {
        for (y in 0 until source.height step rows) {
            currentCoroutineContext().ensureActive()
            val height = minOf(rows, source.height - y)
            source.getPixels(pixels, 0, source.width, 0, y, source.width, height)
            for (i in 0 until source.width * height) {
                val pixel = pixels[i]
                pixels[i] = (pixel and -0x1000000) or (lookup[(pixel ushr 16) and 255] shl 16) or
                    (lookup[(pixel ushr 8) and 255] shl 8) or lookup[pixel and 255]
            }
            result.setPixels(pixels, 0, source.width, 0, y, source.width, height)
        }
        currentCoroutineContext().ensureActive()
        result
    } catch (error: Exception) { result.recycle(); throw error }
}

@Composable fun rememberDisplayBitmap(source: Bitmap?, settings: ReaderSettings): Bitmap? {
    var adjusted by remember(source) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(source, settings.contrast, settings.gamma) {
        adjusted = source?.let { adjustDisplay(it, settings.contrast, settings.gamma) }
    }
    return if (settings.contrast == 1f && settings.gamma == 1f) source else adjusted
}
