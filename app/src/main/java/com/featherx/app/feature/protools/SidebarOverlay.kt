package com.featherx.app.feature.protools

import android.view.MotionEvent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Spring
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.featherx.app.ui.FxChip
import com.featherx.app.ui.FxGlyph
import com.featherx.app.ui.FxRoundToggle
import com.featherx.app.ui.Glyph
import com.featherx.app.ui.HairDivider
import com.featherx.app.ui.theme.FxColors
import java.util.Locale
import kotlinx.coroutines.delay
import kotlin.math.abs

/** Solid navy of the panels. With background blur available they become slightly see-through ("frosted"). */
private val PANEL = Color(0xFF0A0F1C)

/** Which screen edge a sidebar lives on. */
enum class SidebarSide { LEFT, RIGHT }

private val LEFT_WIDTH = 208.dp
private val RIGHT_WIDTH = 236.dp

/**
 * One edge sidebar: a round handle on the screen edge and a compact glass panel that springs open towards the
 * centre. LEFT = Monitor (live stats, mute and profile status); RIGHT = Tools (crosshair, filter, stopwatch,
 * mute toggle, stop). Tap the handle to open/close, drag it to move it up or down.
 *
 * @param frosted true when the window has a real blur behind it (Android 12+ devices that support it).
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun SidebarOverlay(
    side: SidebarSide,
    expanded: Boolean,
    onToggle: () -> Unit,
    currentY: () -> Float,
    onMoveTo: (Float) -> Unit,
    onStop: () -> Unit,
    sampler: StatsSampler,
    frosted: Boolean,
) {
    val left = side == SidebarSide.LEFT
    val origin = TransformOrigin(if (left) 0f else 1f, 0.5f) // grows out of its handle

    @Composable
    fun PanelHost() {
        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn(spring(stiffness = Spring.StiffnessMedium)) +
                scaleIn(spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow), initialScale = 0.9f, transformOrigin = origin),
            exit = fadeOut(tween(120)) + scaleOut(tween(120), targetScale = 0.92f, transformOrigin = origin),
        ) {
            if (left) MonitorPanel(sampler, frosted) else ToolsPanel(onStop, frosted)
        }
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        if (!left) PanelHost()
        EdgeHandle(
            glyph = if (left) Glyph.PULSE else Glyph.CROSSHAIR,
            expanded = expanded,
            currentY = currentY,
            onMoveTo = onMoveTo,
            onToggle = onToggle,
        )
        if (left) PanelHost()
    }
}

/** Round glass handle. 0.30 alpha at rest, 0.85 when touched or while its panel is open. */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun EdgeHandle(
    glyph: Glyph,
    expanded: Boolean,
    currentY: () -> Float,
    onMoveTo: (Float) -> Unit,
    onToggle: () -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    // Raw screen coordinates are used for dragging because the window itself moves while dragging,
    // which would make window-relative coordinates jump.
    val touch = remember { FloatArray(2) } // [0]=finger raw Y at start, [1]=window Y at start
    var dragging by remember { mutableStateOf(false) }
    var pressed by remember { mutableStateOf(false) }
    val handleAlpha by animateFloatAsState(
        targetValue = if (pressed || expanded) 0.85f else 0.30f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "handleAlpha",
    )
    val buttonScale by animateFloatAsState(
        targetValue = if (pressed) 0.88f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "handleScale",
    )
    Box(
        modifier = Modifier
            .size(60.dp) // bigger invisible touch area around the round button
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
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(44.dp)
                .graphicsLayer {
                    scaleX = buttonScale
                    scaleY = buttonScale
                    alpha = handleAlpha
                }
                .clip(CircleShape)
                .background(Brush.verticalGradient(listOf(Color(0xFF16213A), PANEL)))
                .border(1.5.dp, Brush.linearGradient(listOf(FxColors.Cyan, FxColors.Blue)), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            FxGlyph(glyph, tint = FxColors.Cyan, size = 22.dp)
        }
    }
}

/** Shared glass container: never taller than the screen allows (landscape is short), content scrolls. */
@Composable
private fun GlassPanel(
    width: androidx.compose.ui.unit.Dp,
    left: Boolean,
    frosted: Boolean,
    header: @Composable () -> Unit,
    content: @Composable () -> Unit,
) {
    val maxHeight = (LocalConfiguration.current.screenHeightDp * 0.85f).coerceIn(160f, 440f).dp
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier = Modifier
            .padding(start = if (left) 6.dp else 0.dp, end = if (left) 0.dp else 6.dp)
            .width(width)
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
        header()
        Box(
            Modifier
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState()),
        ) { content() }
    }
}

// ------------------------------------------------------------------ right: Tools

