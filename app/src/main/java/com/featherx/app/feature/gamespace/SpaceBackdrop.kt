package com.featherx.app.feature.gamespace

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.featherx.app.ui.theme.FxColors

val SpaceBlack = Color(0xFF050811)

/**
 * Deep-space scene: faint nebula glows plus a fixed starfield, a few stars twinkle.
 * The app draws it full-screen (behind the status bar and the bottom bar) while the Games tab is open.
 */
@Composable
fun SpaceBackdrop(modifier: Modifier = Modifier) {
    val twinkle = rememberInfiniteTransition(label = "stars").animateFloat(
        initialValue = 0.55f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(3200, easing = LinearEasing), RepeatMode.Reverse),
        label = "twinkle",
    )
    Box(
        modifier
        .background(SpaceBlack)
        .drawBehind {
            val w = size.width
            val h = size.height
            fun orb(color: Color, cx: Float, cy: Float, r: Float, a: Float) = drawCircle(
                brush = Brush.radialGradient(listOf(color.copy(alpha = a), Color.Transparent), center = Offset(cx, cy), radius = r),
                radius = r,
                center = Offset(cx, cy),
            )
            orb(FxColors.Cyan, w * 0.88f, h * 0.10f, w * 0.80f, 0.16f)
            orb(Color(0xFF6D5BFF), w * 0.05f, h * 0.45f, w * 0.75f, 0.12f)
            orb(FxColors.Blue, w * 0.70f, h * 0.95f, w * 0.85f, 0.14f)
            for (s in STARS) {
                val a = if (s.twinkles) s.alpha * twinkle.value else s.alpha
                drawCircle(Color.White.copy(alpha = a), radius = s.radius.dp.toPx(), center = Offset(s.x * w, s.y * h))
            }
        },
    )
}

private class Star(val x: Float, val y: Float, val radius: Float, val alpha: Float, val twinkles: Boolean)

/** Fixed seed, so the sky looks the same every time. */
private val STARS: List<Star> = java.util.Random(7).let { rnd ->
    List(70) {
        Star(
            x = rnd.nextFloat(),
            y = rnd.nextFloat(),
            radius = 0.4f + rnd.nextFloat() * 1.0f,
            alpha = 0.25f + rnd.nextFloat() * 0.6f,
            twinkles = rnd.nextInt(3) == 0,
        )
    }
}
