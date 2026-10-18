// SPDX-FileCopyrightText: 2026 MircoDHD
// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.mircodhd.mvplayer.overlay

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.IntOffset
import io.github.mircodhd.mvplayer.input.InputAction
import io.github.mircodhd.mvplayer.settings.OverlaySettings
import kotlin.math.min
import kotlin.math.roundToInt

private const val DPAD_DEAD_ZONE = 0.2f
private const val STICK_DEAD_ZONE = 0.3f

/**
 * On-screen controls drawn above the game. Each control handles its own pointers, so
 * touches elsewhere still reach the WebView, and several fingers work at once. The union
 * of pressed actions is reported through [onActionsChanged] (same pipeline as hardware).
 */
@Composable
fun VirtualGamepad(
    layout: GamepadLayout,
    settings: OverlaySettings,
    onActionsChanged: (Set<InputAction>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val pressed = remember(layout) { mutableStateMapOf<String, Set<InputAction>>() }
    val knobs = remember(layout) { mutableStateMapOf<String, Offset>() }
    val view = LocalView.current
    val colors = controlColors()
    val report by rememberUpdatedState(onActionsChanged)

    LaunchedEffect(layout) {
        snapshotFlow { pressed.values.flatten().toSet() }.collect { report(it) }
    }
    DisposableEffect(layout) {
        onDispose { report(emptySet()) }
    }

    ControlPlacement(layout, settings.scale, modifier.alpha(settings.opacity)) { control, boxModifier ->
        Box(
            boxModifier.pointerInput(control, settings.haptics) {
                trackControl(control) { actions, knob ->
                    val before = pressed[control.id].orEmpty()
                    if (settings.haptics && (actions - before).isNotEmpty()) {
                        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    }
                    pressed[control.id] = actions
                    knobs[control.id] = knob
                }
            },
        ) {
            ControlVisual(control, pressed[control.id].orEmpty(), colors, Modifier.fillMaxSize(), knobs[control.id] ?: Offset.Zero)
        }
    }
}

/**
 * Lays out the visible controls of [layout] inside the safe area; [content] receives a
 * modifier that positions and sizes the control's box.
 */
@Composable
fun ControlPlacement(
    layout: GamepadLayout,
    scale: Float,
    modifier: Modifier = Modifier,
    includeHidden: Boolean = false,
    insets: Boolean = true,
    content: @Composable (OverlayControl, Modifier) -> Unit,
) {
    // System bars and cutouts, but not the keyboard: controls must not move when it opens.
    val area = if (insets) Modifier.windowInsetsPadding(WindowInsets.systemBars.union(WindowInsets.displayCutout)) else Modifier
    BoxWithConstraints(modifier.fillMaxSize().then(area)) {
        val width = constraints.maxWidth.toFloat()
        val height = constraints.maxHeight.toFloat()
        val density = LocalDensity.current
        val controls = if (includeHidden) layout.controls else layout.visibleControls
        for (control in controls) {
            key(control.id) {
                val (wDp, hDp) = controlSizeDp(control, maxHeight, scale)
                val wPx = with(density) { wDp.toPx() }
                val hPx = with(density) { hDp.toPx() }
                val (cx, cy) = control.center(width, height)
                // Keep scaled controls on screen.
                val left = (cx - wPx / 2).coerceIn(0f, (width - wPx).coerceAtLeast(0f))
                val top = (cy - hPx / 2).coerceIn(0f, (height - hPx).coerceAtLeast(0f))
                content(control, Modifier.offset { IntOffset(left.roundToInt(), top.roundToInt()) }.size(wDp, hDp))
            }
        }
    }
}

/** Static, non-interactive rendering of a layout, e.g. for preset cards. */
@Composable
fun LayoutPreview(layout: GamepadLayout, modifier: Modifier = Modifier, scale: Float = 1f) {
    val colors = controlColors()
    ControlPlacement(layout, scale, modifier, insets = false) { control, boxModifier ->
        ControlVisual(control, emptySet(), colors, boxModifier, labelScale = 0.45f)
    }
}

private suspend fun PointerInputScope.trackControl(
    control: OverlayControl,
    onChange: (Set<InputAction>, Offset) -> Unit,
) {
    awaitPointerEventScope {
        while (true) {
            val event = awaitPointerEvent()
            val down = event.changes.filter { it.pressed }
            val center = Offset(size.width / 2f, size.height / 2f)
            val radius = min(size.width, size.height) / 2f
            var knob = Offset.Zero
            val actions: Set<InputAction> = when (control.type) {
                ControlType.DPAD -> down.flatMapTo(HashSet()) { directionsAt(it.position - center, radius, DPAD_DEAD_ZONE) }
                ControlType.STICK -> {
                    val pointer = down.firstOrNull()
                    if (pointer == null) {
                        emptySet()
                    } else {
                        val delta = pointer.position - center
                        val length = delta.getDistance()
                        knob = if (length > radius) delta / length else delta / radius
                        directionsAt(delta, radius, STICK_DEAD_ZONE)
                    }
                }
                else -> if (down.isNotEmpty()) setOfNotNull(control.action) else emptySet()
            }
            event.changes.forEach { it.consume() }
            onChange(actions, knob)
        }
    }
}
