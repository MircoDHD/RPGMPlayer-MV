// SPDX-FileCopyrightText: 2026 MircoDHD
// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.mircodhd.mvplayer.ui.settings

import android.content.res.Resources
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.mircodhd.mvplayer.R
import io.github.mircodhd.mvplayer.overlay.GamepadLayout
import io.github.mircodhd.mvplayer.overlay.LayoutNames
import io.github.mircodhd.mvplayer.settings.GameSettings
import io.github.mircodhd.mvplayer.settings.GlobalSettings
import io.github.mircodhd.mvplayer.settings.OverlayMode
import io.github.mircodhd.mvplayer.settings.OverlaySettings

private enum class GameDialog { OVERLAY_MODE, LAYOUT, OPACITY, SIZE, HAPTICS, MIRROR, FRAME_RATE }

/** Per-game overrides of the global settings (reachable from the info sheet and in game). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameSettingsScreen(
    gameTitle: String,
    global: GlobalSettings,
    game: GameSettings,
    layouts: List<GamepadLayout>,
    onChange: (GameSettings) -> Unit,
    onOpenController: () -> Unit,
    onCustomizeLayout: (GamepadLayout) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    var dialog by remember { mutableStateOf<GameDialog?>(null) }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val defaultSuffix = { value: String -> resources.getString(R.string.set_default_value, value) }
    val layoutName = { id: String -> layouts.firstOrNull { it.id == id }?.let { LayoutNames.name(context, it) } ?: id }
    val modeName = { mode: OverlayMode -> resources.getString(overlayModeLabel(mode)) }
    val onOff = { value: Boolean -> resources.getString(if (value) R.string.value_on else R.string.value_off) }
    val frameRateName = { cap: Int -> frameRateLabel(resources, cap) }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text(stringResource(R.string.game_settings_title, gameTitle)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.set_back))
                    }
                },
                actions = {
                    if (!game.isEmpty) {
                        TextButton(onClick = { onChange(GameSettings()) }) { Text(stringResource(R.string.game_settings_reset_all)) }
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = 32.dp)) {
            item { SectionHeader(stringResource(R.string.set_section_input)) }
            item {
                ValueRow(
                    title = stringResource(R.string.controller_title),
                    value = resources.getString(if (game.mapping == null) R.string.value_default else R.string.controller_mapping_custom),
                    onClick = onOpenController,
                    onReset = game.mapping?.let { { onChange(game.copy(mapping = null)) } },
                )
            }
            item {
                ValueRow(
                    title = stringResource(R.string.overlay_mode),
                    value = game.overlayMode?.let(modeName) ?: defaultSuffix(modeName(global.overlay.mode)),
                    onClick = { dialog = GameDialog.OVERLAY_MODE },
                    onReset = game.overlayMode?.let { { onChange(game.copy(overlayMode = null)) } },
                )
            }
            item {
                ValueRow(
                    title = stringResource(R.string.overlay_layout),
                    value = game.overlayLayoutId?.let(layoutName) ?: defaultSuffix(layoutName(global.overlay.layoutId)),
                    onClick = { dialog = GameDialog.LAYOUT },
                    onReset = game.overlayLayoutId?.let { { onChange(game.copy(overlayLayoutId = null)) } },
                )
            }
            item {
                ValueRow(
                    title = stringResource(R.string.overlay_customize),
                    value = null,
                    onClick = {
                        val id = game.overlayLayoutId ?: global.overlay.layoutId
                        onCustomizeLayout(layouts.firstOrNull { it.id == id } ?: layouts.first())
                    },
                )
            }
            item {
                ValueRow(
                    title = stringResource(R.string.overlay_opacity),
                    value = game.overlayOpacity?.let(::percent) ?: defaultSuffix(percent(global.overlay.opacity)),
                    onClick = { dialog = GameDialog.OPACITY },
                    onReset = game.overlayOpacity?.let { { onChange(game.copy(overlayOpacity = null)) } },
                )
            }
            item {
                ValueRow(
                    title = stringResource(R.string.overlay_size),
                    value = game.overlayScale?.let(::percent) ?: defaultSuffix(percent(global.overlay.scale)),
                    onClick = { dialog = GameDialog.SIZE },
                    onReset = game.overlayScale?.let { { onChange(game.copy(overlayScale = null)) } },
                )
            }
            item {
                ValueRow(
                    title = stringResource(R.string.overlay_haptics),
                    value = game.overlayHaptics?.let(onOff) ?: defaultSuffix(onOff(global.overlay.haptics)),
                    onClick = { dialog = GameDialog.HAPTICS },
                    onReset = game.overlayHaptics?.let { { onChange(game.copy(overlayHaptics = null)) } },
                )
            }
            item {
                ValueRow(
                    title = stringResource(R.string.overlay_mirror),
                    value = game.overlayMirror?.let(onOff) ?: defaultSuffix(onOff(global.overlay.mirror)),
                    onClick = { dialog = GameDialog.MIRROR },
                    onReset = game.overlayMirror?.let { { onChange(game.copy(overlayMirror = null)) } },
                )
            }
            item { SectionHeader(stringResource(R.string.set_section_display)) }
            item {
                ValueRow(
                    title = stringResource(R.string.frame_rate_title),
                    value = game.frameRateCap?.let(frameRateName) ?: defaultSuffix(frameRateName(global.frameRateCap)),
                    summary = stringResource(R.string.frame_rate_summary),
                    badges = if ((game.frameRateCap ?: global.frameRateCap) == 0) listOf(OptionBadge.EXPERIMENTAL) else emptyList(),
                    onClick = { dialog = GameDialog.FRAME_RATE },
                    onReset = game.frameRateCap?.let { { onChange(game.copy(frameRateCap = null)) } },
                )
            }
        }
    }

    val useDefault = stringResource(R.string.set_use_default)
    when (dialog) {
        GameDialog.OVERLAY_MODE -> ChoiceDialog(
            title = stringResource(R.string.overlay_mode),
            choices = listOf(Choice<OverlayMode>(null, "$useDefault (${modeName(global.overlay.mode)})")) +
                OverlayMode.entries.map { Choice(it, modeName(it)) },
            selected = game.overlayMode,
            onSelect = { onChange(game.copy(overlayMode = it)) },
            onDismiss = { dialog = null },
        )
        GameDialog.LAYOUT -> ChoiceDialog(
            title = stringResource(R.string.overlay_layout),
            choices = listOf(Choice<String>(null, "$useDefault (${layoutName(global.overlay.layoutId)})")) +
                layouts.map { Choice(it.id, LayoutNames.name(context, it)) },
            selected = game.overlayLayoutId,
            onSelect = { onChange(game.copy(overlayLayoutId = it)) },
            onDismiss = { dialog = null },
        )
        GameDialog.OPACITY -> SliderDialog(
            title = stringResource(R.string.overlay_opacity),
            value = game.overlayOpacity,
            defaultValue = global.overlay.opacity,
            range = OverlaySettings.OPACITY_RANGE,
            format = ::percent,
            allowDefault = true,
            onConfirm = { onChange(game.copy(overlayOpacity = it)) },
            onDismiss = { dialog = null },
        )
        GameDialog.SIZE -> SliderDialog(
            title = stringResource(R.string.overlay_size),
            value = game.overlayScale,
            defaultValue = global.overlay.scale,
            range = OverlaySettings.SCALE_RANGE,
            format = ::percent,
            allowDefault = true,
            onConfirm = { onChange(game.copy(overlayScale = it)) },
            onDismiss = { dialog = null },
        )
        GameDialog.HAPTICS -> ChoiceDialog(
            title = stringResource(R.string.overlay_haptics),
            choices = onOffChoices(useDefault, onOff, global.overlay.haptics),
            selected = game.overlayHaptics,
            onSelect = { onChange(game.copy(overlayHaptics = it)) },
            onDismiss = { dialog = null },
        )
        GameDialog.MIRROR -> ChoiceDialog(
            title = stringResource(R.string.overlay_mirror),
            choices = onOffChoices(useDefault, onOff, global.overlay.mirror),
            selected = game.overlayMirror,
            onSelect = { onChange(game.copy(overlayMirror = it)) },
            onDismiss = { dialog = null },
        )
        GameDialog.FRAME_RATE -> FrameRateDialog(
            selected = game.frameRateCap,
            defaultCap = global.frameRateCap,
            allowDefault = true,
            onSelect = { onChange(game.copy(frameRateCap = it)) },
            onDismiss = { dialog = null },
        )
        null -> Unit
    }
}

private fun onOffChoices(useDefault: String, onOff: (Boolean) -> String, default: Boolean) = listOf(
    Choice<Boolean>(null, "$useDefault (${onOff(default)})"),
    Choice(true, onOff(true)),
    Choice(false, onOff(false)),
)

@Composable
fun FrameRateDialog(selected: Int?, defaultCap: Int, allowDefault: Boolean, onSelect: (Int?) -> Unit, onDismiss: () -> Unit) {
    val resources = LocalResources.current
    val choices = buildList {
        if (allowDefault) add(Choice<Int>(null, "${resources.getString(R.string.set_use_default)} (${frameRateLabel(resources, defaultCap)})"))
        add(Choice(60, frameRateLabel(resources, 60)))
        add(Choice(0, frameRateLabel(resources, 0), listOf(OptionBadge.EXPERIMENTAL)))
    }
    ChoiceDialog(
        title = stringResource(R.string.frame_rate_title),
        choices = choices,
        selected = selected,
        onSelect = onSelect,
        onDismiss = onDismiss,
        note = stringResource(R.string.frame_rate_unlimited_warning),
    )
}

fun frameRateLabel(resources: Resources, cap: Int): String =
    if (cap <= 0) resources.getString(R.string.frame_rate_unlimited) else resources.getString(R.string.frame_rate_fps, cap)

fun overlayModeLabel(mode: OverlayMode) = when (mode) {
    OverlayMode.AUTO -> R.string.overlay_mode_auto
    OverlayMode.ALWAYS -> R.string.overlay_mode_always
    OverlayMode.NEVER -> R.string.overlay_mode_never
}
