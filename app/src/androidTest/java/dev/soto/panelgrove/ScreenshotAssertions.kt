package dev.soto.panelgrove

import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Build
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.captureToImage
import androidx.test.platform.app.InstrumentationRegistry
import kotlin.math.roundToInt

/** Compose cannot capture dialog windows before API 28; screen capture includes those windows. */
fun SemanticsNodeInteraction.capturePixels(): PixelMap {
    if (Build.VERSION.SDK_INT >= 28) return captureToImage().toPixelMap()

    val instrumentation = InstrumentationRegistry.getInstrumentation()
    val node = fetchSemanticsNode()
    lateinit var region: Rect
    instrumentation.runOnMainSync {
        // Root coordinates alone omit the dialog window's position on the physical screen.
        val screenOffset = node.positionOnScreen - node.positionInRoot
        val bounds = node.boundsInRoot.translate(screenOffset)
        region = Rect(bounds.left.roundToInt(), bounds.top.roundToInt(), bounds.right.roundToInt(), bounds.bottom.roundToInt())
    }
    val screen = checkNotNull(instrumentation.uiAutomation.takeScreenshot()) { "Could not capture the device screen." }
    try {
        check(region.intersect(0, 0, screen.width, screen.height) && !region.isEmpty) { "The captured node is outside the screen." }
        val cropped = Bitmap.createBitmap(screen, region.left, region.top, region.width(), region.height())
        try { return cropped.asImageBitmap().toPixelMap() }
        finally { if (cropped !== screen) cropped.recycle() }
    } finally { screen.recycle() }
}
