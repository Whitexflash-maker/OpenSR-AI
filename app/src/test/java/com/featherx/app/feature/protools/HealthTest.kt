package com.featherx.app.feature.protools

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HealthTest {

    @Test
    fun temperatureLevels() {
        assertEquals(HealthLevel.OK, HealthCheck.tempLevel(null))
        assertEquals(HealthLevel.OK, HealthCheck.tempLevel(40.0f)) // 40 is not "above 40"
        assertEquals(HealthLevel.WARN, HealthCheck.tempLevel(40.1f))
        assertEquals(HealthLevel.WARN, HealthCheck.tempLevel(44.9f))
        assertEquals(HealthLevel.CRITICAL, HealthCheck.tempLevel(45f))
    }

    @Test
    fun ramCriticalByLowFlagOrUnderTenPercent() {
        assertTrue(HealthCheck.ramCritical(2000, 4000, lowMemory = true))
        assertTrue(HealthCheck.ramCritical(300, 4000, lowMemory = false)) // 7 %
        assertFalse(HealthCheck.ramCritical(400, 4000, lowMemory = false)) // exactly 10 %
        assertFalse(HealthCheck.ramCritical(null, 4000, lowMemory = false))
        assertFalse(HealthCheck.ramCritical(100, 0, lowMemory = false))
    }

    @Test
    fun assessCombinesMessages() {
        val ok = HealthCheck.assess(35f, 2000, 4000, false)
        assertEquals(HealthLevel.OK, ok.level)
        assertNull(ok.message)

        val warm = HealthCheck.assess(42f, 2000, 4000, false)
        assertEquals(HealthLevel.WARN, warm.level)
        assertEquals("Warm 42.0 °C", warm.message)

        val both = HealthCheck.assess(46f, 100, 4000, false)
        assertEquals(HealthLevel.CRITICAL, both.level)
        assertEquals("Very hot 46.0 °C · RAM low", both.message)

        assertEquals(HealthLevel.CRITICAL, HealthCheck.assess(30f, 100, 4000, false).level)
    }
}
