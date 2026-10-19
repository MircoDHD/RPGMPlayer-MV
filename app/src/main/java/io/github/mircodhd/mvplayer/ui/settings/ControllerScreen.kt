// SPDX-FileCopyrightText: 2026 MircoDHD
// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.mircodhd.mvplayer.ui.settings

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.hardware.input.InputManager
import android.view.InputDevice
import android.view.Window
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindowProvider
import io.github.mircodhd.mvplayer.R
import io.github.mircodhd.mvplayer.input.ControlNames
import io.github.mircodhd.mvplayer.input.ControllerMapping
import io.github.mircodhd.mvplayer.input.GamepadInput
import io.github.mircodhd.mvplayer.input.InputAction
import io.github.mircodhd.mvplayer.input.InputCapture
import io.github.mircodhd.mvplayer.input.InterceptWindowInput
import kotlinx.coroutines.delay

private const val CAPTURE_TIMEOUT_MS = 10_000L

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ControllerScreen(
    title: String,
    mapping: ControllerMapping,
    /** Per-game screen whose mapping currently follows the global default. */
    followsDefault: Boolean,
    perGame: Boolean,
    onChange: (ControllerMapping) -> Unit,
    onReset: () -> Unit,
    onBack: () -> Unit,
) {
    val capture = remember { InputCapture() }
    var capturing by remember { mutableStateOf<InputAction?>(null) }
    var conflict by remember { mutableStateOf<Pair<InputAction, List<InputAction>>?>(null) }
    InterceptWindowInput(currentWindow(), capture)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.set_back))
                    }
                },
                actions = {
                    TextButton(onClick = onReset) {
                        Text(stringResource(if (perGame) R.string.set_use_default else R.string.controller_reset))
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding() + 8.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item { ConnectedControllers(followsDefault && perGame) }
            items(InputAction.entries, key = { it.id }) { action ->
                ElevatedCard(shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(actionName(action), style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(6.dp))
                            val bindings = mapping.bindingsFor(action)
                            if (bindings.isEmpty()) {
                                Text(
                                    stringResource(R.string.controller_unassigned),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                for (binding in bindings) {
                                    InputChip(
                                        selected = false,
                                        onClick = { onChange(mapping.withRemoved(action, binding)) },
                                        label = { Text(ControlNames.binding(binding)) },
                                        trailingIcon = {
                                            Icon(
                                                Icons.Filled.Close,
                                                contentDescription = stringResource(R.string.controller_remove),
                                                modifier = Modifier.size(InputChipDefaults.IconSize),
                                            )
                                        },
                                    )
                                }
                            }
                            if (conflict?.first == action) {
                                Text(
                                    stringResource(R.string.controller_conflict, conflict!!.second.map { actionName(it, plain = true) }.joinToString()),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.tertiary,
                                )
                            }
                        }
                        FilledTonalIconButton(onClick = {
                            capturing = action
                            capture.start { control ->
                                val binding = listOf(control)
                                val others = mapping.actionsUsing(binding, except = action)
                                conflict = if (others.isNotEmpty()) action to others else null
                                onChange(mapping.withAdded(action, binding))
                                capturing = null
                            }
                        }) {
                            Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.controller_add))
                        }
                    }
                }
            }
        }
    }

    capturing?.let { action ->
        CaptureOverlay(action) {
            capture.cancel()
            capturing = null
        }
    }
}

@Composable
private fun ConnectedControllers(followsDefault: Boolean) {
    val context = LocalContext.current
    val names = remember {
        val inputManager = context.getSystemService(InputManager::class.java)
        inputManager.inputDeviceIds.toList()
            .mapNotNull(InputDevice::getDevice)
            .filter(GamepadInput::isController)
            .map { it.name }
            .distinct()
    }
    OutlinedCard(shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(
                if (names.isEmpty()) stringResource(R.string.controller_none_connected)
                else stringResource(R.string.controller_connected, names.joinToString()),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                stringResource(R.string.controller_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (followsDefault) {
                Spacer(Modifier.height(6.dp))
                Text(
                    stringResource(R.string.controller_follows_default),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun CaptureOverlay(action: InputAction, onCancel: () -> Unit) {
    var progress by remember { mutableFloatStateOf(1f) }
    LaunchedEffect(action) {
        val steps = 100
        repeat(steps) {
            delay(CAPTURE_TIMEOUT_MS / steps)
            progress = 1f - (it + 1f) / steps
        }
        onCancel()
    }
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.6f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
        contentAlignment = Alignment.Center,
    ) {
        ElevatedCard(shape = RoundedCornerShape(32.dp), modifier = Modifier.widthIn(max = 420.dp).padding(24.dp)) {
            Column(Modifier.padding(24.dp)) {
                Text(stringResource(R.string.controller_press_title), style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.controller_press_text, actionName(action, plain = true)))
                Spacer(Modifier.height(16.dp))
                LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = onCancel, modifier = Modifier.align(Alignment.End)) {
                    Text(stringResource(R.string.dialog_cancel))
                }
            }
        }
    }
}

@Composable
fun actionName(action: InputAction, plain: Boolean = false): String {
    val name = stringResource(
        when (action) {
            InputAction.OK -> R.string.action_ok
            InputAction.CANCEL -> R.string.action_cancel
            InputAction.SHIFT -> R.string.action_shift
            InputAction.MENU -> R.string.action_menu
            InputAction.PAGE_UP -> R.string.action_pageup
            InputAction.PAGE_DOWN -> R.string.action_pagedown
            InputAction.UP -> R.string.action_up
            InputAction.DOWN -> R.string.action_down
            InputAction.LEFT -> R.string.action_left
            InputAction.RIGHT -> R.string.action_right
            InputAction.QUICK_MENU -> R.string.action_quickmenu
            InputAction.TOGGLE_HUD -> R.string.action_togglehud
        },
    )
    return if (plain) name.substringBefore(" (") else name
}

/** The window this composable is shown in: a Compose dialog's window, else the activity's. */
@Composable
fun currentWindow(): Window {
    val view = LocalView.current
    (view.parent as? DialogWindowProvider)?.let { return it.window }
    return LocalContext.current.findActivity().window
}

private tailrec fun Context.findActivity(): Activity = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> error("No activity for $this")
}
