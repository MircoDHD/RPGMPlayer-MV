// SPDX-FileCopyrightText: 2026 MircoDHD
// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.mircodhd.mvplayer.settings

import io.github.mircodhd.mvplayer.overlay.GamepadLayout
import io.github.mircodhd.mvplayer.overlay.LayoutJson
import org.json.JSONArray
import org.json.JSONObject

/**
 * Settings export file: global settings, custom on-screen layouts and per-game settings.
 * Games are identified by their folder location and title, because library ids and folder
 * grants do not survive a reinstall; the library itself is not exported.
 */
object SettingsBackup {
    const val FORMAT = "rpgm-player-settings"
    private const val VERSION = 1

    /** A game as written to the export file. */
    data class GameRef(val title: String, val location: String, val settings: GameSettings)

    data class Contents(val global: GlobalSettings, val layouts: List<GamepadLayout>, val games: List<GameRef>)

    fun toJson(appVersion: String, contents: Contents): JSONObject = JSONObject()
        .put("format", FORMAT)
        .put("version", VERSION)
        .put("appVersion", appVersion)
        .put("global", SettingsJson.globalToJson(contents.global))
        .put("layouts", JSONArray(contents.layouts.map(LayoutJson::toJson)))
        .put("games", JSONArray(contents.games.map { game ->
            JSONObject()
                .put("title", game.title)
                .put("location", game.location)
                .put("settings", SettingsJson.gameToJson(game.settings))
        }))

    /** Throws [IllegalArgumentException] for files that are not settings exports. */
    fun fromJson(json: JSONObject): Contents {
        require(json.optString("format") == FORMAT) { "Not an RPGM Player settings file" }
        val layouts = json.optJSONArray("layouts") ?: JSONArray()
        val games = json.optJSONArray("games") ?: JSONArray()
        return Contents(
            global = SettingsJson.globalFromJson(json.optJSONObject("global") ?: JSONObject()),
            layouts = (0 until layouts.length()).mapNotNull { i ->
                runCatching { LayoutJson.parse(layouts.getJSONObject(i), builtIn = false) }.getOrNull()
                    ?.takeIf { it.id.startsWith("custom-") }
            },
            games = (0 until games.length()).mapNotNull { i ->
                val game = games.optJSONObject(i) ?: return@mapNotNull null
                GameRef(
                    title = game.optString("title"),
                    location = game.optString("location"),
                    settings = SettingsJson.gameFromJson(game.optJSONObject("settings") ?: JSONObject()),
                )
            },
        )
    }

    /**
     * Pairs imported game settings with library games ([id] to title and location): the same
     * folder location first, otherwise the same title. Returns matches and unmatched titles.
     */
    fun match(imported: List<GameRef>, library: List<Triple<String, String, String>>): Pair<Map<String, GameSettings>, List<String>> {
        val matches = HashMap<String, GameSettings>()
        val unmatched = ArrayList<String>()
        for (ref in imported) {
            val target = library.firstOrNull { (id, _, location) -> id !in matches && location == ref.location }
                ?: library.firstOrNull { (id, title, _) -> id !in matches && title.equals(ref.title, ignoreCase = true) }
            if (target != null) matches[target.first] = ref.settings else unmatched += ref.title
        }
        return matches to unmatched
    }
}
