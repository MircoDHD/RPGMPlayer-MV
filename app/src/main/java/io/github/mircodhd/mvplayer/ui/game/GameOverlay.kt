// SPDX-FileCopyrightText: 2026 MircoDHD
// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.mircodhd.mvplayer.ui.game

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.clickable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.mircodhd.mvplayer.R
import io.github.mircodhd.mvplayer.hud.HudOverlay
import io.github.mircodhd.mvplayer.hud.HudSnapshot
import io.github.mircodhd.mvplayer.input.ControllerMapping
import io.github.mircodhd.mvplayer.input.InputAction
import io.github.mircodhd.mvplayer.overlay.GamepadLayout
import io.github.mircodhd.mvplayer.overlay.LayoutEditor
import io.github.mircodhd.mvplayer.overlay.LayoutNames
import io.github.mircodhd.mvplayer.overlay.VirtualGamepad
import io.github.mircodhd.mvplayer.settings.GameSettings
import io.github.mircodhd.mvplayer.settings.GlobalSettings
import io.github.mircodhd.mvplayer.settings.OverlayMode
import io.github.mircodhd.mvplayer.settings.OverlaySettings
import io.github.mircodhd.mvplayer.settings.SettingsResolver
import io.github.mircodhd.mvplayer.ui.settings.ChoiceDialog
import io.github.mircodhd.mvplayer.ui.settings.ControllerScreen
import io.github.mircodhd.mvplayer.ui.settings.FrameRateDialog
import io.github.mircodhd.mvplayer.ui.settings.overrideChoices
import io.github.mircodhd.mvplayer.ui.settings.overrideOf
import io.github.mircodhd.mvplayer.ui.settings.GameSettingsScreen
import io.github.mircodhd.mvplayer.ui.settings.frameRateLabel

data class TextInputRequest(val current: String, val maxLength: Int)

data class EditorRequest(val layout: GamepadLayout, val original: GamepadLayout, val background: ImageBitmap?)

/** Everything the overlay shows; owned by GameActivity, observed by Compose. */
class GameOverlayState(val gameTitle: String) {
    var global by mutableStateOf(GlobalSettings())
    var game by mutableStateOf(GameSettings())
    var layouts by mutableStateOf<List<GamepadLayout>>(emptyList())
    /** Effective layout (already mirrored if needed) and appearance. */
    var layout by mutableStateOf<GamepadLayout?>(null)
    var overlay by mutableStateOf(OverlaySettings())
    var controllerConnected by mutableStateOf(false)
    var gameReady by mutableStateOf(false)

    var quickMenu by mutableStateOf(false)
    var settingsOpen by mutableStateOf(false)
    var editor by mutableStateOf<EditorRequest?>(null)
    var textInput by mutableStateOf<TextInputRequest?>(null)
    var confirmQuit by mutableStateOf(false)
    /** The renderer setting changed while the game runs; offer to restart it. */
    var restartPrompt by mutableStateOf(false)
    /** Hides the controls for a moment, e.g. while taking the editor screenshot. */
    var hideControls by mutableStateOf(false)
    /** Latest performance HUD sample; null while the HUD is off or has no data yet. */
    var hud by mutableStateOf<HudSnapshot?>(null)

    val showGamepad: Boolean
        get() = gameReady && !hideControls && editor == null && when (overlay.mode) {
            OverlayMode.ALWAYS -> true
            OverlayMode.NEVER -> false
            OverlayMode.AUTO -> !controllerConnected
        }

    val showHud: Boolean get() = gameReady && global.hud.enabled && editor == null && !hideControls

    /** True while something covers the game and it should be paused. */
    val isModal: Boolean get() = quickMenu || settingsOpen || editor != null || confirmQuit || restartPrompt
}

interface GameOverlayActions {
    fun onGamepadActions(actions: Set<InputAction>)
    fun onTextResult(text: String?)
    fun onGameSettingsChange(settings: GameSettings)
    fun onGlobalSettingsChange(settings: GlobalSettings)
    fun onRestartGame()
    fun onHudDrawn(nanos: Long)
    fun onCustomizeLayout(layout: GamepadLayout)
    fun onEditorSave(layout: GamepadLayout, forGame: Boolean, asCopy: Boolean)
    fun onQuit()
}

