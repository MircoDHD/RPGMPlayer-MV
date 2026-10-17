// SPDX-FileCopyrightText: 2026 MircoDHD
// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.mircodhd.mvplayer.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.mircodhd.mvplayer.R

const val GITHUB_PROFILE_URL = "https://github.com/MircoDHD"
const val LICENSE_URL = "https://www.gnu.org/licenses/gpl-3.0.html"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    versionName: String,
    frameRateCapEnabled: Boolean,
    logToFile: Boolean,
    logLocation: String?,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onFrameRateCapChange: (Boolean) -> Unit,
    onLogToFileChange: (Boolean) -> Unit,
    onShareLogs: () -> Unit,
    onOpenUrl: (String) -> Unit,
) {
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
            item { SectionHeader(stringResource(R.string.set_section_game)) }
            item {
                SwitchItem(
                    icon = Icons.Filled.PlayArrow,
                    title = stringResource(R.string.set_frame_cap),
                    summary = stringResource(R.string.set_frame_cap_summary),
                    checked = frameRateCapEnabled,
                    onCheckedChange = onFrameRateCapChange,
                )
            }

            item { SectionHeader(stringResource(R.string.set_section_privacy)) }
            item {
                SwitchItem(
                    icon = Icons.Filled.Lock,
                    title = stringResource(R.string.set_diagnostics),
                    summary = stringResource(R.string.set_diagnostics_summary),
                    checked = logToFile,
                    onCheckedChange = onLogToFileChange,
                )
            }
            item {
                LinkItem(
                    icon = Icons.Filled.Share,
                    title = stringResource(R.string.set_share_logs),
                    summary = logLocation?.let { stringResource(R.string.set_log_location, it) },
                    onClick = onShareLogs,
                )
            }

            item { SectionHeader(stringResource(R.string.set_section_about)) }
            item {
                LinkItem(Icons.Filled.Info, stringResource(R.string.set_version), versionName, onClick = null)
            }
            item {
                LinkItem(
                    icon = Icons.Filled.Person,
                    title = stringResource(R.string.set_developer),
                    summary = stringResource(R.string.set_developer_summary),
                    onClick = { onOpenUrl(GITHUB_PROFILE_URL) },
                )
            }
            item {
                LinkItem(
                    icon = Icons.Filled.Star,
                    title = stringResource(R.string.set_license),
                    summary = stringResource(R.string.set_license_summary),
                    onClick = { onOpenUrl(LICENSE_URL) },
                )
            }
            item {
                LinkItem(
                    icon = null,
                    title = stringResource(R.string.set_third_party),
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

@Composable
private fun SectionHeader(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 4.dp),
    )
}

@Composable
private fun SwitchItem(
    icon: ImageVector,
    title: String,
    summary: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(summary) },
        leadingContent = { Icon(icon, contentDescription = null) },
        trailingContent = { Switch(checked = checked, onCheckedChange = onCheckedChange) },
        modifier = Modifier.clickable { onCheckedChange(!checked) },
    )
}

@Composable
private fun LinkItem(icon: ImageVector?, title: String, summary: String?, onClick: (() -> Unit)?) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = summary?.let { { Text(it) } },
        leadingContent = icon?.let { { Icon(it, contentDescription = null) } },
        modifier = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier,
    )
}
