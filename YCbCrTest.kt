package com.opensr.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class YCbCrTest {

    @Test
    fun roundTripIsWithinOneLevel() {
        for (r in 0..255 step 15) {
            for (g in 0..255 step 15) {
                for (b in 0..255 step 15) {
                    val argb = YCbCr.toArgb(
                        255, YCbCr.luma(r, g, b), YCbCr.cb(r, g, b), YCbCr.cr(r, g, b)
                    )
                    assertTrue(abs(((argb shr 16) and 0xFF) - r) <= 1)
                    assertTrue(abs(((argb shr 8) and 0xFF) - g) <= 1)
                    assertTrue(abs((argb and 0xFF) - b) <= 1)
                    assertEquals(255, argb ushr 24)
                }
            }
        }
    }

    @Test
    fun grayHasNeutralChroma() {
        assertEquals(0f, YCbCr.luma(0, 0, 0), 1e-4f)
        assertEquals(255f, YCbCr.luma(255, 255, 255), 1e-3f)
        assertEquals(128f, YCbCr.cb(90, 90, 90), 1e-3f)
        assertEquals(128f, YCbCr.cr(90, 90, 90), 1e-3f)
    }

    @Test
    fun outOfRangeLumaIsClamped() {
        assertEquals(0xFFFFFFFF.toInt(), YCbCr.toArgb(255, 400f, 128f, 128f))
        assertEquals(0xFF000000.toInt(), YCbCr.toArgb(255, -50f, 128f, 128f))
    }
}
