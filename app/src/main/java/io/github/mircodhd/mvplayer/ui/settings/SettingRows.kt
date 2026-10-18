// SPDX-FileCopyrightText: 2026 MircoDHD
// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.mircodhd.mvplayer.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import io.github.mircodhd.mvplayer.R

/** Flags shown next to an option. */
enum class OptionBadge { EXPERIMENTAL, CHANGES_GAMEPLAY }

@Composable
fun SectionHeader(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 4.dp),
    )
}

@Composable
fun Badges(badges: List<OptionBadge>) {
    if (badges.isEmpty()) return
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 4.dp)) {
        for (badge in badges) {
            val (text, container, content) = when (badge) {
                OptionBadge.EXPERIMENTAL -> Triple(
                    stringResource(R.string.badge_experimental),
                    MaterialTheme.colorScheme.tertiaryContainer,
                    MaterialTheme.colorScheme.onTertiaryContainer,
                )
                OptionBadge.CHANGES_GAMEPLAY -> Triple(
                    stringResource(R.string.badge_changes_gameplay),
                    MaterialTheme.colorScheme.errorContainer,
                    MaterialTheme.colorScheme.onErrorContainer,
                )
            }
            Surface(shape = MaterialTheme.shapes.small, color = container, contentColor = content) {
                Text(text, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
            }
        }
    }
}

@Composable
fun SwitchRow(
    title: String,
    summary: String?,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    icon: ImageVector? = null,
    badges: List<OptionBadge> = emptyList(),
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = if (summary != null || badges.isNotEmpty()) {
            {
                Column {
                    summary?.let { Text(it) }
                    Badges(badges)
                }
            }
        } else {
            null
        },
        leadingContent = icon?.let { { Icon(it, contentDescription = null) } },
        trailingContent = { Switch(checked = checked, onCheckedChange = onCheckedChange) },
        modifier = Modifier.clickable(role = Role.Switch) { onCheckedChange(!checked) },
    )
}

/**
 * A row showing a value; [isDefault] adds "(default)" handling for per-game overrides,
 * [onReset] shows a reset button when the value is overridden.
 */
@Composable
fun ValueRow(
    title: String,
    value: String?,
    onClick: (() -> Unit)?,
    icon: ImageVector? = null,
    summary: String? = null,
    badges: List<OptionBadge> = emptyList(),
    onReset: (() -> Unit)? = null,
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = if (value != null || summary != null || badges.isNotEmpty()) {
            {
                Column {
                    value?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
                    summary?.let { Text(it) }
                    Badges(badges)
                }
            }
        } else {
            null
        },
        leadingContent = icon?.let { { Icon(it, contentDescription = null) } },
        trailingContent = onReset?.let {
            {
                IconButton(onClick = it) {
                    Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.set_use_default))
                }
            }
        },
        modifier = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier,
    )
}

/** One entry of a [ChoiceDialog]; `value == null` is "Use default". */
data class Choice<T>(val value: T?, val label: String, val badges: List<OptionBadge> = emptyList())

@Composable
fun <T> ChoiceDialog(
    title: String,
    choices: List<Choice<T>>,
    selected: T?,
    onSelect: (T?) -> Unit,
    onDismiss: () -> Unit,
    note: String? = null,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                note?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                }
                for (choice in choices) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .selectable(selected = choice.value == selected, role = Role.RadioButton) {
                                onSelect(choice.value)
                                onDismiss()
                            }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = choice.value == selected, onClick = null)
                        Column(Modifier.padding(start = 12.dp)) {
                            Text(choice.label, style = MaterialTheme.typography.bodyLarge)
                            Badges(choice.badges)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.dialog_cancel)) } },
    )
}

/** Slider with an optional "Use default" checkbox (per-game overrides). */
@Composable
fun SliderDialog(
    title: String,
    value: Float?,
    defaultValue: Float,
    range: ClosedFloatingPointRange<Float>,
    format: (Float) -> String,
    allowDefault: Boolean,
    onConfirm: (Float?) -> Unit,
    onDismiss: () -> Unit,
) {
    var useDefault by remember { mutableStateOf(allowDefault && value == null) }
    var current by remember { mutableFloatStateOf(value ?: defaultValue) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                Text(format(if (useDefault) defaultValue else current), style = MaterialTheme.typography.headlineSmall)
                Slider(
                    value = if (useDefault) defaultValue else current,
                    onValueChange = {
                        useDefault = false
                        current = it
                    },
                    valueRange = range,
                )
                if (allowDefault) {
                    Row(
                        Modifier.clickable { useDefault = !useDefault },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = useDefault, onCheckedChange = { useDefault = it })
                        Text(stringResource(R.string.set_use_default_value, format(defaultValue)))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onConfirm(if (useDefault) null else current)
                onDismiss()
            }) { Text(stringResource(R.string.dialog_ok)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.dialog_cancel)) } },
    )
}
