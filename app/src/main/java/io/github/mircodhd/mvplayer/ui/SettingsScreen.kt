// SPDX-FileCopyrightText: 2026 MircoDHD
// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.mircodhd.mvplayer.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.mircodhd.mvplayer.R
import io.github.mircodhd.mvplayer.settings.GlobalSettings
import io.github.mircodhd.mvplayer.settings.Renderer
import io.github.mircodhd.mvplayer.ui.settings.OptionBadge
import io.github.mircodhd.mvplayer.ui.settings.RendererDialog
import io.github.mircodhd.mvplayer.ui.settings.SectionHeader
import io.github.mircodhd.mvplayer.ui.settings.SwitchRow
import io.github.mircodhd.mvplayer.ui.settings.ValueRow
import io.github.mircodhd.mvplayer.ui.settings.overlayModeLabel
import io.github.mircodhd.mvplayer.ui.settings.rendererLabel

const val GITHUB_PROFILE_URL = "https://github.com/MircoDHD"
const val LICENSE_URL = "https://www.gnu.org/licenses/gpl-3.0.html"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    versionName: String,
    global: GlobalSettings,
    logToFile: Boolean,
    logLocation: String?,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onOpenController: () -> Unit,
    onOpenOverlay: () -> Unit,
    onFrameRateCapChange: (Boolean) -> Unit,
    onRendererChange: (Renderer) -> Unit,
    onOpenHud: () -> Unit,
    onLogToFileChange: (Boolean) -> Unit,
    onShareLogs: () -> Unit,
    onOpenUrl: (String) -> Unit,
    onOpenChangelog: () -> Unit,
    onExportSettings: () -> Unit,
    onImportSettings: () -> Unit,
    onUnlockTitle: () -> Unit,
    onLibraryTitleChange: (String) -> Unit,
) {
    val resources = LocalResources.current
    var versionTaps by remember { mutableIntStateOf(0) }
    var thanks by remember { mutableStateOf(false) }
    var editTitle by remember { mutableStateOf(false) }
    var chooseRenderer by remember { mutableStateOf(false) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.set_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.set_back))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding)) {
            item { SectionHeader(stringResource(R.string.set_section_input)) }
            item {
                ValueRow(
                    title = stringResource(R.string.controller_title),
                    value = null,
                    summary = stringResource(R.string.controller_summary),
                    icon = Icons.Filled.Settings,
                    onClick = onOpenController,
                )
            }
            item {
                ValueRow(
                    title = stringResource(R.string.overlay_title),
                    value = resources.getString(overlayModeLabel(global.overlay.mode)),
                    summary = stringResource(R.string.overlay_summary),
                    icon = Icons.Filled.Build,
                    onClick = onOpenOverlay,
                )
            }

            item { SectionHeader(stringResource(R.string.set_section_game)) }
            item {
                SwitchRow(
                    icon = Icons.Filled.PlayArrow,
                    title = stringResource(R.string.set_frame_cap),
                    summary = stringResource(R.string.set_frame_cap_summary),
                    checked = global.frameRateCap > 0,
                    onCheckedChange = onFrameRateCapChange,
                )
            }
            item {
                ValueRow(
                    icon = Icons.Filled.Create,
                    title = stringResource(R.string.renderer_title),
                    value = rendererLabel(resources, global.renderer),
                    summary = stringResource(R.string.renderer_summary),
                    badges = if (global.renderer == Renderer.WEBGL2) listOf(OptionBadge.EXPERIMENTAL) else emptyList(),
                    onClick = { chooseRenderer = true },
                )
            }
            item {
                ValueRow(
                    icon = Icons.AutoMirrored.Filled.List,
                    title = stringResource(R.string.hud_title),
                    value = resources.getString(if (global.hud.enabled) R.string.value_on else R.string.value_off),
                    summary = stringResource(R.string.hud_summary),
                    onClick = onOpenHud,
                )
            }

            item { SectionHeader(stringResource(R.string.set_section_privacy)) }
            item {
                SwitchRow(
                    icon = Icons.Filled.Lock,
                    title = stringResource(R.string.set_diagnostics),
                    summary = stringResource(R.string.set_diagnostics_summary),
                    checked = logToFile,
                    onCheckedChange = onLogToFileChange,
                )
            }
            item {
                ValueRow(
                    icon = Icons.Filled.Share,
                    title = stringResource(R.string.set_share_logs),
                    value = null,
                    summary = logLocation?.let { stringResource(R.string.set_log_location, it) },
                    onClick = onShareLogs,
                )
            }

            if (global.titleEditUnlocked) {
                item { SectionHeader(stringResource(R.string.set_section_personal)) }
                item {
                    ValueRow(
                        icon = Icons.Filled.Edit,
                        title = stringResource(R.string.set_library_title),
                        value = global.libraryTitle ?: stringResource(R.string.app_name_short),
                        onClick = { editTitle = true },
                    )
                }
            }

            item { SectionHeader(stringResource(R.string.set_section_backup)) }
            item {
                ValueRow(
                    icon = Icons.Filled.KeyboardArrowUp,
                    title = stringResource(R.string.backup_export),
                    value = null,
                    summary = stringResource(R.string.backup_export_summary),
                    onClick = onExportSettings,
                )
            }
            item {
                ValueRow(
                    icon = Icons.Filled.KeyboardArrowDown,
                    title = stringResource(R.string.backup_import),
                    value = null,
                    summary = stringResource(R.string.backup_import_summary),
                    onClick = onImportSettings,
                )
            }

            item { SectionHeader(stringResource(R.string.set_section_about)) }
            item {
                ValueRow(
                    title = stringResource(R.string.app_name),
                    value = null,
                    // Ten taps on the version unlock a thank-you and the custom library title.
                    onClick = {
                        versionTaps++
                        if (versionTaps == EASTER_EGG_TAPS) {
                            versionTaps = 0
                            thanks = true
                        }
                    },
                    icon = Icons.Filled.Info,
                    summary = stringResource(R.string.set_version_value, versionName),
                )
            }
            item {
                ValueRow(
                    icon = Icons.Filled.Notifications,
                    title = stringResource(R.string.changelog_open),
                    value = null,
                    onClick = onOpenChangelog,
                )
            }
            item {
                ValueRow(
                    icon = Icons.Filled.Person,
                    title = stringResource(R.string.set_developer),
                    value = null,
                    summary = stringResource(R.string.set_developer_summary),
                    onClick = { onOpenUrl(GITHUB_PROFILE_URL) },
                )
            }
            item {
                ValueRow(
                    icon = Icons.Filled.Star,
                    title = stringResource(R.string.set_license),
                    value = null,
                    summary = stringResource(R.string.set_license_summary),
                    onClick = { onOpenUrl(LICENSE_URL) },
                )
            }
            item {
                ValueRow(
                    title = stringResource(R.string.set_third_party),
                    value = null,
                    summary = stringResource(R.string.set_third_party_summary),
                    onClick = null,
                )
            }
            item {
                Text(
                    stringResource(R.string.set_trademark),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 20.dp),
                )
            }
        }
    }

    if (thanks) {
        AlertDialog(
            onDismissRequest = {},
            icon = { Icon(Icons.Filled.Favorite, contentDescription = null) },
            title = { Text(stringResource(R.string.egg_title)) },
            text = {
                Text(
                    stringResource(
                        if (global.titleEditUnlocked) R.string.egg_text_again else R.string.egg_text,
                    ),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    thanks = false
                    if (!global.titleEditUnlocked) onUnlockTitle()
                }) { Text(stringResource(R.string.egg_ok)) }
            },
        )
    }

    if (chooseRenderer) {
        RendererDialog(
            selected = global.renderer,
            defaultRenderer = null,
            onSelect = onRendererChange,
            onDismiss = { chooseRenderer = false },
        )
    }

    if (editTitle) {
        TitleDialog(
            current = global.libraryTitle.orEmpty(),
            placeholder = stringResource(R.string.app_name_short),
            onConfirm = {
                editTitle = false
                onLibraryTitleChange(it)
            },
            onDismiss = { editTitle = false },
        )
    }
}

private const val EASTER_EGG_TAPS = 10

@Composable
private fun TitleDialog(current: String, placeholder: String, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.set_library_title)) },
        text = {
            Column {
                Text(stringResource(R.string.set_library_title_text), style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.take(GlobalSettings.MAX_TITLE_LENGTH) },
                    singleLine = true,
                    placeholder = { Text(placeholder) },
                )
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(text) }) { Text(stringResource(R.string.dialog_ok)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.dialog_cancel)) } },
    )
}
