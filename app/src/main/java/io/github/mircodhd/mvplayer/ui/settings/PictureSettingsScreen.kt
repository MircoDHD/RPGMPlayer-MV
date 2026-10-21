// SPDX-FileCopyrightText: 2026 MircoDHD
// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.mircodhd.mvplayer.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
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
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.mircodhd.mvplayer.R
import io.github.mircodhd.mvplayer.settings.PictureOverrides
import io.github.mircodhd.mvplayer.settings.PictureSettings
import io.github.mircodhd.mvplayer.settings.Scaler
import io.github.mircodhd.mvplayer.settings.ScalingMode
import kotlin.math.roundToInt

/**
 * Scaling and filters, globally or for one game. With [overrides] (one game) values that
 * follow [global] carry the Default badge; changes come back as the effective settings.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PictureSettingsScreen(
    title: String,
    global: PictureSettings,
    overrides: PictureOverrides?,
    canvas: Boolean,
    onChange: (PictureSettings) -> Unit,
    onBack: () -> Unit,
) {
    var info by remember { mutableStateOf(false) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.set_back))
                    }
                },
                actions = {
                    IconButton(onClick = { info = true }) {
                        Icon(Icons.Outlined.Info, contentDescription = stringResource(R.string.picture_info_title))
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 32.dp)) {
            PictureControls(global, overrides, canvas, onChange)
        }
    }
    if (info) PictureInfoDialog { info = false }
}

/**
 * In game: the same controls in a panel beside (landscape) or below (portrait) the game,
 * which redraws behind it with every change. A tap outside closes it.
 */
@Composable
fun PicturePanel(
    global: PictureSettings,
    overrides: PictureOverrides,
    canvas: Boolean,
    onChange: (PictureSettings) -> Unit,
    onClose: () -> Unit,
) {
    var info by remember { mutableStateOf(false) }
    BackHandler(onBack = onClose)
    BoxWithConstraints(Modifier.fillMaxSize()) {
        Box(
            Modifier.fillMaxSize().clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClose),
        )
        val landscape = maxWidth > maxHeight
        val panelModifier = if (landscape) {
            Modifier.align(Alignment.CenterEnd).fillMaxHeight().width(minOf(maxWidth * 0.45f, 400.dp))
        } else {
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().fillMaxHeight(0.55f)
        }
        Surface(panelModifier, color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.96f), tonalElevation = 3.dp) {
            Column(Modifier.navigationBarsPadding()) {
                Row(Modifier.padding(start = 16.dp, end = 4.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.picture_title), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    IconButton(onClick = { info = true }) {
                        Icon(Icons.Outlined.Info, contentDescription = stringResource(R.string.picture_info_title))
                    }
                    TextButton(onClick = onClose) { Text(stringResource(R.string.dialog_done)) }
                }
                Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = 16.dp)) {
                    PictureControls(global, overrides, canvas, onChange)
                }
            }
        }
    }
    if (info) PictureInfoDialog { info = false }
}

@Composable
private fun PictureControls(
    global: PictureSettings,
    overrides: PictureOverrides?,
    canvas: Boolean,
    onChange: (PictureSettings) -> Unit,
) {
    val p = overrides?.resolve(global) ?: global
    val badge = { follows: Boolean -> if (overrides != null && follows) listOf(OptionBadge.DEFAULT) else emptyList() }
    val resources = LocalResources.current
    val effect = { v: Float -> if (v <= 0f) resources.getString(R.string.value_off) else percent(v) }

    Labeled(stringResource(R.string.picture_scaling), badge(overrides?.scaling == null)) {
        Segmented(
            options = ScalingMode.entries,
            selected = p.scaling,
            label = { stringResource(scalingLabel(it)) },
            onSelect = { onChange(p.copy(scaling = it)) },
        )
        Hint(stringResource(scalingDescription(p.scaling)), Modifier.padding(top = 4.dp))
    }
    Labeled(stringResource(R.string.picture_scaler), badge(overrides?.scaler == null)) {
        Segmented(
            options = Scaler.entries,
            selected = p.scaler,
            label = { stringResource(scalerLabel(it)) },
            onSelect = { onChange(p.copy(scaler = it)) },
        )
        Hint(stringResource(scalerDescription(p.scaler)), Modifier.padding(top = 4.dp))
    }
    if (canvas) {
        Hint(stringResource(R.string.picture_canvas_note), Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
    }

    SectionHeader(stringResource(R.string.picture_section_effects))
    val effects = !canvas
    PictureSlider(stringResource(R.string.picture_sharpen), p.sharpen, PictureSettings.EFFECT_RANGE, effect,
        badge(overrides?.sharpen == null), enabled = effects) { onChange(p.copy(sharpen = it)) }
    PictureSlider(stringResource(R.string.picture_scanlines), p.scanlines, PictureSettings.EFFECT_RANGE, effect,
        badge(overrides?.scanlines == null), enabled = effects) { onChange(p.copy(scanlines = it)) }

    SectionHeader(stringResource(R.string.picture_section_colour))
    PictureSlider(stringResource(R.string.picture_brightness), p.brightness, PictureSettings.BRIGHTNESS_RANGE, ::percent,
        badge(overrides?.brightness == null)) { onChange(p.copy(brightness = it)) }
    PictureSlider(stringResource(R.string.picture_contrast), p.contrast, PictureSettings.CONTRAST_RANGE, ::percent,
        badge(overrides?.contrast == null)) { onChange(p.copy(contrast = it)) }
    PictureSlider(stringResource(R.string.picture_saturation), p.saturation, PictureSettings.SATURATION_RANGE, ::percent,
        badge(overrides?.saturation == null)) { onChange(p.copy(saturation = it)) }

    Hint(stringResource(R.string.picture_cost_note), Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
    val resetTo = if (overrides != null) global else PictureSettings()
    if (p != resetTo) {
        TextButton(onClick = { onChange(resetTo) }, modifier = Modifier.padding(horizontal = 8.dp)) {
            Text(stringResource(if (overrides != null) R.string.picture_reset_game else R.string.picture_reset))
        }
    }
}

@Composable
private fun Labeled(title: String, badges: List<OptionBadge>, content: @Composable () -> Unit) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (badges.isNotEmpty()) Spacer(Modifier.width(8.dp))
            Badges(badges, inline = true)
        }
        Spacer(Modifier.padding(top = 6.dp))
        content()
    }
}

