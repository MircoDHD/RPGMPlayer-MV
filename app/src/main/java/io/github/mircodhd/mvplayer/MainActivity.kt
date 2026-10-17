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
import io.github.mircodhd.mvplayer.library.GameEntry
import io.github.mircodhd.mvplayer.ui.Format
import io.github.mircodhd.mvplayer.ui.GameInfoSheet
import io.github.mircodhd.mvplayer.ui.LibraryScreen
import io.github.mircodhd.mvplayer.ui.LibraryViewModel
import io.github.mircodhd.mvplayer.ui.SettingsScreen
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
        startActivity(Intent(this, GameActivity::class.java).putExtra(GameActivity.EXTRA_GAME_DIR, game.path))
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

private enum class Screen { Library, Settings }

@Composable
private fun PlayerHome(
    viewModel: LibraryViewModel,
    versionName: String,
    onPlay: (GameEntry) -> Unit,
    onRequestAccess: () -> Unit,
    onOpenUrl: (String) -> Unit,
    onStartIntent: (Intent) -> Unit,
) {
    val context = LocalContext.current
    var screen by rememberSaveable { mutableStateOf(Screen.Library) }
    var infoGameId by rememberSaveable { mutableStateOf<String?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }

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

    when (screen) {
        Screen.Library -> LibraryScreen(
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
            onOpenSettings = { screen = Screen.Settings },
            onRequestAccess = onRequestAccess,
        )
        Screen.Settings -> {
            BackHandler { screen = Screen.Library }
            SettingsScreen(
                versionName = versionName,
                frameRateCapEnabled = viewModel.playerSettings.frameRateCap > 0,
                logToFile = viewModel.logToFile,
                logLocation = if (viewModel.logToFile) DebugLog.logDirectory()?.let { Format.path(it.path) } else null,
                snackbarHostState = snackbarHostState,
                onBack = { screen = Screen.Library },
                onFrameRateCapChange = viewModel::setFrameRateCapEnabled,
                onLogToFileChange = viewModel::setLogToFileEnabled,
                onShareLogs = { viewModel.exportLogs(onStartIntent) },
                onOpenUrl = onOpenUrl,
            )
        }
    }

    val infoGame = infoGameId?.let { id -> viewModel.games.firstOrNull { it.id == id } }
    if (infoGame != null && screen == Screen.Library) {
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
        )
    }
}
