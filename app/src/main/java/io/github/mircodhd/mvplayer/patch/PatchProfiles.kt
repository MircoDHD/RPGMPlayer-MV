// SPDX-FileCopyrightText: 2026 MircoDHD
// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.mircodhd.mvplayer.patch

import android.content.res.AssetManager
import io.github.mircodhd.mvplayer.debug.DebugLog
import io.github.mircodhd.mvplayer.game.GameInfo
import io.github.mircodhd.mvplayer.ui.Changelog
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest

/** Where a script runs; see `assets/patches/profiles.json`. */
enum class PatchPosition(val id: String) {
    BEFORE_ENGINE("beforeEngine"),
    BEFORE_PLUGINS("beforePlugins"),
    AFTER_PLUGINS("afterPlugins"),
}

/**
 * Script URLs injected into a game, in order. Built-in patches are served under
 * `/__mvplayer/`, the game's own patches from its `mvplayer-patches` folder.
 */
data class PatchSet(
    val beforeEngine: List<String>,
    val beforePlugins: List<String>,
    val afterPlugins: List<String>,
)

/** A script from the game's `mvplayer-patches` folder; [path] is relative to the web root. */
data class UserPatch(val path: String, val position: PatchPosition) {
    val file: String get() = path.substringAfter('/')
    val url: String get() = "/" + path.replace(" ", "%20")
}

/** The result of [PatchProfiles.select]. */
data class PatchSelection(
    val profileIds: List<String>,
    val userPatches: List<UserPatch>,
    val patches: PatchSet,
    val modScripts: List<UserPatch> = emptyList(),
)

/** A group of built-in patches applied when all of its [Match] rules hold. */
data class PatchProfile(
    val id: String,
    val match: Match,
    val scripts: Map<PatchPosition, List<String>>,
) {
    data class Match(
        val title: Regex? = null,
        val plugins: List<String> = emptyList(),
        val pluginSha256: Map<String, String> = emptyMap(),
        val engineMin: String? = null,
        val engineMax: String? = null,
        val usesGreenworks: Boolean? = null,
    )
}

/**
 * Built-in patches are described in `assets/patches/profiles.json`, so a fix for a known
 * plugin is a small file plus a profile matching that plugin's name or exact file hash.
 * [load] reads them once at app start; [select] picks the profiles for a game and adds the
 * game's own patches ([UserPatches]) and the scripts of its active mods.
 */
object PatchProfiles {
    private const val TAG = "Patches"
    private const val BUILT_IN_PREFIX = "/__mvplayer/"

    @Volatile private var profiles: List<PatchProfile> = emptyList()

    fun load(assets: AssetManager) {
        profiles = runCatching { parse(assets.open("patches/profiles.json").use { it.reader().readText() }) }
            .onFailure { DebugLog.e(TAG, "Cannot read patches/profiles.json", it) }
            .getOrDefault(emptyList())
    }

    fun parse(text: String): List<PatchProfile> {
        val array = JSONObject(text).getJSONArray("profiles")
        return (0 until array.length()).map { i ->
            val o = array.getJSONObject(i)
            val m = o.optJSONObject("match") ?: JSONObject()
            val hashes = m.optJSONObject("pluginSha256")
            PatchProfile(
                id = o.getString("id"),
                match = PatchProfile.Match(
                    title = m.optString("title").takeIf { it.isNotEmpty() }?.let { Regex(it, RegexOption.IGNORE_CASE) },
                    plugins = m.optJSONArray("plugins").strings(),
                    pluginSha256 = hashes?.keys()?.asSequence()?.associateWith { hashes.getString(it).lowercase() }.orEmpty(),
                    engineMin = m.optJSONObject("engine")?.optString("min")?.ifEmpty { null },
                    engineMax = m.optJSONObject("engine")?.optString("max")?.ifEmpty { null },
                    usesGreenworks = if (m.has("usesGreenworks")) m.getBoolean("usesGreenworks") else null,
                ),
                scripts = PatchPosition.entries.associateWith { o.optJSONArray(it.id).strings() },
            )
        }
    }

    fun select(info: GameInfo, from: List<PatchProfile> = profiles): PatchSelection {
        val hashes = HashMap<String, String?>()
        val active = from.filter { matches(it.match, info) { name -> hashes.getOrPut(name) { sha256(info, name) } } }
        val user = UserPatches.find(info.index)
        // Per position: built-in patches, then the game's own, then the active mods' scripts.
        fun urls(position: PatchPosition) =
            active.flatMap { p -> p.scripts[position].orEmpty().map { BUILT_IN_PREFIX + it } } +
                user.filter { it.position == position }.map { it.url } +
                info.modScripts.filter { it.position == position }.map { it.url }
        return PatchSelection(
            profileIds = active.map { it.id },
            userPatches = user,
            patches = PatchSet(urls(PatchPosition.BEFORE_ENGINE), urls(PatchPosition.BEFORE_PLUGINS), urls(PatchPosition.AFTER_PLUGINS)),
            modScripts = info.modScripts,
        )
    }

    internal fun matches(match: PatchProfile.Match, info: GameInfo, pluginHash: (String) -> String?): Boolean {
        if (match.title != null && !match.title.containsMatchIn(info.title)) return false
        if (!info.enabledPlugins.containsAll(match.plugins)) return false
        if (match.usesGreenworks != null && match.usesGreenworks != info.usesGreenworks) return false
        if (match.engineMin != null || match.engineMax != null) {
            val version = info.engineVersion ?: return false
            if (match.engineMin != null && Changelog.compareVersions(version, match.engineMin) < 0) return false
            if (match.engineMax != null && Changelog.compareVersions(version, match.engineMax) > 0) return false
        }
        return match.pluginSha256.all { (name, hash) -> name in info.enabledPlugins && pluginHash(name) == hash }
    }

    private fun sha256(info: GameInfo, plugin: String): String? = runCatching {
        val bytes = info.index.resolve("js/plugins/$plugin.js")?.readBytes() ?: return null
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    }.getOrNull()

    private fun JSONArray?.strings(): List<String> =
        if (this == null) emptyList() else (0 until length()).map { getString(it) }
}
