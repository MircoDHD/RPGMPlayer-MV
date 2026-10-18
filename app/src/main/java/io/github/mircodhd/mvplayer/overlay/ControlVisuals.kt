// SPDX-FileCopyrightText: 2026 MircoDHD
// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.mircodhd.mvplayer.overlay

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.mircodhd.mvplayer.input.InputAction
import kotlin.math.atan2
import kotlin.math.hypot

/** Colours of a control, derived from the app theme (no console-specific colours). */
data class ControlColors(val fill: Color, val pressed: Color, val outline: Color, val content: Color)

@Composable
fun controlColors(): ControlColors {
    val scheme = MaterialTheme.colorScheme
    return ControlColors(
        fill = scheme.surfaceContainerHighest.copy(alpha = 0.85f),
        pressed = scheme.primary.copy(alpha = 0.9f),
        outline = scheme.onSurface.copy(alpha = 0.55f),
        content = scheme.onSurface,
    )
}

/**
 * Draws one control at its full box size. [active] are the actions currently pressed on
 * it; [stick] is the stick knob offset in -1..1 for analog sticks.
 */
@Composable
fun ControlVisual(
    control: OverlayControl,
    active: Set<InputAction>,
    colors: ControlColors,
    modifier: Modifier = Modifier,
    stick: Offset = Offset.Zero,
) {
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            when (control.type) {
                ControlType.DPAD -> drawDpad(active, colors)
                ControlType.STICK -> drawStick(stick, active.isNotEmpty(), colors)
                ControlType.BUTTON -> drawCircleButton(active.isNotEmpty(), colors)
                ControlType.PILL, ControlType.SHOULDER -> drawPill(active.isNotEmpty(), colors)
            }
        }
        if (control.action == InputAction.QUICK_MENU) {
            Icon(Icons.Filled.Menu, contentDescription = null, tint = colors.content, modifier = Modifier.fillMaxSize(0.6f))
        } else if (control.label != null) {
            // The label scales with the control, so it fits and stays centred at any size
            // (previews, size slider, editor) instead of overflowing to one side.
            val byHeight = maxHeight * if (control.type == ControlType.BUTTON) 0.36f else 0.5f
            val byWidth = maxWidth * 0.78f / (control.label.length * AVERAGE_GLYPH_WIDTH)
            Text(
                text = control.label,
                color = colors.content,
                fontWeight = FontWeight.SemiBold,
                fontSize = with(LocalDensity.current) { minOf(byHeight, byWidth).toSp() },
                textAlign = TextAlign.Center,
                maxLines = 1,
                softWrap = false,
                style = CenteredLabel,
                modifier = Modifier.wrapContentSize(unbounded = true),
            )
        }
    }
}

/** Typical glyph width of the bold UI font relative to its size. */
private const val AVERAGE_GLYPH_WIDTH = 0.62f

/** Centres the glyphs themselves vertically (no extra font padding above or below). */
private val CenteredLabel = TextStyle(
    platformStyle = PlatformTextStyle(includeFontPadding = false),
    lineHeightStyle = LineHeightStyle(alignment = LineHeightStyle.Alignment.Center, trim = LineHeightStyle.Trim.Both),
)

private fun DrawScope.drawCircleButton(pressed: Boolean, colors: ControlColors) {
    val radius = size.minDimension / 2
    drawCircle(if (pressed) colors.pressed else colors.fill, radius)
    drawCircle(colors.outline, radius - 1.dp.toPx(), style = Stroke(1.5.dp.toPx()))
}

private fun DrawScope.drawPill(pressed: Boolean, colors: ControlColors) {
    val corner = CornerRadius(size.height / 2, size.height / 2)
    drawRoundRect(if (pressed) colors.pressed else colors.fill, cornerRadius = corner)
    drawRoundRect(colors.outline, cornerRadius = corner, style = Stroke(1.5.dp.toPx()))
}

private fun DrawScope.drawDpad(active: Set<InputAction>, colors: ControlColors) {
    val s = size.minDimension
    val arm = s / 3
    val corner = CornerRadius(arm * 0.25f, arm * 0.25f)
    val origin = Offset((size.width - s) / 2, (size.height - s) / 2)
    // Base cross
    drawRoundRect(colors.fill, origin + Offset(arm, 0f), Size(arm, s), corner)
    drawRoundRect(colors.fill, origin + Offset(0f, arm), Size(s, arm), corner)
    // Pressed arms
    val arms = mapOf(
        InputAction.UP to (origin + Offset(arm, 0f)),
        InputAction.DOWN to (origin + Offset(arm, arm * 2)),
        InputAction.LEFT to (origin + Offset(0f, arm)),
        InputAction.RIGHT to (origin + Offset(arm * 2, arm)),
    )
    for ((action, topLeft) in arms) {
        if (action in active) drawRoundRect(colors.pressed, topLeft, Size(arm, arm), corner)
    }
    drawRoundRect(colors.outline, origin + Offset(arm, 0f), Size(arm, s), corner, style = Stroke(1.5.dp.toPx()))
    drawRoundRect(colors.outline, origin + Offset(0f, arm), Size(s, arm), corner, style = Stroke(1.5.dp.toPx()))
}

private fun DrawScope.drawStick(knob: Offset, pressed: Boolean, colors: ControlColors) {
    val radius = size.minDimension / 2
    drawCircle(colors.fill, radius)
    drawCircle(colors.outline, radius - 1.dp.toPx(), style = Stroke(1.5.dp.toPx()))
    val knobRadius = radius * 0.45f
    val travel = radius - knobRadius
    drawCircle(if (pressed) colors.pressed else colors.outline.copy(alpha = 0.8f), knobRadius, center + knob * travel)
}

/**
 * Directions for a touch at [offset] (pixels from the control centre) on a d-pad or
 * stick of [radius]. Eight 45° sectors, so sliding between directions works without
 * lifting the finger; a small dead zone in the middle.
 */
fun directionsAt(offset: Offset, radius: Float, deadZone: Float): Set<InputAction> {
    if (hypot(offset.x, offset.y) < radius * deadZone) return emptySet()
    val angle = Math.toDegrees(atan2(offset.y.toDouble(), offset.x.toDouble())) // 0 = right, 90 = down
    val sector = (((angle + 360 + 22.5) % 360) / 45).toInt()
    return when (sector) {
        0 -> setOf(InputAction.RIGHT)
        1 -> setOf(InputAction.RIGHT, InputAction.DOWN)
        2 -> setOf(InputAction.DOWN)
        3 -> setOf(InputAction.DOWN, InputAction.LEFT)
        4 -> setOf(InputAction.LEFT)
        5 -> setOf(InputAction.LEFT, InputAction.UP)
        6 -> setOf(InputAction.UP)
        else -> setOf(InputAction.UP, InputAction.RIGHT)
    }
}

/** Box size of [control] in dp for a safe area whose height is [heightDp]. */
fun controlSizeDp(control: OverlayControl, heightDp: Dp, scale: Float): Pair<Dp, Dp> {
    val base = heightDp * control.size * scale
    return base * control.aspect.first to base * control.aspect.second
}
