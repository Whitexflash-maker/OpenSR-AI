package com.featherx.app.feature.upscaler

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import kotlin.math.max
import kotlin.math.roundToInt

data class LoadedImage(
    val bitmap: Bitmap,
    val originalWidth: Int,
    val originalHeight: Int,
) {
    val wasDownscaled: Boolean get() = bitmap.width != originalWidth || bitmap.height != originalHeight
}

object ImageLoader {
    /** Longest allowed input side. 640 -> output at most 1920x1920 (~14 MB bitmap). */
    const val MAX_SIDE = 640

    /** Blocking; call from a background thread. Requires API 28+ (ImageDecoder). */
    fun load(context: Context, uri: Uri): LoadedImage {
        val source = ImageDecoder.createSource(context.contentResolver, uri)
        var origW = 0
        var origH = 0
        var bmp: Bitmap = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            origW = info.size.width
            origH = info.size.height
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val longSide = max(origW, origH)
            if (longSide > MAX_SIDE) {
                val s = MAX_SIDE.toFloat() / longSide
                decoder.setTargetSize(
                    max(1, (origW * s).roundToInt()),
                    max(1, (origH * s).roundToInt()),
                )
            }
        }

        // EXIF rotation may swap sides, so re-check the final size.
        if (max(bmp.width, bmp.height) > MAX_SIDE) {
            val s = MAX_SIDE.toFloat() / max(bmp.width, bmp.height)
            val scaled = Bitmap.createScaledBitmap(
                bmp,
                max(1, (bmp.width * s).roundToInt()),
                max(1, (bmp.height * s).roundToInt()),
                true,
            )
            if (scaled !== bmp) bmp.recycle()
            bmp = scaled
        }
        // Wide-gamut images can decode as RGBA_F16; the engine needs ARGB_8888.
        if (bmp.config != Bitmap.Config.ARGB_8888) {
            val copy = bmp.copy(Bitmap.Config.ARGB_8888, false)
                ?: throw IllegalStateException("Could not convert image to ARGB_8888")
            bmp.recycle()
            bmp = copy
        }
        return LoadedImage(bmp, origW, origH)
    }
}
