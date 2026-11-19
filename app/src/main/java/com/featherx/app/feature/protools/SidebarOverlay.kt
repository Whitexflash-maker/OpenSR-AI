package com.featherx.app.feature.protools

import android.view.MotionEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.featherx.app.ui.FxChip
import com.featherx.app.ui.FxSwatch
import com.featherx.app.ui.theme.FxColors
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The floating sidebar: a small handle on the right screen edge, and a panel that opens to its left.
 * Tap the handle to open/close, drag it to move it up or down.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun SidebarOverlay(
    expanded: Boolean,
    onToggle: () -> Unit,
    currentY: () -> Float,
    onMoveTo: (Float) -> Unit,
    onStop: () -> Unit,
    sampler: StatsSampler,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (expanded) {
            Panel(onStop = onStop, sampler = sampler)
        }
        // Handle. Raw screen coordinates are used for dragging because the window itself moves
        // while dragging, which would make window-relative coordinates jump.
        val touch = remember { FloatArray(2) } // [0]=finger raw Y at start, [1]=window Y at start
        var dragging by remember { mutableStateOf(false) }
        Box(
            modifier = Modifier
                .size(width = 24.dp, height = 84.dp)
                .clip(RoundedCornerShape(topStart = 14.dp, bottomStart = 14.dp))
                .background(FxColors.Blue.copy(alpha = 0.92f))
                .pointerInteropFilter { ev ->
                    when (ev.actionMasked) {
                        MotionEvent.ACTION_DOWN -> {
                            touch[0] = ev.rawY
                            touch[1] = currentY()
                            dragging = false
                            true
                        }
                        MotionEvent.ACTION_MOVE -> {
                            val delta = ev.rawY - touch[0]
                            if (dragging || abs(delta) > 12f) {
                                dragging = true
                                onMoveTo(touch[1] + delta)
                            }
                            true
                        }
                        MotionEvent.ACTION_UP -> {
                            if (!dragging) onToggle()
                            true
                        }
                        else -> true
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = if (expanded) "›" else "‹",
                color = Color.White,
                style = androidx.compose.material3.MaterialTheme.typography.titleLarge,
            )
        }
    }
}

@Composable
private fun Panel(onStop: () -> Unit, sampler: StatsSampler) {
    val settings by OverlayController.settings.collectAsState()
    var stats by remember { mutableStateOf<StatsSnapshot?>(null) }
    // Runs only while the panel is open: closing the panel cancels it (no background polling).
    LaunchedEffect(Unit) { sampler.stream().collect { stats = it } }

    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier = Modifier
            .width(236.dp)
            .heightIn(max = 300.dp)
            .clip(shape)
            .background(FxColors.Card.copy(alpha = 0.97f))
            .border(1.dp, FxColors.Blue.copy(alpha = 0.4f), shape)
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("FeatherX Tools", color = FxColors.Text, fontWeight = FontWeight.Bold)

        // ---- Crosshair
        SwitchRow("Crosshair", settings.crosshairOn) { on -> OverlayController.update { it.copy(crosshairOn = on) } }
        if (settings.crosshairOn) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                CrosshairStyle.entries.forEach { st ->
                    FxChip(st.label, settings.crosshairStyle == st) {
                        OverlayController.update { it.copy(crosshairStyle = st) }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CrosshairColor.entries.forEach { c ->
                    FxSwatch(Color(c.argb), c.label, settings.crosshairColor == c) {
                        OverlayController.update { it.copy(crosshairColor = c) }
                    }
                }
            }
            Slider(
                value = settings.crosshairSizeDp.toFloat(),
                onValueChange = { v -> OverlayController.update { it.copy(crosshairSizeDp = v.roundToInt()) } },
                valueRange = OverlaySettings.MIN_CROSSHAIR_DP.toFloat()..OverlaySettings.MAX_CROSSHAIR_DP.toFloat(),
            )
        }

        // ---- Screen filter
        SwitchRow("Screen filter", settings.filterOn) { on -> OverlayController.update { it.copy(filterOn = on) } }
        if (settings.filterOn) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterMode.entries.forEach { m ->
                    FxChip(m.label, settings.filterMode == m) {
                        OverlayController.update { it.copy(filterMode = m) }
                    }
                }
            }
            Slider(
                value = settings.filterStrength.toFloat(),
                onValueChange = { v -> OverlayController.update { it.copy(filterStrength = v.roundToInt()) } },
                valueRange = 0f..100f,
            )
        }

        // ---- Live stats
        Text("Live stats", color = FxColors.BlueSoft, fontWeight = FontWeight.SemiBold)
        val s = stats
        if (s == null) {
            Text("Measuring…", color = FxColors.Muted)
        } else {
            val temp = s.batteryTempC
            StatRow(
                "Battery temp",
                if (temp == null) "n/a" else "%.1f °C".format(java.util.Locale.US, temp),
                when {
                    temp == null -> FxColors.Muted
                    temp >= 45f -> FxColors.Danger
                    temp >= 40f -> FxColors.Warn
                    else -> FxColors.Ok
                },
            )
            StatRow(
                "Free RAM",
                "${SpeedCalc.formatMemory(s.ramFreeMb)} / ${SpeedCalc.formatMemory(s.ramTotalMb)}",
                if (s.lowMemory) FxColors.Danger else FxColors.Text,
            )
            StatRow(
                "Ping (1.1.1.1)",
                s.pingMs?.let { "$it ms" } ?: "no connection",
                if (s.pingMs == null) FxColors.Danger else FxColors.Text,
            )
            StatRow("Download", SpeedCalc.format(s.downBytesPerSec), FxColors.Text)
            StatRow("Upload", SpeedCalc.format(s.upBytesPerSec), FxColors.Text)
        }

        Button(onClick = onStop, modifier = Modifier.fillMaxWidth()) { Text("Stop overlay") }
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = FxColors.Text)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun StatRow(name: String, value: String, valueColor: Color) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(name, color = FxColors.Muted, style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
        Text(value, color = valueColor, style = androidx.compose.material3.MaterialTheme.typography.labelLarge)
    }
}
