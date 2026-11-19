package com.featherx.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.featherx.app.feature.gamespace.GameSpaceScreen
import com.featherx.app.feature.gamespace.GameSpaceViewModel
import com.featherx.app.feature.home.HomeScreen
import com.featherx.app.feature.performance.PerformanceScreen
import com.featherx.app.feature.protools.ProToolsScreen
import com.featherx.app.feature.protools.ProToolsViewModel
import com.featherx.app.feature.settings.SettingsScreen
import com.featherx.app.ui.FxGlyph
import com.featherx.app.ui.Glyph
import com.featherx.app.ui.theme.FxColors

private enum class Destination(val label: String, val icon: @Composable () -> Unit) {
    Home("Home", { Icon(Icons.Filled.Home, contentDescription = null) }),
    GameSpace("Games", { Icon(Icons.Filled.PlayArrow, contentDescription = null) }),
    Performance("Perform", { Icon(Icons.Filled.Build, contentDescription = null) }),
    ProTools("Pro Tools", { FxGlyph(Glyph.CROSSHAIR) }),
    Settings("Settings", { Icon(Icons.Filled.Settings, contentDescription = null) }),
}

@Composable
fun FeatherXApp(proTools: ProToolsViewModel, gameSpace: GameSpaceViewModel) {
    // The selected tab survives rotation and process recreation (saved as a plain Int).
    var selected by rememberSaveable { mutableStateOf(0) }
    val destinations = Destination.entries
    val current = destinations[selected.coerceIn(0, destinations.lastIndex)]

    // Ambient glow: two faint radial lights (cyan top-right, blue bottom-left) behind translucent glass cards.
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(FxColors.Black)
            .drawBehind {
                drawRect(
                    Brush.radialGradient(
                        listOf(FxColors.Cyan.copy(alpha = 0.12f), Color.Transparent),
                        center = Offset(size.width * 0.9f, 0f),
                        radius = size.width * 0.95f,
                    ),
                )
                drawRect(
                    Brush.radialGradient(
                        listOf(FxColors.Blue.copy(alpha = 0.10f), Color.Transparent),
                        center = Offset(0f, size.height * 0.92f),
                        radius = size.width * 0.95f,
                    ),
                )
            },
    ) {
    Scaffold(
        containerColor = Color.Transparent,
        bottomBar = {
            NavigationBar(containerColor = FxColors.Navy, tonalElevation = 0.dp) {
                destinations.forEachIndexed { index, destination ->
                    NavigationBarItem(
                        selected = index == selected,
                        onClick = { selected = index },
                        icon = destination.icon,
                        label = {
                            Text(
                                text = destination.label,
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = FxColors.Cyan,
                            selectedTextColor = FxColors.Cyan,
                            unselectedIconColor = FxColors.Muted,
                            unselectedTextColor = FxColors.Muted,
                            indicatorColor = FxColors.Cyan.copy(alpha = 0.14f),
                        ),
                    )
                }
            }
        },
    ) { innerPadding ->
        // innerPadding already contains the status-bar and navigation-bar insets (Android 15 edge-to-edge).
        Box(Modifier.padding(innerPadding)) {
            when (current) {
                Destination.Home -> HomeScreen()
                Destination.GameSpace -> GameSpaceScreen(gameSpace)
                Destination.Performance -> PerformanceScreen()
                Destination.ProTools -> ProToolsScreen(proTools)
                Destination.Settings -> SettingsScreen()
            }
        }
    }
    }
}
