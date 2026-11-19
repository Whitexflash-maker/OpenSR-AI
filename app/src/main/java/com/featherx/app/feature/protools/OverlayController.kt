package com.featherx.app.feature.protools

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * One place that both the app screen and the overlay service read and write.
 * Same process, so a StateFlow is enough. Settings are saved to SharedPreferences.
 */
object OverlayController {
    private const val PREFS = "pro_tools_overlay"

    private val _settings = MutableStateFlow(OverlaySettings())
    val settings: StateFlow<OverlaySettings> = _settings.asStateFlow()

    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running.asStateFlow()

    private var prefs: SharedPreferences? = null

    @Synchronized
    fun init(context: Context) {
        if (prefs != null) return
        val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs = p
        _settings.value = OverlaySettings(
            crosshairOn = p.getBoolean("crosshairOn", false),
            crosshairStyle = enumOrDefault(p.getString("crosshairStyle", null), CrosshairStyle.DOT),
            crosshairColor = enumOrDefault(p.getString("crosshairColor", null), CrosshairColor.RED),
            crosshairSizeDp = p.getInt("crosshairSizeDp", OverlaySettings.DEFAULT_CROSSHAIR_DP),
            filterOn = p.getBoolean("filterOn", false),
            filterMode = enumOrDefault(p.getString("filterMode", null), FilterMode.LIFT),
            filterStrength = p.getInt("filterStrength", OverlaySettings.DEFAULT_STRENGTH),
        ).sanitized()
    }

    fun update(transform: (OverlaySettings) -> OverlaySettings) {
        val next = transform(_settings.value).sanitized()
        _settings.value = next
        prefs?.edit()
            ?.putBoolean("crosshairOn", next.crosshairOn)
            ?.putString("crosshairStyle", next.crosshairStyle.name)
            ?.putString("crosshairColor", next.crosshairColor.name)
            ?.putInt("crosshairSizeDp", next.crosshairSizeDp)
            ?.putBoolean("filterOn", next.filterOn)
            ?.putString("filterMode", next.filterMode.name)
            ?.putInt("filterStrength", next.filterStrength)
            ?.apply()
    }

    fun setRunning(value: Boolean) {
        _running.value = value
    }
}
