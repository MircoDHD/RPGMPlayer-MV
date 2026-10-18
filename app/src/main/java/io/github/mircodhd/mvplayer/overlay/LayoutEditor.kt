// SPDX-FileCopyrightText: 2026 MircoDHD
// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.mircodhd.mvplayer.overlay

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.mircodhd.mvplayer.R

private const val MIN_SIZE = 0.06f
private const val MAX_SIZE = 0.6f

/**
 * Edit mode for on-screen layouts: drag controls, change their size, hide them, reset to
 * the original. Shown over a dimmed screenshot of the game or a neutral background.
 */
@Composable
fun LayoutEditor(
    initial: GamepadLayout,
    original: GamepadLayout,
    background: ImageBitmap?,
    scale: Float,
    canSaveForGame: Boolean,
    onSave: (GamepadLayout, forGame: Boolean) -> Unit,
    onCancel: () -> Unit,
) {
    val controls = remember(initial) { mutableStateListOf(*initial.controls.toTypedArray()) }
    var selectedId by remember(initial) { mutableStateOf<String?>(null) }
    var askScope by remember { mutableStateOf(false) }
    val colors = controlColors()
    val current = initial.copy(controls = controls.toList())

    BackHandler(onBack = onCancel)

    Box(Modifier.fillMaxSize()) {
        if (background != null) {
            Image(background, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f)))
        } else {
            Box(
                Modifier.fillMaxSize().background(
                    Brush.linearGradient(listOf(MaterialTheme.colorScheme.surfaceContainer, MaterialTheme.colorScheme.surfaceContainerHighest)),
                ),
            )
        }

        ControlPlacement(current, scale, includeHidden = true) { control, boxModifier ->
            val selected = control.id == selectedId
            Box(
                boxModifier
                    .alpha(if (control.hidden) 0.3f else 1f)
                    .then(if (selected) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp)) else Modifier)
                    .pointerInput(control.id) {
                        detectTapGestures { selectedId = control.id }
                    }
                    .pointerInput(control.id) {
                        detectDragGestures(onDragStart = { selectedId = control.id }) { change, drag ->
                            change.consume()
                            val index = controls.indexOfFirst { it.id == control.id }
                            if (index < 0) return@detectDragGestures
                            val c = controls[index]
                            // Convert pixels to layout units (fractions of the safe-area height).
                            val unit = size.height / (c.size * scale * c.aspect.second)
                            val dx = drag.x / unit
                            val dy = drag.y / unit
                            controls[index] = c.copy(
                                x = (c.x + if (c.anchor.horizontal == 1) -dx else dx),
                                y = (c.y + if (c.anchor.vertical == 1) -dy else dy),
                            )
                        }
                    },
            ) {
                ControlVisual(control, emptySet(), colors, Modifier.fillMaxSize())
            }
        }

        EditorToolbar(
            selected = controls.firstOrNull { it.id == selectedId },
            onSize = { size -> update(controls, selectedId) { it.copy(size = size) } },
            onToggleHidden = { update(controls, selectedId) { it.copy(hidden = !it.hidden) } },
            onReset = {
                controls.clear()
                controls.addAll(original.controls)
                selectedId = null
            },
            onCancel = onCancel,
            onSave = { if (canSaveForGame) askScope = true else onSave(current, false) },
            // Centred, so it never covers controls, which sit at the edges.
            modifier = Modifier.align(Alignment.Center).safeDrawingPadding(),
        )
    }

    if (askScope) {
        AlertDialog(
            onDismissRequest = { askScope = false },
            title = { Text(stringResource(R.string.editor_save_title)) },
            text = { Text(stringResource(R.string.editor_save_text)) },
            confirmButton = {
                TextButton(onClick = {
                    askScope = false
                    onSave(current, true)
                }) { Text(stringResource(R.string.editor_save_game)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    askScope = false
                    onSave(current, false)
                }) { Text(stringResource(R.string.editor_save_global)) }
            },
        )
    }
}

private fun update(controls: MutableList<OverlayControl>, id: String?, transform: (OverlayControl) -> OverlayControl) {
    val index = controls.indexOfFirst { it.id == id }
    if (index >= 0) controls[index] = transform(controls[index])
}

@Composable
private fun EditorToolbar(
    selected: OverlayControl?,
    onSize: (Float) -> Unit,
    onToggleHidden: () -> Unit,
    onReset: () -> Unit,
    onCancel: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(shape = CircleShape, tonalElevation = 6.dp, shadowElevation = 6.dp, modifier = modifier) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (selected == null) {
                Text(stringResource(R.string.editor_hint), style = MaterialTheme.typography.bodyMedium)
            } else {
                Text(selected.label ?: selected.id.uppercase(), style = MaterialTheme.typography.labelLarge)
                Slider(
                    value = selected.size,
                    onValueChange = onSize,
                    valueRange = MIN_SIZE..MAX_SIZE,
                    modifier = Modifier.width(160.dp),
                )
                FilterChip(
                    selected = selected.hidden,
                    onClick = onToggleHidden,
                    label = { Text(stringResource(R.string.editor_hidden)) },
                )
            }
            Spacer(Modifier.width(8.dp))
            TextButton(onClick = onReset) { Text(stringResource(R.string.editor_reset)) }
            TextButton(onClick = onCancel) { Text(stringResource(R.string.dialog_cancel)) }
            TextButton(onClick = onSave) { Text(stringResource(R.string.editor_save)) }
        }
    }
}
