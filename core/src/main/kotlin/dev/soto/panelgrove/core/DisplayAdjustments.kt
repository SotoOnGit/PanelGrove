package dev.soto.panelgrove.core

import kotlin.math.pow
import kotlin.math.roundToInt

/** Contrast around mid-gray, then gamma. Gamma above 1 brightens midtones. */
fun displayLookup(contrast: Float, gamma: Float): IntArray {
    require(contrast in .5f..2f && gamma in .5f..2f)
    return IntArray(256) { channel ->
        val contrasted = ((channel / 255.0 - .5) * contrast + .5).coerceIn(0.0, 1.0)
        (contrasted.pow(1.0 / gamma) * 255).roundToInt().coerceIn(0, 255)
    }
}
