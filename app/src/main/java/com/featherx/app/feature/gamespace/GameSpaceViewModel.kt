package com.featherx.app.feature.gamespace

import android.app.Application
import android.util.LruCache
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class GameSpaceViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = GameRepository(application)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    // At most 64 small icons (about 36 KB each) are kept in memory.
    private val iconCache = LruCache<String, ImageBitmap>(MAX_CACHED_ICONS)

    private var prefs = LibraryPrefs()
    private var lastLaunch: Map<String, Long> = emptyMap()

    var loading by mutableStateOf(true)
        private set
    var installed by mutableStateOf<List<InstalledApp>>(emptyList())
        private set
    var library by mutableStateOf<List<LibraryEntry>>(emptyList())
        private set
    var recents by mutableStateOf<List<InstalledApp>>(emptyList())
        private set
    var message by mutableStateOf<String?>(null)
        private set

    /** Re-reads installed apps (new installs, uninstalls) and the saved library. */
    fun refresh() {
        scope.launch {
            loading = true
            val data = withContext(Dispatchers.IO) {
                Triple(repository.queryLaunchableApps(), repository.loadPrefs(), repository.loadLastLaunch())
            }
            installed = data.first
            prefs = data.second
            lastLaunch = data.third
            recompute()
            loading = false
        }
    }

    fun add(packageName: String) {
        prefs = LibraryLogic.withAdded(prefs, packageName)
        repository.savePrefs(prefs)
        message = null
        recompute()
    }

    fun remove(packageName: String) {
        prefs = LibraryLogic.withRemoved(prefs, packageName)
        repository.savePrefs(prefs)
        message = null
        recompute()
    }

    fun launch(packageName: String) {
        if (repository.launch(packageName)) {
            val now = System.currentTimeMillis()
            lastLaunch = lastLaunch + (packageName to now)
            repository.saveLaunch(packageName, now)
            message = null
            recompute()
        } else {
            message = "Could not open that app. It may have been uninstalled or has no launch screen."
            refresh()
        }
    }

    suspend fun loadIcon(packageName: String): ImageBitmap? {
        iconCache.get(packageName)?.let { return it }
        val bitmap = withContext(Dispatchers.IO) { repository.loadIconBitmap(packageName, ICON_SIZE_PX) }
        val image = bitmap?.asImageBitmap() ?: return null
        iconCache.put(packageName, image)
        return image
    }

    private fun recompute() {
        library = LibraryLogic.resolve(installed, prefs)
        recents = LibraryLogic.recents(library, lastLaunch)
    }

    override fun onCleared() {
        scope.cancel()
        iconCache.evictAll()
    }

    private companion object {
        const val MAX_CACHED_ICONS = 64
        const val ICON_SIZE_PX = 96
    }
}
