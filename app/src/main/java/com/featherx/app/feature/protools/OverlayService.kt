package com.featherx.app.feature.protools

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.graphics.drawable.Icon
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.featherx.app.MainActivity
import com.featherx.app.R
import com.featherx.app.ui.theme.FeatherXTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Foreground service that owns three overlay windows (needs the "Display over other apps" permission):
 *  1. sidebar (handle + panel), touchable, tiny when closed
 *  2. crosshair, centred, NOT touchable (touches pass through to the game)
 *  3. screen filter, full screen, NOT touchable
 * Windows 2 and 3 exist only while their switch is on.
 */
class OverlayService : Service() {

    private lateinit var wm: WindowManager
    private val owner = OverlayLifecycleOwner()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var started = false

    private var crosshairView: CrosshairView? = null
    private var filterView: View? = null
    private var sidebarView: ComposeView? = null
    private var sidebarParams: WindowManager.LayoutParams? = null
    private var expanded by mutableStateOf(false)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        OverlayController.init(this)
        wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager

        // startForegroundService() requires startForeground() promptly, so do it first.
        startInForeground()

        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return
        }
        try {
            owner.start()
            addSidebar()
            started = true
            OverlayController.setRunning(true)
            scope.launch {
                OverlayController.settings.collect { s ->
                    applyCrosshair(s)
                    applyFilter(s)
                }
            }
        } catch (e: Exception) { // e.g. permission revoked between the check and addView
            stopSelf()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) stopSelf()
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        removeSafely(crosshairView); crosshairView = null
        removeSafely(filterView); filterView = null
        removeSafely(sidebarView); sidebarView = null
        if (started) owner.stop()
        OverlayController.setRunning(false)
        super.onDestroy()
    }

    // ------------------------------------------------------------------ windows

    private fun baseParams(width: Int, height: Int, flags: Int) = WindowManager.LayoutParams(
        width, height,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        flags,
        PixelFormat.TRANSLUCENT,
    )

    private val passThroughFlags =
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS

    private fun addSidebar() {
        val params = baseParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = 0
            y = resources.displayMetrics.heightPixels / 3
        }
        val sampler = StatsSampler(this)
        val view = ComposeView(this).apply {
            setViewTreeLifecycleOwner(owner)
            setViewTreeSavedStateRegistryOwner(owner)
            setViewTreeViewModelStoreOwner(owner)
            setContent {
                FeatherXTheme {
                    SidebarOverlay(
                        expanded = expanded,
                        onToggle = { expanded = !expanded },
                        currentY = { sidebarParams?.y?.toFloat() ?: 0f },
                        onMoveTo = { y -> moveSidebarTo(y) },
                        onStop = { stopSelf() },
                        sampler = sampler,
                    )
                }
            }
        }
        sidebarParams = params
        sidebarView = view
        wm.addView(view, params)
    }

    private fun moveSidebarTo(y: Float) {
        val view = sidebarView ?: return
        val params = sidebarParams ?: return
        val maxY = (resources.displayMetrics.heightPixels - view.height).coerceAtLeast(0)
        params.y = y.toInt().coerceIn(0, maxY)
        try {
            wm.updateViewLayout(view, params)
        } catch (e: IllegalArgumentException) {
            // view already removed
        }
    }

    private fun applyCrosshair(s: OverlaySettings) {
        if (!s.crosshairOn) {
            removeSafely(crosshairView)
            crosshairView = null
            return
        }
        val sizePx = (s.crosshairSizeDp * resources.displayMetrics.density).toInt()
        val existing = crosshairView
        if (existing == null) {
            val view = CrosshairView(this).apply {
                crosshairStyle = s.crosshairStyle
                argb = s.crosshairColor.argb
            }
            val params = baseParams(sizePx, sizePx, passThroughFlags).apply {
                gravity = Gravity.CENTER
                alpha = MAX_UNTRUSTED_ALPHA
                applyCutoutMode(this)
            }
            if (tryAdd(view, params)) crosshairView = view
        } else {
            existing.crosshairStyle = s.crosshairStyle
            existing.argb = s.crosshairColor.argb
            val params = existing.layoutParams as? WindowManager.LayoutParams ?: return
            if (params.width != sizePx) {
                params.width = sizePx
                params.height = sizePx
                try { wm.updateViewLayout(existing, params) } catch (e: IllegalArgumentException) { }
            }
        }
    }

    private fun applyFilter(s: OverlaySettings) {
        if (!s.filterOn) {
            removeSafely(filterView)
            filterView = null
            return
        }
        val existing = filterView
        if (existing == null) {
            val view = View(this).apply { setBackgroundColor(s.filterArgb()) }
            val params = baseParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                passThroughFlags,
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                alpha = MAX_UNTRUSTED_ALPHA
                applyCutoutMode(this)
            }
            if (tryAdd(view, params)) filterView = view
        } else {
            existing.setBackgroundColor(s.filterArgb())
        }
    }

    /** Draw under the camera cut-out too, so the filter covers the whole screen. */
    private fun applyCutoutMode(params: WindowManager.LayoutParams) {
        params.layoutInDisplayCutoutMode =
            if (Build.VERSION.SDK_INT >= 30) WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            else WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
    }

    private fun tryAdd(view: View, params: WindowManager.LayoutParams): Boolean = try {
        wm.addView(view, params)
        true
    } catch (e: Exception) {
        false
    }

    private fun removeSafely(view: View?) {
        if (view == null) return
        try {
            wm.removeView(view)
        } catch (e: IllegalArgumentException) {
            // not attached
        }
    }

    // ------------------------------------------------------------------ notification

    private fun startInForeground() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Pro Tools overlay", NotificationManager.IMPORTANCE_LOW),
        )
        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val stop = PendingIntent.getService(
            this, 1,
            Intent(this, OverlayService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val stopAction = Notification.Action.Builder(
            Icon.createWithResource(this, R.drawable.ic_stat_featherx), "Stop", stop,
        ).build()
        val notification = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_featherx)
            .setContentTitle("FeatherX Pro Tools")
            .setContentText("Sidebar overlay is running")
            .setContentIntent(open)
            .addAction(stopAction)
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    companion object {
        const val ACTION_STOP = "com.featherx.app.action.STOP_OVERLAY"
        private const val CHANNEL_ID = "pro_tools_overlay"
        private const val NOTIFICATION_ID = 1001

        /** Android 12+ blocks touches that pass through an overlay more opaque than 0.8. */
        private const val MAX_UNTRUSTED_ALPHA = 0.8f
    }
}
