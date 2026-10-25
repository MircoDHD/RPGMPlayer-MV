// SPDX-FileCopyrightText: 2026 MircoDHD
// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.mircodhd.mvplayer.ui.saves

import android.graphics.BitmapFactory
import android.text.format.DateUtils
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.mircodhd.mvplayer.R
import io.github.mircodhd.mvplayer.saves.ResumePoint
import io.github.mircodhd.mvplayer.saves.ResumePoints
import java.io.File

/**
 * The app saves of one game, newest first: load, keep (up to [ResumePoints.MAX_KEPT]) or
 * delete. [loadWarning] asks before loading over a running game; a point made with other
 * mods than [activeMods] (ids, top first) always asks first.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppSavesScreen(
    title: String,
    points: List<ResumePoint>,
    thumbnail: (String) -> File,
    loadWarning: Boolean,
    activeMods: List<String>,
    onLoad: (String) -> Unit,
    onKeep: (String, Boolean) -> Unit,
    onDelete: (String) -> Unit,
    onBack: () -> Unit,
    /** Export and import as a zip; null hides them (in game). */
    onExport: (() -> Unit)? = null,
    onImport: (() -> Unit)? = null,
    message: String? = null,
) {
    var confirmLoad by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf<String?>(null) }
    val keptFull = points.count { it.kept } >= ResumePoints.MAX_KEPT

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.set_back))
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 32.dp)) {
            Text(
                stringResource(R.string.app_saves_intro, ResumePoints.INTERVAL_MINUTES, ResumePoints.MAX_AUTO, ResumePoints.MAX_KEPT),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            if (onExport != null && onImport != null) {
                Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(onClick = onExport, enabled = points.isNotEmpty()) { Text(stringResource(R.string.app_saves_export)) }
                    OutlinedButton(onClick = onImport) { Text(stringResource(R.string.app_saves_import)) }
                }
            }
            message?.let { Text(it, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) }
            if (points.isEmpty()) {
                Text(
                    stringResource(R.string.app_saves_empty, ResumePoints.INTERVAL_MINUTES),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(16.dp),
                )
            }
            for (point in points) {
                PointCard(
                    point = point,
                    thumbnail = thumbnail(point.id),
                    canKeep = point.kept || !keptFull,
                    onLoad = { if (loadWarning || !point.sameMods(activeMods)) confirmLoad = point.id else onLoad(point.id) },
                    onKeep = { onKeep(point.id, !point.kept) },
                    onDelete = { confirmDelete = point.id },
                )
            }
        }
    }

    confirmLoad?.let { id ->
        val other = points.firstOrNull { it.id == id }?.takeIf { !it.sameMods(activeMods) }
        val none = stringResource(R.string.app_saves_no_mods)
        AlertDialog(
            onDismissRequest = { confirmLoad = null },
            title = { Text(stringResource(if (other != null) R.string.app_saves_mods_title else R.string.app_saves_load_title)) },
            text = {
                Text(
                    if (other == null) {
                        stringResource(R.string.app_saves_load_text)
                    } else {
                        stringResource(R.string.app_saves_mods_text, other.mods.joinToString().ifEmpty { none }, activeMods.joinToString().ifEmpty { none }) +
                            if (loadWarning) "\n\n" + stringResource(R.string.app_saves_load_text) else ""
                    },
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmLoad = null
                    onLoad(id)
                }) { Text(stringResource(if (other != null) R.string.app_saves_load_anyway else R.string.app_saves_load)) }
            },
            dismissButton = { TextButton(onClick = { confirmLoad = null }) { Text(stringResource(R.string.dialog_cancel)) } },
        )
    }
    confirmDelete?.let { id ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text(stringResource(R.string.app_saves_delete_title)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = null
                    onDelete(id)
                }) { Text(stringResource(R.string.app_saves_delete)) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text(stringResource(R.string.dialog_cancel)) } },
        )
    }
}

@Composable
private fun PointCard(
    point: ResumePoint,
    thumbnail: File,
    canKeep: Boolean,
    onLoad: () -> Unit,
    onKeep: () -> Unit,
    onDelete: () -> Unit,
) {
    val image = remember(point.id) {
        runCatching { BitmapFactory.decodeFile(thumbnail.path)?.asImageBitmap() }.getOrNull()
    }
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(width = 120.dp, height = 90.dp).clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                ) {
                    if (image != null) {
                        Image(image, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(if (point.kept) R.string.app_saves_kept else R.string.app_saves_auto),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (point.kept) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
                    )
                    Text(point.location.ifEmpty { "–" }, style = MaterialTheme.typography.titleMedium)
                    Text(
                        stringResource(R.string.app_saves_playtime, point.playtime),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    if (point.mods.isNotEmpty()) {
                        Text(
                            stringResource(R.string.app_saves_with_mods, point.mods.joinToString()),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        if (System.currentTimeMillis() - point.createdAt < DateUtils.MINUTE_IN_MILLIS) {
                            stringResource(R.string.app_saves_just_now)
                        } else {
                            DateUtils.getRelativeTimeSpanString(point.createdAt).toString()
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                FilledTonalButton(onClick = onLoad) { Text(stringResource(R.string.app_saves_load)) }
                OutlinedButton(onClick = onKeep, enabled = canKeep) {
                    Text(stringResource(if (point.kept) R.string.app_saves_release else R.string.app_saves_keep))
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onDelete) { Text(stringResource(R.string.app_saves_delete)) }
            }
        }
    }
}