@Composable
fun GameOverlay(state: GameOverlayState, actions: GameOverlayActions) {
    // The HUD comes first, so on-screen controls drawn on top of it keep their touches; a
    // tap on the HUD where no control is switches its layout.
    if (state.showHud) {
        HudOverlay(
            snapshot = state.hud ?: HudSnapshot.EMPTY,
            settings = state.global.hud,
            onTap = { actions.onGlobalSettingsChange(state.global.copy(hud = state.global.hud.copy(detailed = !state.global.hud.detailed))) },
            onMove = { x, y -> actions.onGlobalSettingsChange(state.global.copy(hud = state.global.hud.copy(offsetX = x, offsetY = y))) },
            onDrawNanos = actions::onHudDrawn,
        )
    }
    val layout = state.layout
    if (state.showGamepad && layout != null) {
        VirtualGamepad(layout, state.overlay, actions::onGamepadActions)
    }

    if (state.quickMenu) QuickMenu(state, actions)

    if (state.settingsOpen) InGameSettings(state, actions)

    state.editor?.let { request ->
        LayoutEditor(
            initial = request.layout,
            original = request.original,
            background = request.background,
            scale = state.overlay.scale,
            canSaveForGame = true,
            onSave = { saved, forGame, asCopy ->
                state.editor = null
                actions.onEditorSave(saved, forGame, asCopy)
            },
            onCancel = { state.editor = null },
        )
    }

    state.textInput?.let { request ->
        TextInputDialog(request) { result ->
            state.textInput = null
            actions.onTextResult(result)
        }
    }

    if (state.restartPrompt) {
        AlertDialog(
            onDismissRequest = { state.restartPrompt = false },
            title = { Text(stringResource(R.string.renderer_restart_title)) },
            text = { Text(stringResource(R.string.renderer_restart_text)) },
            confirmButton = { TextButton(onClick = actions::onRestartGame) { Text(stringResource(R.string.renderer_restart_now)) } },
            dismissButton = { TextButton(onClick = { state.restartPrompt = false }) { Text(stringResource(R.string.renderer_restart_later)) } },
        )
    }

    if (state.confirmQuit) {
        AlertDialog(
            onDismissRequest = { state.confirmQuit = false },
            title = { Text(stringResource(R.string.game_quit_title)) },
            text = { Text(stringResource(R.string.game_quit_message)) },
            confirmButton = { TextButton(onClick = actions::onQuit) { Text(stringResource(R.string.game_quit_confirm)) } },
            dismissButton = { TextButton(onClick = { state.confirmQuit = false }) { Text(stringResource(R.string.game_quit_cancel)) } },
        )
    }
}

private enum class QuickDialog { LAYOUT, FRAME_RATE }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuickMenu(state: GameOverlayState, actions: GameOverlayActions) {
    val context = LocalContext.current
    var dialog by remember { mutableStateOf<QuickDialog?>(null) }
    val effective = SettingsResolver.resolve(state.global, state.game)
    val close = { state.quickMenu = false }

    ModalBottomSheet(onDismissRequest = close, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = 16.dp)) {
            Text(
                state.gameTitle,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
            Text(
                stringResource(R.string.quick_paused),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
            )
            MenuItem(Icons.Filled.PlayArrow, stringResource(R.string.quick_resume), onClick = close)
            HorizontalDivider(Modifier.padding(vertical = 4.dp))
            ListItem(
                headlineContent = { Text(stringResource(R.string.quick_show_controls)) },
                leadingContent = { Icon(Icons.Filled.CheckCircle, contentDescription = null) },
                trailingContent = {
                    Switch(checked = state.showGamepadSetting(), onCheckedChange = { actions.onGameSettingsChange(state.game.withOverlay(it)) })
                },
                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                modifier = Modifier.clickable { actions.onGameSettingsChange(state.game.withOverlay(!state.showGamepadSetting())) },
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.quick_hud)) },
                leadingContent = { Icon(Icons.Filled.Info, contentDescription = null) },
                trailingContent = {
                    Switch(checked = state.global.hud.enabled, onCheckedChange = { actions.onGlobalSettingsChange(state.global.withHud(it)) })
                },
                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                modifier = Modifier.clickable { actions.onGlobalSettingsChange(state.global.withHud(!state.global.hud.enabled)) },
            )
            MenuItem(
                Icons.AutoMirrored.Filled.List,
                stringResource(R.string.overlay_layout),
                supporting = state.layouts.firstOrNull { it.id == effective.overlay.layoutId }?.let { LayoutNames.name(context, it) },
            ) { dialog = QuickDialog.LAYOUT }
            MenuItem(Icons.Filled.Edit, stringResource(R.string.overlay_customize)) {
                state.layouts.firstOrNull { it.id == effective.overlay.layoutId }?.let(actions::onCustomizeLayout)
            }
            MenuItem(
                Icons.Filled.PlayArrow,
                stringResource(R.string.frame_rate_title),
                supporting = frameRateLabel(LocalResources.current, effective.frameRateCap),
            ) { dialog = QuickDialog.FRAME_RATE }
            MenuItem(Icons.Filled.Settings, stringResource(R.string.quick_game_settings)) {
                state.quickMenu = false
                state.settingsOpen = true
            }
            HorizontalDivider(Modifier.padding(vertical = 4.dp))
            MenuItem(Icons.AutoMirrored.Filled.ExitToApp, stringResource(R.string.quick_quit)) {
                state.quickMenu = false
                state.confirmQuit = true
            }
        }
    }

    when (dialog) {
        QuickDialog.LAYOUT -> ChoiceDialog(
            title = stringResource(R.string.overlay_layout),
            choices = overrideChoices(state.layouts.map { it.id to LayoutNames.name(context, it) }, state.global.overlay.layoutId),
            selected = effective.overlay.layoutId,
            onSelect = { actions.onGameSettingsChange(state.game.copy(overlayLayoutId = overrideOf(it, state.global.overlay.layoutId))) },
            onDismiss = { dialog = null },
        )
        QuickDialog.FRAME_RATE -> FrameRateDialog(
            selected = effective.frameRateCap,
            defaultCap = state.global.frameRateCap,
            onSelect = { actions.onGameSettingsChange(state.game.copy(frameRateCap = overrideOf(it, state.global.frameRateCap))) },
            onDismiss = { dialog = null },
        )
        null -> Unit
    }
}

