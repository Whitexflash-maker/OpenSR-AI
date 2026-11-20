package com.featherx.app.feature.protools

import android.view.MotionEvent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.GenericShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.featherx.app.ui.FxChip
import com.featherx.app.ui.FxRoundToggle
import com.featherx.app.ui.Glyph
import com.featherx.app.ui.HairDivider
import com.featherx.app.ui.theme.FxColors
import java.util.Locale
import kotlin.math.abs
import kotlinx.coroutines.delay

/** Obsidian navy of the wings. With background blur available they become slightly see-through ("frosted"). */
private val PANEL = Color(0xFF0A0F1C)
private val PANEL_DEEP = Color(0xFF0C1630)

/** Which screen edge a wing lives on. */
enum class SidebarSide { LEFT, RIGHT }

private val LEFT_WIDTH = 232.dp
private val RIGHT_WIDTH = 264.dp
private val SLANT = 26.dp    // how far the inner edge leans
private val CHAMFER = 14.dp  // cut corners on the inner edge

/**
 * One edge "wing" (RedMagic Game Space style):
 *  - closed: a thin glass trigger bar flush against the screen edge. Tap it, or swipe inwards from it, to open;
 *    drag it up/down to move it; long-press it to open BOTH wings.
 *  - open: a full-height glass wing with an angled inner edge and a glowing cyan edge line that slides in
 *    from its own side. LEFT = Monitor (live stats), RIGHT = Tools (crosshair, filter, stopwatch, mute, stop).
 *
 * The service resizes the window: thin strip when [docked], full height when open. The centre of the screen
 * is never covered.
 */
@Composable
fun SidebarOverlay(
    side: SidebarSide,
    expanded: Boolean,
    docked: Boolean,
    onToggle: () -> Unit,
    onOpenBoth: () -> Unit,
    onClose: () -> Unit,
    currentY: () -> Float,
    onMoveTo: (Float) -> Unit,
    onStop: () -> Unit,
    sampler: StatsSampler,
    frosted: Boolean,
) {
    val left = side == SidebarSide.LEFT
    val linked by OverlayController.linkWings.collectAsState()

    Box(contentAlignment = if (left) Alignment.TopStart else Alignment.TopEnd) {
        if (docked && !expanded) {
            EdgeTrigger(left, currentY, onMoveTo, onToggle, onOpenBoth)
        }
        AnimatedVisibility(
            visible = expanded,
            enter = slideInHorizontally(
                animationSpec = spring<IntOffset>(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow),
                initialOffsetX = { if (left) -it else it },
            ) + fadeIn(tween(120)),
            exit = slideOutHorizontally(
                animationSpec = tween<IntOffset>(200),
                targetOffsetX = { if (left) -it else it },
            ) + fadeOut(tween(160)),
        ) {
            Wing(left, linked, frosted, onClose, onStop, sampler)
        }
    }
}

// ------------------------------------------------------------------ closed: edge trigger

