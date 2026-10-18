// SPDX-FileCopyrightText: 2026 MircoDHD
// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.mircodhd.mvplayer

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
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
import io.github.mircodhd.mvplayer.ui.Format
import io.github.mircodhd.mvplayer.ui.GameInfoSheet
import io.github.mircodhd.mvplayer.ui.LibraryScreen
import io.github.mircodhd.mvplayer.ui.LibraryViewModel
import io.github.mircodhd.mvplayer.ui.SettingsScreen
import io.github.mircodhd.mvplayer.ui.settings.ControllerScreen
import io.github.mircodhd.mvplayer.ui.settings.GameSettingsScreen
import io.github.mircodhd.mvplayer.ui.settings.OverlaySettingsScreen
import io.github.mircodhd.mvplayer.ui.theme.MvPlayerTheme

/** Start screen: the game library and the settings. */
class MainActivity : ComponentActivity() {
    private val viewModel: LibraryViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        @Suppress("DEPRECATION") // the flags overload needs API 33
        val versionName = packageManager.getPackageInfo(packageName, 0).versionName.orEmpty()
        setContent {
            MvPlayerTheme {
                PlayerHome(
                    viewModel = viewModel,
                    versionName = versionName,
                    onPlay = ::launchGame,
                    onRequestAccess = ::requestAllFilesAccess,
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
        DebugLog.i(TAG, "Launching '${game.title}' from ${game.path}")
        startActivity(
            Intent(this, GameActivity::class.java)
                .putExtra(GameActivity.EXTRA_GAME_DIR, game.path)
                .putExtra(GameActivity.EXTRA_GAME_ID, game.id),
        )
    }

    private fun customizeLayout(layout: GamepadLayout, gameId: String?) {
        startActivity(
            Intent(this, LayoutEditorActivity::class.java)
                .putExtra(LayoutEditorActivity.EXTRA_LAYOUT_ID, layout.id)
                .putExtra(LayoutEditorActivity.EXTRA_GAME_ID, gameId),
        )
    }

    private fun requestAllFilesAccess() {
        val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, "package:$packageName".toUri())
        runCatching { startActivity(intent) }
            .onFailure { startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)) }
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
    const val GAME = "game/"
    const val GAME_CONTROLLER = "game-controller/"

    fun parent(route: String): String = when {
        route.startsWith(GAME_CONTROLLER) -> GAME + route.removePrefix(GAME_CONTROLLER)
        route == CONTROLLER || route == OVERLAY -> SETTINGS
        else -> LIBRARY
    }
}

@Composable
private fun PlayerHome(
    viewModel: LibraryViewModel,
    versionName: String,
    onPlay: (GameEntry) -> Unit,
    onRequestAccess: () -> Unit,
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

    val folderUnresolvable = stringResource(R.string.lib_folder_unresolvable)
    val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val dir = StoragePaths.treeUriToFile(context, uri)
        DebugLog.i("Main", "Picked $uri -> $dir")
        if (dir == null || !dir.isDirectory) {
            viewModel.showMessage(folderUnresolvable)
        } else {
            viewModel.addGame(dir)
        }
    }

    if (route != Routes.LIBRARY) BackHandler(onBack = back)

    when {
        route == Routes.SETTINGS -> SettingsScreen(
            versionName = versionName,
            global = viewModel.global,
            logToFile = viewModel.logToFile,
            logLocation = if (viewModel.logToFile) DebugLog.logDirectory()?.let { Format.path(it.path) } else null,
            snackbarHostState = snackbarHostState,
            onBack = back,
            onOpenController = { route = Routes.CONTROLLER },
            onOpenOverlay = { route = Routes.OVERLAY },
            onFrameRateCapChange = viewModel::setFrameRateCapEnabled,
            onLogToFileChange = viewModel::setLogToFileEnabled,
            onShareLogs = { viewModel.exportLogs(onStartIntent) },
            onOpenUrl = onOpenUrl,
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
                    onCustomizeLayout = { onCustomizeLayout(it, id) },
                    onBack = back,
                )
            }
        }
        else -> LibraryScreen(
            games = viewModel.games,
            covers = viewModel.covers,
            missing = viewModel.missing,
            hasAccess = viewModel.hasAccess,
            adding = viewModel.adding,
            snackbarHostState = snackbarHostState,
            onAddGame = { pickFolder.launch(null) },
            onPlay = onPlay,
            onInfo = { game ->
                infoGameId = game.id
                viewModel.refresh(game.id)
            },
            onOpenSettings = { route = Routes.SETTINGS },
            onRequestAccess = onRequestAccess,
        )
    }

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
