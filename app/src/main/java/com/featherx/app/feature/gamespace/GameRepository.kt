package com.featherx.app.feature.gamespace

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.os.Build

/**
 * Talks to Android (PackageManager, SharedPreferences). Only public APIs.
 * It reads app names, icons and the "game" category of apps that have a launcher icon.
 * Nothing is sent anywhere; everything stays on the phone.
 */
class GameRepository(context: Context) {

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** Apps with a launcher icon, except FeatherX itself. Needs the <queries> entry in the manifest. */
    fun queryLaunchableApps(): List<InstalledApp> {
        val pm = appContext.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolved: List<ResolveInfo> = if (Build.VERSION.SDK_INT >= 33) {
            pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0L))
        } else {
            queryLegacy(pm, intent)
        }
        return resolved
            .asSequence()
            .map { it.activityInfo }
            .filter { it.packageName != appContext.packageName }
            .distinctBy { it.packageName }
            .map { info ->
                InstalledApp(
                    packageName = info.packageName,
                    label = info.loadLabel(pm).toString().ifBlank { info.packageName },
                    isGameCategory = info.applicationInfo?.category == ApplicationInfo.CATEGORY_GAME,
                )
            }
            .toList()
    }

    @Suppress("DEPRECATION")
    private fun queryLegacy(pm: PackageManager, intent: Intent): List<ResolveInfo> =
        pm.queryIntentActivities(intent, 0)

    fun loadPrefs(): LibraryPrefs = LibraryPrefs(
        manual = readSet(KEY_MANUAL),
        removed = readSet(KEY_REMOVED),
    )

    fun savePrefs(p: LibraryPrefs) {
        prefs.edit()
            .putStringSet(KEY_MANUAL, HashSet(p.manual))
            .putStringSet(KEY_REMOVED, HashSet(p.removed))
            .apply()
    }

    fun loadLastLaunch(): Map<String, Long> {
        val result = HashMap<String, Long>()
        for ((key, value) in prefs.all) {
            if (key.startsWith(KEY_LAUNCH_PREFIX) && value is Long) {
                result[key.removePrefix(KEY_LAUNCH_PREFIX)] = value
            }
        }
        return result
    }

    fun saveLaunch(packageName: String, timeMillis: Long) {
        prefs.edit().putLong(KEY_LAUNCH_PREFIX + packageName, timeMillis).apply()
    }

    /** Starts the app's normal launcher screen. Returns false if it cannot be started. */
    fun launch(packageName: String): Boolean {
        val intent = appContext.packageManager.getLaunchIntentForPackage(packageName) ?: return false
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            appContext.startActivity(intent)
            true
        } catch (e: ActivityNotFoundException) {
            false
        } catch (e: SecurityException) {
            false
        }
    }

    /** Small square icon bitmap, or null if the app is gone. */
    fun loadIconBitmap(packageName: String, sizePx: Int): Bitmap? {
        return try {
            drawableToBitmap(appContext.packageManager.getApplicationIcon(packageName), sizePx)
        } catch (e: PackageManager.NameNotFoundException) {
            null
        }
    }

    private fun drawableToBitmap(drawable: Drawable, sizePx: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, sizePx, sizePx)
        drawable.draw(canvas)
        return bitmap
    }

    // getStringSet returns a set that must not be modified, so always copy it.
    private fun readSet(key: String): Set<String> = HashSet(prefs.getStringSet(key, emptySet()) ?: emptySet())

    private companion object {
        const val PREFS_NAME = "featherx_game_space"
        const val KEY_MANUAL = "manual"
        const val KEY_REMOVED = "removed"
        const val KEY_LAUNCH_PREFIX = "launch:"
    }
}
