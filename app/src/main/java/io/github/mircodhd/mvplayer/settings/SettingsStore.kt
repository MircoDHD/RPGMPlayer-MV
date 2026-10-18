// SPDX-FileCopyrightText: 2026 MircoDHD
// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.mircodhd.mvplayer.settings

import android.annotation.SuppressLint
import android.content.Context
import androidx.core.content.edit
import io.github.mircodhd.mvplayer.debug.DebugLog
import org.json.JSONObject
import java.io.File
import java.io.IOException

/**
 * Global settings in `filesDir/settings.json`, per-game overrides in
 * `filesDir/game-settings/<gameId>.json`. Synchronized; returns immutable values.
 */
class SettingsStore private constructor(private val context: Context) {
    private val globalFile = File(context.filesDir, "settings.json")
    private val gameDir = File(context.filesDir, "game-settings")
    private var global: GlobalSettings = loadGlobal()
    private val games = HashMap<String, GameSettings>()

    @Synchronized
    fun global(): GlobalSettings = global

    @Synchronized
    fun setGlobal(settings: GlobalSettings) {
        global = settings
        write(globalFile, SettingsJson.globalToJson(settings))
    }

    @Synchronized
    fun game(gameId: String): GameSettings = games.getOrPut(gameId) {
        val file = gameFile(gameId)
        if (!file.isFile) return@getOrPut GameSettings()
        runCatching { SettingsJson.gameFromJson(JSONObject(file.readText())) }
            .onFailure { DebugLog.e(TAG, "Could not read $file", it) }
            .getOrDefault(GameSettings())
    }

    @Synchronized
    fun setGame(gameId: String, settings: GameSettings) {
        games[gameId] = settings
        if (settings.isEmpty) gameFile(gameId).delete() else write(gameFile(gameId), SettingsJson.gameToJson(settings))
    }

    @Synchronized
    fun removeGame(gameId: String) {
        games.remove(gameId)
        gameFile(gameId).delete()
    }

    fun effective(gameId: String?): EffectiveSettings =
        SettingsResolver.resolve(global(), gameId?.let(::game) ?: GameSettings())

    private fun gameFile(gameId: String) = File(gameDir, "${gameId.filter { it.isLetterOrDigit() || it == '-' }}.json")

    private fun loadGlobal(): GlobalSettings {
        if (globalFile.isFile) {
            return runCatching { SettingsJson.globalFromJson(JSONObject(globalFile.readText())) }
                .onFailure { DebugLog.e(TAG, "Could not read $globalFile, using defaults", it) }
                .getOrDefault(GlobalSettings())
        }
        // 0.1.4–0.2.0 kept only the frame cap, in SharedPreferences.
        val legacy = context.getSharedPreferences("player_settings", Context.MODE_PRIVATE)
        val settings = GlobalSettings(frameRateCap = legacy.getInt("frame_rate_cap", GlobalSettings.DEFAULT_FRAME_RATE_CAP))
        write(globalFile, SettingsJson.globalToJson(settings))
        legacy.edit { clear() }
        return settings
    }

    private fun write(file: File, json: JSONObject) {
        try {
            file.parentFile?.mkdirs()
            val tmp = File(file.parentFile, "${file.name}.tmp")
            tmp.writeText(json.toString(2))
            if (!tmp.renameTo(file)) throw IOException("rename failed")
        } catch (e: IOException) {
            DebugLog.e(TAG, "Could not write $file", e)
        }
    }

    companion object {
        private const val TAG = "Settings"

        // Holds the application context only, which lives as long as the process.
        @SuppressLint("StaticFieldLeak")
        @Volatile private var instance: SettingsStore? = null

        fun get(context: Context): SettingsStore =
            instance ?: synchronized(this) { instance ?: SettingsStore(context.applicationContext).also { instance = it } }
    }
}
