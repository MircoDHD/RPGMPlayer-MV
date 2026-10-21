// SPDX-FileCopyrightText: 2026 MircoDHD
// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.mircodhd.mvplayer.ui.settings

import android.content.res.Resources
import android.view.View
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
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.mircodhd.mvplayer.R
import io.github.mircodhd.mvplayer.overlay.GamepadLayout
import io.github.mircodhd.mvplayer.overlay.Haptics
import io.github.mircodhd.mvplayer.overlay.LayoutNames
import io.github.mircodhd.mvplayer.settings.GameSettings
import io.github.mircodhd.mvplayer.settings.GlobalSettings
import io.github.mircodhd.mvplayer.settings.OverlayMode
import io.github.mircodhd.mvplayer.settings.OverlaySettings
import io.github.mircodhd.mvplayer.settings.Renderer
import io.github.mircodhd.mvplayer.settings.SettingsResolver

private enum class GameDialog { OVERLAY_MODE, LAYOUT, OPACITY, SIZE, HAPTICS, MIRROR, FRAME_RATE, RENDERER, CONTINUE_ERRORS }

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
    onOpenPicture: () -> Unit,
    onOpenFont: () -> Unit,
    onCustomizeLayout: (GamepadLayout) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    var dialog by remember { mutableStateOf<GameDialog?>(null) }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val layoutName = { id: String -> layouts.firstOrNull { it.id == id }?.let { LayoutNames.name(context, it) } ?: id }
    val modeName = { mode: OverlayMode -> resources.getString(overlayModeLabel(mode)) }
    val onOff = { value: Boolean -> resources.getString(if (value) R.string.value_on else R.string.value_off) }
    val frameRateName = { cap: Int -> frameRateLabel(resources, cap) }
    val effective = SettingsResolver.resolve(global, game)
    val view = LocalView.current

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
                    value = game.mapping?.let { resources.getString(R.string.controller_mapping_custom) },
                    badges = defaultBadge(game.mapping),
                    onClick = onOpenController,
                    onReset = game.mapping?.let { { onChange(game.copy(mapping = null)) } },
                )
            }
            item {
                ValueRow(
                    title = stringResource(R.string.overlay_mode),
                    value = modeName(effective.overlay.mode),
                    badges = defaultBadge(game.overlayMode),
                    onClick = { dialog = GameDialog.OVERLAY_MODE },
                )
            }
            item {
                ValueRow(
                    title = stringResource(R.string.overlay_layout),
                    value = layoutName(effective.overlay.layoutId),
                    badges = defaultBadge(game.overlayLayoutId),
                    onClick = { dialog = GameDialog.LAYOUT },
                )
            }
            item {
                ValueRow(
                    title = stringResource(R.string.overlay_customize),
                    value = null,
                    onClick = { onCustomizeLayout(layouts.firstOrNull { it.id == effective.overlay.layoutId } ?: layouts.first()) },
                )
            }
            item {
                ValueRow(
                    title = stringResource(R.string.overlay_opacity),
                    value = percent(effective.overlay.opacity),
                    badges = defaultBadge(game.overlayOpacity),
                    onClick = { dialog = GameDialog.OPACITY },
                )
            }
            item {
                ValueRow(
                    title = stringResource(R.string.overlay_size),
                    value = percent(effective.overlay.scale),
                    badges = defaultBadge(game.overlayScale),
                    onClick = { dialog = GameDialog.SIZE },
                )
            }
            item {
                ValueRow(
                    title = stringResource(R.string.overlay_haptics),
                    value = onOff(effective.overlay.haptics),
                    badges = defaultBadge(game.overlayHaptics),
                    onClick = { dialog = GameDialog.HAPTICS },
                )
            }
            item {
                ValueRow(
                    title = stringResource(R.string.overlay_mirror),
                    value = onOff(effective.overlay.mirror),
                    badges = defaultBadge(game.overlayMirror),
                    onClick = { dialog = GameDialog.MIRROR },
                )
            }
            item { SectionHeader(stringResource(R.string.set_section_display)) }
            item {
                ValueRow(
                    title = stringResource(R.string.frame_rate_title),
                    value = frameRateName(effective.frameRateCap),
                    summary = stringResource(R.string.frame_rate_summary),
                    badges = defaultBadge(game.frameRateCap) +
                        if (effective.frameRateCap == 0) listOf(OptionBadge.EXPERIMENTAL) else emptyList(),
                    onClick = { dialog = GameDialog.FRAME_RATE },
                )
            }
            item {
                ValueRow(
                    title = stringResource(R.string.renderer_title),
                    value = rendererLabel(resources, effective.renderer),
                    summary = stringResource(R.string.renderer_summary),
                    badges = defaultBadge(game.renderer) +
                        if (effective.renderer == Renderer.WEBGL2) listOf(OptionBadge.EXPERIMENTAL) else emptyList(),
                    onClick = { dialog = GameDialog.RENDERER },
                )
            }
            item {
                ValueRow(
                    title = stringResource(R.string.picture_title),
                    value = pictureSummary(resources, effective.picture),
                    summary = stringResource(R.string.picture_summary),
                    badges = defaultBadge(game.picture.takeUnless { it.isEmpty }),
                    onClick = onOpenPicture,
                )
            }
            item {
                ValueRow(
                    title = stringResource(R.string.font_title),
                    value = fontLabel(context, effective.font),
                    summary = stringResource(R.string.font_summary),
                    badges = defaultBadge(game.fontId ?: game.fontScale),
                    onClick = onOpenFont,
                )
            }
            item { SectionHeader(stringResource(R.string.set_section_compat)) }
            item {
                ValueRow(
                    title = stringResource(R.string.continue_errors_title),
                    value = onOff(effective.continueAfterErrors),
                    summary = stringResource(R.string.continue_errors_summary),
                    badges = defaultBadge(game.continueAfterErrors),
                    onClick = { dialog = GameDialog.CONTINUE_ERRORS },
                )
            }
        }
    }

    val o = global.overlay
    when (dialog) {
        GameDialog.OVERLAY_MODE -> ChoiceDialog(
            title = stringResource(R.string.overlay_mode),
            choices = overrideChoices(OverlayMode.entries.map { it to modeName(it) }, o.mode),
            selected = effective.overlay.mode,
            onSelect = { onChange(game.copy(overlayMode = overrideOf(it, o.mode))) },
            onDismiss = { dialog = null },
        )
        GameDialog.LAYOUT -> ChoiceDialog(
            title = stringResource(R.string.overlay_layout),
            choices = overrideChoices(layouts.map { it.id to LayoutNames.name(context, it) }, o.layoutId),
            selected = effective.overlay.layoutId,
            onSelect = { onChange(game.copy(overlayLayoutId = overrideOf(it, o.layoutId))) },
            onDismiss = { dialog = null },
        )
        GameDialog.OPACITY -> SliderDialog(
            title = stringResource(R.string.overlay_opacity),
            value = effective.overlay.opacity,
            defaultValue = o.opacity,
            range = OverlaySettings.OPACITY_RANGE,
            format = ::percent,
            onConfirm = { onChange(game.copy(overlayOpacity = it)) },
            onDismiss = { dialog = null },
        )
        GameDialog.SIZE -> SliderDialog(
            title = stringResource(R.string.overlay_size),
            value = effective.overlay.scale,
            defaultValue = o.scale,
            range = OverlaySettings.SCALE_RANGE,
            format = ::percent,
            onConfirm = { onChange(game.copy(overlayScale = it)) },
            onDismiss = { dialog = null },
        )
        GameDialog.HAPTICS -> ChoiceDialog(
            title = stringResource(R.string.overlay_haptics),
            choices = overrideChoices(listOf(true to onOff(true), false to onOff(false)), o.haptics),
            selected = effective.overlay.haptics,
            onSelect = {
                if (it) previewHaptics(view) // let the player feel what the buttons will do
                onChange(game.copy(overlayHaptics = overrideOf(it, o.haptics)))
            },
            onDismiss = { dialog = null },
        )
        GameDialog.CONTINUE_ERRORS -> ChoiceDialog(
            title = stringResource(R.string.continue_errors_title),
            choices = overrideChoices(listOf(true to onOff(true), false to onOff(false)), global.continueAfterErrors),
            selected = effective.continueAfterErrors,
            onSelect = { onChange(game.copy(continueAfterErrors = overrideOf(it, global.continueAfterErrors))) },
            onDismiss = { dialog = null },
            note = stringResource(R.string.continue_errors_summary),
        )
        GameDialog.MIRROR -> ChoiceDialog(
            title = stringResource(R.string.overlay_mirror),
            choices = overrideChoices(listOf(true to onOff(true), false to onOff(false)), o.mirror),
            selected = effective.overlay.mirror,
            onSelect = { onChange(game.copy(overlayMirror = overrideOf(it, o.mirror))) },
            onDismiss = { dialog = null },
        )
        GameDialog.RENDERER -> RendererDialog(
            selected = effective.renderer,
            defaultRenderer = global.renderer,
            onSelect = { onChange(game.copy(renderer = overrideOf(it, global.renderer))) },
            onDismiss = { dialog = null },
        )
        GameDialog.FRAME_RATE -> FrameRateDialog(
            selected = effective.frameRateCap,
            defaultCap = global.frameRateCap,
            onSelect = { onChange(game.copy(frameRateCap = overrideOf(it, global.frameRateCap))) },
            onDismiss = { dialog = null },
        )
        null -> Unit
    }
}

