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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import io.github.mircodhd.mvplayer.R

/** Flags shown next to an option; DEFAULT marks the value a game inherits from the global settings. */
enum class OptionBadge { DEFAULT, EXPERIMENTAL, CHANGES_GAMEPLAY }

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
/** [inline]: next to a value on the same line instead of below a text. */
fun Badges(badges: List<OptionBadge>, modifier: Modifier = Modifier, inline: Boolean = false) {
    if (badges.isEmpty()) return
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = modifier.padding(top = if (inline) 0.dp else 4.dp)) {
        for (badge in badges) {
            val (text, container, content) = when (badge) {
                OptionBadge.DEFAULT -> Triple(
                    stringResource(R.string.badge_default),
                    MaterialTheme.colorScheme.secondaryContainer,
                    MaterialTheme.colorScheme.onSecondaryContainer,
                )
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
                Text(text, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 8.dp, vertical = 1.dp))
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

/** A row showing a value; [onReset] shows a reset button. */
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
                    if (value != null) {
                        // Value and tags on one line, e.g. "60 fps  [Default]", sharing one baseline:
                        // ListItem reads differing first and last baselines as a second line.
                        Row {
                            Text(value, color = MaterialTheme.colorScheme.primary, modifier = Modifier.alignByBaseline())
                            if (badges.isNotEmpty()) Spacer(Modifier.width(8.dp))
                            Badges(badges, Modifier.alignByBaseline(), inline = true)
                        }
                    }
                    summary?.let { Text(it) }
                    if (value == null) Badges(badges)
                }
            }
        } else {
            null
        },
        leadingContent = icon?.let { { Icon(it, contentDescription = null) } },
        trailingContent = onReset?.let {
            {
                IconButton(onClick = it) {
                    Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.set_reset_default))
                }
            }
        },
        modifier = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier,
    )
}

/** One entry of a [ChoiceDialog]. */
/** One entry of a [ChoiceDialog]; [description] is a short line under the label. */
data class Choice<T>(
    val value: T,
    val label: String,
    val badges: List<OptionBadge> = emptyList(),
    val description: String? = null,
)

/**
 * Choices for a per-game override: the option equal to the global value [default] carries
 * the DEFAULT badge. Pass the picked value through [overrideOf] to store it.
 */
fun <T> overrideChoices(
    options: List<Pair<T, String>>,
    default: T,
    extra: (T) -> List<OptionBadge> = { emptyList() },
    describe: (T) -> String? = { null },
) = options.map { (value, label) ->
    Choice(value, label, (if (value == default) listOf(OptionBadge.DEFAULT) else emptyList()) + extra(value), describe(value))
}

/** Picking the global value again means "follow the default" (no override). */
fun <T> overrideOf(picked: T, default: T): T? = picked.takeUnless { it == default }

/** DEFAULT badge for a row whose per-game value follows the global setting. */
fun defaultBadge(override: Any?): List<OptionBadge> = if (override == null) listOf(OptionBadge.DEFAULT) else emptyList()

@Composable
fun <T> ChoiceDialog(
    title: String,
    choices: List<Choice<T>>,
    selected: T,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit,
    note: String? = null,
) {
    // Wider than the platform default, so descriptions fit on one line and all choices stay
    // visible in landscape (games run in landscape, where dialogs have little height).
    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier.padding(horizontal = 24.dp),
        title = { Text(title) },
        text = {
            // Choices first, the note below: a long note must not push them out of view.
            Column(Modifier.verticalScroll(rememberScrollState())) {
                for (choice in choices) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .selectable(selected = choice.value == selected, role = Role.RadioButton) {
                                onSelect(choice.value)
                                onDismiss()
                            }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = choice.value == selected, onClick = null)
                        Column(Modifier.padding(start = 12.dp)) {
                            Row {
                                Text(choice.label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.alignByBaseline())
                                if (choice.badges.isNotEmpty()) Spacer(Modifier.width(8.dp))
                                Badges(choice.badges, Modifier.alignByBaseline(), inline = true)
                            }
                            choice.description?.let {
                                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
                note?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.dialog_cancel)) } },
    )
}

/**
 * Slider with the current value. With a [defaultValue] (per-game overrides) the default is
 * marked with a badge and one tap away; confirming the default stores no override (null).
 */
@Composable
fun SliderDialog(
    title: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    format: (Float) -> String,
    onConfirm: (Float?) -> Unit,
    onDismiss: () -> Unit,
    defaultValue: Float? = null,
) {
    var current by remember { mutableFloatStateOf(value) }
    val isDefault = defaultValue != null && kotlin.math.abs(current - defaultValue) < 0.005f
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(format(current), style = MaterialTheme.typography.headlineSmall)
                    if (isDefault) {
                        Spacer(Modifier.width(12.dp))
                        Badges(listOf(OptionBadge.DEFAULT), inline = true)
                    }
                }
                Slider(value = current, onValueChange = { current = it }, valueRange = range)
                if (defaultValue != null && !isDefault) {
                    TextButton(onClick = { current = defaultValue }) {
                        Text(stringResource(R.string.set_back_to_default, format(defaultValue)))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onConfirm(if (isDefault) null else current)
                onDismiss()
            }) { Text(stringResource(R.string.dialog_ok)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.dialog_cancel)) } },
    )
}
