package com.featherx.app.feature.protools

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StopwatchTest {

    @Test
    fun countsOnlyWhileRunningAndKeepsTimeAcrossPause() {
        val sw = Stopwatch()
        assertEquals(0L, sw.elapsedMs(1000))
        sw.start(1000)
        assertTrue(sw.running)
        assertEquals(500L, sw.elapsedMs(1500))
        sw.pause(2000)
        assertFalse(sw.running)
        assertEquals(1000L, sw.elapsedMs(9999)) // paused: does not advance
        sw.start(10_000)
        assertEquals(1300L, sw.elapsedMs(10_300))
    }

    @Test
    fun startWhileRunningDoesNotRestart() {
        val sw = Stopwatch()
        sw.start(100)
        sw.start(900)
        assertEquals(1000L, sw.elapsedMs(1100))
    }

    @Test
    fun resetClearsAndStops() {
        val sw = Stopwatch()
        sw.start(0)
        sw.reset()
        assertFalse(sw.running)
        assertEquals(0L, sw.elapsedMs(5000))
    }

    @Test
    fun formatsMinutesAndHours() {
        assertEquals("00:00.0", Stopwatch.format(0))
        assertEquals("03:07.4", Stopwatch.format(187_400))
        assertEquals("59:59.9", Stopwatch.format(3_599_900))
        assertEquals("1:02:09", Stopwatch.format(3_729_000))
        assertEquals("00:00.0", Stopwatch.format(-5))
    }
}
