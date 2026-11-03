package com.featherx.app.feature.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class DeviceNameTest {

    @Test
    fun brandAlreadyInModelIsNotRepeated() {
        assertEquals("TECNO KM8n", deviceName("TECNO", "TECNO KM8n"))
        assertEquals("TECNO KM8n", deviceName("tecno", "TECNO KM8n"))
    }

    @Test
    fun brandIsAddedWhenMissingFromModel() {
        assertEquals("samsung SM-S918B", deviceName("samsung", "SM-S918B"))
    }

    @Test
    fun missingPartsAreHandled() {
        assertEquals("Pixel", deviceName("", "Pixel"))
        assertEquals("Google", deviceName("Google", ""))
        assertEquals("Unknown device", deviceName("", ""))
        assertEquals("TECNO KM8n", deviceName("  TECNO ", " TECNO KM8n "))
    }
}
