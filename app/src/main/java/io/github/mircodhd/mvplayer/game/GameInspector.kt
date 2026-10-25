// SPDX-FileCopyrightText: 2026 MircoDHD
// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.mircodhd.mvplayer.game

import android.os.SystemClock
import io.github.mircodhd.mvplayer.debug.DebugLog
import io.github.mircodhd.mvplayer.patch.UserPatch
import io.github.mircodhd.mvplayer.storage.GameNode
import org.json.JSONArray
import org.json.JSONObject

enum class Engine { MV, MZ, RGSS, RPG2K, UNKNOWN }

/** Result of a cheap check that only looks at a few marker files. */
data class EngineMatch(val engine: Engine, val webRoot: GameNode?)

/** Everything the player needs to know about an MV game before it starts. */
data class GameInfo(
    val webRoot: GameNode,
    /** Name of the picked game folder, used when System.json has no title. */
    val folderName: String,
    val title: String,
    val engineVersion: String?,
    val enabledPlugins: List<String>,
    /** Entries in js/plugins.js, enabled or not (separator lines included, as in the editor). */
    val totalPlugins: Int,
    val pixiVersion: String?,
    /** Title screen images from System.json, relative to the web root without extension. */
    val titleImages: List<String>,
    val usesGreenworks: Boolean,
    val audioExt: String,
    val videoExt: String,
    /** Asset key from System.json, null if the game is not encrypted. */
    val encryptionKey: ByteArray?,
    val index: FileIndex,
    /** Scripts of the active mods (`mod.json`), run after the game's own patches. */
    val modScripts: List<UserPatch> = emptyList(),
    /** Ids of the active mods, top first. */
    val mods: List<String> = emptyList(),
)

object GameInspector {
    private const val TAG = "Inspector"
    private const val BOM = "\uFEFF"

    /** Finds the folder that holds index.html and identifies the engine. */
    fun detect(dir: GameNode): EngineMatch = detectEngine(dir).also {
        DebugLog.i(TAG, "Detected ${it.engine} in ${dir.name} (web root ${it.webRoot?.name})")
    }

    private fun detectEngine(dir: GameNode): EngineMatch {
        val top = dir.children()
        fun List<GameNode>.find(name: String) = firstOrNull { it.name.equals(name, ignoreCase = true) }
        val www = top.find("www")?.takeIf { it.isDirectory }
        val wwwChildren = www?.children().orEmpty()
        val webRoot = when {
            wwwChildren.find("index.html") != null -> www
            top.find("index.html") != null -> dir
            else -> null
        }
        if (webRoot != null) {
            val js = (if (webRoot === dir) top else wwwChildren).find("js")?.children().orEmpty()
            if (js.find("rpg_core.js") != null) return EngineMatch(Engine.MV, webRoot)
            if (js.find("rmmz_core.js") != null) return EngineMatch(Engine.MZ, webRoot)
        }
        val names = top.map { it.name.lowercase() }.toSet()
        if ("rpg_rt.ldb" in names || "rpg_rt.lmt" in names) return EngineMatch(Engine.RPG2K, null)
        val rgssArchives = setOf("game.rgssad", "game.rgss2a", "game.rgss3a")
        if ("game.ini" in names && (names.any { it in rgssArchives } || "data" in names)) {
            return EngineMatch(Engine.RGSS, null)
        }
        return EngineMatch(Engine.UNKNOWN, null)
    }

