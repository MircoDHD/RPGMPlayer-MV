// SPDX-FileCopyrightText: 2026 MircoDHD
// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.mircodhd.mvplayer.ui

import android.app.Application
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
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
import io.github.mircodhd.mvplayer.settings.SettingsBackup
import io.github.mircodhd.mvplayer.settings.SettingsStore
import io.github.mircodhd.mvplayer.storage.DocumentNode
import org.json.JSONObject
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
    /** Ids of games whose folder is gone or no longer shared with the app. */
    var missing by mutableStateOf(emptySet<String>())
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
    /** Versions for the "What's new" box; null when it is closed. */
    var changelog by mutableStateOf<List<ChangelogEntry>?>(null)
        private set
    val versionName: String = run {
        @Suppress("DEPRECATION") // the flags overload needs API 33
        app.packageManager.getPackageInfo(app.packageName, 0).versionName.orEmpty()
    }

    init {
        games.forEach(::loadCover)
        refreshAll()
        showChangelogAfterUpdate()
    }

    /** After an update, shows what changed since the version the user saw last. */
    private fun showChangelogAfterUpdate() {
        val prefs = getApplication<Application>().getSharedPreferences(APP_STATE, Application.MODE_PRIVATE)
        val lastSeen = prefs.getString(KEY_LAST_VERSION, null)
        prefs.edit { putString(KEY_LAST_VERSION, versionName) }
        if (lastSeen == null || lastSeen == versionName) return // fresh install or no update
        val entries = Changelog.between(loadChangelog(getApplication()), lastSeen, versionName)
        if (entries.isNotEmpty()) changelog = entries
    }

    /** "What's new" from the settings: the notes of the installed version. */
    fun openChangelog() {
        val entries = loadChangelog(getApplication())
        changelog = entries.filter { it.version == versionName }.ifEmpty { entries.take(1) }
    }

    fun closeChangelog() {
        changelog = null
    }

    fun unlockTitleEdit() {
        updateGlobal(global.copy(titleEditUnlocked = true))
        DebugLog.i(TAG, "Library title editing unlocked")
    }

    fun setLibraryTitle(title: String) {
        updateGlobal(global.copy(libraryTitle = title.trim().take(GlobalSettings.MAX_TITLE_LENGTH).ifBlank { null }))
    }

    fun exportSettings(uri: Uri) {
        val app = getApplication<Application>()
        io.execute {
            val ok = runCatching {
                val contents = SettingsBackup.Contents(
                    global = settingsStore.global(),
                    layouts = layoutRepository.custom(),
                    games = library.all().mapNotNull { entry ->
                        settingsStore.game(entry.id).takeUnless { it.isEmpty }
                            ?.let { SettingsBackup.GameRef(entry.title, entry.location, it) }
                    },
                )
                val json = SettingsBackup.toJson(versionName, contents).toString(2)
                app.contentResolver.openOutputStream(uri, "wt")!!.use { it.write(json.toByteArray()) }
                DebugLog.i(TAG, "Exported settings: ${contents.layouts.size} layouts, ${contents.games.size} games")
            }.onFailure { DebugLog.e(TAG, "Settings export failed", it) }.isSuccess
            main.post { message = string(if (ok) R.string.backup_exported else R.string.backup_export_failed) }
        }
    }

    fun importSettings(uri: Uri) {
        val app = getApplication<Application>()
        io.execute {
            val result = runCatching {
                val text = app.contentResolver.openInputStream(uri)!!.use { it.reader().readText() }
                val contents = SettingsBackup.fromJson(JSONObject(text))
                contents.layouts.forEach(layoutRepository::saveImported)
                settingsStore.setGlobal(contents.global)
                val (matches, unmatched) = SettingsBackup.match(contents.games, library.all().map { Triple(it.id, it.title, it.location) })
                matches.forEach { (id, settings) -> settingsStore.setGame(id, settings) }
                DebugLog.i(TAG, "Imported settings: ${contents.layouts.size} layouts, ${matches.size} games, ${unmatched.size} unmatched")
                unmatched
            }.onFailure { DebugLog.e(TAG, "Settings import failed", it) }
            main.post {
                global = settingsStore.global()
                layouts = layoutRepository.all()
                gameSettings.keys.toList().forEach { gameSettings[it] = settingsStore.game(it) }
                message = result.fold(
                    onSuccess = { unmatched ->
                        if (unmatched.isEmpty()) string(R.string.backup_imported)
                        else string(R.string.backup_imported_unmatched, unmatched.joinToString())
                    },
                    onFailure = { string(R.string.backup_import_failed) },
                )
            }
        }
    }

    fun onResume() {
        games = library.all() // play time changes while a game runs
        // Settings and layouts may have changed in a game or in the layout editor.
        global = settingsStore.global()
        layouts = layoutRepository.all()
        gameSettings.keys.toList().forEach { gameSettings[it] = settingsStore.game(it) }
        val snapshot = games
        io.execute {
            val gone = snapshot.filter { DocumentNode.root(getApplication(), it.treeUri) == null }.map { it.id }.toSet()
            main.post { missing = gone }
        }
    }

    /** [uri] comes from the folder picker; the app keeps read/write access to it. */
    fun addGame(uri: Uri) {
        adding = true
        io.execute {
            val result = runCatching { addBlocking(uri) }
                .onFailure { DebugLog.e(TAG, "Adding $uri failed", it) }
                .getOrElse { string(R.string.lib_add_failed) }
            main.post {
                adding = false
                games = library.all()
                if (result != null) message = result
            }
        }
    }

    /** Returns a message for the user, or null on success. */
    private fun addBlocking(uri: Uri): String? {
        val app = getApplication<Application>()
        library.findByUri(uri.toString())?.let { return string(R.string.lib_already_added, it.title) }
        val dir = DocumentNode.root(app, uri) ?: return string(R.string.lib_folder_unresolvable)
        val match = GameInspector.detect(dir)
        val error = when (match.engine) {
            Engine.MV -> null
            Engine.MZ -> R.string.engine_mz_unsupported
            Engine.RGSS -> R.string.engine_rgss_unsupported
            Engine.RPG2K -> R.string.engine_2k_unsupported
            Engine.UNKNOWN -> R.string.engine_unknown
        }
        if (error != null) return string(error)

        app.contentResolver.takePersistableUriPermission(uri, DocumentNode.GRANT_FLAGS)
        val placeholder = library.add(uri.toString(), dir.name) ?: return null
        val cover = library.coverFile(placeholder.id)
        val (info, details) = GameScanner.scan(dir, cover) ?: return string(R.string.engine_unknown)
        library.update(placeholder.id) { it.copy(title = info.title, details = details) }
        main.post { loadCover(placeholder) }
        return null
    }

    fun removeGame(id: String) {
        library.find(id)?.let { entry ->
            // Hand the folder grant back; the files themselves stay untouched.
            runCatching { getApplication<Application>().contentResolver.releasePersistableUriPermission(entry.treeUri, DocumentNode.GRANT_FLAGS) }
        }
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
        val folder = DocumentNode.root(getApplication(), entry.treeUri)
        if (folder == null) {
            main.post { missing = missing + entry.id }
            return
        }
        val result = runCatching { GameScanner.scan(folder, library.coverFile(entry.id)) }
            .onFailure { DebugLog.w(TAG, "Scanning ${entry.location} failed", it) }
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

    fun setFrameRateCap(cap: Int) {
        updateGlobal(global.copy(frameRateCap = cap))
        DebugLog.i(TAG, "Frame rate cap set to $cap")
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

    private fun string(id: Int, vararg args: Any): String = getApplication<Application>().getString(id, *args)

    override fun onCleared() {
        io.shutdown()
    }

    private companion object {
        const val TAG = "Library"
        const val APP_STATE = "app_state"
        const val KEY_LAST_VERSION = "last_version"
    }
}
