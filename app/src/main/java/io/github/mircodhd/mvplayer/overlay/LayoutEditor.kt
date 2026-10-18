// SPDX-FileCopyrightText: 2026 MircoDHD
// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.mircodhd.mvplayer.overlay

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import io.github.mircodhd.mvplayer.R
import io.github.mircodhd.mvplayer.input.InputAction
import io.github.mircodhd.mvplayer.ui.settings.actionName
import java.util.UUID
import kotlin.math.roundToInt

private const val MIN_SIZE = 0.06f
private const val MAX_SIZE = 0.6f
private const val MAX_LABEL_LENGTH = 6

/**
 * Edit mode for on-screen layouts: drag controls, change their size (slider or typed value),
 * hide, add and delete them, reset to the original. Labels can be renamed only when
 * [labelsEditable] (editing for one game); a layout with renamed labels is always saved as
 * a copy for that game. Shown over a dimmed screenshot of the game or a neutral background.
 */
@Composable
fun LayoutEditor(
    initial: GamepadLayout,
    original: GamepadLayout,
    background: ImageBitmap?,
    scale: Float,
    canSaveForGame: Boolean,
    onSave: (GamepadLayout, forGame: Boolean, asCopy: Boolean) -> Unit,
    onCancel: () -> Unit,
    labelsEditable: Boolean = canSaveForGame,
) {
    val controls = remember(initial) { mutableStateListOf(*initial.controls.toTypedArray()) }
    var selectedId by remember(initial) { mutableStateOf<String?>(null) }
    var askScope by remember { mutableStateOf(false) }
    var adding by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var toolbarOffset by remember { mutableStateOf(Offset.Zero) }
    val colors = controlColors()
    val current = initial.copy(controls = controls.toList())
    val initialLabels = remember(initial) { initial.controls.associate { it.id to it.label } }
    val labelsChanged = controls.any { it.id in initialLabels && initialLabels[it.id] != it.label }
    val selected = controls.firstOrNull { it.id == selectedId }

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
            val isSelected = control.id == selectedId
            Box(
                boxModifier
                    .alpha(if (control.hidden) 0.3f else 1f)
                    .then(if (isSelected) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp)) else Modifier)
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
                if (isSelected) {
                    // Size readout next to the control, to match sizes while dragging the slider.
                    SizeTag(control.size, Modifier.align(Alignment.TopCenter).offset(y = (-28).dp))
                }
            }
        }

        EditorToolbar(
            selected = selected,
            labelsEditable = labelsEditable,
            onDrag = { toolbarOffset += it },
            onSize = { size -> update(controls, selectedId) { it.copy(size = size.coerceIn(MIN_SIZE, MAX_SIZE)) } },
            onToggleHidden = { update(controls, selectedId) { it.copy(hidden = !it.hidden) } },
            onRename = { renaming = true },
            onDelete = {
                controls.removeAll { it.id == selectedId }
                selectedId = null
            },
            onAdd = { adding = true },
            onReset = {
                controls.clear()
                controls.addAll(original.controls)
                selectedId = null
            },
            onCancel = onCancel,
            onSave = {
                when {
                    labelsChanged -> onSave(current, true, true) // renamed labels belong to this game only
                    canSaveForGame -> askScope = true
                    else -> onSave(current, false, false)
                }
            },
            // Starts centred, where it never covers controls; can be dragged aside by its handle.
            modifier = Modifier
                .align(Alignment.Center)
                .offset { IntOffset(toolbarOffset.x.roundToInt(), toolbarOffset.y.roundToInt()) }
                .safeDrawingPadding(),
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
                    onSave(current, true, false)
                }) { Text(stringResource(R.string.editor_save_game)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    askScope = false
                    onSave(current, false, false)
                }) { Text(stringResource(R.string.editor_save_global)) }
            },
        )
    }

    if (adding) {
        AddControlDialog(
            onAdd = { control ->
                adding = false
                controls += control
                selectedId = control.id
            },
            onDismiss = { adding = false },
        )
    }

    if (renaming && selected != null) {
        RenameDialog(
            current = selected.label.orEmpty(),
            onRename = { label ->
                renaming = false
                update(controls, selectedId) { it.copy(label = label.ifBlank { null }) }
            },
            onDismiss = { renaming = false },
        )
    }
}

private fun update(controls: MutableList<OverlayControl>, id: String?, transform: (OverlayControl) -> OverlayControl) {
    val index = controls.indexOfFirst { it.id == id }
    if (index >= 0) controls[index] = transform(controls[index])
}

/** Sizes are shown in percent of the screen height, the unit layouts use internally. */
private fun sizePercent(size: Float): Int = (size * 100).roundToInt()

