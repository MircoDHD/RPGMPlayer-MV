// SPDX-FileCopyrightText: 2026 MircoDHD
// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.mircodhd.mvplayer.ui.settings

import android.os.PowerManager
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import io.github.mircodhd.mvplayer.R
import io.github.mircodhd.mvplayer.hud.DeviceStats
import io.github.mircodhd.mvplayer.hud.HudPanel
import io.github.mircodhd.mvplayer.hud.HudPreset
import io.github.mircodhd.mvplayer.hud.HudRegistry
import io.github.mircodhd.mvplayer.hud.HudSnapshot
import io.github.mircodhd.mvplayer.hud.JsStats
import io.github.mircodhd.mvplayer.settings.HudCorner
import io.github.mircodhd.mvplayer.settings.HudSettings
import io.github.mircodhd.mvplayer.settings.HudSize
import kotlin.math.sin

/** Global settings of the performance HUD, with a live preview on sample data. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HudSettingsScreen(settings: HudSettings, onChange: (HudSettings) -> Unit, onBack: () -> Unit) {
    var info by remember { mutableStateOf(false) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.hud_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.set_back))
                    }
                },
                actions = {
                    IconButton(onClick = { info = true }) {
                        Icon(Icons.Outlined.Info, contentDescription = stringResource(R.string.hud_info_title))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 32.dp),
        ) {
            HudPreview(settings)
            SwitchRow(
                title = stringResource(R.string.hud_show),
                summary = stringResource(R.string.hud_show_summary),
                checked = settings.enabled,
                onCheckedChange = { onChange(settings.copy(enabled = it)) },
            )

            SectionHeader(stringResource(R.string.hud_preset))
            val preset = HudRegistry.presetOf(settings.modules)
            Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (p in HudPreset.entries) {
                    FilterChip(
                        selected = preset == p,
                        onClick = { onChange(settings.copy(modules = HudRegistry.modulesOf(p))) },
                        label = { Text(stringResource(presetLabel(p))) },
                    )
                }
                if (preset == null) FilterChip(selected = true, onClick = {}, label = { Text(stringResource(R.string.hud_preset_custom)) })
            }

            SectionHeader(stringResource(R.string.hud_appearance))
            Labeled(stringResource(R.string.hud_position)) {
                Segmented(
                    options = HudCorner.entries,
                    selected = settings.corner,
                    label = { cornerArrow(it) },
                    labelStyle = MaterialTheme.typography.titleLarge,
                    description = { stringResource(cornerLabel(it)) },
                    // A new corner starts without the dragged offset.
                    onSelect = { onChange(settings.copy(corner = it, offsetX = 0f, offsetY = 0f)) },
                )
                if (settings.moved) {
                    TextButton(onClick = { onChange(settings.copy(offsetX = 0f, offsetY = 0f)) }) {
                        Text(stringResource(R.string.hud_position_reset))
                    }
                }
            }
            Labeled(stringResource(R.string.hud_size)) {
                Segmented(
                    options = HudSize.entries,
                    selected = settings.size,
                    label = { stringResource(sizeLabel(it)) },
                    onSelect = { onChange(settings.copy(size = it)) },
                )
            }
            Labeled(stringResource(R.string.hud_detail)) {
                Segmented(
                    options = listOf(false, true),
                    selected = settings.detailed,
                    label = { stringResource(if (it) R.string.hud_detail_detailed else R.string.hud_detail_compact) },
                    onSelect = { onChange(settings.copy(detailed = it)) },
                )
            }
            Labeled(stringResource(R.string.hud_labels)) {
                Segmented(
                    options = listOf(false, true),
                    selected = settings.shortLabels,
                    label = { stringResource(if (it) R.string.hud_labels_short else R.string.hud_labels_full) },
                    onSelect = { onChange(settings.copy(shortLabels = it)) },
                )
            }
            Labeled(stringResource(R.string.hud_width)) {
                Segmented(
                    options = HudSettings.WIDTHS,
                    selected = settings.widthPercent,
                    label = { "$it %" },
                    onSelect = { onChange(settings.copy(widthPercent = it)) },
                )
            }
            Labeled(stringResource(R.string.hud_interval)) {
                Segmented(
                    options = HudSettings.INTERVALS,
                    selected = settings.intervalMs,
                    label = { stringResource(R.string.hud_interval_value, it) },
                    onSelect = { onChange(settings.copy(intervalMs = it)) },
                )
            }
            Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.hud_opacity), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    Text(percent(settings.opacity), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                }
                Slider(
                    value = settings.opacity,
                    onValueChange = { onChange(settings.copy(opacity = it)) },
                    valueRange = HudSettings.OPACITY_RANGE,
                )
            }

            SectionHeader(stringResource(R.string.hud_modules))
            Text(
                stringResource(R.string.hud_modules_summary),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            ModuleList(settings.modules) { onChange(settings.copy(modules = it)) }
        }
    }

    if (info) HudInfoDialog(onDismiss = { info = false })
}

/** What every module shows and what it is good for, with both of its names. */
@Composable
private fun HudInfoDialog(onDismiss: () -> Unit) {
    val resources = LocalResources.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.hud_info_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(stringResource(R.string.hud_info_intro), style = MaterialTheme.typography.bodyMedium)
                for (module in HudRegistry.modules) {
                    Spacer(Modifier.height(12.dp))
                    val title = stringResource(module.title)
                    Text(
                        if (title == module.shortLabel) title else "$title  (${module.shortLabel})",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    val full = if (module.graph) stringResource(R.string.hud_graph_example) else module.compact(PreviewData, resources) ?: "…"
                    val short = if (module.graph) full else module.compact(PreviewData, resources, short = true) ?: "…"
                    Text(
                        if (full == short) stringResource(R.string.hud_example_same, full) else stringResource(R.string.hud_example, full, short),
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(stringResource(infoText(module.id)), style = MaterialTheme.typography.bodyMedium)
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.dialog_ok)) } },
    )
}

