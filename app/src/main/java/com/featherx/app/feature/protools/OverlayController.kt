package com.featherx.app.feature.protools

import android.content.Context
import android.content.SharedPreferences
import android.os.SystemClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class ActiveGame(val packageName: String, val label: String)

/**
 * One place that both the app screens and the overlay service read and write (same process, so StateFlow is enough).
 * Holds: the current overlay settings, whether the overlay runs, the stopwatch, and per-game profiles.
 */
object OverlayController {
    private const val PREFS = "pro_tools_overlay"
    private const val PROFILE_PREFS = "pro_tools_profiles"

    private val _settings = MutableStateFlow(OverlaySettings())
    val settings: StateFlow<OverlaySettings> = _settings.asStateFlow()

    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running.asStateFlow()

    /** The game most recently launched from Game Space (profiles belong to it). */
    private val _activeGame = MutableStateFlow<ActiveGame?>(null)
    val activeGame: StateFlow<ActiveGame?> = _activeGame.asStateFlow()

    /** Package names that have a saved profile. */
    private val _profiled = MutableStateFlow<Set<String>>(emptySet())
    val profiled: StateFlow<Set<String>> = _profiled.asStateFlow()

    /** When on, opening or closing one wing opens or closes both (RedMagic-style symmetric HUD). */
    private val _linkWings = MutableStateFlow(false)
    val linkWings: StateFlow<Boolean> = _linkWings.asStateFlow()

    val stopwatch = Stopwatch()

    private var prefs: SharedPreferences? = null
    private var profilePrefs: SharedPreferences? = null

    @Synchronized
    fun init(context: Context) {
        if (prefs != null) return
        val app = context.applicationContext
        val p = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val pp = app.getSharedPreferences(PROFILE_PREFS, Context.MODE_PRIVATE)
        prefs = p
        profilePrefs = pp
        _settings.value = OverlaySettings.decode(p.getString("settings", null)) ?: OverlaySettings()
        _linkWings.value = p.getBoolean("linkWings", false)
        val pkg = p.getString("activePkg", null)
        val label = p.getString("activeLabel", null)
        if (pkg != null && label != null) _activeGame.value = ActiveGame(pkg, label)
        _profiled.value = pp.all.keys.toSet()
    }

    fun update(transform: (OverlaySettings) -> OverlaySettings) {
        val next = transform(_settings.value).sanitized()
        _settings.value = next
        prefs?.edit()?.putString("settings", next.encode())?.apply()
    }

    fun setLinkWings(value: Boolean) {
        _linkWings.value = value
        prefs?.edit()?.putBoolean("linkWings", value)?.apply()
    }

    fun setRunning(value: Boolean) {
        _running.value = value
    }

    // ---------------------------------------------------------------- stopwatch
    fun now(): Long = SystemClock.elapsedRealtime()

    // ---------------------------------------------------------------- per-game profiles

    /** Called by Game Space after a game was launched from FeatherX. Applies that game's saved profile, if any. */
    fun onGameLaunched(packageName: String, label: String) {
        _activeGame.value = ActiveGame(packageName, label)
        prefs?.edit()?.putString("activePkg", packageName)?.putString("activeLabel", label)?.apply()
        OverlaySettings.decode(profilePrefs?.getString(packageName, null))?.let { saved -> update { saved } }
    }

    /** Saves the current crosshair and filter settings for the active game. Returns false if no game is active. */
    fun saveProfileForActiveGame(): Boolean {
        val game = _activeGame.value ?: return false
        profilePrefs?.edit()?.putString(game.packageName, _settings.value.encode())?.apply()
        _profiled.value = _profiled.value + game.packageName
        return true
    }

    fun clearProfileForActiveGame() {
        val game = _activeGame.value ?: return
        profilePrefs?.edit()?.remove(game.packageName)?.apply()
        _profiled.value = _profiled.value - game.packageName
    }
}
