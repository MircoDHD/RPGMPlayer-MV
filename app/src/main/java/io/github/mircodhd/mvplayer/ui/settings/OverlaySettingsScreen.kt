// SPDX-FileCopyrightText: 2026 MircoDHD
// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.mircodhd.mvplayer.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.mircodhd.mvplayer.R
import io.github.mircodhd.mvplayer.overlay.GamepadLayout
import io.github.mircodhd.mvplayer.overlay.LayoutNames
import io.github.mircodhd.mvplayer.overlay.LayoutPreview
import io.github.mircodhd.mvplayer.settings.OverlayMode
import io.github.mircodhd.mvplayer.settings.OverlaySettings
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OverlaySettingsScreen(
    settings: OverlaySettings,
    layouts: List<GamepadLayout>,
    onChange: (OverlaySettings) -> Unit,
    onCustomize: (GamepadLayout) -> Unit,
    onDeleteLayout: (GamepadLayout) -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.overlay_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.set_back))
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = 32.dp)) {
            item {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text(stringResource(R.string.overlay_mode), style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(8.dp))
                    OverlayModeSelector(settings.mode) { onChange(settings.copy(mode = it)) }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        stringResource(R.string.overlay_mode_summary),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            item { SectionHeader(stringResource(R.string.overlay_layout)) }
            item {
                LayoutGallery(
                    layouts = layouts,
                    selectedId = settings.layoutId,
                    onSelect = { onChange(settings.copy(layoutId = it.id)) },
                    onDelete = onDeleteLayout,
                )
            }
            item {
                Button(
                    onClick = { onCustomize(layouts.firstOrNull { it.id == settings.layoutId } ?: layouts.first()) },
                    shape = CircleShape,
                    contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                    Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                    Text(stringResource(R.string.overlay_customize))
                }
            }
            item { SectionHeader(stringResource(R.string.overlay_appearance)) }
            item {
                SliderRow(
                    title = stringResource(R.string.overlay_opacity),
                    value = settings.opacity,
                    range = OverlaySettings.OPACITY_RANGE,
                    label = percent(settings.opacity),
                ) { onChange(settings.copy(opacity = it)) }
            }
            item {
                SliderRow(
                    title = stringResource(R.string.overlay_size),
                    value = settings.scale,
                    range = OverlaySettings.SCALE_RANGE,
                    label = percent(settings.scale),
                ) { onChange(settings.copy(scale = it)) }
            }
            item {
                SwitchRow(
                    stringResource(R.string.overlay_haptics),
                    stringResource(R.string.overlay_haptics_summary),
                    settings.haptics,
                    { onChange(settings.copy(haptics = it)) },
                )
            }
            item {
                SwitchRow(
                    stringResource(R.string.overlay_mirror),
                    stringResource(R.string.overlay_mirror_summary),
                    settings.mirror,
                    { onChange(settings.copy(mirror = it)) },
                )
            }
        }
    }
}

fun percent(value: Float) = "${(value * 100).roundToInt()} %"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OverlayModeSelector(mode: OverlayMode, onSelect: (OverlayMode) -> Unit) {
    val options = listOf(
        OverlayMode.AUTO to stringResource(R.string.overlay_mode_auto),
        OverlayMode.ALWAYS to stringResource(R.string.overlay_mode_always),
        OverlayMode.NEVER to stringResource(R.string.overlay_mode_never),
    )
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        options.forEachIndexed { index, (value, label) ->
            SegmentedButton(
                selected = mode == value,
                onClick = { onSelect(value) },
                shape = SegmentedButtonDefaults.itemShape(index, options.size),
            ) { Text(label) }
        }
    }
}

@Composable
private fun SliderRow(title: String, value: Float, range: ClosedFloatingPointRange<Float>, label: String, onChange: (Float) -> Unit) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
        Slider(value = value, onValueChange = onChange, valueRange = range)
    }
}

/** Horizontally scrolling preset cards with a live preview of each layout. */
@Composable
fun LayoutGallery(
    layouts: List<GamepadLayout>,
    selectedId: String?,
    onSelect: (GamepadLayout) -> Unit,
    onDelete: ((GamepadLayout) -> Unit)?,
) {
    val context = LocalContext.current
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(layouts, key = { it.id }) { layout ->
            val selected = layout.id == selectedId
            Card(
                onClick = { onSelect(layout) },
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.elevatedCardColors(),
                elevation = CardDefaults.elevatedCardElevation(),
                border = if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
                modifier = Modifier.width(232.dp),
            ) {
                Box(
                    Modifier
                        .padding(10.dp)
                        .fillMaxWidth()
                        .aspectRatio(20f / 9f)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerLowest),
                ) {
                    LayoutPreview(layout)
                }
                Row(Modifier.padding(start = 14.dp, end = 4.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        LayoutNames.name(context, layout),
                        style = MaterialTheme.typography.labelLarge,
                        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                        maxLines = 2,
                    )
                    if (!layout.builtIn && onDelete != null) {
                        IconButton(onClick = { onDelete(layout) }) {
                            Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.overlay_delete_layout))
                        }
                    }
                }
            }
        }
    }
}
