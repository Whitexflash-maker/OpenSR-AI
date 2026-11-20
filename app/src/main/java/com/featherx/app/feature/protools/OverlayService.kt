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
import androidx.compose.runtime.snapshotFlow
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Foreground service that owns three overlay windows (needs the "Display over other apps" permission):
 *  1. two edge wings, LEFT (Monitor) and RIGHT (Tools): a thin trigger strip when closed, a full-height wing when open
 *  2. crosshair, centred, NOT touchable (touches pass through to the game)
 *  3. screen filter, full screen, NOT touchable
 * Windows 2 and 3 are created first (so the sidebar stays on top) and are hidden while their switch is off.
 */
class OverlayService : Service() {

    private lateinit var wm: WindowManager
    private val owner = OverlayLifecycleOwner()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var started = false

    private var crosshairView: CrosshairView? = null
    private var filterView: View? = null
    private var frosted = false

    /** One edge sidebar window (its own ComposeView, position and open/closed state). */
    private inner class Sidebar(val side: SidebarSide) {
        var view: ComposeView? = null
        var params: WindowManager.LayoutParams? = null
        var expanded by mutableStateOf(false)   // wing open (or sliding out)
        var docked by mutableStateOf(true)       // window is the thin trigger strip
        var triggerY = 0                          // where the trigger sits while closed
    }

    private val leftBar = Sidebar(SidebarSide.LEFT)
    private val rightBar = Sidebar(SidebarSide.RIGHT)

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
            // Window order = order of adding (later = on top). Crosshair and filter windows are created
            // FIRST and only shown/hidden afterwards, so the sidebar is always the top-most window and
            // the crosshair can never be drawn over the panel.
            addCrosshairWindow()
            addFilterWindow()
            addSidebar(leftBar)
            addSidebar(rightBar)
            started = true
            OverlayController.setRunning(true)
            // Blur only while the panel is open, so the closed handle never blurs a strip of the game.
            scope.launch { snapshotFlow { leftBar.expanded }.collect { applySidebarBlur(leftBar, it) } }
            scope.launch { snapshotFlow { rightBar.expanded }.collect { applySidebarBlur(rightBar, it) } }
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
        removeSafely(leftBar.view); leftBar.view = null
        removeSafely(rightBar.view); rightBar.view = null
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

