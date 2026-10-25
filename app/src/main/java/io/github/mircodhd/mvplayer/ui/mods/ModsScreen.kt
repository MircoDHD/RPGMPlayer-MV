// SPDX-FileCopyrightText: 2026 MircoDHD
// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.mircodhd.mvplayer.ui.mods

import android.net.Uri
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import io.github.mircodhd.mvplayer.R
import io.github.mircodhd.mvplayer.game.FileIndex
import io.github.mircodhd.mvplayer.game.GameInfoCache
import io.github.mircodhd.mvplayer.game.GameInspector
import io.github.mircodhd.mvplayer.game.Mod
import io.github.mircodhd.mvplayer.game.ModConflict
import io.github.mircodhd.mvplayer.game.Mods
import io.github.mircodhd.mvplayer.storage.DocumentNode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Conflict groups longer than this start collapsed. */
private const val GROUP_COLLAPSE = 20

/** What the active mods replace: game files per mod and files several of them share. */
private class ModAnalysis(val replaced: Map<String, Int>, val conflicts: List<ModConflict>)

/**
 * The mods found in a game folder: switch them on or off, set their order (the top one
 * wins where several replace the same file), see what they replace and where they clash,
 * and remove a mod's folder. [enabled] is the stored order of active mods.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModsScreen(title: String, treeUri: Uri, enabled: List<String>, onChange: (List<String>) -> Unit, onOpenGuide: () -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var found by remember { mutableStateOf<List<Mod>?>(null) }
    var reload by remember { mutableIntStateOf(0) }
    var info by remember { mutableStateOf(false) }
    var toRemove by remember { mutableStateOf<Mod?>(null) }
    var gameIndex by remember { mutableStateOf<FileIndex?>(null) }
    // Indexes of mod folders, built once per visit (a full www copy has thousands of files).
    val modIndexes = remember { mutableStateMapOf<String, FileIndex>() }
    var analysis by remember { mutableStateOf<ModAnalysis?>(null) }

    LaunchedEffect(treeUri, reload) {
        val known = gameIndex
        val (list, index) = withContext(Dispatchers.IO) {
            runCatching {
                val root = DocumentNode.root(context, treeUri) ?: return@runCatching emptyList<Mod>() to known
                val webRoot = GameInspector.detect(root).webRoot ?: return@runCatching emptyList<Mod>() to known
                Mods.find(root, webRoot) to (known ?: FileIndex.build(webRoot))
            }.getOrDefault(emptyList<Mod>() to known)
        }
        gameIndex = index
        found = list
    }
    // Active mods first, in their order, then the others by name.
    val mods = found.orEmpty()
    val active = enabled.mapNotNull { id -> mods.firstOrNull { it.id.equals(id, ignoreCase = true) } }
    val inactive = mods.filter { m -> active.none { it.id == m.id } }.sortedBy { it.name.lowercase() }

    LaunchedEffect(active.map { it.id }, gameIndex) {
        val game = gameIndex ?: return@LaunchedEffect
        analysis = null
        analysis = withContext(Dispatchers.IO) {
            runCatching {
                val layers = active.map { mod -> mod to modIndexes.getOrPut(mod.id) { FileIndex.build(mod.content) } }
                ModAnalysis(
                    replaced = layers.associate { (mod, index) -> mod.id to Mods.replacedCount(index, game) },
                    conflicts = Mods.conflicts(layers),
                )
            }.getOrNull()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.set_back))
                    }
                },
                actions = {
                    TextButton(onClick = onOpenGuide) { Text(stringResource(R.string.mods_guide)) }
                    IconButton(onClick = { info = true }) {
                        Icon(Icons.Outlined.Info, contentDescription = stringResource(R.string.mods_info_title))
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 32.dp)) {
            Text(
                stringResource(R.string.mods_intro),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            when {
                found == null -> Text(stringResource(R.string.mods_searching), modifier = Modifier.padding(16.dp))
                mods.isEmpty() -> Text(stringResource(R.string.mods_none), modifier = Modifier.padding(16.dp))
            }
            active.forEachIndexed { i, mod ->
                ModCard(
                    mod = mod,
                    on = true,
                    position = i + 1,
                    replaced = analysis?.replaced?.get(mod.id),
                    onToggle = { onChange(active.map { it.id } - mod.id) },
                    onUp = if (i > 0) { { onChange(active.map { it.id }.toMutableList().apply { add(i - 1, removeAt(i)) }) } } else null,
                    onDown = if (i < active.size - 1) { { onChange(active.map { it.id }.toMutableList().apply { add(i + 1, removeAt(i)) }) } } else null,
                    onRemove = { toRemove = mod },
                )
            }
            inactive.forEach { mod ->
                ModCard(
                    mod = mod, on = false, position = null, replaced = null,
                    onToggle = { onChange(active.map { it.id } + mod.id) }, onUp = null, onDown = null,
                    onRemove = { toRemove = mod },
                )
            }
            if (active.size >= 2) ConflictSection(analysis, active)
            if (active.isNotEmpty()) {
                Text(
                    stringResource(R.string.mods_saves_warning),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        }
    }
    if (info) ModsInfoDialog { info = false }
    toRemove?.let { mod ->
        AlertDialog(
            onDismissRequest = { toRemove = null },
            title = { Text(stringResource(R.string.mods_remove_title)) },
            text = { Text(stringResource(R.string.mods_remove_text, mod.folder.name)) },
            confirmButton = {
                TextButton(onClick = {
                    toRemove = null
                    scope.launch {
                        val ok = withContext(Dispatchers.IO) { Mods.remove(mod) }
                        if (ok) {
                            if (active.any { it.id == mod.id }) onChange(active.map { it.id } - mod.id)
                            modIndexes.remove(mod.id)
                            GameInfoCache.remove(treeUri.toString())
                        }
                        reload++
                        snackbar.showSnackbar(resources.getString(if (ok) R.string.mods_removed else R.string.mods_remove_failed, mod.name))
                    }
                }) { Text(stringResource(R.string.mods_remove)) }
            },
            dismissButton = { TextButton(onClick = { toRemove = null }) { Text(stringResource(R.string.dialog_cancel)) } },
        )
    }
}

/** Files several active mods replace, grouped by top folder; the winner (top mod) in bold. */
@Composable
private fun ConflictSection(analysis: ModAnalysis?, active: List<Mod>) {
    val names = active.associate { it.id to it.name }
    val expanded = remember { mutableStateMapOf<String, Boolean>() }
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(stringResource(R.string.mods_conflicts_title), style = MaterialTheme.typography.titleMedium)
        when {
            analysis == null -> Text(stringResource(R.string.mods_conflicts_checking), style = MaterialTheme.typography.bodyMedium)
            analysis.conflicts.isEmpty() -> Text(stringResource(R.string.mods_conflicts_none), style = MaterialTheme.typography.bodyMedium)
            else -> {
                Text(
                    stringResource(R.string.mods_conflicts_intro, analysis.conflicts.size),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val root = stringResource(R.string.mods_root_folder)
                analysis.conflicts.groupBy { c -> c.path.substringBefore('/', "").ifEmpty { root }.let { if (it == root) it else "$it/" } }
                    .forEach { (group, list) ->
                        val open = expanded[group] ?: (list.size <= GROUP_COLLAPSE)
                        Text("$group (${list.size})", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 10.dp))
                        (if (open) list else list.take(3)).forEach { ConflictRow(it, names) }
                        if (list.size > GROUP_COLLAPSE) {
                            TextButton(onClick = { expanded[group] = !open }) {
                                Text(if (open) stringResource(R.string.mods_show_less) else stringResource(R.string.mods_show_all, list.size))
                            }
                        }
                    }
            }
        }
    }
}

