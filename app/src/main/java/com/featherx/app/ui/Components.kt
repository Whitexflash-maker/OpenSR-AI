package com.featherx.app.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.featherx.app.ui.theme.FxColors

/**
 * Frosted dark-glass card: translucent navy over the app's ambient glow, a soft top-lit gradient and a
 * hairline border that is brighter at the top-left (light catching the glass edge). No blur effect and
 * no animation here, so it stays cheap to draw on a 4 GB phone.
 */
@Composable
fun FxCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Brush.verticalGradient(listOf(FxColors.GlassTop.copy(alpha = 0.78f), FxColors.Card.copy(alpha = 0.66f))))
            .border(
                1.dp,
                Brush.linearGradient(listOf(Color(0x40FFFFFF), Color(0x0FFFFFFF), Color(0x1AFFFFFF))),
                shape,
            ),
        content = content,
    )
}

/**
 * Click with a springy press (shrinks a little, bounces back) and a light haptic tick.
 * Haptics follow the phone's own "touch feedback" setting.
 */
@Composable
fun Modifier.fxClickable(onClick: () -> Unit): Modifier {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.93f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "press",
    )
    val haptic = LocalHapticFeedback.current
    return this
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .clickable(interactionSource = interaction, indication = null) {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onClick()
        }
}

/** Switch with FeatherX colours and a haptic tick. */
@Composable
fun FxSwitch(checked: Boolean, onChange: (Boolean) -> Unit) {
    val haptic = LocalHapticFeedback.current
    Switch(
        checked = checked,
        onCheckedChange = {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onChange(it)
        },
        colors = SwitchDefaults.colors(
            checkedThumbColor = FxColors.Black,
            checkedTrackColor = FxColors.Cyan,
            uncheckedThumbColor = FxColors.Muted,
            uncheckedTrackColor = FxColors.Black,
            uncheckedBorderColor = FxColors.Border,
        ),
    )
}

/** Thin separator line. */
@Composable
fun HairDivider() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(FxColors.Border))
}

@Composable
fun ScreenHeader(title: String, subtitle: String, trailing: @Composable (() -> Unit)? = null) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (trailing != null) trailing()
    }
}

/** Small cyan caps label that starts a group. */
@Composable
fun SectionLabel(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = FxColors.Cyan,
        modifier = Modifier.padding(start = 4.dp, top = 4.dp),
    )
}

/** "Name ........ value" row used for status and info lists. */
@Composable
fun InfoRow(name: String, value: String, valueColor: Color = MaterialTheme.colorScheme.onSurface) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = name,
            modifier = Modifier
                .weight(1f)
                .padding(end = 12.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelLarge,
            color = valueColor,
            textAlign = TextAlign.End,
        )
    }
}

/** Dot + short word in a tinted pill, e.g. "Running". */
@Composable
fun StatusPill(text: String, color: Color) {
    val shape = RoundedCornerShape(50)
    Row(
        modifier = Modifier
            .clip(shape)
            .background(color.copy(alpha = 0.14f))
            .border(1.dp, color.copy(alpha = 0.35f), shape)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(color))
        Text(text, style = MaterialTheme.typography.labelMedium, color = color)
    }
}

/** Small selectable pill (used for presets and modes, in the app and in the overlay). */
@Composable
fun FxChip(text: String, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    Box(
        modifier = Modifier
            .clip(shape)
            .background(if (selected) FxColors.Cyan.copy(alpha = 0.16f) else Color.Transparent)
            .border(1.dp, if (selected) FxColors.Cyan else FxColors.Border, shape)
            .fxClickable(onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) FxColors.Cyan else FxColors.Muted,
        )
    }
}

