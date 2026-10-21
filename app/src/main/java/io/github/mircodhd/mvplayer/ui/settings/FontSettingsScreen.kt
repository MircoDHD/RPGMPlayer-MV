// SPDX-FileCopyrightText: 2026 MircoDHD
// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.mircodhd.mvplayer.ui.settings

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.mircodhd.mvplayer.R
import io.github.mircodhd.mvplayer.fonts.FontLibrary
import io.github.mircodhd.mvplayer.fonts.FontOption
import io.github.mircodhd.mvplayer.settings.FontSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/**
 * Font for the game's text, globally or for one game. With [overrideId]/[overrideScale]
 * (one game; null follows [global]) the global choice carries the Default badge.
 * [forApp]: the font of the app's own screens instead; the first choice is Android's
 * font and there is no size (Android's text size applies).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FontSettingsScreen(
    title: String,
    global: FontSettings,
    perGame: Boolean,
    overrideId: String?,
    overrideScale: Float?,
    onChange: (id: String?, scale: Float?) -> Unit,
    onBack: () -> Unit,
    forApp: Boolean = false,
) {
    val baseId = if (forApp) FontSettings.SYSTEM_FONT else FontSettings.GAME_FONT
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // Bumped after adding or removing a font, to list the files again.
    var revision by remember { mutableIntStateOf(0) }
    var importFailed by remember { mutableStateOf(false) }
    val fonts = remember(revision) { FontLibrary.all(context) }
    val id = overrideId ?: global.id
    val scale = overrideScale ?: global.scale
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val added = withContext(Dispatchers.IO) { FontLibrary.import(context, uri, displayName(context, uri)) }
            importFailed = added == null
            if (added != null) {
                revision++
                onChange(added, overrideScale)
            }
        }
    }

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
                stringResource(if (forApp) R.string.app_font_intro else R.string.font_intro),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            val select = { picked: String -> onChange(if (perGame && picked == global.id) null else picked, overrideScale) }
            FontRow(
                name = stringResource(if (forApp) R.string.app_font_system else R.string.font_game),
                description = stringResource(if (forApp) R.string.app_font_system_description else R.string.font_game_description),
                font = null,
                selected = id == baseId,
                isDefault = perGame && global.id == baseId,
                onSelect = { select(baseId) },
            )
            for (font in fonts) {
                FontRow(
                    name = font.name,
                    description = stringResource(
                        when {
                            font.file != null -> R.string.font_custom_description
                            font.pixel -> R.string.font_pixel_description
                            else -> R.string.font_readable_description
                        },
                    ),
                    font = font,
                    selected = id == font.id,
                    isDefault = perGame && global.id == font.id,
                    onSelect = { select(font.id) },
                    onDelete = if (font.file != null) {
                        {
                            FontLibrary.delete(context, font.id)
                            revision++
                            if (id == font.id) onChange(if (perGame) null else baseId, overrideScale)
                        }
                    } else {
                        null
                    },
                )
            }
            TextButton(
                onClick = { pick.launch(arrayOf("font/ttf", "font/otf", "font/woff", "font/woff2", "application/x-font-ttf", "application/octet-stream")) },
                modifier = Modifier.padding(horizontal = 8.dp),
            ) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.font_add))
            }
            if (importFailed) {
                Text(
                    stringResource(R.string.font_add_failed),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }

            if (!forApp) SectionHeader(stringResource(R.string.font_size))
            Column(Modifier.padding(horizontal = 16.dp)) {
                if (!forApp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.font_size_summary), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    if (perGame && overrideScale == null) Badges(listOf(OptionBadge.DEFAULT), inline = true)
                    Spacer(Modifier.width(8.dp))
                    Text(percent(scale), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                }
                Slider(
                    value = scale,
                    onValueChange = { v ->
                        val snapped = (v * 20).roundToInt() / 20f
                        onChange(overrideId, if (perGame && snapped == global.scale) null else snapped)
                    },
                    valueRange = FontSettings.SCALE_RANGE,
                )
                }
                val selected = fonts.firstOrNull { it.id == id }
                Text(
                    stringResource(R.string.font_preview_text),
                    fontFamily = rememberFontFamily(selected),
                    fontSize = (20 * scale).sp,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun FontRow(
    name: String,
    description: String,
    font: FontOption?,
    selected: Boolean,
    isDefault: Boolean,
    onSelect: () -> Unit,
    onDelete: (() -> Unit)? = null,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        RadioButton(selected = selected, onClick = null, modifier = Modifier.padding(8.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(name, style = MaterialTheme.typography.bodyLarge)
                if (isDefault) {
                    Spacer(Modifier.width(8.dp))
                    Badges(listOf(OptionBadge.DEFAULT), inline = true)
                }
            }
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (font != null) {
                Text(stringResource(R.string.font_sample), fontFamily = rememberFontFamily(font), fontSize = 18.sp)
            }
        }
        if (onDelete != null) {
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.font_delete))
            }
        }
    }
}

/** The font as a Compose family for previews; null (or a broken file) gives the default. */
@Composable
private fun rememberFontFamily(font: FontOption?): FontFamily {
    val context = LocalContext.current
    return remember(font?.id) {
        runCatching {
            when {
                font?.asset != null -> FontFamily(Font(font.asset, context.assets))
                font?.file != null -> FontFamily(Font(font.file))
                else -> FontFamily.Default
            }
        }.getOrDefault(FontFamily.Default)
    }
}

private fun displayName(context: Context, uri: Uri): String? = runCatching {
    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) cursor.getString(0) else null
    }
}.getOrNull()

/** Short label for the app font row. */
fun appFontLabel(context: Context, id: String): String =
    FontLibrary.find(context, id)?.name ?: context.getString(R.string.app_font_system)

/** Short label for a settings row. */
fun fontLabel(context: Context, font: FontSettings): String {
    val name = if (font.isGameFont) context.getString(R.string.font_game) else FontLibrary.find(context, font.id)?.name ?: context.getString(R.string.font_game)
    return if (font.scale == 1f) name else "$name · ${percent(font.scale)}"
}
