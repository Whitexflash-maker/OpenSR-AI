package com.featherx.app.feature.protools

import android.view.MotionEvent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.featherx.app.ui.FxChip
import com.featherx.app.ui.HairDivider
import com.featherx.app.ui.theme.FxColors
import java.util.Locale
import kotlin.math.abs

/** Solid navy of the panel. With background blur available it becomes slightly see-through ("frosted"). */
private val PANEL = Color(0xFF0A0F1C)

/**
 * The floating HUD: a slim handle on the right screen edge and a panel with two mini-tabs (Tools / Stats)
 * that springs open to its left. Tap the handle to open/close; drag it to move it up or down.
 *
 * @param frosted true when the window has a real blur behind it (Android 12+ devices that support it).
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
    frosted: Boolean,
) {
    val haptic = LocalHapticFeedback.current
    val origin = TransformOrigin(1f, 0.5f) // grows out of the handle

    Row(verticalAlignment = Alignment.CenterVertically) {
        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn(spring(stiffness = Spring.StiffnessMedium)) +
                scaleIn(spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow), initialScale = 0.9f, transformOrigin = origin),
            exit = fadeOut(tween(120)) + scaleOut(tween(120), targetScale = 0.92f, transformOrigin = origin),
        ) {
            Panel(onStop = onStop, sampler = sampler, frosted = frosted)
        }

        // ---- Handle. Raw screen coordinates are used for dragging because the window itself moves while
        // dragging, which would make window-relative coordinates jump.
        val touch = remember { FloatArray(2) } // [0]=finger raw Y at start, [1]=window Y at start
        var dragging by remember { mutableStateOf(false) }
        var pressed by remember { mutableStateOf(false) }
        val handleAlpha by animateFloatAsState(
            targetValue = if (pressed || expanded) 0.85f else 0.30f,
            animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
            label = "handleAlpha",
        )
        val barWidth by animateDpAsState(
            targetValue = if (pressed) 9.dp else 5.dp,
            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
            label = "handleWidth",
        )
        Box(
            modifier = Modifier
                .size(width = 30.dp, height = 96.dp)
                .pointerInteropFilter { ev ->
                    when (ev.actionMasked) {
                        MotionEvent.ACTION_DOWN -> {
                            pressed = true
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
                            pressed = false
                            if (!dragging) {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onToggle()
                            }
                            true
                        }
                        MotionEvent.ACTION_CANCEL -> {
                            pressed = false
                            true
                        }
                        else -> true
                    }
                },
            contentAlignment = Alignment.CenterEnd,
        ) {
            Box(
                Modifier
                    .size(width = barWidth, height = 56.dp)
                    .alpha(handleAlpha)
                    .clip(RoundedCornerShape(topStart = 5.dp, bottomStart = 5.dp))
                    .background(Brush.verticalGradient(listOf(FxColors.Cyan, FxColors.Blue))),
            )
        }
    }
}

@Composable
private fun Panel(onStop: () -> Unit, sampler: StatsSampler, frosted: Boolean) {
    val settings by OverlayController.settings.collectAsState()
    var stats by remember { mutableStateOf<StatsSnapshot?>(null) }
    var tab by remember { mutableIntStateOf(0) }

    // Measures only while the panel exists: closing the panel cancels it (no background polling).
    LaunchedEffect(Unit) { sampler.stream().collect { stats = it } }

    val health = remember(stats) {
        stats?.let { HealthCheck.assess(it.batteryTempC, it.ramFreeMb, it.ramTotalMb, it.lowMemory) }
            ?: Health(HealthLevel.OK, null)
    }

    // Never taller than the screen allows (landscape has little height); the content scrolls.
    val maxHeight = (LocalConfiguration.current.screenHeightDp * 0.85f).coerceIn(160f, 440f).dp
    val shape = RoundedCornerShape(20.dp)

    Column(
        modifier = Modifier
            .padding(end = 6.dp)
            .width(264.dp)
            .heightIn(max = maxHeight)
            .clip(shape)
            .background(if (frosted) PANEL.copy(alpha = 0.86f) else PANEL)
            .border(
                1.dp,
                Brush.linearGradient(listOf(Color(0x59FFFFFF), Color(0x14FFFFFF), Color(0x26FFFFFF))),
                shape,
            )
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FxChip("Tools", tab == 0) { tab = 0 }
            StatsTab(selected = tab == 1, health = health) { tab = 1 }
            Spacer(Modifier.weight(1f))
            FxChip("Stop", false, onStop)
        }
        Box(
            Modifier
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState()),
        ) {
            Crossfade(targetState = tab, animationSpec = tween(140), label = "tab") { t ->
                if (t == 0) ToolsPage(settings) else StatsPage(stats, health)
            }
        }
    }
}

/** The "Stats" tab chip with a small colour dot when the warning badge is active. */
@Composable
private fun StatsTab(selected: Boolean, health: Health, onClick: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        FxChip("Stats", selected, onClick)
        if (health.level != HealthLevel.OK) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(levelColor(health.level)))
        }
    }
}

