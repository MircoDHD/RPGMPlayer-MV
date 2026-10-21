// SPDX-FileCopyrightText: 2026 MircoDHD
// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.mircodhd.mvplayer

import android.content.Context
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.net.toUri
import io.github.mircodhd.mvplayer.debug.DebugLog
import io.github.mircodhd.mvplayer.input.ControllerMapping
import io.github.mircodhd.mvplayer.library.GameEntry
import io.github.mircodhd.mvplayer.overlay.GamepadLayout
import io.github.mircodhd.mvplayer.ui.ChangelogDialog
import io.github.mircodhd.mvplayer.ui.Format
import io.github.mircodhd.mvplayer.ui.GameInfoSheet
import io.github.mircodhd.mvplayer.ui.LibraryScreen
import io.github.mircodhd.mvplayer.ui.LibraryViewModel
import io.github.mircodhd.mvplayer.ui.SettingsScreen
import io.github.mircodhd.mvplayer.ui.settings.ControllerScreen
import io.github.mircodhd.mvplayer.ui.settings.GameSettingsScreen
import io.github.mircodhd.mvplayer.ui.settings.HudSettingsScreen
import io.github.mircodhd.mvplayer.ui.settings.OverlaySettingsScreen
import io.github.mircodhd.mvplayer.ui.settings.PictureSettingsScreen
import io.github.mircodhd.mvplayer.ui.settings.FontSettingsScreen
import io.github.mircodhd.mvplayer.settings.AppLanguage
import io.github.mircodhd.mvplayer.settings.FontSettings
import io.github.mircodhd.mvplayer.settings.PictureOverrides
import io.github.mircodhd.mvplayer.settings.Renderer
import io.github.mircodhd.mvplayer.ui.theme.MvPlayerTheme

/** Start screen: the game library and the settings. */
class MainActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLanguage.wrap(newBase))
    }

    private val viewModel: LibraryViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            MvPlayerTheme(appFont = viewModel.global.appFont) {
                PlayerHome(
                    viewModel = viewModel,
                    onPlay = ::launchGame,
                    onOpenUrl = ::openUrl,
                    onStartIntent = ::startActivity,
                    onCustomizeLayout = ::customizeLayout,
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.onResume()
    }

    private fun launchGame(game: GameEntry) {
        DebugLog.i(TAG, "Launching '${game.title}' from ${game.location}")
        startActivity(Intent(this, GameActivity::class.java).putExtra(GameActivity.EXTRA_GAME_ID, game.id))
    }

    private fun customizeLayout(layout: GamepadLayout, gameId: String?) {
        startActivity(
            Intent(this, LayoutEditorActivity::class.java)
                .putExtra(LayoutEditorActivity.EXTRA_LAYOUT_ID, layout.id)
                .putExtra(LayoutEditorActivity.EXTRA_GAME_ID, gameId),
        )
    }

    private fun openUrl(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
        } catch (e: ActivityNotFoundException) {
            viewModel.showMessage(getString(R.string.set_no_browser))
        }
    }

    private companion object {
        const val TAG = "Main"
    }
}

/** Navigation routes; "game/<id>" and "game-controller/<id>" carry a library id. */
private object Routes {
    const val LIBRARY = "library"
    const val SETTINGS = "settings"
    const val CONTROLLER = "controller"
    const val OVERLAY = "overlay"
    const val HUD = "hud"
    const val PICTURE = "picture"
    const val FONT = "font"
    const val APP_FONT = "app-font"
    const val GAME = "game/"
    const val GAME_CONTROLLER = "game-controller/"
    const val GAME_PICTURE = "game-picture/"
    const val GAME_FONT = "game-font/"

    fun parent(route: String): String = when {
        route.startsWith(GAME_CONTROLLER) -> GAME + route.removePrefix(GAME_CONTROLLER)
        route.startsWith(GAME_PICTURE) -> GAME + route.removePrefix(GAME_PICTURE)
        route.startsWith(GAME_FONT) -> GAME + route.removePrefix(GAME_FONT)
        route == CONTROLLER || route == OVERLAY || route == HUD || route == PICTURE || route == FONT || route == APP_FONT -> SETTINGS
        else -> LIBRARY
    }
}

