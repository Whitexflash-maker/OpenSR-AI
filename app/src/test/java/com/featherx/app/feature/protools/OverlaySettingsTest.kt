package com.featherx.app.feature.protools

import org.junit.Assert.assertEquals
import org.junit.Test

class OverlaySettingsTest {

    @Test
    fun sanitizedClampsOutOfRangeValues() {
        val s = OverlaySettings(crosshairSizeDp = 500, filterStrength = -20).sanitized()
        assertEquals(OverlaySettings.MAX_CROSSHAIR_DP, s.crosshairSizeDp)
        assertEquals(0, s.filterStrength)
        assertEquals(OverlaySettings.MIN_CROSSHAIR_DP, OverlaySettings(crosshairSizeDp = 1).sanitized().crosshairSizeDp)
        assertEquals(100, OverlaySettings(filterStrength = 999).sanitized().filterStrength)
    }

    @Test
    fun filterColourNeverExceedsMaxAlpha() {
        val full = OverlaySettings(filterMode = FilterMode.DIM, filterStrength = 100).filterArgb()
        assertEquals(OverlaySettings.MAX_TINT_ALPHA, (full ushr 24) and 0xFF)
        assertEquals(0x000000, full and 0xFFFFFF)
    }

    @Test
    fun zeroStrengthIsFullyTransparent() {
        val c = OverlaySettings(filterMode = FilterMode.LIFT, filterStrength = 0).filterArgb()
        assertEquals(0, (c ushr 24) and 0xFF)
        assertEquals(0xFFFFFF, c and 0xFFFFFF)
    }

    @Test
    fun midStrengthAlphaIsProportional() {
        val c = OverlaySettings(filterStrength = 50).filterArgb()
        assertEquals(OverlaySettings.MAX_TINT_ALPHA * 50 / 100, (c ushr 24) and 0xFF)
    }

    @Test
    fun enumOrDefaultFallsBackOnUnknownOrNull() {
        assertEquals(CrosshairStyle.CROSS, enumOrDefault("CROSS", CrosshairStyle.DOT))
        assertEquals(CrosshairStyle.DOT, enumOrDefault("NOPE", CrosshairStyle.DOT))
        assertEquals(FilterMode.WARM, enumOrDefault(null, FilterMode.WARM))
    }

    @Test
    fun encodeDecodeRoundTrip() {
        val original = OverlaySettings(
            crosshairOn = true, crosshairStyle = CrosshairStyle.CIRCLE, crosshairColor = CrosshairColor.CYAN,
            crosshairSizeDp = 31, crosshairOffsetXDp = -12, crosshairOffsetYDp = 7,
            filterOn = true, filterMode = FilterMode.WARM, filterStrength = 64,
        )
        assertEquals(original, OverlaySettings.decode(original.encode()))
    }

    @Test
    fun decodeHandlesBlankBrokenAndOutOfRange() {
        assertEquals(null, OverlaySettings.decode(null))
        assertEquals(null, OverlaySettings.decode("  "))
        val d = OverlaySettings.decode("co=true&sz=abc&ox=999&zzz&fm=NOPE")!!
        assertEquals(true, d.crosshairOn)
        assertEquals(OverlaySettings.DEFAULT_CROSSHAIR_DP, d.crosshairSizeDp)
        assertEquals(OverlaySettings.MAX_OFFSET_DP, d.crosshairOffsetXDp)
        assertEquals(FilterMode.LIFT, d.filterMode)
    }
}