/** Whether the controls are switched on for this game (ignores the temporary hiding). */
private fun GameOverlayState.showGamepadSetting(): Boolean = when (overlay.mode) {
    OverlayMode.ALWAYS -> true
    OverlayMode.NEVER -> false
    OverlayMode.AUTO -> !controllerConnected
}

private fun GlobalSettings.withHud(enabled: Boolean) = copy(hud = hud.copy(enabled = enabled))

private fun GameSettings.withOverlay(visible: Boolean) = copy(overlayMode = if (visible) OverlayMode.ALWAYS else OverlayMode.NEVER)

@Composable
private fun MenuItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    supporting: String? = null,
    onClick: () -> Unit,
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = supporting?.let { { Text(it) } },
        leadingContent = { Icon(icon, contentDescription = null) },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    )
}

@Composable
private fun InGameSettings(state: GameOverlayState, actions: GameOverlayActions) {
    var controllerOpen by remember { mutableStateOf(false) }
    Dialog(
        onDismissRequest = { if (controllerOpen) controllerOpen = false else state.settingsOpen = false },
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Surface(Modifier.fillMaxSize()) {
            if (controllerOpen) {
                ControllerScreen(
                    title = stringResource(R.string.controller_title_game, state.gameTitle),
                    mapping = state.game.mapping ?: state.global.mapping,
                    followsDefault = state.game.mapping == null,
                    perGame = true,
                    onChange = { mapping: ControllerMapping -> actions.onGameSettingsChange(state.game.copy(mapping = mapping)) },
                    onReset = { actions.onGameSettingsChange(state.game.copy(mapping = null)) },
                    onBack = { controllerOpen = false },
                )
            } else {
                GameSettingsScreen(
                    gameTitle = state.gameTitle,
                    global = state.global,
                    game = state.game,
                    layouts = state.layouts,
                    onChange = actions::onGameSettingsChange,
                    onOpenController = { controllerOpen = true },
                    onCustomizeLayout = { layout ->
                        state.settingsOpen = false
                        actions.onCustomizeLayout(layout)
                    },
                    onBack = { state.settingsOpen = false },
                )
            }
        }
    }
}

@Composable
private fun TextInputDialog(request: TextInputRequest, onResult: (String?) -> Unit) {
    val maxLength = request.maxLength.coerceAtLeast(1)
    // The current name is selected, so typing replaces it.
    var value by remember {
        val initial = request.current.take(maxLength)
        mutableStateOf(TextFieldValue(initial, TextRange(0, initial.length)))
    }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    AlertDialog(
        onDismissRequest = { onResult(null) },
        title = { Text(stringResource(R.string.text_input_title)) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { new ->
                    value = if (new.text.length <= maxLength) new else new.copy(text = new.text.take(maxLength), selection = TextRange(maxLength))
                },
                singleLine = true,
                supportingText = { Text("${value.text.length} / $maxLength") },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { if (value.text.isNotBlank()) onResult(value.text) }),
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
            )
        },
        confirmButton = {
            TextButton(onClick = { onResult(value.text) }, enabled = value.text.isNotBlank()) { Text(stringResource(R.string.dialog_ok)) }
        },
        dismissButton = { TextButton(onClick = { onResult(null) }) { Text(stringResource(R.string.text_input_use_grid)) } },
    )
}