@Composable
private fun PlayerHome(
    viewModel: LibraryViewModel,
    onPlay: (GameEntry) -> Unit,
    onOpenUrl: (String) -> Unit,
    onStartIntent: (Intent) -> Unit,
    onCustomizeLayout: (GamepadLayout, String?) -> Unit,
) {
    val context = LocalContext.current
    var route by rememberSaveable { mutableStateOf(Routes.LIBRARY) }
    var infoGameId by rememberSaveable { mutableStateOf<String?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val back = { route = Routes.parent(route) }

    val message = viewModel.message
    LaunchedEffect(message) {
        if (message != null) {
            snackbarHostState.showSnackbar(message)
            viewModel.messageShown()
        }
    }

    val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        DebugLog.i("Main", "Picked $uri")
        viewModel.addGame(uri)
    }

    val exportSettings = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) viewModel.exportSettings(uri)
    }
    val importSettings = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.importSettings(uri)
    }

    if (route != Routes.LIBRARY) BackHandler(onBack = back)

    when {
        route == Routes.SETTINGS -> SettingsScreen(
            versionName = viewModel.versionName,
            global = viewModel.global,
            logToFile = viewModel.logToFile,
            logLocation = if (viewModel.logToFile) DebugLog.logDirectory()?.let { Format.path(it.path) } else null,
            snackbarHostState = snackbarHostState,
            onBack = back,
            onOpenController = { route = Routes.CONTROLLER },
            onOpenOverlay = { route = Routes.OVERLAY },
            onFrameRateCapChange = viewModel::setFrameRateCap,
            onRendererChange = { viewModel.updateGlobal(viewModel.global.copy(renderer = it)) },
            onOpenHud = { route = Routes.HUD },
            onOpenPicture = { route = Routes.PICTURE },
            onOpenFont = { route = Routes.FONT },
            onOpenAppFont = { route = Routes.APP_FONT },
            onLanguageChange = { tag -> (context as? android.app.Activity)?.let { AppLanguage.set(it, tag) } },
            onGlobalChange = viewModel::updateGlobal,
            onLogToFileChange = viewModel::setLogToFileEnabled,
            onShareLogs = { viewModel.exportLogs(onStartIntent) },
            onOpenUrl = onOpenUrl,
            onOpenChangelog = viewModel::openChangelog,
            onExportSettings = { exportSettings.launch("rpgm-player-settings_${Format.fileStamp()}.json") },
            onImportSettings = { importSettings.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) },
            onUnlockTitle = viewModel::unlockTitleEdit,
            onLibraryTitleChange = viewModel::setLibraryTitle,
        )
        route == Routes.CONTROLLER -> ControllerScreen(
            title = stringResource(R.string.controller_title),
            mapping = viewModel.global.mapping,
            followsDefault = false,
            perGame = false,
            onChange = { viewModel.updateGlobal(viewModel.global.copy(mapping = it)) },
            onReset = { viewModel.updateGlobal(viewModel.global.copy(mapping = ControllerMapping.DEFAULT)) },
            onBack = back,
        )
        route == Routes.HUD -> HudSettingsScreen(
            settings = viewModel.global.hud,
            onChange = { viewModel.updateGlobal(viewModel.global.copy(hud = it)) },
            onBack = back,
        )
        route == Routes.PICTURE -> PictureSettingsScreen(
            title = stringResource(R.string.picture_title),
            global = viewModel.global.picture,
            overrides = null,
            canvas = viewModel.global.renderer == Renderer.CANVAS,
            onChange = { viewModel.updateGlobal(viewModel.global.copy(picture = it)) },
            onBack = back,
        )
        route == Routes.FONT -> FontSettingsScreen(
            title = stringResource(R.string.font_title),
            global = viewModel.global.font,
            perGame = false,
            overrideId = viewModel.global.font.id,
            overrideScale = viewModel.global.font.scale,
            onChange = { id, scale ->
                viewModel.updateGlobal(viewModel.global.copy(font = FontSettings(id ?: FontSettings.GAME_FONT, scale ?: 1f)))
            },
            onBack = back,
        )
        route == Routes.APP_FONT -> FontSettingsScreen(
            title = stringResource(R.string.app_font_title),
            global = FontSettings(viewModel.global.appFont),
            perGame = false,
            overrideId = viewModel.global.appFont,
            overrideScale = null,
            onChange = { id, _ -> viewModel.updateGlobal(viewModel.global.copy(appFont = id ?: FontSettings.SYSTEM_FONT)) },
            onBack = back,
            forApp = true,
        )
        route.startsWith(Routes.GAME_FONT) -> {
            val id = route.removePrefix(Routes.GAME_FONT)
            val game = viewModel.games.firstOrNull { it.id == id }
            val settings = viewModel.gameSettings(id)
            FontSettingsScreen(
                title = stringResource(R.string.font_title_game, game?.title.orEmpty()),
                global = viewModel.global.font,
                perGame = true,
                overrideId = settings.fontId,
                overrideScale = settings.fontScale,
                onChange = { fontId, scale -> viewModel.setGameSettings(id, viewModel.gameSettings(id).copy(fontId = fontId, fontScale = scale)) },
                onBack = back,
            )
        }
        route.startsWith(Routes.GAME_PICTURE) -> {
            val id = route.removePrefix(Routes.GAME_PICTURE)
            val game = viewModel.games.firstOrNull { it.id == id }
            val settings = viewModel.gameSettings(id)
            val global = viewModel.global
            PictureSettingsScreen(
                title = stringResource(R.string.picture_title_game, game?.title.orEmpty()),
                global = global.picture,
                overrides = settings.picture,
                canvas = (settings.renderer ?: global.renderer) == Renderer.CANVAS,
                onChange = {
                    viewModel.setGameSettings(id, viewModel.gameSettings(id).copy(picture = PictureOverrides.of(it, global.picture)))
                },
                onBack = back,
            )
        }
        route == Routes.OVERLAY -> OverlaySettingsScreen(
            settings = viewModel.global.overlay,
            layouts = viewModel.layouts,
            onChange = { viewModel.updateGlobal(viewModel.global.copy(overlay = it)) },
            onCustomize = { onCustomizeLayout(it, null) },
            onDeleteLayout = viewModel::deleteLayout,
            onBack = back,
        )
        route.startsWith(Routes.GAME_CONTROLLER) -> {
            val id = route.removePrefix(Routes.GAME_CONTROLLER)
            val game = viewModel.games.firstOrNull { it.id == id }
            val settings = viewModel.gameSettings(id)
            ControllerScreen(
                title = stringResource(R.string.controller_title_game, game?.title.orEmpty()),
                mapping = settings.mapping ?: viewModel.global.mapping,
                followsDefault = settings.mapping == null,
                perGame = true,
                onChange = { viewModel.setGameSettings(id, viewModel.gameSettings(id).copy(mapping = it)) },
                onReset = { viewModel.setGameSettings(id, viewModel.gameSettings(id).copy(mapping = null)) },
                onBack = back,
            )
        }
        route.startsWith(Routes.GAME) -> {
            val id = route.removePrefix(Routes.GAME)
            val game = viewModel.games.firstOrNull { it.id == id }
            if (game == null) {
                LaunchedEffect(id) { route = Routes.LIBRARY }
            } else {
                GameSettingsScreen(
                    gameTitle = game.title,
                    global = viewModel.global,
                    game = viewModel.gameSettings(id),
                    layouts = viewModel.layouts,
                    onChange = { viewModel.setGameSettings(id, it) },
                    onOpenController = { route = Routes.GAME_CONTROLLER + id },
                    onOpenPicture = { route = Routes.GAME_PICTURE + id },
                    onOpenFont = { route = Routes.GAME_FONT + id },
                    onCustomizeLayout = { onCustomizeLayout(it, id) },
                    onBack = back,
                )
            }
        }
        else -> LibraryScreen(
            games = viewModel.games,
            covers = viewModel.covers,
            missing = viewModel.missing,
            title = viewModel.global.libraryTitle ?: stringResource(R.string.app_name_short),
            adding = viewModel.adding,
            snackbarHostState = snackbarHostState,
            onAddGame = { pickFolder.launch(null) },
            onPlay = onPlay,
            onInfo = { game ->
                infoGameId = game.id
                viewModel.refresh(game.id)
            },
            onOpenSettings = { route = Routes.SETTINGS },
        )
    }

    viewModel.changelog?.let { entries -> ChangelogDialog(entries, onDismiss = viewModel::closeChangelog) }

    val infoGame = infoGameId?.let { id -> viewModel.games.firstOrNull { it.id == id } }
    if (infoGame != null && route == Routes.LIBRARY) {
        GameInfoSheet(
            game = infoGame,
            cover = viewModel.covers[infoGame.id],
            folderMissing = infoGame.id in viewModel.missing,
            onDismiss = { infoGameId = null },
            onPlay = {
                infoGameId = null
                onPlay(infoGame)
            },
            onRemove = {
                infoGameId = null
                viewModel.removeGame(infoGame.id)
            },
            onOpenSettings = {
                infoGameId = null
                route = Routes.GAME + infoGame.id
            },
        )
    }
}