/** The same short pulse a touch control gives (VirtualGamepad), as a preview in the settings. */
fun previewHaptics(view: View) {
    Haptics.click(view.context)
}

/** [defaultCap] marks the global value when choosing for one game; null in the global settings. */
@Composable
fun FrameRateDialog(selected: Int, defaultCap: Int?, onSelect: (Int) -> Unit, onDismiss: () -> Unit) {
    val resources = LocalResources.current
    val options = GlobalSettings.FRAME_RATE_CAPS.map { it to frameRateLabel(resources, it) }
    val experimental = { cap: Int -> if (cap == 0) listOf(OptionBadge.EXPERIMENTAL) else emptyList() }
    val describe = { cap: Int ->
        resources.getString(
            when (cap) {
                30 -> R.string.frame_rate_30_description
                0 -> R.string.frame_rate_unlimited_warning
                else -> R.string.frame_rate_60_description
            },
        )
    }
    ChoiceDialog(
        title = stringResource(R.string.frame_rate_title),
        choices = if (defaultCap != null) {
            overrideChoices(options, defaultCap, experimental, describe)
        } else {
            options.map { (cap, label) -> Choice(cap, label, experimental(cap), describe(cap)) }
        },
        selected = selected,
        onSelect = onSelect,
        onDismiss = onDismiss,
        info = InfoContent(
            title = stringResource(R.string.frame_rate_info_title),
            intro = stringResource(R.string.frame_rate_info_intro),
            sections = listOf(
                InfoSection(
                    frameRateLabel(resources, 30),
                    stringResource(R.string.frame_rate_info_30_use),
                    stringResource(R.string.frame_rate_info_30_pros),
                    stringResource(R.string.frame_rate_info_30_cons),
                ),
                InfoSection(
                    frameRateLabel(resources, 60),
                    stringResource(R.string.frame_rate_info_60_use),
                    stringResource(R.string.frame_rate_info_60_pros),
                    stringResource(R.string.frame_rate_info_60_cons),
                ),
                InfoSection(
                    frameRateLabel(resources, 0),
                    stringResource(R.string.frame_rate_info_unlimited_use),
                    stringResource(R.string.frame_rate_info_unlimited_pros),
                    stringResource(R.string.frame_rate_info_unlimited_cons),
                ),
            ),
            footer = stringResource(R.string.frame_rate_info_footer),
        ),
    )
}

