package com.featherx.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import com.featherx.app.feature.gamespace.GameSpaceScreen
import com.featherx.app.feature.gamespace.GameSpaceViewModel
import com.featherx.app.feature.home.HomeScreen
import com.featherx.app.feature.performance.PerformanceScreen
import com.featherx.app.feature.settings.SettingsScreen
import com.featherx.app.feature.upscaler.UpscalerScreen
import com.featherx.app.feature.upscaler.UpscalerViewModel
import com.featherx.app.ui.theme.FxColors

private enum class Destination(val label: String, val icon: ImageVector) {
    Home("Home", Icons.Filled.Home),
    GameSpace("Game Space", Icons.Filled.PlayArrow),
    Performance("Performance", Icons.Filled.Build),
    Upscaler("AI Upscaler", Icons.Filled.Search),
    Settings("Settings", Icons.Filled.Settings),
}

@Composable
fun FeatherXApp(upscaler: UpscalerViewModel, gameSpace: GameSpaceViewModel) {
    // The selected tab survives rotation and process recreation (saved as a plain Int).
    var selected by rememberSaveable { mutableStateOf(0) }
    val destinations = Destination.entries
    val current = destinations[selected.coerceIn(0, destinations.lastIndex)]

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(containerColor = FxColors.Navy) {
                destinations.forEachIndexed { index, destination ->
                    NavigationBarItem(
                        selected = index == selected,
                        onClick = { selected = index },
                        icon = { Icon(destination.icon, contentDescription = destination.label) },
                        label = {
                            Text(
                                text = destination.label,
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = FxColors.Blue,
                            selectedTextColor = FxColors.BlueSoft,
                            unselectedIconColor = FxColors.Muted,
                            unselectedTextColor = FxColors.Muted,
                            indicatorColor = MaterialTheme.colorScheme.secondaryContainer,
                        ),
                    )
                }
            }
        },
    ) { innerPadding ->
        // innerPadding already contains the status-bar and navigation-bar insets (Android 15 edge-to-edge).
        Box(Modifier.padding(innerPadding)) {
            when (current) {
                Destination.Home -> HomeScreen(upscaler)
                Destination.GameSpace -> GameSpaceScreen(gameSpace)
                Destination.Performance -> PerformanceScreen()
                Destination.Upscaler -> UpscalerScreen(upscaler)
                Destination.Settings -> SettingsScreen()
            }
        }
    }
}
