package com.featherx.app.feature.protools

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.featherx.app.ui.FxCard
import com.featherx.app.ui.FxChip
import com.featherx.app.ui.FxSwatch
import com.featherx.app.ui.InfoRow
import com.featherx.app.ui.ScreenHeader
import com.featherx.app.ui.theme.FxColors
import kotlin.math.roundToInt

@Composable
fun ProToolsScreen(vm: ProToolsViewModel) {
    val context = LocalContext.current
    val settings by OverlayController.settings.collectAsState()
    val running by OverlayController.running.collectAsState()

    // Android 13+: ask for notification permission once so the "overlay is running" notice is visible.
    // The overlay works either way; we start it after the answer.
    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { vm.startOverlay() }

    fun start() {
        val needsAsk = Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        if (needsAsk) notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) else vm.startOverlay()
    }

    fun openOverlaySettings() {
        val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))
        try {
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            context.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION))
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ScreenHeader("Pro Tools", "Floating sidebar for use over games")

        // ---- Sidebar on/off
        FxCard {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Sidebar", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                InfoRow(
                    "Display over other apps",
                    if (vm.canDrawOverlays) "Allowed" else "Not allowed",
                    if (vm.canDrawOverlays) FxColors.Ok else FxColors.Warn,
                )
                InfoRow("Sidebar", if (running) "Running" else "Stopped", if (running) FxColors.Ok else FxColors.Muted)
                if (!vm.canDrawOverlays) {
                    Text(
                        "The sidebar is drawn on top of games, which Android only allows after you switch on " +
                            "\"Display over other apps\" for FeatherX. Tap the button, switch it on, then come back.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(onClick = { openOverlaySettings() }, modifier = Modifier.fillMaxWidth()) {
                        Text("Open permission settings")
                    }
                } else if (running) {
                    Button(onClick = { vm.stopOverlay() }, modifier = Modifier.fillMaxWidth()) { Text("Stop sidebar") }
                } else {
                    Button(onClick = { start() }, modifier = Modifier.fillMaxWidth()) { Text("Start sidebar") }
                }
                Text(
                    "After starting, a small blue handle sits on the right edge of the screen. Tap it to open " +
                        "the tools, drag it up or down to move it. Everything below can also be changed from " +
                        "the sidebar itself. Changes show on screen only while the sidebar is running.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // ---- Crosshair
        FxCard {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ToggleHeader("Crosshair", settings.crosshairOn) { on ->
                    OverlayController.update { it.copy(crosshairOn = on) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CrosshairStyle.entries.forEach { st ->
                        FxChip(st.label, settings.crosshairStyle == st) {
                            OverlayController.update { it.copy(crosshairStyle = st) }
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    CrosshairColor.entries.forEach { c ->
                        FxSwatch(Color(c.argb), c.label, settings.crosshairColor == c) {
                            OverlayController.update { it.copy(crosshairColor = c) }
                        }
                    }
                }
                Text("Size: ${settings.crosshairSizeDp} dp", style = MaterialTheme.typography.bodySmall, color = FxColors.Muted)
                Slider(
                    value = settings.crosshairSizeDp.toFloat(),
                    onValueChange = { v -> OverlayController.update { it.copy(crosshairSizeDp = v.roundToInt()) } },
                    valueRange = OverlaySettings.MIN_CROSSHAIR_DP.toFloat()..OverlaySettings.MAX_CROSSHAIR_DP.toFloat(),
                )
                Text(
                    "Drawn at the exact centre of the screen. Touches pass through it to the game. " +
                        "Fair play: some online games treat a crosshair overlay as unfair help and can ban " +
                        "accounts. Check the game's rules and use it at your own risk.",
                    style = MaterialTheme.typography.bodySmall,
                    color = FxColors.Warn,
                )
            }
        }

        // ---- Screen filter
        FxCard {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ToggleHeader("Screen filter", settings.filterOn) { on ->
                    OverlayController.update { it.copy(filterOn = on) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterMode.entries.forEach { m ->
                        FxChip(m.label, settings.filterMode == m) {
                            OverlayController.update { it.copy(filterMode = m) }
                        }
                    }
                }
                Text(settings.filterMode.hint, style = MaterialTheme.typography.bodySmall, color = FxColors.Muted)
                Text("Strength: ${settings.filterStrength}%", style = MaterialTheme.typography.bodySmall, color = FxColors.Muted)
                Slider(
                    value = settings.filterStrength.toFloat(),
                    onValueChange = { v -> OverlayController.update { it.copy(filterStrength = v.roundToInt()) } },
                    valueRange = 0f..100f,
                )
                Text(
                    "A filter can only be laid over the picture, so it cannot add real brightness. " +
                        "\"Lift\" makes very dark areas easier to see by lowering contrast.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // ---- Live stats
        FxCard {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Live stats (inside the sidebar)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    "Battery temperature comes from the battery sensor, not the CPU. " +
                        "Free RAM comes from Android's memory info. " +
                        "Ping is the time to open a connection to 1.1.1.1 (Cloudflare), not to your game server. " +
                        "Speed is the total for the whole phone, all apps. " +
                        "These are measured only while the sidebar panel is open, every 2 seconds.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // ---- Not built
        FxCard {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Screen magnifier", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    "Not built yet. Zooming another app's screen needs Android's screen-capture permission " +
                        "(a system prompt each time) and some games block capture. It will be the next separate step.",
                    style = MaterialTheme.typography.bodySmall,
                    color = FxColors.Warn,
                )
            }
        }
    }
}

@Composable
private fun ToggleHeader(title: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