    /** Full inspection of an MV game. Reads plugin files, so call it off the main thread. */
    fun inspectMv(webRoot: GameNode, folderName: String, mods: List<Mod> = emptyList()): GameInfo {
        val start = SystemClock.elapsedRealtime()
        val gameIndex = FileIndex.build(webRoot)
        DebugLog.i(TAG, "Indexed ${gameIndex.size} files under $folderName in ${SystemClock.elapsedRealtime() - start} ms")
        // Active mods lie over the game, the first one on top.
        val index = if (mods.isEmpty()) gameIndex else FileIndex.layered(mods.map { mod ->
            val modStart = SystemClock.elapsedRealtime()
            FileIndex.build(mod.content).also {
                DebugLog.i(TAG, "Mod ${mod.id}: ${it.size} files in ${SystemClock.elapsedRealtime() - modStart} ms")
            }
        } + gameIndex)
        val (plugins, totalPlugins) = readPlugins(index)
        val usesGreenworks = plugins.any { name ->
            val file = index.resolve("js/plugins/$name.js") ?: return@any false
            val text = runCatching { file.readText() }.getOrDefault("")
            text.contains("greenworks", ignoreCase = true)
        }
        val system = readSystem(index)
        return GameInfo(
            webRoot = webRoot,
            folderName = folderName,
            title = system?.optString("gameTitle")?.takeIf { it.isNotBlank() } ?: folderName,
            engineVersion = readCoreVersion(index),
            enabledPlugins = plugins,
            totalPlugins = totalPlugins,
            pixiVersion = readPixiVersion(index),
            titleImages = listOf("img/titles1/" to "title1Name", "img/titles2/" to "title2Name")
                .mapNotNull { (folder, key) -> system?.optString(key)?.takeIf { it.isNotBlank() }?.let { folder + it } },
            usesGreenworks = usesGreenworks,
            audioExt = pickAudioExt(index),
            videoExt = pickVideoExt(index),
            encryptionKey = MvDecrypter.parseKey(system?.optString("encryptionKey")),
            index = index,
            modScripts = Mods.existingScripts(mods, index),
            mods = mods.map { it.id },
        ).also {
            DebugLog.i(TAG, "${it.title}: MV ${it.engineVersion}, PIXI ${it.pixiVersion}, " +
                "${plugins.size}/$totalPlugins plugins, " +
                "greenworks=${it.usesGreenworks}, audio=${it.audioExt}, video=${it.videoExt}, " +
                "encrypted=${it.encryptionKey != null}, inspected in ${SystemClock.elapsedRealtime() - start} ms")
            DebugLog.i(TAG, "Enabled plugins: ${plugins.joinToString()}")
            DebugLog.d(TAG, "File types: ${index.extensionCounts()}")
        }
    }

    private fun readSystem(index: FileIndex): JSONObject? = runCatching {
        JSONObject(index.require("data/System.json").readText().removePrefix(BOM))
    }.onFailure { DebugLog.w(TAG, "Could not read data/System.json", it) }.getOrNull()

    private fun readCoreVersion(index: FileIndex): String? = runCatching {
        val head = index.require("js/rpg_core.js").readHead(16 * 1024)
        Regex("""RPGMAKER_VERSION\s*=\s*["']([^"']+)""").find(head)?.groupValues?.get(1)
            ?: Regex("""rpg_core\.js v([\d.]+)""").find(head)?.groupValues?.get(1)
    }.getOrNull()

    /** Enabled plugin names and the total number of entries. */
    private fun readPlugins(index: FileIndex): Pair<List<String>, Int> = runCatching {
        val text = index.require("js/plugins.js").readText()
        val array = JSONArray(text.substring(text.indexOf('['), text.lastIndexOf(']') + 1))
        val enabled = (0 until array.length())
            .map { array.getJSONObject(it) }
            .filter { it.optBoolean("status") }
            .map { it.optString("name") }
            .filter { it.isNotBlank() }
        enabled to array.length()
    }.getOrElse {
        DebugLog.w(TAG, "Could not read js/plugins.js", it)
        emptyList<String>() to 0
    }

    /** From the banner ("pixi.js - v4.5.4"), else from the VERSION constant further down. */
    private fun readPixiVersion(index: FileIndex): String? = runCatching {
        val file = index.resolve("js/libs/pixi.js") ?: return null
        val head = file.readHead(1024)
        Regex("""pixi\.js - v([\d.]+)""").find(head)?.groupValues?.get(1)
            ?: Regex("""VERSION\s*=\s*(?:exports\.VERSION\s*=\s*)?['"]([\d.]+)['"]""").find(file.readText())?.groupValues?.get(1)
    }.getOrNull()

    private fun FileIndex.require(path: String): GameNode = resolve(path) ?: throw java.io.FileNotFoundException(path)

    // Utils.isMobileDevice() makes MV ask for .m4a/.mp4; serve whatever the game actually ships.
    private fun pickAudioExt(index: FileIndex): String {
        val hasOgg = index.anyUnder("audio/") { it.endsWith(".ogg") || it.endsWith(".rpgmvo") }
        val hasM4a = index.anyUnder("audio/") { it.endsWith(".m4a") || it.endsWith(".rpgmvm") }
        return if (!hasOgg && hasM4a) ".m4a" else ".ogg"
    }

    private fun pickVideoExt(index: FileIndex): String {
        val hasWebm = index.anyUnder("movies/") { it.endsWith(".webm") }
        val hasMp4 = index.anyUnder("movies/") { it.endsWith(".mp4") }
        return if (!hasWebm && hasMp4) ".mp4" else ".webm"
    }
}
