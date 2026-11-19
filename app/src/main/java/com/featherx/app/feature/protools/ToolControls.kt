package com.featherx.app.feature.protools

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.featherx.app.ui.FxButton
import com.featherx.app.ui.FxChip
import com.featherx.app.ui.FxDial
import com.featherx.app.ui.FxRoundToggle
import com.featherx.app.ui.FxGlyph
import com.featherx.app.ui.FxSwatch
import com.featherx.app.ui.FxSwitch
import com.featherx.app.ui.Glyph
import com.featherx.app.ui.theme.FxColors
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

/*
 * Controls shared by the Pro Tools screen and the floating sidebar, so both always behave the same.
 */

/** Icon + title on the left, optional switch on the right. */
@Composable
fun ToolHeader(glyph: Glyph, title: String, checked: Boolean? = null, onChecked: (Boolean) -> Unit = {}) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        FxGlyph(glyph, tint = if (checked == false) FxColors.Muted else FxColors.Cyan)
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleSmall,
            color = FxColors.Text,
        )
        if (checked != null) {
            FxSwitch(checked, onChecked)
        }
    }
}

private fun signed(v: Int) = if (v > 0) "+$v" else "$v"

/** Presets, colours, and three round dials: size, fine X offset, fine Y offset. */
@Composable
fun CrosshairControls(s: OverlaySettings) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            CrosshairStyle.entries.forEach { st ->
                FxChip(st.label, s.crosshairStyle == st) { OverlayController.update { it.copy(crosshairStyle = st) } }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            CrosshairColor.entries.forEach { c ->
                FxSwatch(Color(c.argb), s.crosshairColor == c) { OverlayController.update { it.copy(crosshairColor = c) } }
            }
        }
        val m = OverlaySettings.MAX_OFFSET_DP.toFloat()
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            FxDial(
                "Size", "${s.crosshairSizeDp}", s.crosshairSizeDp.toFloat(),
                OverlaySettings.MIN_CROSSHAIR_DP.toFloat()..OverlaySettings.MAX_CROSSHAIR_DP.toFloat(),
                { v -> OverlayController.update { it.copy(crosshairSizeDp = v.roundToInt()) } },
            )
            FxDial("X", signed(s.crosshairOffsetXDp), s.crosshairOffsetXDp.toFloat(), -m..m, { v ->
                OverlayController.update { it.copy(crosshairOffsetXDp = v.roundToInt()) }
            })
            FxDial("Y", signed(s.crosshairOffsetYDp), s.crosshairOffsetYDp.toFloat(), -m..m, { v ->
                OverlayController.update { it.copy(crosshairOffsetYDp = v.roundToInt()) }
            })
        }
    }
}

@Composable
fun FilterControls(s: OverlaySettings) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterMode.entries.forEach { m ->
                FxChip(m.label, s.filterMode == m) { OverlayController.update { it.copy(filterMode = m) } }
            }
        }
        FxDial("Strength", "${s.filterStrength}%", s.filterStrength.toFloat(), 0f..100f, { v ->
            OverlayController.update { it.copy(filterStrength = v.roundToInt()) }
        })
    }
}

/** Match / zone stopwatch. State lives in [OverlayController], so closing the panel does not reset it. */
@Composable
fun StopwatchRow() {
    val sw = OverlayController.stopwatch
    var running by remember { mutableStateOf(sw.running) }
    var elapsed by remember { mutableLongStateOf(sw.elapsedMs(OverlayController.now())) }

    // Redraws 10x a second, but only while this row is on screen and the stopwatch is running.
    LaunchedEffect(running) {
        elapsed = sw.elapsedMs(OverlayController.now())
        while (running) {
            delay(100)
            elapsed = sw.elapsedMs(OverlayController.now())
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ToolHeader(Glyph.TIMER, "Stopwatch")
        Text(
            text = Stopwatch.format(elapsed),
            style = MaterialTheme.typography.headlineSmall,
            fontFamily = FontFamily.Monospace,
            color = if (running) FxColors.Cyan else FxColors.Text,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FxButton(if (running) "Pause" else "Start", onClick = {
                if (running) sw.pause(OverlayController.now()) else sw.start(OverlayController.now())
                running = sw.running
                elapsed = sw.elapsedMs(OverlayController.now())
            })
            FxButton("Reset", filled = false, onClick = {
                sw.reset()
                running = false
                elapsed = 0L
            })
        }
    }
}

/** Do Not Disturb shortcut. Shows "Allow" until Android's one-time access is granted. */
@Composable
fun MuteRow() {
    val context = LocalContext.current
    val mute = remember { QuickMute(context) }
    var muted by remember { mutableStateOf(mute.isMuted()) }
    var access by remember { mutableStateOf(mute.hasAccess()) }

    // The DND state can change from outside (quick settings), so re-read it while this row is visible.
    LaunchedEffect(Unit) {
        while (true) {
            muted = mute.isMuted()
            access = mute.hasAccess()
            delay(1500)
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        FxGlyph(Glyph.BELL, tint = if (muted) FxColors.Cyan else FxColors.Muted)
        Column(Modifier.weight(1f)) {
            Text("Mute (DND)", style = MaterialTheme.typography.titleSmall, color = FxColors.Text)
            Text(
                if (!access) "Needs access" else if (muted) "Alarms only" else "Off",
                style = MaterialTheme.typography.bodySmall,
                color = FxColors.Muted,
            )
        }
        if (!access) {
            FxButton("Allow", filled = false, onClick = { mute.openAccessSettings() })
        } else {
            FxSwitch(muted) {
                mute.toggle()
                muted = mute.isMuted()
            }
        }
    }
}

/** Crosshair + filter profile of the game last launched from Game Space. */
@Composable
fun ProfileRow() {
    val game by OverlayController.activeGame.collectAsState()
    val profiled by OverlayController.profiled.collectAsState()
    val g = game
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ToolHeader(Glyph.BOOKMARK, "Game profile")
        if (g == null) {
            Text("Launch a game from Games to link it", style = MaterialTheme.typography.bodySmall, color = FxColors.Muted)
        } else {
            val saved = g.packageName in profiled
            Text(
                text = if (saved) "${g.label} · saved" else "${g.label} · not saved",
                style = MaterialTheme.typography.bodySmall,
                color = if (saved) FxColors.Cyan else FxColors.Muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FxButton(if (saved) "Update" else "Save", onClick = { OverlayController.saveProfileForActiveGame() })
                if (saved) FxButton("Clear", filled = false, onClick = { OverlayController.clearProfileForActiveGame() })
            }
        }
    }
}

/** Round quick-toggle for Do Not Disturb (same logic as [MuteRow]). Opens Android's access screen the first time. */
@Composable
fun MuteToggle() {
    val context = LocalContext.current
    val mute = remember { QuickMute(context) }
    var muted by remember { mutableStateOf(mute.isMuted()) }
    var access by remember { mutableStateOf(mute.hasAccess()) }
    LaunchedEffect(Unit) {
        while (true) {
            muted = mute.isMuted()
            access = mute.hasAccess()
            delay(1500)
        }
    }
    FxRoundToggle(Glyph.BELL, if (!access) "Allow DND" else "Mute", muted && access, onClick = {
        if (!access) mute.openAccessSettings() else {
            mute.toggle()
            muted = mute.isMuted()
        }
    })
}
