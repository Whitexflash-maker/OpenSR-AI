package com.featherx.app.feature.protools

import android.app.Application
import android.content.Intent
import android.provider.Settings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel

class ProToolsViewModel(app: Application) : AndroidViewModel(app) {

    /** Whether "Display over other apps" is granted. Re-checked every time the app comes back to the screen. */
    var canDrawOverlays by mutableStateOf(false)
        private set

    init {
        OverlayController.init(app)
        refreshPermission()
    }

    fun refreshPermission() {
        canDrawOverlays = Settings.canDrawOverlays(getApplication())
    }

    fun startOverlay() {
        refreshPermission()
        if (!canDrawOverlays) return
        val app = getApplication<Application>()
        app.startForegroundService(Intent(app, OverlayService::class.java))
    }

    fun stopOverlay() {
        val app = getApplication<Application>()
        app.stopService(Intent(app, OverlayService::class.java))
    }
}
