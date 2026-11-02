package com.featherx.app.feature.upscaler

import android.app.ActivityManager
import android.content.Context

object MemoryGuard {
    /** Rough peak: yOut(4B) + pixels(4B) + scaled bitmap(4B) + result bitmap(4B) per output pixel. */
    fun requiredBytes(inW: Int, inH: Int): Long =
        inW.toLong() * inH * 9 * 16 + 80L * 1024 * 1024  // + ONNX Runtime working set

    /** Returns null if OK, otherwise a message for the user. */
    fun check(context: Context, inW: Int, inH: Int): String? {
        val need = requiredBytes(inW, inH)
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val mi = ActivityManager.MemoryInfo()
        am.getMemoryInfo(mi)
        if (mi.lowMemory || mi.availMem < need + mi.threshold) {
            return "Not enough free RAM (${mi.availMem shr 20} MB free, ~${need shr 20} MB needed). " +
                "Close other apps and try again."
        }
        val rt = Runtime.getRuntime()
        val javaFree = rt.maxMemory() - (rt.totalMemory() - rt.freeMemory())
        val javaNeed = inW.toLong() * inH * 9 * 8 + 16L * 1024 * 1024  // two Java arrays of the output size
        if (javaFree < javaNeed) {
            return "App heap too small for this image (${javaFree shr 20} MB free, ~${javaNeed shr 20} MB needed)."
        }
        return null
    }
}
