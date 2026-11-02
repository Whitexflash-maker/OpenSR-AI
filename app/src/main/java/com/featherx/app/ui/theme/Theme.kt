package com.featherx.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** FeatherX palette: black / deep navy surfaces with an electric-blue accent. */
object FxColors {
    val Black = Color(0xFF05070D)
    val Navy = Color(0xFF0A1020)
    val Card = Color(0xFF111A2E)
    val Blue = Color(0xFF2D7CFF)
    val BlueSoft = Color(0xFF7FB0FF)
    val Text = Color(0xFFE8EEFF)
    val Muted = Color(0xFF93A4C3)
    val Ok = Color(0xFF3DDC97)
    val Warn = Color(0xFFFFB454)
    val Danger = Color(0xFFFF6B6B)
}

private val FeatherDarkColors = darkColorScheme(
    primary = FxColors.Blue,
    onPrimary = Color.White,
    secondary = FxColors.BlueSoft,
    onSecondary = FxColors.Black,
    background = FxColors.Black,
    onBackground = FxColors.Text,
    surface = FxColors.Navy,
    onSurface = FxColors.Text,
    surfaceVariant = FxColors.Card,
    onSurfaceVariant = FxColors.Muted,
    secondaryContainer = Color(0xFF16305F),
    onSecondaryContainer = FxColors.Text,
    outline = Color(0xFF2A3A5C),
    error = FxColors.Danger,
    onError = FxColors.Black,
)

/** Always dark (gaming look, easy on the battery). No dynamic colour, no animations. */
@Composable
fun FeatherXTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = FeatherDarkColors, content = content)
}