private fun infoText(id: String) = when (id) {
    "fps" -> R.string.hud_info_fps
    "frametime" -> R.string.hud_info_frametime
    "framegraph" -> R.string.hud_info_framegraph
    "lows" -> R.string.hud_info_lows
    "timing" -> R.string.hud_info_timing
    "jswork" -> R.string.hud_info_jswork
    "heap" -> R.string.hud_info_heap
    "images" -> R.string.hud_info_images
    "appram" -> R.string.hud_info_appram
    "battery" -> R.string.hud_info_battery
    "thermal" -> R.string.hud_info_thermal
    "renderer" -> R.string.hud_info_renderer
    "clock" -> R.string.hud_info_clock
    else -> R.string.hud_info_cost
}

/** Sample data on a dark backdrop, laid out like in the game. */
@Composable
private fun HudPreview(settings: HudSettings) {
    BoxWithConstraints(
        Modifier
            .padding(16.dp)
            .fillMaxWidth()
            .heightIn(min = 190.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(Brush.linearGradient(listOf(Color(0xFF2B3A55), Color(0xFF14161C)))),
    ) {
        HudPanel(
            PreviewData,
            settings,
            Modifier
                .align(settings.corner.previewAlignment)
                .padding(8.dp)
                .widthIn(max = (maxWidth - 16.dp) * (settings.widthPercent / 100f)),
        )
    }
}

private val HudCorner.previewAlignment: Alignment
    get() = when (this) {
        HudCorner.TOP_START -> Alignment.TopStart
        HudCorner.TOP_END -> Alignment.TopEnd
        HudCorner.BOTTOM_START -> Alignment.BottomStart
        HudCorner.BOTTOM_END -> Alignment.BottomEnd
    }

private val PreviewData = HudSnapshot(
    js = JsStats(
        fps = 60.0, displayHz = 60.0, logicUps = 60.0, frameMs = 16.7, maxFrameMs = 24.3, low1Fps = 52.0, slowFrames = 1,
        workMs = 4.2, maxWorkMs = 9.8, heapUsedMb = 78, heapLimitMb = 512, images = 124, imageMegapixels = 48.3,
        rendererType = "webgl", gpu = "Sample GPU", rendererFallback = false, paused = false, frameIntervalMs = 1000.0 / 60,
        frameGaps = emptyList(),
    ),
    device = DeviceStats(
        batteryPercent = 82, charging = false, batteryTempC = 34.5f, thermalStatus = PowerManager.THERMAL_STATUS_NONE,
        thermalHeadroom = 0.31f, appPssMb = 212, displayHz = 120f,
    ),
    frameHistory = List(180) { i -> 16.7f + 1.2f * sin(i / 6f) + if (i == 120) 30f else 0f },
    clockMillis = 1_760_000_000_000,
    sessionMs = 42 * 60_000L,
    costMs = 0.12,
)

@Composable
private fun Labeled(title: String, content: @Composable () -> Unit) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(6.dp))
        content()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun <T> Segmented(
    options: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    description: (@Composable (T) -> String)? = null,
    labelStyle: TextStyle? = null,
) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        options.forEachIndexed { index, option ->
            val text = description?.invoke(option)
            SegmentedButton(
                selected = option == selected,
                onClick = { onSelect(option) },
                shape = SegmentedButtonDefaults.itemShape(index, options.size),
                modifier = if (text != null) Modifier.semantics { contentDescription = text } else Modifier,
            ) { Text(label(option), style = labelStyle ?: LocalTextStyle.current) }
        }
    }
}

/**
 * Every module with a checkbox; checked ones are shown, in this order. Long-press a row
 * and drag to move it. The gesture lives on the list, not on the rows, because rows move
 * while dragging.
 */
