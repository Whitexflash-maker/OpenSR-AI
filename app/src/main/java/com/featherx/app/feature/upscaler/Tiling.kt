package com.featherx.app.feature.upscaler

import kotlin.math.min

/** One core region of the source image handled by a single model run. */
data class Tile(val cx: Int, val cy: Int, val cw: Int, val ch: Int)

object TilePlanner {
    const val TILE = 224            // model input size (fixed in the ONNX graph)
    const val SCALE = 3             // model upscale factor
    const val MARGIN = 8            // context pixels dropped on each side (receptive field radius is 5)
    const val CORE = TILE - 2 * MARGIN
    const val OUT_TILE = TILE * SCALE

    fun plan(width: Int, height: Int): List<Tile> {
        require(width > 0 && height > 0) { "Empty image" }
        val tiles = ArrayList<Tile>()
        var cy = 0
        while (cy < height) {
            var cx = 0
            while (cx < width) {
                tiles.add(Tile(cx, cy, min(CORE, width - cx), min(CORE, height - cy)))
                cx += CORE
            }
            cy += CORE
        }
        return tiles
    }
}

/** Full-range BT.601 YCbCr (same as JPEG / PIL), values in 0..255. */
object YCbCr {
    fun luma(r: Int, g: Int, b: Int): Float = 0.299f * r + 0.587f * g + 0.114f * b
    fun cb(r: Int, g: Int, b: Int): Float = 128f - 0.168736f * r - 0.331264f * g + 0.5f * b
    fun cr(r: Int, g: Int, b: Int): Float = 128f + 0.5f * r - 0.418688f * g - 0.081312f * b

    fun toArgb(alpha: Int, y: Float, cb: Float, cr: Float): Int {
        val r = clamp255(y + 1.402f * (cr - 128f))
        val g = clamp255(y - 0.344136f * (cb - 128f) - 0.714136f * (cr - 128f))
        val b = clamp255(y + 1.772f * (cb - 128f))
        return (alpha shl 24) or (r shl 16) or (g shl 8) or b
    }

    private fun clamp255(v: Float): Int = (v + 0.5f).toInt().coerceIn(0, 255)
}
