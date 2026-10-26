package dev.soto.panelgrove

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import dev.soto.panelgrove.ui.ViewportTransform
import org.junit.Assert.*
import org.junit.Test

class ViewportTransformTest {
    @Test fun zoomKeepsThePointUnderTheFingersAndTransformsTheWholeViewport() {
        val camera = ViewportTransform().apply { size = IntSize(1000, 2000) }
        val focal = Offset(300f, 700f)
        camera.transform(focal, Offset.Zero, 2.5f)
        val center = Offset(500f, 1000f)
        val mapped = (focal - center) * camera.scale + center + camera.pan
        assertEquals(focal.x, mapped.x, .001f)
        assertEquals(focal.y, mapped.y, .001f)
        assertTrue(camera.zoomed)
        assertEquals(IntSize(1000, 2000), camera.size)
        camera.reset()
        assertEquals(1f, camera.scale, 0f); assertEquals(Offset.Zero, camera.pan)
    }
    @Test fun cameraUsesViewportBoundsAndNeverPansBeyondItsEdges() {
        val camera = ViewportTransform().apply { size = IntSize(1000, 2000) }
        camera.transform(Offset(500f, 1000f), Offset(10_000f, -10_000f), 2f)
        assertEquals(Offset(500f, -1000f), camera.pan)
        camera.transform(Offset(500f, 1000f), Offset.Zero, .001f)
        assertEquals(1f, camera.scale, 0f); assertEquals(Offset.Zero, camera.pan)
    }
}
