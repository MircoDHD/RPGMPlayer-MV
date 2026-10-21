// SPDX-FileCopyrightText: 2026 MircoDHD
// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.mircodhd.mvplayer.ui.settings

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import io.github.mircodhd.mvplayer.R
import io.github.mircodhd.mvplayer.patch.PatchFolder
import io.github.mircodhd.mvplayer.patch.UserPatches
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Where a game's patch folder is, for the guide's per-game buttons. */
data class PatchGuideGame(val title: String, val location: String, val treeUri: Uri)

/**
 * Step-by-step guide to the game's own patches (docs/patches.md). Opened for one [game]
 * it shows the real folder and can create it and copy a picked file into it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PatchGuideScreen(game: PatchGuideGame?, onBack: () -> Unit) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()
    val pager = rememberPagerState { STEPS }
    // Files in the folder; null = no folder; refreshed after every change.
    var files by remember { mutableStateOf<List<String>?>(null) }
    var revision by remember { mutableIntStateOf(0) }
    var status by remember { mutableStateOf<String?>(null) }
    if (game != null) {
        LaunchedEffect(revision) { files = withContext(Dispatchers.IO) { PatchFolder.files(context, game.treeUri) } }
    }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null || game == null) return@rememberLauncherForActivityResult
        scope.launch {
            val name = withContext(Dispatchers.IO) { PatchFolder.add(context, game.treeUri, uri, displayName(context, uri)) }
            status = if (name != null) resources.getString(R.string.patch_guide_added, name) else resources.getString(R.string.patch_guide_failed)
            revision++
        }
    }
    val createFolder = {
        if (game != null) {
            scope.launch {
                val ok = withContext(Dispatchers.IO) { PatchFolder.create(context, game.treeUri) }
                status = resources.getString(if (ok) R.string.patch_guide_created else R.string.patch_guide_failed)
                revision++
            }
        }
    }
    val folderPath = game?.let { "${it.location}/…/${UserPatches.FOLDER}" } ?: "www/${UserPatches.FOLDER}"

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.patch_guide_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.set_back))
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            HorizontalPager(pager, modifier = Modifier.weight(1f)) { page ->
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp)) {
                    StepHeader(page + 1, stringResource(TITLES[page]))
                    when (page) {
                        0 -> {
                            Paragraph(stringResource(R.string.patch_guide_1_text))
                            Paragraph(stringResource(R.string.patch_guide_1_warning), warning = true)
                        }
                        1 -> {
                            Paragraph(stringResource(R.string.patch_guide_2_text))
                            Code(folderPath)
                            if (game != null) {
                                Paragraph(
                                    if (files == null) stringResource(R.string.patch_guide_folder_missing)
                                    else stringResource(R.string.patch_guide_folder_found, files!!.size),
                                )
                                if (files == null) Button(onClick = { createFolder() }) { Text(stringResource(R.string.patch_guide_create)) }
                            } else {
                                Paragraph(stringResource(R.string.patch_guide_2_per_game))
                            }
                        }
                        2 -> {
                            Paragraph(stringResource(R.string.patch_guide_3_text))
                            Code(EXAMPLE)
                            if (game != null) {
                                FilledTonalButton(onClick = { pick.launch(arrayOf("application/javascript", "text/javascript", "application/json", "text/plain", "application/octet-stream")) }) {
                                    Text(stringResource(R.string.patch_guide_add))
                                }
                                files?.takeIf { it.isNotEmpty() }?.let { Paragraph(stringResource(R.string.patch_guide_files, it.joinToString())) }
                            }
                        }
                        3 -> {
                            Paragraph(stringResource(R.string.patch_guide_4_text))
                            Code(LIST_EXAMPLE)
                            Paragraph(stringResource(R.string.patch_guide_4_positions))
                        }
                        else -> Paragraph(stringResource(R.string.patch_guide_5_text))
                    }
                    status?.let { Paragraph(it) }
                }
            }
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { scope.launch { pager.animateScrollToPage(pager.currentPage - 1) } }, enabled = pager.currentPage > 0) {
                    Text(stringResource(R.string.patch_guide_previous))
                }
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.Center) {
                    repeat(STEPS) { i ->
                        Box(
                            Modifier.padding(3.dp).size(8.dp),
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = if (i == pager.currentPage) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                modifier = Modifier.fillMaxSize(),
                            ) {}
                        }
                    }
                }
                if (pager.currentPage < STEPS - 1) {
                    Button(onClick = { scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } }) {
                        Text(stringResource(R.string.patch_guide_next))
                    }
                } else {
                    Button(onClick = onBack) { Text(stringResource(R.string.dialog_done)) }
                }
            }
        }
    }
}

@Composable
private fun StepHeader(number: Int, title: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 8.dp)) {
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(32.dp)) {
            Box(contentAlignment = Alignment.Center) {
                Text(number.toString(), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
        }
        Spacer(Modifier.size(12.dp))
        Text(title, style = MaterialTheme.typography.titleLarge)
    }
}

@Composable
private fun Paragraph(text: String, warning: Boolean = false) {
    Text(
        text,
        style = MaterialTheme.typography.bodyLarge,
        color = if (warning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(vertical = 6.dp),
    )
}

@Composable
private fun Code(text: String) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHighest, shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
            modifier = Modifier.horizontalScroll(rememberScrollState()).padding(12.dp),
        )
    }
    Spacer(Modifier.height(4.dp))
}

private fun displayName(context: android.content.Context, uri: Uri): String? = runCatching {
    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
        if (c.moveToFirst()) c.getString(0) else null
    }
}.getOrNull()

private const val STEPS = 5

private val TITLES = listOf(
    R.string.patch_guide_1_title,
    R.string.patch_guide_2_title,
    R.string.patch_guide_3_title,
    R.string.patch_guide_4_title,
    R.string.patch_guide_5_title,
)

private val EXAMPLE = """
    // fast-text.js: shows messages at once
    (function () {
        Window_Message.prototype.isFastForward = function () {
            return true;
        };
    })();
""".trimIndent()

private val LIST_EXAMPLE = """
    {
      "patches": [
        { "file": "early.js", "position": "beforePlugins" },
        { "file": "fast-text.js" }
      ]
    }
""".trimIndent()