private fun levelColor(level: HealthLevel): Color = when (level) {
    HealthLevel.OK -> FxColors.Ok
    HealthLevel.WARN -> FxColors.Warn
    HealthLevel.CRITICAL -> FxColors.Danger
}

@Composable
private fun ToolsPage(s: OverlaySettings) {
    val spring = spring<androidx.compose.ui.unit.IntSize>(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ToolHeader(com.featherx.app.ui.Glyph.CROSSHAIR, "Crosshair", s.crosshairOn) { on ->
                OverlayController.update { it.copy(crosshairOn = on) }
            }
            AnimatedVisibility(s.crosshairOn, enter = expandVertically(spring) + fadeIn(), exit = shrinkVertically(spring) + fadeOut()) {
                CrosshairControls(s)
            }
        }
        HairDivider()
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ToolHeader(com.featherx.app.ui.Glyph.FILTER, "Screen filter", s.filterOn) { on ->
                OverlayController.update { it.copy(filterOn = on) }
            }
            AnimatedVisibility(s.filterOn, enter = expandVertically(spring) + fadeIn(), exit = shrinkVertically(spring) + fadeOut()) {
                FilterControls(s)
            }
        }
        HairDivider()
        StopwatchRow()
        HairDivider()
        MuteRow()
        HairDivider()
        ProfileRow()
    }
}

@Composable
private fun StatsPage(stats: StatsSnapshot?, health: Health) {
    if (stats == null) {
        Text("Measuring…", style = MaterialTheme.typography.bodySmall, color = FxColors.Muted)
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (health.message != null) {
            com.featherx.app.ui.StatusPill(health.message, levelColor(health.level))
        }
        val temp = stats.batteryTempC
        val tempColor = when (HealthCheck.tempLevel(temp)) {
            HealthLevel.OK -> if (temp == null) FxColors.Muted else FxColors.Ok
            HealthLevel.WARN -> FxColors.Warn
            HealthLevel.CRITICAL -> FxColors.Danger
        }
        val ramBad = HealthCheck.ramCritical(stats.ramFreeMb, stats.ramTotalMb, stats.lowMemory)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile("Battery", temp?.let { String.format(Locale.US, "%.1f °C", it) } ?: "n/a", tempColor, Modifier.weight(1f))
            StatTile(
                "Free RAM",
                "${SpeedCalc.formatMemory(stats.ramFreeMb)} / ${SpeedCalc.formatMemory(stats.ramTotalMb)}",
                if (ramBad) FxColors.Danger else FxColors.Text,
                Modifier.weight(1f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile(
                "Ping",
                stats.pingMs?.let { "$it ms" } ?: "offline",
                if (stats.pingMs == null) FxColors.Danger else FxColors.Text,
                Modifier.weight(1f),
            )
            StatTile(
                "Speed",
                "↓ ${SpeedCalc.format(stats.downBytesPerSec)}\n↑ ${SpeedCalc.format(stats.upBytesPerSec)}",
                FxColors.Text,
                Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun StatTile(label: String, value: String, valueColor: Color, modifier: Modifier) {
    val shape = RoundedCornerShape(12.dp)
    Column(
        modifier = modifier
            .clip(shape)
            .background(Color(0x0DFFFFFF))
            .border(1.dp, FxColors.Border, shape)
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = FxColors.Muted)
        Text(value, style = MaterialTheme.typography.titleSmall, color = valueColor)
    }
}
