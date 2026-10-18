// SPDX-FileCopyrightText: 2026 MircoDHD
// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.mircodhd.mvplayer.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Environment
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import io.github.mircodhd.mvplayer.R
import io.github.mircodhd.mvplayer.debug.DebugLog
import io.github.mircodhd.mvplayer.debug.LogExporter
import io.github.mircodhd.mvplayer.game.Engine
import io.github.mircodhd.mvplayer.game.GameInspector
import io.github.mircodhd.mvplayer.library.GameEntry
import io.github.mircodhd.mvplayer.library.GameLibrary
import io.github.mircodhd.mvplayer.library.GameScanner
import io.github.mircodhd.mvplayer.overlay.GamepadLayout
import io.github.mircodhd.mvplayer.overlay.LayoutRepository
import io.github.mircodhd.mvplayer.settings.GameSettings
import io.github.mircodhd.mvplayer.settings.GlobalSettings
import io.github.mircodhd.mvplayer.settings.OverlaySettings
import io.github.mircodhd.mvplayer.settings.SettingsStore
import java.io.File
import java.util.concurrent.Executors

class LibraryViewModel(app: Application) : AndroidViewModel(app) {
    private val library = GameLibrary.get(app)
    private val settingsStore = SettingsStore.get(app)
    private val layoutRepository = LayoutRepository(app)
    private val io = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    var games by mutableStateOf(library.all())
        private set
    val covers = mutableStateMapOf<String, ImageBitmap>()
    /** Ids of games whose folder no longer exists. */
    var missing by mutableStateOf(emptySet<String>())
        private set
    var hasAccess by mutableStateOf(Environment.isExternalStorageManager())
        private set
    var adding by mutableStateOf(false)
        private set
    var global by mutableStateOf(settingsStore.global())
        private set
    var layouts by mutableStateOf(layoutRepository.all())
        private set
    /** Per-game overrides that the UI has loaded, by game id. */
    val gameSettings = mutableStateMapOf<String, GameSettings>()
    var logToFile by mutableStateOf(DebugLog.settings.logToFile)
        private set
    /** One-off message for the snackbar; cleared by [messageShown]. */
    var message by mutableStateOf<String?>(null)
        private set

    init {
        migrateLegacyLastGame()
        games = library.all()
        games.forEach(::loadCover)
        refreshAll()
    }

    fun onResume() {
        hasAccess = Environment.isExternalStorageManager()
        if (hasAccess) DebugLog.relocateIfPossible()
        games = library.all() // play time changes while a game runs
        // Settings and layouts may have changed in a game or in the layout editor.
        global = settingsStore.global()
        layouts = layoutRepository.all()
        gameSettings.keys.toList().forEach { gameSettings[it] = settingsStore.game(it) }
        missing = games.filterNot { it.folder.isDirectory }.map { it.id }.toSet()
    }

    fun addGame(dir: File) {
        adding = true
        io.execute {
            val result = runCatching { addBlocking(dir) }
                .onFailure { DebugLog.e(TAG, "Adding $dir failed", it) }
                .getOrElse { string(R.string.lib_add_failed) }
            main.post {
                adding = false
                games = library.all()
                if (result != null) message = result
            }
        }
    }

    /** Returns a message for the user, or null on success. */
    private fun addBlocking(dir: File): String? {
        val match = GameInspector.detect(dir)
        val error = when (match.engine) {
            Engine.MV -> null
            Engine.MZ -> R.string.engine_mz_unsupported
            Engine.RGSS -> R.string.engine_rgss_unsupported
            Engine.RPG2K -> R.string.engine_2k_unsupported
            Engine.UNKNOWN -> R.string.engine_unknown
        }
        if (error != null) return string(error)
        library.findByPath(dir.absolutePath)?.let { return string(R.string.lib_already_added, it.title) }

        val placeholder = library.add(dir.absolutePath, dir.name) ?: return null
        val cover = library.coverFile(placeholder.id)
        val (info, details) = GameScanner.scan(dir, cover) ?: return string(R.string.engine_unknown)
        library.update(placeholder.id) { it.copy(title = info.title, details = details) }
        main.post { loadCover(placeholder) }
        return null
    }

