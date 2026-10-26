package dev.soto.panelgrove.ui

import dev.soto.panelgrove.R
import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import dev.soto.panelgrove.core.*
import dev.soto.panelgrove.engine.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlin.math.*

@Composable fun PageContent(session: DocumentSession, index: Int, model: ReaderViewModel, settings: ReaderSettings,
    modifier: Modifier, dimensions: (Int, Int) -> Unit = { _, _ -> }) {
    val strings = rememberStrings()

    var size by remember { mutableStateOf(IntSize.Zero) }
    var coordinates by remember { mutableStateOf<androidx.compose.ui.layout.LayoutCoordinates?>(null) }
    var bitmap by remember(session, index) { mutableStateOf<Bitmap?>(null) }
    var error by remember(session, index) { mutableStateOf<String?>(null) }
    var retry by remember { mutableIntStateOf(0) }
    var tile by remember(session, index) { mutableStateOf<Pair<PageRegion, Bitmap>?>(null) }
    val viewport = LocalReaderViewport.current
    val scale = viewport?.scale ?: 1f
    val pan = viewport?.pan ?: Offset.Zero
    val page = session.index.pages[index]
    val sourceWidth = (page.width.takeIf { it > 0 } ?: bitmap?.width ?: 1125).toFloat()
    val sourceHeight = (page.height.takeIf { it > 0 } ?: bitmap?.height ?: 1600).toFloat()
    val base = if (size.width == 0) 1f else if (settings.fit == FitMode.WIDTH) size.width / sourceWidth
        else min(size.width / sourceWidth, size.height / sourceHeight)
    val baseWidth = sourceWidth * base
    val baseHeight = sourceHeight * base
    val left = (size.width - baseWidth) / 2f
    val top = (size.height - baseHeight) / 2f
    LaunchedEffect(size, session, index, retry) {
        if (size.width == 0 || size.height == 0) return@LaunchedEffect
        error = null
        try {
            val h = (size.width * sourceHeight / sourceWidth).roundToInt().coerceIn(1, 3072)
            bitmap = model.app.engine.render(session, index, size.width, h)
            bitmap?.let { model.measured(session, index, it.width, it.height); dimensions(it.width, it.height) }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            error = dev.soto.panelgrove.FailureMessages.user(e, strings[R.string.this_page_could_not_be_displayed_try_again_or])
        }
    }
    LaunchedEffect(scale, pan, bitmap, size, coordinates, viewport?.coordinates) {
        tile = null
        val pageCoordinates = coordinates ?: return@LaunchedEffect
        val viewportCoordinates = viewport?.coordinates ?: return@LaunchedEffect
        if (bitmap == null || scale <= 1.05f || baseWidth <= 0 || baseHeight <= 0) return@LaunchedEffect
        delay(140)
        // Convert the fixed viewport through the camera into this page's local coordinates.
        // The high-resolution tile overlays the same rectangle without changing page layout.
        withFrameNanos { }
        if (!pageCoordinates.isAttached || !viewportCoordinates.isAttached) return@LaunchedEffect
        val start = pageCoordinates.localPositionOf(viewportCoordinates, Offset.Zero)
        val end = pageCoordinates.localPositionOf(viewportCoordinates, Offset(viewport.size.width.toFloat(), viewport.size.height.toFloat()))
        val region = PageRegion(((start.x - left) / baseWidth).coerceIn(0f, 1f),
            ((start.y - top) / baseHeight).coerceIn(0f, 1f),
            ((end.x - left) / baseWidth).coerceIn(0f, 1f), ((end.y - top) / baseHeight).coerceIn(0f, 1f))
        if (region.right <= region.left || region.bottom <= region.top) return@LaunchedEffect
        try { tile = region to model.app.engine.render(session, index, viewport.size.width, viewport.size.height, region) }
        catch (e: Exception) {
            if (e is CancellationException) throw e
            dev.soto.panelgrove.FailureMessages.recoverable(e, strings[R.string.zoom_detail_unavailable_keeping_the_base_image])
        }
    }
    Box(modifier.onSizeChanged { size = it }.onGloballyPositioned { coordinates = it }.semantics {
        contentDescription = strings[R.string.page_pinch_or_double_tap_to_zoom, index + 1]
        stateDescription = strings[R.string.zoom, (scale * 100).roundToInt()]
        customActions = listOf(
            CustomAccessibilityAction(strings[R.string.next_page]) { model.move(index + 1); true },
            CustomAccessibilityAction(strings[R.string.previous_page]) { model.move(index - 1); true },
            CustomAccessibilityAction(strings[R.string.reset_zoom]) { viewport?.reset(); true },
        )
    }, contentAlignment = Alignment.Center) {
        val image = bitmap
        if (image != null) Canvas(Modifier.fillMaxSize().testTag("rendered-page-${index + 1}")) {
            drawImage(image.asImageBitmap(), dstOffset = IntOffset(left.roundToInt(), top.roundToInt()),
                dstSize = IntSize(baseWidth.roundToInt().coerceAtLeast(1), baseHeight.roundToInt().coerceAtLeast(1)), filterQuality = FilterQuality.High)
            tile?.let { (crop, high) ->
                drawImage(high.asImageBitmap(), dstOffset = IntOffset((left + crop.left * baseWidth).roundToInt(), (top + crop.top * baseHeight).roundToInt()),
                    dstSize = IntSize(((crop.right - crop.left) * baseWidth).roundToInt().coerceAtLeast(1), ((crop.bottom - crop.top) * baseHeight).roundToInt().coerceAtLeast(1)), filterQuality = FilterQuality.High)
            }
        } else if (error == null) CircularProgressIndicator(Modifier.size(28.dp))
        if (error != null) Surface(Modifier.padding(24.dp), shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)) {
            Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(strings[R.string.page_could_not_be_read, index + 1])
                Text(error!!, style = MaterialTheme.typography.bodySmall)
                Row { TextButton({ retry++ }) { Text(strings[R.string.retry]) }; TextButton({ model.move(index + 1) }) { Text(strings[R.string.skip_page]) } }
            }
        }
    }
}

@Composable fun PageThumbnail(session: DocumentSession, index: Int, model: ReaderViewModel, modifier: Modifier) {
    val strings = rememberStrings()

    val bitmap by produceState<Bitmap?>(null, session, index) {
        try {
            val page = session.index.pages[index]
            val height = if (page.width > 0) (120f * page.height / page.width).toInt().coerceIn(1, 240) else 180
            value = model.app.engine.render(session, index, 120, height)
        } catch (e: Exception) { if (e is CancellationException) throw e }
    }
    Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
        bitmap?.let { Image(it.asImageBitmap(), strings[R.string.page_thumbnail, index + 1], Modifier.fillMaxSize(), contentScale = ContentScale.Fit) }
    }
}
