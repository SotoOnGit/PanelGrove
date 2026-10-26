package dev.soto.panelgrove.ui

import dev.soto.panelgrove.R
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.IntSize
import kotlin.math.roundToInt

class ViewportTransform {
    var scale by mutableFloatStateOf(1f)
        private set
    var pan by mutableStateOf(Offset.Zero)
        private set
    var size by mutableStateOf(IntSize.Zero)
    var coordinates by mutableStateOf<LayoutCoordinates?>(null)
    val zoomed get() = scale > 1.02f
    fun reset() { scale = 1f; pan = Offset.Zero }
    fun transform(centroid: Offset, movement: Offset, zoom: Float) {
        val next = (scale * zoom).coerceIn(1f, 6f)
        if (next == 1f) { reset(); return }
        val center = Offset(size.width / 2f, size.height / 2f)
        // Keep the content under the fingers stationary as the entire viewport magnifies.
        val nextPan = (pan - (centroid - center)) * (next / scale) + (centroid - center) + movement
        scale = next
        pan = Offset(nextPan.x.coerceIn(-size.width * (next - 1) / 2, size.width * (next - 1) / 2),
            nextPan.y.coerceIn(-size.height * (next - 1) / 2, size.height * (next - 1) / 2))
    }
}

val LocalReaderViewport = staticCompositionLocalOf<ViewportTransform?> { null }

/** Layout, pagination and page dimensions stay fixed. One camera transforms the reader. */
@Composable fun ReaderViewport(key: Any, tap: (Float) -> Unit, content: @Composable (Boolean) -> Unit) {
    val strings = rememberStrings()

    val viewport = remember(key) { ViewportTransform() }
    val currentTap by rememberUpdatedState(tap)
    Box(Modifier.fillMaxSize().clipToBounds().testTag("reader-viewport")
        .onSizeChanged { if (viewport.size != it) { viewport.size = it; viewport.reset() } }
        .onGloballyPositioned { viewport.coordinates = it }
        .semantics { stateDescription = strings[R.string.zoom, (viewport.scale * 100).roundToInt()] }
        .pointerInput(viewport) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                do {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    if (event.changes.count { it.pressed } >= 2 || viewport.zoomed) {
                        val centroid = event.calculateCentroid(useCurrent = false)
                        if (centroid != Offset.Unspecified) viewport.transform(centroid, event.calculatePan(), event.calculateZoom())
                        if (event.changes.any { it.positionChanged() }) event.changes.forEach { it.consume() }
                    }
                } while (event.changes.any { it.pressed })
            }
        }
        .pointerInput(viewport) {
            detectTapGestures(onDoubleTap = { point ->
                if (viewport.zoomed) viewport.reset() else viewport.transform(point, Offset.Zero, 2.5f)
            }, onTap = { point -> currentTap(if (viewport.zoomed) .5f else point.x / viewport.size.width.coerceAtLeast(1)) })
        }) {
        CompositionLocalProvider(LocalReaderViewport provides viewport) {
            Box(Modifier.fillMaxSize().graphicsLayer {
                scaleX = viewport.scale; scaleY = viewport.scale
                translationX = viewport.pan.x; translationY = viewport.pan.y
            }) { content(viewport.zoomed) }
        }
    }
}
