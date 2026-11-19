package com.featherx.app.feature.protools

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SpeedCalcTest {

    @Test
    fun computesBytesPerSecond() {
        assertEquals(1000L, SpeedCalc.bytesPerSecond(0, 2000, 2000))
        assertEquals(500L, SpeedCalc.bytesPerSecond(1000, 1250, 500))
    }

    @Test
    fun unsupportedOrBackwardsOrZeroTimeIsNull() {
        assertNull(SpeedCalc.bytesPerSecond(-1, 100, 1000)) // TrafficStats.UNSUPPORTED
        assertNull(SpeedCalc.bytesPerSecond(100, -1, 1000))
        assertNull(SpeedCalc.bytesPerSecond(500, 100, 1000)) // counter reset
        assertNull(SpeedCalc.bytesPerSecond(0, 100, 0))
    }

    @Test
    fun formatsSpeed() {
        assertEquals("n/a", SpeedCalc.format(null))
        assertEquals("0 KB/s", SpeedCalc.format(0))
        assertEquals("512 KB/s", SpeedCalc.format(512 * 1024L))
        assertEquals("1.5 MB/s", SpeedCalc.format((1.5 * 1024 * 1024).toLong()))
    }

    @Test
    fun formatsMemory() {
        assertEquals("n/a", SpeedCalc.formatMemory(null))
        assertEquals("812 MB", SpeedCalc.formatMemory(812))
        assertEquals("1.5 GB", SpeedCalc.formatMemory(1536))
    }
}
