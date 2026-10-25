// SPDX-FileCopyrightText: 2026 MircoDHD
// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.mircodhd.mvplayer.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.mircodhd.mvplayer.R
import io.github.mircodhd.mvplayer.saves.ResumePoint
import io.github.mircodhd.mvplayer.library.GameEntry

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun GameInfoSheet(
    game: GameEntry,
    cover: ImageBitmap?,
    folderMissing: Boolean,
    onDismiss: () -> Unit,
    onPlay: () -> Unit,
    onRemove: () -> Unit,
    onOpenSettings: () -> Unit,
    appSaves: List<ResumePoint> = emptyList(),
    onOpenAppSaves: () -> Unit = {},
    onOpenGameSaves: () -> Unit = {},
    activeMods: Int = 0,
    onOpenMods: () -> Unit = {},
) {
    val context = LocalContext.current
    var confirmRemove by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp),
        ) {
            Cover(
                cover = cover,
                title = game.title,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(28.dp)),
            )
            Spacer(Modifier.height(16.dp))
            Text(game.title, style = MaterialTheme.typography.headlineSmall)
            Text(
                game.location,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(onClick = onPlay, enabled = !folderMissing, contentPadding = ButtonDefaults.ButtonWithIconContentPadding) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                    Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                    Text(stringResource(R.string.lib_play))
                }
                FilledTonalButton(onClick = onOpenSettings, contentPadding = ButtonDefaults.ButtonWithIconContentPadding) {
                    Icon(Icons.Filled.Settings, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                    Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                    Text(stringResource(R.string.info_game_settings))
                }
                OutlinedButton(onClick = onOpenGameSaves, enabled = !folderMissing, contentPadding = ButtonDefaults.ButtonWithIconContentPadding) {
                    Text(stringResource(R.string.game_saves_title))
                }
                OutlinedButton(onClick = onOpenMods, enabled = !folderMissing, contentPadding = ButtonDefaults.ButtonWithIconContentPadding) {
                    Text(if (activeMods > 0) stringResource(R.string.mods_button_on, activeMods) else stringResource(R.string.mods_button))
                }
                OutlinedButton(onClick = onOpenAppSaves, contentPadding = ButtonDefaults.ButtonWithIconContentPadding) {
                    Text(stringResource(R.string.app_saves_title) + if (appSaves.isEmpty()) "" else " (${appSaves.size})")
                }
                OutlinedButton(onClick = { confirmRemove = true }, contentPadding = ButtonDefaults.ButtonWithIconContentPadding) {
                    Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                    Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                    Text(stringResource(R.string.info_remove))
                }
            }
            Spacer(Modifier.height(20.dp))

            Section(stringResource(R.string.info_section_play))
            InfoRow(stringResource(R.string.info_playtime), Format.playTime(context, game.playTimeMs))
            InfoRow(
                stringResource(R.string.info_last_played),
                if (game.lastPlayedAt > 0) Format.date(context, game.lastPlayedAt) else stringResource(R.string.info_never),
            )
            InfoRow(stringResource(R.string.info_added), Format.date(context, game.addedAt))

            Section(stringResource(R.string.info_section_technical))
            val details = game.details
            if (details == null) {
                Row(Modifier.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(12.dp))
                    Text(stringResource(R.string.info_reading), style = MaterialTheme.typography.bodyMedium)
                }
            } else {
                val unknown = stringResource(R.string.info_unknown)
                val yes = stringResource(R.string.info_yes)
                val no = stringResource(R.string.info_no)
                InfoRow(stringResource(R.string.info_engine), "RPG Maker MV ${details.engineVersion ?: unknown}")
                InfoRow(stringResource(R.string.info_pixi), details.pixiVersion ?: unknown)
                InfoRow(
                    stringResource(R.string.info_plugins),
                    stringResource(R.string.info_plugins_value, details.enabledPlugins, details.totalPlugins),
                )
                InfoRow(stringResource(R.string.info_size), Format.size(context, details.sizeBytes))
                InfoRow(stringResource(R.string.info_files), details.fileCount.toString())
                InfoRow(stringResource(R.string.info_encrypted), if (details.encrypted) yes else no)
                InfoRow(stringResource(R.string.info_steam_stub), if (details.usesGreenworks) yes else no)
                InfoRow(stringResource(R.string.info_profiles), details.profiles.joinToString())
                if (details.userPatches.isNotEmpty()) {
                    InfoRow(stringResource(R.string.info_user_patches), details.userPatches.joinToString("\n"))
                    Text(
                        stringResource(R.string.info_user_patches_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
            }
            InfoRow(stringResource(R.string.info_folder), game.location)
        }
    }

    if (confirmRemove) {
        AlertDialog(
            onDismissRequest = { confirmRemove = false },
            icon = { Icon(Icons.Filled.Delete, contentDescription = null) },
            title = { Text(stringResource(R.string.info_remove_title, game.title)) },
            text = { Text(stringResource(R.string.info_remove_text)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmRemove = false
                    onRemove()
                }) { Text(stringResource(R.string.info_remove)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmRemove = false }) { Text(stringResource(R.string.dialog_cancel)) }
            },
        )
    }
}

@Composable
private fun Section(title: String) {
    Spacer(Modifier.height(12.dp))
    Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
    Spacer(Modifier.height(4.dp))
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.Top) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.42f),
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(0.58f),
        )
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
}