@Composable
private fun PictureSlider(
    title: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    format: (Float) -> String,
    badges: List<OptionBadge>,
    enabled: Boolean = true,
    onChange: (Float) -> Unit,
) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (badges.isNotEmpty()) Spacer(Modifier.width(8.dp))
            Badges(badges, inline = true)
            Spacer(Modifier.weight(1f))
            Text(format(value), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
        // Values snap to 5 % (without tick marks), so the defaults (0 % or 100 %) are reached exactly.
        Slider(
            value = value,
            onValueChange = { v -> onChange((v * 20).roundToInt() / 20f) },
            valueRange = range,
            enabled = enabled,
        )
    }
}

@Composable
private fun Hint(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = modifier)
}

@Composable
private fun PictureInfoDialog(onDismiss: () -> Unit) {
    InfoDialog(
        InfoContent(
            title = stringResource(R.string.picture_info_title),
            intro = stringResource(R.string.picture_info_intro),
            sections = listOf(
                InfoSection(
                    stringResource(R.string.picture_scaler_smooth),
                    stringResource(R.string.picture_info_smooth_use),
                    stringResource(R.string.picture_info_smooth_pros),
                    stringResource(R.string.picture_info_smooth_cons),
                ),
                InfoSection(
                    stringResource(R.string.picture_scaler_sharp),
                    stringResource(R.string.picture_info_sharp_use),
                    stringResource(R.string.picture_info_sharp_pros),
                    stringResource(R.string.picture_info_sharp_cons),
                ),
                InfoSection(
                    stringResource(R.string.picture_scaler_pixel),
                    stringResource(R.string.picture_info_pixel_use),
                    stringResource(R.string.picture_info_pixel_pros),
                    stringResource(R.string.picture_info_pixel_cons),
                ),
                InfoSection(
                    stringResource(R.string.picture_sharpen),
                    stringResource(R.string.picture_info_sharpen_use),
                    stringResource(R.string.picture_info_sharpen_pros),
                    stringResource(R.string.picture_info_sharpen_cons),
                ),
                InfoSection(
                    stringResource(R.string.picture_scanlines),
                    stringResource(R.string.picture_info_scanlines_use),
                    stringResource(R.string.picture_info_scanlines_pros),
                    stringResource(R.string.picture_info_scanlines_cons),
                ),
            ),
            footer = stringResource(R.string.picture_info_footer),
        ),
        onDismiss,
    )
}

fun scalingLabel(mode: ScalingMode) = when (mode) {
    ScalingMode.FIT -> R.string.picture_scaling_fit
    ScalingMode.INTEGER -> R.string.picture_scaling_integer
    ScalingMode.STRETCH -> R.string.picture_scaling_stretch
}

private fun scalingDescription(mode: ScalingMode) = when (mode) {
    ScalingMode.FIT -> R.string.picture_scaling_fit_description
    ScalingMode.INTEGER -> R.string.picture_scaling_integer_description
    ScalingMode.STRETCH -> R.string.picture_scaling_stretch_description
}

fun scalerLabel(scaler: Scaler) = when (scaler) {
    Scaler.SMOOTH -> R.string.picture_scaler_smooth
    Scaler.SHARP -> R.string.picture_scaler_sharp
    Scaler.PIXEL -> R.string.picture_scaler_pixel
}

private fun scalerDescription(scaler: Scaler) = when (scaler) {
    Scaler.SMOOTH -> R.string.picture_scaler_smooth_description
    Scaler.SHARP -> R.string.picture_scaler_sharp_description
    Scaler.PIXEL -> R.string.picture_scaler_pixel_description
}

/** Short summary for a settings row, e.g. "Fit · Sharp · Scanlines". */
fun pictureSummary(resources: android.content.res.Resources, p: PictureSettings): String {
    val parts = mutableListOf(resources.getString(scalingLabel(p.scaling)), resources.getString(scalerLabel(p.scaler)))
    if (p.sharpen > 0f) parts += resources.getString(R.string.picture_sharpen)
    if (p.scanlines > 0f) parts += resources.getString(R.string.picture_scanlines)
    if (p.brightness != 1f || p.contrast != 1f || p.saturation != 1f) parts += resources.getString(R.string.picture_section_colour)
    return parts.joinToString(" · ")
}