@Composable
private fun ConflictRow(conflict: ModConflict, names: Map<String, String>) {
    val core = stringResource(R.string.mods_conflict_core)
    val over = stringResource(R.string.mods_conflict_over)
    Column(Modifier.padding(vertical = 3.dp)) {
        Text(
            conflict.path + if (conflict.isCore) "  ($core)" else "",
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            color = if (conflict.isCore) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
        )
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(names[conflict.mods.first()] ?: conflict.mods.first()) }
                append(" $over ")
                append(conflict.mods.drop(1).joinToString(", ") { names[it] ?: it })
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Which folder of an unpacked mod goes where, as a small folder tree. */
@Composable
private fun ModsInfoDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.mods_info_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(stringResource(R.string.mods_info_text), style = MaterialTheme.typography.bodyMedium)
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                ) {
                    Text(
                        stringResource(R.string.mods_info_tree),
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        modifier = Modifier.horizontalScroll(rememberScrollState()).padding(12.dp),
                    )
                }
                Text(stringResource(R.string.mods_info_warning), style = MaterialTheme.typography.bodyMedium)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.dialog_ok)) } },
    )
}

@Composable
private fun ModCard(
    mod: Mod,
    on: Boolean,
    position: Int?,
    replaced: Int?,
    onToggle: () -> Unit,
    onUp: (() -> Unit)?,
    onDown: (() -> Unit)?,
    onRemove: () -> Unit,
) {
    Surface(
        color = if (on) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
    ) {
        Row(Modifier.padding(start = 12.dp, top = 8.dp, bottom = 8.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    listOfNotNull(position?.let { "$it." }, mod.name, mod.version).joinToString(" "),
                    style = MaterialTheme.typography.titleMedium,
                )
                listOfNotNull(mod.author, mod.id.takeIf { it != mod.name }).takeIf { it.isNotEmpty() }?.let {
                    Text(it.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                mod.description?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                listOfNotNull(
                    replaced?.let { stringResource(R.string.mods_replaces, it) },
                    mod.scripts.size.takeIf { it > 0 }?.let { stringResource(R.string.mods_scripts, it) },
                ).takeIf { it.isNotEmpty() }?.let {
                    Text(it.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (on) {
                Column {
                    IconButton(onClick = { onUp?.invoke() }, enabled = onUp != null) {
                        Icon(Icons.Filled.KeyboardArrowUp, contentDescription = stringResource(R.string.mods_up))
                    }
                    IconButton(onClick = { onDown?.invoke() }, enabled = onDown != null) {
                        Icon(Icons.Filled.KeyboardArrowDown, contentDescription = stringResource(R.string.mods_down))
                    }
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Switch(checked = on, onCheckedChange = { onToggle() }, modifier = Modifier.padding(horizontal = 8.dp))
                IconButton(onClick = onRemove) {
                    Icon(Icons.Outlined.Delete, contentDescription = stringResource(R.string.mods_remove))
                }
            }
        }
    }
}