    private fun addSidebar(bar: Sidebar) {
        val left = bar.side == SidebarSide.LEFT
        bar.triggerY = resources.displayMetrics.heightPixels / 3
        val params = baseParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        ).apply {
            gravity = Gravity.TOP or (if (left) Gravity.START else Gravity.END)
            x = 0 // flush against the extreme screen edge
            y = bar.triggerY
        }
        val sampler = StatsSampler(this)
        // Real blur behind an open wing (public API, Android 12+, only where the device supports it).
        frosted = Build.VERSION.SDK_INT >= 31 && wm.isCrossWindowBlurEnabled
        val view = ComposeView(this).apply {
            setViewTreeLifecycleOwner(owner)
            setViewTreeSavedStateRegistryOwner(owner)
            setViewTreeViewModelStoreOwner(owner)
            setContent {
                FeatherXTheme {
                    SidebarOverlay(
                        side = bar.side,
                        expanded = bar.expanded,
                        docked = bar.docked,
                        onToggle = { toggleBar(bar) },
                        onOpenBoth = { toggleBoth() },
                        onClose = { closeBar(bar) },
                        currentY = { bar.params?.y?.toFloat() ?: 0f },
                        onMoveTo = { y -> moveSidebarTo(bar, y) },
                        onStop = { stopSelf() },
                        sampler = sampler,
                        frosted = frosted,
                    )
                }
            }
        }
        bar.params = params
        bar.view = view
        wm.addView(view, params)
    }

    /**
     * Open: the window grows to full height at once and the wing slides in.
     * Close: the wing slides out first, then the window shrinks back to the thin trigger strip.
     */
    private fun setOpen(bar: Sidebar, open: Boolean) {
        if (bar.expanded == open) return
        bar.expanded = open
        val view = bar.view ?: return
        val params = bar.params ?: return
        if (open) {
            bar.docked = false
            params.height = WindowManager.LayoutParams.MATCH_PARENT
            params.y = 0
            try { wm.updateViewLayout(view, params) } catch (e: IllegalArgumentException) { }
        } else {
            scope.launch {
                delay(CLOSE_ANIMATION_MS)
                if (!bar.expanded) {
                    params.height = WindowManager.LayoutParams.WRAP_CONTENT
                    params.y = bar.triggerY
                    try { wm.updateViewLayout(view, params) } catch (e: IllegalArgumentException) { }
                    bar.docked = true
                }
            }
        }
    }

    private fun toggleBar(bar: Sidebar) {
        val open = !bar.expanded
        if (OverlayController.linkWings.value) {
            setOpen(leftBar, open)
            setOpen(rightBar, open)
        } else {
            setOpen(bar, open)
        }
    }

    private fun closeBar(bar: Sidebar) {
        if (OverlayController.linkWings.value) {
            setOpen(leftBar, false)
            setOpen(rightBar, false)
        } else {
            setOpen(bar, false)
        }
    }

    /** Long-press on a trigger: both wings open together (or both close if both are already open). */
    private fun toggleBoth() {
        val open = !(leftBar.expanded && rightBar.expanded)
        setOpen(leftBar, open)
        setOpen(rightBar, open)
    }

    private fun applySidebarBlur(bar: Sidebar, open: Boolean) {
        if (!frosted || Build.VERSION.SDK_INT < 31) return
        val view = bar.view ?: return
        val params = bar.params ?: return
        params.blurBehindRadius = if (open) BLUR_RADIUS_PX else 0
        params.flags = if (open) params.flags or WindowManager.LayoutParams.FLAG_BLUR_BEHIND
        else params.flags and WindowManager.LayoutParams.FLAG_BLUR_BEHIND.inv()
        try { wm.updateViewLayout(view, params) } catch (e: IllegalArgumentException) { }
    }

    /** Only the thin trigger can be moved (an open wing is full height). */
    private fun moveSidebarTo(bar: Sidebar, y: Float) {
        if (!bar.docked) return
        val view = bar.view ?: return
        val params = bar.params ?: return
        val maxY = (resources.displayMetrics.heightPixels - view.height).coerceAtLeast(0)
        params.y = y.toInt().coerceIn(0, maxY)
        bar.triggerY = params.y
        try {
            wm.updateViewLayout(view, params)
        } catch (e: IllegalArgumentException) {
            // view already removed
        }
    }

    private fun addCrosshairWindow() {
        val s = OverlayController.settings.value
        val sizePx = crosshairPx(s)
        val view = CrosshairView(this).apply {
            crosshairStyle = s.crosshairStyle
            argb = s.crosshairColor.argb
            visibility = if (s.crosshairOn) View.VISIBLE else View.GONE
        }
        // Its own window, centred on the screen, never takes focus or touches.
        val params = baseParams(sizePx, sizePx, passThroughFlags).apply {
            gravity = Gravity.CENTER
            x = offsetPx(s.crosshairOffsetXDp)
            y = offsetPx(s.crosshairOffsetYDp)
            alpha = MAX_UNTRUSTED_ALPHA
            applyCutoutMode(this)
        }
        wm.addView(view, params)
        crosshairView = view
    }

    private fun addFilterWindow() {
        val s = OverlayController.settings.value
        val view = View(this).apply {
            setBackgroundColor(s.filterArgb())
            visibility = if (s.filterOn) View.VISIBLE else View.GONE
        }
        val params = baseParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            passThroughFlags,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            alpha = MAX_UNTRUSTED_ALPHA
            applyCutoutMode(this)
        }
        wm.addView(view, params)
        filterView = view
    }

    private fun crosshairPx(s: OverlaySettings) = offsetPx(s.crosshairSizeDp)

    private fun offsetPx(dp: Int) = (dp * resources.displayMetrics.density).toInt()

    private fun applyCrosshair(s: OverlaySettings) {
        val view = crosshairView ?: return
        view.crosshairStyle = s.crosshairStyle
        view.argb = s.crosshairColor.argb
        view.visibility = if (s.crosshairOn) View.VISIBLE else View.GONE
        val params = view.layoutParams as? WindowManager.LayoutParams ?: return
        val sizePx = crosshairPx(s)
        val x = offsetPx(s.crosshairOffsetXDp)
        val y = offsetPx(s.crosshairOffsetYDp)
        if (params.width != sizePx || params.x != x || params.y != y) {
            params.width = sizePx
            params.height = sizePx
            params.x = x
            params.y = y
            try { wm.updateViewLayout(view, params) } catch (e: IllegalArgumentException) { }
        }
    }

    private fun applyFilter(s: OverlaySettings) {
        val view = filterView ?: return
        view.setBackgroundColor(s.filterArgb())
        view.visibility = if (s.filterOn) View.VISIBLE else View.GONE
    }

    /** Draw under the camera cut-out too, so the filter covers the whole screen. */
    private fun applyCutoutMode(params: WindowManager.LayoutParams) {
        params.layoutInDisplayCutoutMode =
            if (Build.VERSION.SDK_INT >= 30) WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            else WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
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
        private const val BLUR_RADIUS_PX = 28
        private const val CLOSE_ANIMATION_MS = 280L
    }
}
