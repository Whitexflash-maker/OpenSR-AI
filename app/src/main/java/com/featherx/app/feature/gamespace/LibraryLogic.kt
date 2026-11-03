package com.featherx.app.feature.gamespace

import java.util.Locale

/** An app that has a launcher icon on this phone. */
data class InstalledApp(
    val packageName: String,
    val label: String,
    /** True only when the app itself declares the "game" category to Android. */
    val isGameCategory: Boolean,
)

data class LibraryEntry(val app: InstalledApp, val addedByUser: Boolean)

/**
 * What the user changed.
 * manual  = apps the user added by hand.
 * removed = apps the user removed (stops automatic detection from bringing them back).
 */
data class LibraryPrefs(
    val manual: Set<String> = emptySet(),
    val removed: Set<String> = emptySet(),
)

/** Pure Kotlin (no Android classes) so it can be unit-tested without a phone. */
object LibraryLogic {

    private fun sortKey(label: String): String = label.lowercase(Locale.ROOT)

    /** Library = apps the user added + auto-detected games the user has not removed. */
    fun resolve(installed: List<InstalledApp>, prefs: LibraryPrefs): List<LibraryEntry> =
        installed
            .filter { it.packageName in prefs.manual || (it.isGameCategory && it.packageName !in prefs.removed) }
            .map { LibraryEntry(it, addedByUser = it.packageName in prefs.manual) }
            .sortedWith(compareBy<LibraryEntry>({ sortKey(it.app.label) }, { it.app.packageName }))

    fun withAdded(prefs: LibraryPrefs, packageName: String): LibraryPrefs =
        LibraryPrefs(manual = prefs.manual + packageName, removed = prefs.removed - packageName)

    fun withRemoved(prefs: LibraryPrefs, packageName: String): LibraryPrefs =
        LibraryPrefs(manual = prefs.manual - packageName, removed = prefs.removed + packageName)

    /** Apps that could still be added, filtered by a search text (matches name or package). */
    fun addCandidates(
        installed: List<InstalledApp>,
        library: List<LibraryEntry>,
        query: String,
    ): List<InstalledApp> {
        val inLibrary = library.mapTo(HashSet()) { it.app.packageName }
        val q = query.trim().lowercase(Locale.ROOT)
        return installed
            .filter { app ->
                app.packageName !in inLibrary &&
                    (q.isEmpty() ||
                        app.label.lowercase(Locale.ROOT).contains(q) ||
                        app.packageName.lowercase(Locale.ROOT).contains(q))
            }
            .sortedWith(compareBy<InstalledApp>({ sortKey(it.label) }, { it.packageName }))
    }

    /** Library apps launched from FeatherX, newest first. Apps never launched here are not listed. */
    fun recents(
        library: List<LibraryEntry>,
        lastLaunch: Map<String, Long>,
        limit: Int = 5,
    ): List<InstalledApp> =
        library
            .mapNotNull { entry -> lastLaunch[entry.app.packageName]?.let { time -> entry.app to time } }
            .sortedByDescending { it.second }
            .take(limit)
            .map { it.first }
}