/** Thin glass bar on the very edge. 0.25 alpha at rest, 0.80 when touched. */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun EdgeTrigger(
    left: Boolean,
    currentY: () -> Float,
    onMoveTo: (Float) -> Unit,
    onToggle: () -> Unit,
    onOpenBoth: () -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    // Raw screen coordinates are used because the window itself moves while dragging.
    val start = remember { FloatArray(3) }   // [0]=raw X, [1]=raw Y, [2]=window Y at touch start
    val mode = remember { IntArray(1) }      // 0 = undecided, 1 = moving up/down, 2 = already handled (opened)
    var pressed by remember { mutableStateOf(false) }
    var pressId by remember { mutableIntStateOf(0) }

    val alpha by animateFloatAsState(
        targetValue = if (pressed) 0.80f else 0.25f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "triggerAlpha",
    )
    val barWidth by animateDpAsState(
        targetValue = if (pressed) 6.dp else 4.dp,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "triggerWidth",
    )

    // Long press (still, 0.45 s) opens both wings together.
    LaunchedEffect(pressId) {
        if (pressId > 0) {
            delay(450)
            if (pressed && mode[0] == 0) {
                mode[0] = 2
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onOpenBoth()
            }
        }
    }

    Box(
        modifier = Modifier
            .size(width = 24.dp, height = 120.dp) // comfortable touch strip around the thin visible bar
            .pointerInteropFilter { ev ->
                when (ev.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        pressed = true
                        pressId++
                        mode[0] = 0
                        start[0] = ev.rawX
                        start[1] = ev.rawY
                        start[2] = currentY()
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        if (mode[0] != 2) {
                            val inward = if (left) ev.rawX - start[0] else start[0] - ev.rawX
                            val dy = ev.rawY - start[1]
                            if (mode[0] == 0) {
                                if (inward > 28f && inward > abs(dy)) {
                                    mode[0] = 2
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onToggle()
                                } else if (abs(dy) > 16f && abs(dy) > abs(inward)) {
                                    mode[0] = 1
                                }
                            }
                            if (mode[0] == 1) onMoveTo(start[2] + dy)
                        }
                        true
                    }
                    MotionEvent.ACTION_UP -> {
                        pressed = false
                        if (mode[0] == 0) {
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
        contentAlignment = if (left) Alignment.CenterStart else Alignment.CenterEnd,
    ) {
        val inner = 4.dp
        Box(
            Modifier
                .size(width = barWidth, height = 84.dp)
                .graphicsLayer { this.alpha = alpha }
                .clip(
                    if (left) RoundedCornerShape(topEnd = inner, bottomEnd = inner)
                    else RoundedCornerShape(topStart = inner, bottomStart = inner),
                )
                .background(Brush.verticalGradient(listOf(FxColors.Cyan, FxColors.Blue))),
        )
    }
}

// ------------------------------------------------------------------ open: the wing

/** Sci-fi trapezoid: straight on the screen edge, leaning inner edge, chamfered corners. */
private fun wingShape(left: Boolean, slant: Float, chamfer: Float) = GenericShape { size, _ ->
    val w = size.width
    val h = size.height
    val pts = listOf(
        Offset(0f, 0f), Offset(w - chamfer, 0f), Offset(w, chamfer),
        Offset(w - slant, h - chamfer), Offset(w - slant - chamfer, h), Offset(0f, h),
    ).map { if (left) it else Offset(w - it.x, it.y) }
    moveTo(pts[0].x, pts[0].y)
    for (i in 1 until pts.size) lineTo(pts[i].x, pts[i].y)
    close()
}

/** Glowing cyan line along the angled inner edge (soft wide stroke under a crisp thin one). */
private fun DrawScope.drawWingEdge(left: Boolean, slant: Float, chamfer: Float) {
    val w = size.width
    val h = size.height
    val e = 1.5.dp.toPx() // keep the line just inside the clip so it is not cut in half
    val raw = listOf(
        Offset(w - chamfer, e), Offset(w - e, chamfer),
        Offset(w - slant - e, h - chamfer), Offset(w - slant - chamfer, h - e),
    ).map { if (left) it else Offset(w - it.x, it.y) }
    val path = Path().apply {
        moveTo(raw[0].x, raw[0].y)
        for (i in 1 until raw.size) lineTo(raw[i].x, raw[i].y)
    }
    drawPath(path, FxColors.Cyan.copy(alpha = 0.22f), style = Stroke(7.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    drawPath(path, FxColors.Cyan, style = Stroke(1.6.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
}

@Composable
private fun Wing(
    left: Boolean,
    linked: Boolean,
    frosted: Boolean,
    onClose: () -> Unit,
    onStop: () -> Unit,
    sampler: StatsSampler,
) {
    val density = LocalDensity.current
    val slantPx = with(density) { SLANT.toPx() }
    val chamferPx = with(density) { CHAMFER.toPx() }
    val shape = remember(left, slantPx, chamferPx) { wingShape(left, slantPx, chamferPx) }
    val a = if (frosted) 0.88f else 1f
    val swipeOut = remember { FloatArray(1) }

    Box(
        modifier = Modifier
            .width(if (left) LEFT_WIDTH else RIGHT_WIDTH)
            .fillMaxHeight()
            .clip(shape)
            .background(Brush.verticalGradient(listOf(PANEL.copy(alpha = a), PANEL_DEEP.copy(alpha = a))))
            .drawWithContent {
                drawContent()
                drawWingEdge(left, slantPx, chamferPx)
            },
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                // keep content off the leaning edge
                .padding(
                    start = if (left) 12.dp else 12.dp + SLANT,
                    end = if (left) 12.dp + SLANT else 12.dp,
                    top = 18.dp,
                    bottom = 14.dp,
                ),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    // swipe the header towards the screen edge to close
                    .pointerInput(left) {
                        detectHorizontalDragGestures(
                            onDragStart = { swipeOut[0] = 0f },
                            onHorizontalDrag = { _, dx -> swipeOut[0] += dx },
                            onDragEnd = {
                                val outward = if (left) -swipeOut[0] else swipeOut[0]
                                if (outward > 60f) onClose()
                            },
                        )
                    },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    if (left) "Monitor" else "Tools",
                    style = MaterialTheme.typography.titleSmall,
                    color = FxColors.Text,
                    modifier = Modifier.weight(1f),
                )
                FxChip("Link", linked) { OverlayController.setLinkWings(!linked) }
                if (!left) FxChip("Stop", false, onStop)
                FxChip("×", false, onClose)
            }
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
            ) {
                if (left) MonitorContent(sampler) else {
                    val settings by OverlayController.settings.collectAsState()
                    ToolsPage(settings)
                }
            }
        }
    }
}

@Composable
private fun MonitorContent(sampler: StatsSampler) {
    var stats by remember { mutableStateOf<StatsSnapshot?>(null) }
    // Measures only while the Monitor wing is open: closing it cancels the sampling (no background polling).
    LaunchedEffect(Unit) { sampler.stream().collect { stats = it } }
    val health = remember(stats) {
        stats?.let { HealthCheck.assess(it.batteryTempC, it.ramFreeMb, it.ramTotalMb, it.lowMemory) }
            ?: Health(HealthLevel.OK, null)
    }
    MonitorPage(stats, health)
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