@Composable
private fun SizeTag(size: Float, modifier: Modifier = Modifier) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        modifier = modifier.wrapContentSize(unbounded = true),
    ) {
        Text(
            stringResource(R.string.editor_size_value, sizePercent(size)),
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}

@Composable
private fun EditorToolbar(
    selected: OverlayControl?,
    labelsEditable: Boolean,
    onDrag: (Offset) -> Unit,
    onSize: (Float) -> Unit,
    onToggleHidden: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onAdd: () -> Unit,
    onReset: () -> Unit,
    onCancel: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(shape = RoundedCornerShape(28.dp), tonalElevation = 6.dp, shadowElevation = 6.dp, modifier = modifier) {
        Row(Modifier.padding(start = 4.dp, end = 12.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            DragHandle(onDrag)
            Column {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        selected?.let { controlName(it) } ?: stringResource(R.string.editor_hint),
                        style = if (selected != null) MaterialTheme.typography.labelLarge else MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(start = 4.dp, end = 8.dp),
                    )
                    TextButton(onClick = onAdd) { Text(stringResource(R.string.editor_add)) }
                    TextButton(onClick = onReset) { Text(stringResource(R.string.editor_reset)) }
                    TextButton(onClick = onCancel) { Text(stringResource(R.string.dialog_cancel)) }
                    TextButton(onClick = onSave) { Text(stringResource(R.string.editor_save)) }
                }
                if (selected != null) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Slider(
                            value = selected.size,
                            onValueChange = onSize,
                            valueRange = MIN_SIZE..MAX_SIZE,
                            modifier = Modifier.width(170.dp),
                        )
                        SizeField(selected, onSize)
                        FilterChip(
                            selected = selected.hidden,
                            onClick = onToggleHidden,
                            label = { Text(stringResource(R.string.editor_hidden)) },
                        )
                        if (labelsEditable && selected.hasLabel) {
                            TextButton(onClick = onRename) { Text(stringResource(R.string.editor_rename)) }
                        }
                        IconButton(onClick = onDelete) {
                            Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.editor_delete))
                        }
                    }
                }
            }
        }
    }
}

/** Label, or what the control is when it has none (d-pad, stick, quick menu). */
@Composable
private fun controlName(control: OverlayControl): String = control.label ?: when {
    control.type == ControlType.DPAD -> stringResource(R.string.editor_add_dpad)
    control.type == ControlType.STICK -> stringResource(R.string.editor_add_stick)
    control.action != null -> actionName(control.action)
    else -> control.id
}

private val OverlayControl.hasLabel: Boolean
    get() = type != ControlType.DPAD && type != ControlType.STICK && action != InputAction.QUICK_MENU

/** Typed size in percent; applied on "done" or when the field loses focus. */
@Composable
private fun SizeField(control: OverlayControl, onSize: (Float) -> Unit) {
    var text by remember(control.id, control.size) { mutableStateOf(sizePercent(control.size).toString()) }
    val apply = { text.toIntOrNull()?.let { onSize(it / 100f) } }
    OutlinedTextField(
        value = text,
        onValueChange = { value -> text = value.filter(Char::isDigit).take(2) },
        singleLine = true,
        suffix = { Text("%") },
        textStyle = MaterialTheme.typography.bodyMedium,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { apply() }),
        modifier = Modifier
            .width(84.dp)
            .onFocusChanged { if (!it.isFocused) apply() },
    )
}

/** Grip to move the toolbar out of the way. */
@Composable
private fun DragHandle(onDrag: (Offset) -> Unit) {
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        Modifier
            .size(width = 28.dp, height = 48.dp)
            .pointerInput(Unit) {
                detectDragGestures { change, drag ->
                    change.consume()
                    onDrag(drag)
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(width = 10.dp, height = 18.dp)) {
            val r = 1.6.dp.toPx()
            for (col in 0..1) for (row in 0..2) {
                drawCircle(color, r, Offset(r + col * (size.width - 2 * r), r + row * (size.height - 2 * r) / 2))
            }
        }
    }
}

/** What can be added: a d-pad, a stick, or a button for any action. */
private data class AddOption(val label: String, val create: (String) -> OverlayControl)

@Composable
private fun AddControlDialog(onAdd: (OverlayControl) -> Unit, onDismiss: () -> Unit) {
    val options = listOf(
        AddOption(stringResource(R.string.editor_add_dpad)) { id -> OverlayControl(id, ControlType.DPAD, Anchor.CENTER_START, 0.3f, 0f, 0.38f) },
        AddOption(stringResource(R.string.editor_add_stick)) { id -> OverlayControl(id, ControlType.STICK, Anchor.CENTER_START, 0.3f, 0f, 0.34f) },
    ) + listOf(InputAction.OK, InputAction.CANCEL, InputAction.MENU, InputAction.SHIFT, InputAction.PAGE_UP, InputAction.PAGE_DOWN, InputAction.QUICK_MENU)
        .map { action ->
            AddOption(actionName(action)) { id ->
                val (type, size) = when (action) {
                    InputAction.PAGE_UP, InputAction.PAGE_DOWN -> ControlType.SHOULDER to 0.26f
                    InputAction.QUICK_MENU -> ControlType.PILL to 0.16f
                    else -> ControlType.BUTTON to 0.14f
                }
                OverlayControl(id, type, Anchor.CENTER_END, 0.3f, 0f, size, label = DEFAULT_LABELS[action], action = action)
            }
        }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.editor_add_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                for (option in options) {
                    Text(
                        option.label,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onAdd(option.create("added-${UUID.randomUUID().toString().take(8)}")) }
                            .padding(vertical = 12.dp),
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.dialog_cancel)) } },
    )
}

private val DEFAULT_LABELS = mapOf(
    InputAction.OK to "A",
    InputAction.CANCEL to "B",
    InputAction.MENU to "X",
    InputAction.SHIFT to "Y",
    InputAction.PAGE_UP to "L",
    InputAction.PAGE_DOWN to "R",
)

@Composable
private fun RenameDialog(current: String, onRename: (String) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.editor_rename_title)) },
        text = {
            Column {
                Text(stringResource(R.string.editor_rename_text), style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.take(MAX_LABEL_LENGTH) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { onRename(text.trim()) }),
                )
            }
        },
        confirmButton = { TextButton(onClick = { onRename(text.trim()) }) { Text(stringResource(R.string.dialog_ok)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.dialog_cancel)) } },
    )
}
