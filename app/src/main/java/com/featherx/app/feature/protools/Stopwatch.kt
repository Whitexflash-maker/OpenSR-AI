package com.featherx.app.feature.protools

import java.util.Locale

/**
 * Match / zone stopwatch. Pure logic: the caller passes the current time from
 * SystemClock.elapsedRealtime() (monotonic; nothing about the system clock is changed).
 */
class Stopwatch {
    private var accumulatedMs = 0L
    private var startedAt: Long? = null

    val running: Boolean get() = startedAt != null

    fun start(now: Long) {
        if (startedAt == null) startedAt = now
    }

    fun pause(now: Long) {
        startedAt?.let { accumulatedMs += (now - it).coerceAtLeast(0) }
        startedAt = null
    }

    fun reset() {
        accumulatedMs = 0L
        startedAt = null
    }

    fun elapsedMs(now: Long): Long = accumulatedMs + (startedAt?.let { (now - it).coerceAtLeast(0) } ?: 0L)

    companion object {
        /** "03:07.4", or "1:02:09" from one hour on. */
        fun format(ms: Long): String {
            val total = ms.coerceAtLeast(0)
            val tenths = (total / 100) % 10
            val totalSeconds = total / 1000
            val s = totalSeconds % 60
            val m = (totalSeconds / 60) % 60
            val h = totalSeconds / 3600
            return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s)
            else String.format(Locale.US, "%02d:%02d.%d", m, s, tenths)
        }
    }
}
