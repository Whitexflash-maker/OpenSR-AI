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
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.featherx.app.ui.FxButton
import com.featherx.app.ui.FxCard
import com.featherx.app.ui.Glyph
import com.featherx.app.ui.ScreenHeader
import com.featherx.app.ui.SectionLabel
import com.featherx.app.ui.StatusPill
import com.featherx.app.ui.theme.FxColors

@Composable
fun ProToolsScreen(vm: ProToolsViewModel) {
    val context = LocalContext.current
    val settings by OverlayController.settings.collectAsState()
    val running by OverlayController.running.collectAsState()

    // Android 13+: ask for notification permission once so the "running" notice is visible.
    // The sidebar works either way; it is started after the answer.
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

    val spring = spring<IntSize>(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ScreenHeader("Pro Tools", "In-game sidebar") {
            StatusPill(if (running) "Running" else "Stopped", if (running) FxColors.Ok else FxColors.Muted)
        }

        // ---- Sidebar
        FxCard {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (!vm.canDrawOverlays) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text("Display over other apps", style = MaterialTheme.typography.titleSmall)
                            Text("Required · currently off", style = MaterialTheme.typography.bodySmall, color = FxColors.Warn)
                        }
                        FxButton("Allow", onClick = { openOverlaySettings() })
                    }
                } else if (running) {
                    FxButton("Stop sidebar", onClick = { vm.stopOverlay() }, modifier = Modifier.fillMaxWidth(), filled = false)
                } else {
                    FxButton("Start sidebar", onClick = { start() }, modifier = Modifier.fillMaxWidth())
                }
                val linked by OverlayController.linkWings.collectAsState()
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text("Open both wings together", style = MaterialTheme.typography.titleSmall)
                        Text("Tap or swipe one edge, both open", style = MaterialTheme.typography.bodySmall, color = FxColors.Muted)
                    }
                    com.featherx.app.ui.FxSwitch(linked) { OverlayController.setLinkWings(it) }
                }
                Text(
                    "Edge tabs on both sides · tap or swipe in · long-press opens both",
                    style = MaterialTheme.typography.bodySmall,
                    color = FxColors.Muted,
                )
            }
        }

        SectionLabel("Tools")

        FxCard {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ToolHeader(Glyph.CROSSHAIR, "Crosshair", settings.crosshairOn) { on ->
                    OverlayController.update { it.copy(crosshairOn = on) }
                }
                AnimatedVisibility(
                    visible = settings.crosshairOn,
                    enter = expandVertically(spring) + fadeIn(),
                    exit = shrinkVertically(spring) + fadeOut(),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        CrosshairControls(settings)
                        Text(
                            "Some online games ban crosshair overlays. Use at your own risk.",
                            style = MaterialTheme.typography.bodySmall,
                            color = FxColors.Warn,
                        )
                    }
                }
            }
        }

        FxCard {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ToolHeader(Glyph.FILTER, "Screen filter", settings.filterOn) { on ->
                    OverlayController.update { it.copy(filterOn = on) }
                }
                AnimatedVisibility(
                    visible = settings.filterOn,
                    enter = expandVertically(spring) + fadeIn(),
                    exit = shrinkVertically(spring) + fadeOut(),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterControls(settings)
                        Text(settings.filterMode.hint, style = MaterialTheme.typography.bodySmall, color = FxColors.Muted)
                    }
                }
            }
        }

        SectionLabel("Gaming")

        FxCard { Column(Modifier.padding(16.dp)) { ProfileRow() } }
        FxCard { Column(Modifier.padding(16.dp)) { MuteRow() } }

        Text(
            "Stopwatch and live stats are inside the sidebar.",
            modifier = Modifier.padding(horizontal = 4.dp),
            style = MaterialTheme.typography.bodySmall,
            color = FxColors.Muted,
        )
    }
}
