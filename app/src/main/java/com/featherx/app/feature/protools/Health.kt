package com.featherx.app.feature.protools

import java.util.Locale

enum class HealthLevel { OK, WARN, CRITICAL }

data class Health(val level: HealthLevel, val message: String?)

/** Pure rules for the warning badge. Temperature is the BATTERY sensor, not the CPU. */
object HealthCheck {
    const val TEMP_WARN_C = 40f
    const val TEMP_CRITICAL_C = 45f
    const val RAM_CRITICAL_PERCENT = 10

    fun tempLevel(tempC: Float?): HealthLevel = when {
        tempC == null -> HealthLevel.OK
        tempC >= TEMP_CRITICAL_C -> HealthLevel.CRITICAL
        tempC > TEMP_WARN_C -> HealthLevel.WARN
        else -> HealthLevel.OK
    }

    fun ramCritical(freeMb: Long?, totalMb: Long?, lowMemory: Boolean): Boolean {
        if (lowMemory) return true
        if (freeMb == null || totalMb == null || totalMb <= 0) return false
        return freeMb * 100 / totalMb < RAM_CRITICAL_PERCENT
    }

    fun assess(tempC: Float?, freeMb: Long?, totalMb: Long?, lowMemory: Boolean): Health {
        val temp = tempLevel(tempC)
        val ram = ramCritical(freeMb, totalMb, lowMemory)
        val parts = buildList {
            if (temp == HealthLevel.CRITICAL) add(String.format(Locale.US, "Very hot %.1f °C", tempC))
            else if (temp == HealthLevel.WARN) add(String.format(Locale.US, "Warm %.1f °C", tempC))
            if (ram) add("RAM low")
        }
        val level = when {
            temp == HealthLevel.CRITICAL || ram -> HealthLevel.CRITICAL
            temp == HealthLevel.WARN -> HealthLevel.WARN
            else -> HealthLevel.OK
        }
        return Health(level, parts.takeIf { it.isNotEmpty() }?.joinToString(" · "))
    }
}
