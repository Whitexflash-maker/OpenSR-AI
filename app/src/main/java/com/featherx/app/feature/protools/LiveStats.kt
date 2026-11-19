package com.featherx.app.feature.protools

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.TrafficStats
import android.os.BatteryManager
import android.os.SystemClock
import java.net.InetSocketAddress
import java.net.Socket
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext

/** Pure helpers (unit-tested). */
object SpeedCalc {
    /** Bytes per second between two TrafficStats readings, or null when it cannot be known. */
    fun bytesPerSecond(prev: Long, now: Long, elapsedMs: Long): Long? {
        if (prev < 0 || now < 0) return null // TrafficStats.UNSUPPORTED is -1
        if (now < prev || elapsedMs <= 0) return null
        return (now - prev) * 1000 / elapsedMs
    }

    fun format(bytesPerSecond: Long?): String {
        if (bytesPerSecond == null) return "n/a"
        val kb = bytesPerSecond / 1024.0
        return if (kb >= 1024) String.format(Locale.US, "%.1f MB/s", kb / 1024.0)
        else String.format(Locale.US, "%.0f KB/s", kb)
    }

    fun formatMemory(mb: Long?): String {
        if (mb == null) return "n/a"
        return if (mb >= 1024) String.format(Locale.US, "%.1f GB", mb / 1024.0) else "$mb MB"
    }
}

data class StatsSnapshot(
    val batteryTempC: Float?,
    val ramFreeMb: Long?,
    val ramTotalMb: Long?,
    val lowMemory: Boolean,
    /** null = ping failed (no network or blocked) */
    val pingMs: Int?,
    val downBytesPerSec: Long?,
    val upBytesPerSec: Long?,
)

/**
 * Reads only public values:
 *  - battery temperature: the battery sensor (NOT the CPU temperature)
 *  - RAM: ActivityManager.MemoryInfo
 *  - speed: TrafficStats totals for the whole phone (all apps), differenced over time
 *  - ping: time to open a TCP connection to 1.1.1.1:443 (Cloudflare). It is NOT the ping to a game server.
 * It only runs while the sidebar panel is open (the collector is cancelled when the panel closes).
 */
class StatsSampler(context: Context) {
    private val app = context.applicationContext

    fun stream(intervalMs: Long = 2000L): Flow<StatsSnapshot> = flow {
        var prevRx = TrafficStats.getTotalRxBytes()
        var prevTx = TrafficStats.getTotalTxBytes()
        var prevAt = SystemClock.elapsedRealtime()
        while (true) {
            val ping = withContext(Dispatchers.IO) { measurePingMs() }
            val rx = TrafficStats.getTotalRxBytes()
            val tx = TrafficStats.getTotalTxBytes()
            val now = SystemClock.elapsedRealtime()
            val mem = ActivityManager.MemoryInfo()
            (app.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(mem)
            emit(
                StatsSnapshot(
                    batteryTempC = batteryTempC(),
                    ramFreeMb = mem.availMem / (1024 * 1024),
                    ramTotalMb = mem.totalMem / (1024 * 1024),
                    lowMemory = mem.lowMemory,
                    pingMs = ping,
                    downBytesPerSec = SpeedCalc.bytesPerSecond(prevRx, rx, now - prevAt),
                    upBytesPerSec = SpeedCalc.bytesPerSecond(prevTx, tx, now - prevAt),
                ),
            )
            prevRx = rx
            prevTx = tx
            prevAt = now
            delay(intervalMs)
        }
    }

    private fun batteryTempC(): Float? {
        val intent: Intent = app.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return null
        val tenths = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE)
        return if (tenths == Int.MIN_VALUE) null else tenths / 10f
    }

    private fun measurePingMs(): Int? = try {
        val start = System.nanoTime()
        Socket().use { it.connect(InetSocketAddress("1.1.1.1", 443), 2000) }
        ((System.nanoTime() - start) / 1_000_000).toInt()
    } catch (e: Exception) {
        null
    }
}