/** [defaultRenderer] marks the global value when choosing for one game; null in the global settings. */
@Composable
fun RendererDialog(selected: Renderer, defaultRenderer: Renderer?, onSelect: (Renderer) -> Unit, onDismiss: () -> Unit) {
    val resources = LocalResources.current
    val options = Renderer.entries.map { it to rendererLabel(resources, it) }
    val experimental = { r: Renderer -> if (r == Renderer.WEBGL2) listOf(OptionBadge.EXPERIMENTAL) else emptyList() }
    val describe = { r: Renderer ->
        resources.getString(
            when (r) {
                Renderer.WEBGL -> R.string.renderer_webgl_description
                Renderer.CANVAS -> R.string.renderer_canvas_description
                Renderer.WEBGL2 -> R.string.renderer_webgl2_description
            },
        )
    }
    ChoiceDialog(
        title = stringResource(R.string.renderer_title),
        choices = if (defaultRenderer != null) {
            overrideChoices(options, defaultRenderer, experimental, describe)
        } else {
            options.map { (r, label) -> Choice(r, label, experimental(r), describe(r)) }
        },
        selected = selected,
        onSelect = onSelect,
        onDismiss = onDismiss,
        note = stringResource(R.string.renderer_note),
        info = InfoContent(
            title = stringResource(R.string.renderer_info_title),
            intro = stringResource(R.string.renderer_info_intro),
            sections = listOf(
                InfoSection(
                    rendererLabel(resources, Renderer.WEBGL),
                    stringResource(R.string.renderer_info_webgl_use),
                    stringResource(R.string.renderer_info_webgl_pros),
                    stringResource(R.string.renderer_info_webgl_cons),
                ),
                InfoSection(
                    rendererLabel(resources, Renderer.CANVAS),
                    stringResource(R.string.renderer_info_canvas_use),
                    stringResource(R.string.renderer_info_canvas_pros),
                    stringResource(R.string.renderer_info_canvas_cons),
                ),
                InfoSection(
                    rendererLabel(resources, Renderer.WEBGL2),
                    stringResource(R.string.renderer_info_webgl2_use),
                    stringResource(R.string.renderer_info_webgl2_pros),
                    stringResource(R.string.renderer_info_webgl2_cons),
                ),
            ),
            footer = stringResource(R.string.renderer_info_footer),
        ),
    )
}

fun rendererLabel(resources: Resources, renderer: Renderer): String = resources.getString(
    when (renderer) {
        Renderer.WEBGL -> R.string.renderer_webgl
        Renderer.CANVAS -> R.string.renderer_canvas
        Renderer.WEBGL2 -> R.string.renderer_webgl2
    },
)

fun frameRateLabel(resources: Resources, cap: Int): String =
    if (cap <= 0) resources.getString(R.string.frame_rate_unlimited) else resources.getString(R.string.frame_rate_fps, cap)

fun overlayModeLabel(mode: OverlayMode) = when (mode) {
    OverlayMode.AUTO -> R.string.overlay_mode_auto
    OverlayMode.ALWAYS -> R.string.overlay_mode_always
    OverlayMode.NEVER -> R.string.overlay_mode_never
}