    fun removeGame(id: String) {
        library.remove(id)
        settingsStore.removeGame(id)
        gameSettings.remove(id)
        covers.remove(id)
        games = library.all()
    }

    /** Re-reads details and the cover of one game, e.g. when its info sheet opens. */
    fun refresh(id: String) {
        val entry = library.find(id) ?: return
        io.execute { scanBlocking(entry) }
    }

    private fun refreshAll() {
        io.execute { library.all().forEach(::scanBlocking) }
    }

    private fun scanBlocking(entry: GameEntry) {
        if (!entry.folder.isDirectory) {
            main.post { missing = missing + entry.id }
            return
        }
        val result = runCatching { GameScanner.scan(entry.folder, library.coverFile(entry.id)) }
            .onFailure { DebugLog.w(TAG, "Scanning ${entry.path} failed", it) }
            .getOrNull() ?: return
        library.update(entry.id) { it.copy(details = result.second) }
        main.post {
            missing = missing - entry.id
            games = library.all()
            if (entry.id !in covers) loadCover(entry)
        }
    }

    private fun loadCover(entry: GameEntry) {
        io.execute {
            val file = library.coverFile(entry.id)
            val bitmap = file.takeIf { it.isFile }?.let { BitmapFactory.decodeFile(it.path) } ?: return@execute
            main.post { covers[entry.id] = bitmap.asImageBitmap() }
        }
    }

    fun updateGlobal(settings: GlobalSettings) {
        settingsStore.setGlobal(settings)
        global = settings
    }

    fun setFrameRateCapEnabled(enabled: Boolean) {
        updateGlobal(global.copy(frameRateCap = if (enabled) GlobalSettings.DEFAULT_FRAME_RATE_CAP else 0))
        DebugLog.i(TAG, "Frame rate cap set to ${global.frameRateCap}")
    }

    fun gameSettings(id: String): GameSettings = gameSettings.getOrPut(id) { settingsStore.game(id) }

    fun setGameSettings(id: String, settings: GameSettings) {
        settingsStore.setGame(id, settings)
        gameSettings[id] = settings
    }

    /** Deletes a custom layout; settings that used it fall back to the default preset. */
    fun deleteLayout(layout: GamepadLayout) {
        layoutRepository.deleteCustom(layout.id)
        if (global.overlay.layoutId == layout.id) {
            updateGlobal(global.copy(overlay = global.overlay.copy(layoutId = OverlaySettings.DEFAULT_LAYOUT)))
        }
        for (entry in library.all()) {
            val game = settingsStore.game(entry.id)
            if (game.overlayLayoutId == layout.id) setGameSettings(entry.id, game.copy(overlayLayoutId = null))
        }
        layouts = layoutRepository.all()
    }

    fun setLogToFileEnabled(enabled: Boolean) {
        DebugLog.setLogToFile(enabled)
        logToFile = enabled
    }

    fun exportLogs(onReady: (Intent) -> Unit) {
        io.execute {
            val intent = runCatching { LogExporter.createShareIntent(getApplication(), string(R.string.set_share_logs_chooser)) }
                .onFailure { DebugLog.e(TAG, "Log export failed", it) }
                .getOrNull()
            main.post { if (intent != null) onReady(intent) else message = string(R.string.set_no_logs) }
        }
    }

    fun showMessage(text: String) {
        message = text
    }

    fun messageShown() {
        message = null
    }

    /** Version 0.1.x remembered only the last opened folder; turn it into a library entry. */
    private fun migrateLegacyLastGame() {
        val prefs = getApplication<Application>().getSharedPreferences("player", Context.MODE_PRIVATE)
        val path = prefs.getString("last_game_dir", null) ?: return
        val title = prefs.getString("last_game_title", null) ?: File(path).name
        if (File(path).isDirectory) library.add(path, title)
        prefs.edit { clear() }
        DebugLog.i(TAG, "Migrated last opened game $path into the library")
    }

    private fun string(id: Int, vararg args: Any): String = getApplication<Application>().getString(id, *args)

    override fun onCleared() {
        io.shutdown()
    }

    private companion object {
        const val TAG = "Library"
    }
}
