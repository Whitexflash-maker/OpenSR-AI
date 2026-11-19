package com.featherx.app.feature.protools

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.provider.Settings

/**
 * One-tap mute through Android's public Do Not Disturb API (alarms still ring).
 * Android requires the user to grant "Do Not Disturb access" to the app once; without it nothing can be changed.
 */
class QuickMute(private val context: Context) {
    private val nm: NotificationManager = context.getSystemService(NotificationManager::class.java)

    fun hasAccess(): Boolean = nm.isNotificationPolicyAccessGranted

    fun isMuted(): Boolean = when (nm.currentInterruptionFilter) {
        NotificationManager.INTERRUPTION_FILTER_ALARMS,
        NotificationManager.INTERRUPTION_FILTER_NONE -> true
        else -> false
    }

    /** Returns false when access has not been granted (nothing was changed). */
    fun toggle(): Boolean {
        if (!hasAccess()) return false
        if (isMuted()) {
            nm.setInterruptionFilter(previousFilter ?: NotificationManager.INTERRUPTION_FILTER_ALL)
        } else {
            val current = nm.currentInterruptionFilter
            previousFilter = current.takeIf { it != NotificationManager.INTERRUPTION_FILTER_UNKNOWN }
            nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALARMS)
        }
        return true
    }

    fun openAccessSettings() {
        try {
            context.startActivity(
                Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        } catch (e: Exception) {
            // no settings screen on this device
        }
    }

    private companion object {
        /** What DND was set to before we muted, so un-muting restores it. */
        @Volatile var previousFilter: Int? = null
    }
}