@Composable
private fun ModuleList(modules: List<String>, onChange: (List<String>) -> Unit) {
    val enabled = modules.toSet()
    // Shown modules first (in their order), then the rest in registry order.
    val order = remember(modules) {
        mutableStateOf(modules.filter { HudRegistry.find(it) != null } + HudRegistry.modules.map { it.id }.filterNot { it in enabled })
    }
    // The gesture outlives recompositions; it must read the latest values.
    val currentOrder by rememberUpdatedState(order)
    val currentEnabled by rememberUpdatedState(enabled)
    val currentOnChange by rememberUpdatedState(onChange)
    var dragging by remember { mutableStateOf<String?>(null) }
    var offset by remember { mutableFloatStateOf(0f) }
    val tops = remember { mutableStateMapOf<String, Float>() }
    val heights = remember { mutableStateMapOf<String, Int>() }
    val resources = LocalResources.current

    Column(
        Modifier.pointerInput(Unit) {
            detectDragGesturesAfterLongPress(
                onDragStart = { position ->
                    dragging = currentOrder.value.firstOrNull { id ->
                        val top = tops[id] ?: return@firstOrNull false
                        position.y >= top && position.y < top + (heights[id] ?: 0)
                    }
                    offset = 0f
                },
                onDragEnd = {
                    if (dragging != null) currentOnChange(currentOrder.value.filter { it in currentEnabled })
                    dragging = null
                    offset = 0f
                },
                onDragCancel = {
                    dragging = null
                    offset = 0f
                },
            ) { change, amount ->
                val id = dragging ?: return@detectDragGesturesAfterLongPress
                change.consume()
                offset += amount.y
                val list = currentOrder.value.toMutableList()
                val index = list.indexOf(id)
                val below = list.getOrNull(index + 1)
                val above = list.getOrNull(index - 1)
                if (below != null && offset > (heights[below] ?: 0) / 2f) {
                    offset -= heights[below] ?: 0
                    list[index] = below
                    list[index + 1] = id
                    currentOrder.value = list
                } else if (above != null && offset < -(heights[above] ?: 0) / 2f) {
                    offset += heights[above] ?: 0
                    list[index] = above
                    list[index - 1] = id
                    currentOrder.value = list
                }
            }
        },
    ) {
        for (id in order.value) {
            val module = HudRegistry.find(id) ?: continue
            key(id) {
                val isDragged = dragging == id
                ListItem(
                    headlineContent = { Text(stringResource(module.title)) },
                    supportingContent = {
                        Column {
                            Text(stringResource(module.summary))
                            // How the module reads in the HUD, so each label maps to its setting.
                            val full = if (module.graph) stringResource(R.string.hud_graph_example) else module.compact(PreviewData, resources) ?: "…"
                            val short = if (module.graph) full else module.compact(PreviewData, resources, short = true) ?: "…"
                            Text(
                                if (full == short) stringResource(R.string.hud_example_same, full) else stringResource(R.string.hud_example, full, short),
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(top = 2.dp),
                            )
                        }
                    },
                    leadingContent = {
                        Checkbox(
                            checked = id in enabled,
                            onCheckedChange = { checked ->
                                val newEnabled = if (checked) enabled + id else enabled - id
                                onChange(order.value.filter { it in newEnabled })
                            },
                        )
                    },
                    trailingContent = {
                        Icon(Icons.Filled.Menu, contentDescription = stringResource(R.string.hud_drag), modifier = Modifier.padding(8.dp))
                    },
                    modifier = Modifier
                        .onGloballyPositioned {
                            tops[id] = it.positionInParent().y
                            heights[id] = it.size.height
                        }
                        .zIndex(if (isDragged) 1f else 0f)
                        .graphicsLayer {
                            translationY = if (isDragged) offset else 0f
                            shadowElevation = if (isDragged) 8.dp.toPx() else 0f
                        },
                )
            }
        }
    }
}

private fun presetLabel(preset: HudPreset) = when (preset) {
    HudPreset.MINIMAL -> R.string.hud_preset_minimal
    HudPreset.STANDARD -> R.string.hud_preset_standard
    HudPreset.FULL -> R.string.hud_preset_full
}

private fun cornerArrow(corner: HudCorner) = when (corner) {
    HudCorner.TOP_START -> "↖"
    HudCorner.TOP_END -> "↗"
    HudCorner.BOTTOM_START -> "↙"
    HudCorner.BOTTOM_END -> "↘"
}

private fun cornerLabel(corner: HudCorner) = when (corner) {
    HudCorner.TOP_START -> R.string.hud_corner_top_start
    HudCorner.TOP_END -> R.string.hud_corner_top_end
    HudCorner.BOTTOM_START -> R.string.hud_corner_bottom_start
    HudCorner.BOTTOM_END -> R.string.hud_corner_bottom_end
}

private fun sizeLabel(size: HudSize) = when (size) {
    HudSize.SMALL -> R.string.hud_size_small
    HudSize.MEDIUM -> R.string.hud_size_medium
    HudSize.LARGE -> R.string.hud_size_large
}