/** Button: filled cyan (primary) or outlined (secondary). Compact, fits the overlay. */
@Composable
fun FxButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, filled: Boolean = true) {
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier = modifier
            .clip(shape)
            .background(if (filled) FxColors.Cyan else Color.Transparent)
            .border(1.dp, if (filled) FxColors.Cyan else FxColors.Border, shape)
            .fxClickable(onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = if (filled) FxColors.Black else FxColors.Text,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

/** Round colour swatch; a white ring marks the selected one. */
@Composable
fun FxSwatch(color: Color, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(28.dp)
            .clip(CircleShape)
            .background(color)
            .border(if (selected) 2.5.dp else 1.dp, if (selected) Color.White else FxColors.Border, CircleShape)
            .fxClickable(onClick),
    )
}

// ------------------------------------------------------------------ icons

/** Own small line icons (drawn with Canvas) so every tool has a matching, crisp glyph. */
enum class Glyph { CROSSHAIR, FILTER, TIMER, BELL, PULSE, BOOKMARK }

@Composable
fun FxGlyph(glyph: Glyph, modifier: Modifier = Modifier, tint: Color = LocalContentColor.current, size: Dp = 22.dp) {
    Canvas(modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val stroke = Stroke(width = 1.8.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        when (glyph) {
            Glyph.CROSSHAIR -> {
                drawCircle(tint, radius = w * 0.27f, center = Offset(w / 2, h / 2), style = stroke)
                drawCircle(tint, radius = w * 0.04f, center = Offset(w / 2, h / 2))
                line(tint, stroke, w / 2, h * 0.06f, w / 2, h * 0.28f)
                line(tint, stroke, w / 2, h * 0.72f, w / 2, h * 0.94f)
                line(tint, stroke, w * 0.06f, h / 2, w * 0.28f, h / 2)
                line(tint, stroke, w * 0.72f, h / 2, w * 0.94f, h / 2)
            }
            Glyph.FILTER -> {
                val r = w * 0.38f
                drawCircle(tint, radius = r, center = Offset(w / 2, h / 2), style = stroke)
                drawArc(
                    color = tint, startAngle = 90f, sweepAngle = 180f, useCenter = true,
                    topLeft = Offset(w / 2 - r, h / 2 - r), size = Size(r * 2, r * 2),
                )
            }
            Glyph.TIMER -> {
                drawCircle(tint, radius = w * 0.36f, center = Offset(w / 2, h * 0.56f), style = stroke)
                line(tint, stroke, w * 0.40f, h * 0.07f, w * 0.60f, h * 0.07f)
                line(tint, stroke, w / 2, h * 0.56f, w * 0.64f, h * 0.40f)
            }
            Glyph.BELL -> {
                val path = Path().apply {
                    moveTo(w * 0.22f, h * 0.70f)
                    lineTo(w * 0.22f, h * 0.44f)
                    arcTo(Rect(w * 0.22f, h * 0.14f, w * 0.78f, h * 0.74f), 180f, 180f, false)
                    lineTo(w * 0.78f, h * 0.70f)
                    close()
                }
                drawPath(path, tint, style = stroke)
                line(tint, stroke, w * 0.14f, h * 0.70f, w * 0.86f, h * 0.70f)
                drawCircle(tint, radius = w * 0.07f, center = Offset(w / 2, h * 0.86f))
            }
            Glyph.PULSE -> {
                val path = Path().apply {
                    moveTo(w * 0.06f, h * 0.55f)
                    lineTo(w * 0.28f, h * 0.55f)
                    lineTo(w * 0.40f, h * 0.22f)
                    lineTo(w * 0.56f, h * 0.82f)
                    lineTo(w * 0.67f, h * 0.45f)
                    lineTo(w * 0.74f, h * 0.55f)
                    lineTo(w * 0.94f, h * 0.55f)
                }
                drawPath(path, tint, style = stroke)
            }
            Glyph.BOOKMARK -> {
                val path = Path().apply {
                    moveTo(w * 0.28f, h * 0.10f)
                    lineTo(w * 0.72f, h * 0.10f)
                    lineTo(w * 0.72f, h * 0.90f)
                    lineTo(w * 0.50f, h * 0.70f)
                    lineTo(w * 0.28f, h * 0.90f)
                    close()
                }
                drawPath(path, tint, style = stroke)
            }
        }
    }
}

private fun DrawScope.line(color: Color, stroke: Stroke, x1: Float, y1: Float, x2: Float, y2: Float) {
    drawLine(color, Offset(x1, y1), Offset(x2, y2), strokeWidth = stroke.width, cap = StrokeCap.Round)
}

// ------------------------------------------------------------------ round controls

/** Maps a touch point on a dial to 0..1. The arc starts bottom-left (135 deg) and sweeps 270 deg clockwise. */
private fun dialFraction(pos: Offset, width: Float, height: Float): Float {
    val deg = Math.toDegrees(atan2((pos.y - height / 2f).toDouble(), (pos.x - width / 2f).toDouble())).toFloat()
    val a = (deg - 135f + 360f) % 360f
    return when {
        a <= 270f -> a / 270f
        a < 315f -> 1f   // in the gap, nearer to the end
        else -> 0f       // in the gap, nearer to the start
    }
}

/**
 * Circular dial instead of a straight slider: drag (or tap) anywhere around the ring.
 * The value is shown in the middle, the name below.
 */
@Composable
fun FxDial(
    label: String,
    valueText: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current
    val latest by rememberUpdatedState(onChange)
    val fraction = ((value - range.start) / (range.endInclusive - range.start)).coerceIn(0f, 1f)
    fun apply(pos: Offset, w: Float, h: Float) {
        latest(range.start + dialFraction(pos, w, h) * (range.endInclusive - range.start))
    }

    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .pointerInput(range) {
                    detectTapGestures { apply(it, size.width.toFloat(), size.height.toFloat()) }
                }
                .pointerInput(range) {
                    detectDragGestures(
                        onDragStart = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            apply(it, size.width.toFloat(), size.height.toFloat())
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            apply(change.position, size.width.toFloat(), size.height.toFloat())
                        },
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val stroke = 7.dp.toPx()
                val inset = stroke / 2f + 4.dp.toPx()
                val arcSize = Size(size.width - inset * 2, size.height - inset * 2)
                val topLeft = Offset(inset, inset)
                val style = Stroke(width = stroke, cap = StrokeCap.Round)
                drawArc(Color(0x24FFFFFF), 135f, 270f, false, topLeft, arcSize, style = style)
                if (fraction > 0f) {
                    drawArc(FxColors.Cyan.copy(alpha = 0.25f), 135f, 270f * fraction, false, topLeft, arcSize, style = Stroke(width = stroke + 5.dp.toPx(), cap = StrokeCap.Round))
                    drawArc(FxColors.Cyan, 135f, 270f * fraction, false, topLeft, arcSize, style = style)
                }
                // round knob at the end of the arc
                val r = arcSize.width / 2f
                val rad = Math.toRadians((135f + 270f * fraction).toDouble())
                val knob = Offset(size.width / 2f + r * cos(rad).toFloat(), size.height / 2f + r * sin(rad).toFloat())
                drawCircle(Color.White, radius = 5.dp.toPx(), center = knob)
                drawCircle(FxColors.Cyan, radius = 5.dp.toPx(), center = knob, style = Stroke(width = 2.dp.toPx()))
            }
            Text(valueText, style = MaterialTheme.typography.labelMedium, color = FxColors.Text)
        }
        Text(label, style = MaterialTheme.typography.labelSmall, color = FxColors.Muted)
    }
}

/** Round icon button with a name under it: glows cyan when on (like the quick tiles of a gaming phone). */
@Composable
fun FxRoundToggle(glyph: Glyph, label: String, on: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(
            modifier = Modifier
                .size(54.dp)
                .fxClickable(onClick)
                .drawBehind {
                    if (on) {
                        drawCircle(
                            Brush.radialGradient(listOf(FxColors.Cyan.copy(alpha = 0.35f), Color.Transparent)),
                            radius = size.width * 0.85f,
                        )
                    }
                }
                .clip(CircleShape)
                .background(if (on) FxColors.Cyan.copy(alpha = 0.16f) else Color(0x0DFFFFFF))
                .border(1.5.dp, if (on) FxColors.Cyan else FxColors.Border, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            FxGlyph(glyph, tint = if (on) FxColors.Cyan else FxColors.Muted, size = 24.dp)
        }
        Text(label, style = MaterialTheme.typography.labelSmall, color = if (on) FxColors.Cyan else FxColors.Muted)
    }
}