@Composable
private fun ToolsPanel(onStop: () -> Unit, frosted: Boolean) {
    val settings by OverlayController.settings.collectAsState()
    GlassPanel(
        width = RIGHT_WIDTH, left = false, frosted = frosted,
        header = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Tools", style = MaterialTheme.typography.titleSmall, color = FxColors.Text, modifier = Modifier.weight(1f))
                FxChip("Stop", false, onStop)
            }
        },
        content = { ToolsPage(settings) },
    )
}

// ------------------------------------------------------------------ left: Monitor

@Composable
private fun MonitorPanel(sampler: StatsSampler, frosted: Boolean) {
    var stats by remember { mutableStateOf<StatsSnapshot?>(null) }
    // Measures only while this panel exists: closing it cancels the sampling (no background polling).
    LaunchedEffect(Unit) { sampler.stream().collect { stats = it } }

    val health = remember(stats) {
        stats?.let { HealthCheck.assess(it.batteryTempC, it.ramFreeMb, it.ramTotalMb, it.lowMemory) }
            ?: Health(HealthLevel.OK, null)
    }
    GlassPanel(
        width = LEFT_WIDTH, left = true, frosted = frosted,
        header = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Monitor", style = MaterialTheme.typography.titleSmall, color = FxColors.Text)
                if (health.level != HealthLevel.OK) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(levelColor(health.level)))
                }
            }
        },
        content = { MonitorPage(stats, health) },
    )
}

@Composable
private fun MonitorPage(stats: StatsSnapshot?, health: Health) {
    val context = LocalContext.current
    val mute = remember { QuickMute(context) }
    var muted by remember { mutableStateOf(mute.isMuted()) }
    var access by remember { mutableStateOf(mute.hasAccess()) }
    LaunchedEffect(Unit) {
        while (true) {
            muted = mute.isMuted()
            access = mute.hasAccess()
            delay(2000)
        }
    }
    val game by OverlayController.activeGame.collectAsState()
    val profiled by OverlayController.profiled.collectAsState()

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (health.message != null) {
            com.featherx.app.ui.StatusPill(health.message, levelColor(health.level))
        }
        if (stats == null) {
            Text("Measuring…", style = MaterialTheme.typography.bodySmall, color = FxColors.Muted)
        } else {
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
                    "Free RAM", SpeedCalc.formatMemory(stats.ramFreeMb),
                    if (ramBad) FxColors.Danger else FxColors.Text, Modifier.weight(1f),
                    sub = "of ${SpeedCalc.formatMemory(stats.ramTotalMb)}",
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatTile(
                    "Ping", stats.pingMs?.let { "$it ms" } ?: "offline",
                    if (stats.pingMs == null) FxColors.Danger else FxColors.Text, Modifier.weight(1f),
                )
                StatTile(
                    "Speed", "↓ ${SpeedCalc.format(stats.downBytesPerSec)}\n↑ ${SpeedCalc.format(stats.upBytesPerSec)}",
                    FxColors.Text, Modifier.weight(1f),
                )
            }
        }
        HairDivider()
        StatusLine("Mute", if (!access) "No access" else if (muted) "On" else "Off", if (access && muted) FxColors.Cyan else FxColors.Muted)
        val g = game
        StatusLine(
            "Profile",
            if (g == null) "None" else if (g.packageName in profiled) "${g.label} · saved" else "${g.label} · not saved",
            if (g != null && g.packageName in profiled) FxColors.Cyan else FxColors.Muted,
        )
    }
}

@Composable
private fun StatusLine(label: String, value: String, valueColor: Color) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = FxColors.Muted)
        Text(
            value,
            modifier = Modifier.padding(start = 8.dp),
            style = MaterialTheme.typography.labelMedium,
            color = valueColor,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
        )
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
        // Round quick toggles
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            FxRoundToggle(Glyph.CROSSHAIR, "Crosshair", s.crosshairOn, onClick = {
                OverlayController.update { it.copy(crosshairOn = !it.crosshairOn) }
            })
            FxRoundToggle(Glyph.FILTER, "Filter", s.filterOn, onClick = {
                OverlayController.update { it.copy(filterOn = !it.filterOn) }
            })
            MuteToggle()
        }
        AnimatedVisibility(s.crosshairOn, enter = expandVertically(spring) + fadeIn(), exit = shrinkVertically(spring) + fadeOut()) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                HairDivider()
                CrosshairControls(s)
            }
        }
        AnimatedVisibility(s.filterOn, enter = expandVertically(spring) + fadeIn(), exit = shrinkVertically(spring) + fadeOut()) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                HairDivider()
                FilterControls(s)
            }
        }
        HairDivider()
        StopwatchRow()
        HairDivider()
        ProfileRow()
    }
}

@Composable
private fun StatTile(label: String, value: String, valueColor: Color, modifier: Modifier, sub: String? = null) {
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
        if (sub != null) Text(sub, style = MaterialTheme.typography.labelSmall, color = FxColors.Muted)
    }
}
