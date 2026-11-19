package com.featherx.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** FeatherX palette: deep charcoal-navy glass surfaces with a cyan / electric-blue accent. */
object FxColors {
    val Black = Color(0xFF04070F)      // app background
    val Navy = Color(0xFF0A0F1C)       // surfaces, bars, overlay panel
    val Card = Color(0xFF0A0F1C)
    val GlassTop = Color(0xFF121B30)   // top of the glass gradient on cards
    val Border = Color(0x24FFFFFF)     // 1 dp hairline on cards
    val Cyan = Color(0xFF22D3EE)       // primary accent
    val Blue = Color(0xFF3B82F6)       // secondary accent
    val BlueSoft = Color(0xFF7FB0FF)
    val Text = Color(0xFFE8EEFF)
    val Muted = Color(0xFF8B9AB8)
    val Ok = Color(0xFF34D399)
    val Warn = Color(0xFFFBBF24)
    val Danger = Color(0xFFF87171)
}

private val FeatherDarkColors = darkColorScheme(
    primary = FxColors.Cyan,
    onPrimary = FxColors.Black,
    secondary = FxColors.Blue,
    onSecondary = Color.White,
    background = FxColors.Black,
    onBackground = FxColors.Text,
    surface = FxColors.Navy,
    onSurface = FxColors.Text,
    surfaceVariant = FxColors.Card,
    onSurfaceVariant = FxColors.Muted,
    secondaryContainer = Color(0xFF0F2A3D),
    onSecondaryContainer = FxColors.Text,
    outline = Color(0xFF26334F),
    error = FxColors.Danger,
    onError = FxColors.Black,
)

/** One consistent type scale: tight bold headings, calm medium labels. */
private val FxTypography = Typography(
    displaySmall = TextStyle(fontWeight = FontWeight.Bold, fontSize = 34.sp, lineHeight = 40.sp, letterSpacing = (-0.5).sp),
    headlineSmall = TextStyle(fontWeight = FontWeight.Bold, fontSize = 24.sp, lineHeight = 30.sp, letterSpacing = (-0.2).sp),
    titleLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 20.sp, lineHeight = 26.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp, letterSpacing = 0.1.sp),
    titleSmall = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp),
    bodyLarge = TextStyle(fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 13.sp, lineHeight = 18.sp, letterSpacing = 0.2.sp),
    labelMedium = TextStyle(fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.2.sp),
    labelSmall = TextStyle(fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 14.sp, letterSpacing = 0.6.sp),
)

/** Always dark (gaming look, easy on the battery). No dynamic colour, no animations. */
@Composable
fun FeatherXTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = FeatherDarkColors, typography = FxTypography, content = content)
}
