// SPDX-FileCopyrightText: 2026 MircoDHD
// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.mircodhd.mvplayer.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.mircodhd.mvplayer.R
import io.github.mircodhd.mvplayer.settings.GlobalSettings
import io.github.mircodhd.mvplayer.ui.settings.SectionHeader
import io.github.mircodhd.mvplayer.ui.settings.SwitchRow
import io.github.mircodhd.mvplayer.ui.settings.ValueRow
import io.github.mircodhd.mvplayer.ui.settings.overlayModeLabel

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
    onLogToFileChange: (Boolean) -> Unit,
    onShareLogs: () -> Unit,
    onOpenUrl: (String) -> Unit,
) {
    val resources = LocalResources.current
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

            item { SectionHeader(stringResource(R.string.set_section_about)) }
            item {
                ValueRow(
                    title = stringResource(R.string.app_name),
                    value = null,
                    onClick = null,
                    icon = Icons.Filled.Info,
                    summary = stringResource(R.string.set_version_value, versionName),
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
}
