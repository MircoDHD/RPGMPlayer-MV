// SPDX-FileCopyrightText: 2026 MircoDHD
// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.mircodhd.mvplayer.game

import io.github.mircodhd.mvplayer.debug.DebugLog
import io.github.mircodhd.mvplayer.patch.PatchPosition
import io.github.mircodhd.mvplayer.patch.UserPatch
import io.github.mircodhd.mvplayer.patch.UserPatches
import io.github.mircodhd.mvplayer.storage.GameNode
import org.json.JSONArray
import org.json.JSONObject

/** A mod found in the game folder; [content] is laid over the game's web root. */
data class Mod(
    /** The mod's folder name; stored in the game settings to switch it on. */
    val id: String,
    val name: String,
    val version: String?,
    val author: String?,
    val description: String?,
    val content: GameNode,
    /** The mod's own folder (the one to delete); [content] may be its www subfolder. */
    val folder: GameNode = content,
    /** Scripts from `mod.json`, paths relative to the web root (the mod lies over it). */
    val scripts: List<UserPatch> = emptyList(),
)

/** A file that several active mods replace: [path] as the top mod has it, [mods] top first. */
data class ModConflict(val path: String, val mods: List<String>) {
    /** Core scripts and the page decide whether mods fit together at all. */
    val isCore: Boolean get() = path.lowercase().let { it == "index.html" || it == "js/plugins.js" || CORE_SCRIPT.matches(it) }

    private companion object {
        val CORE_SCRIPT = Regex("js/rpg_[a-z]+\\.js")
    }
}

/**
 * Finds mods in a game folder without changing anything in it. Mods are usually shipped
 * as "<Mod name>/www/…" to be copied over the game; placed next to the game's `www` folder
 * instead, they become a switchable overlay. Also looked at: `mvplayer-mods/<mod>/` in the
 * game folder or next to index.html. A folder counts as a mod when it (or its `www`) holds
 * game folders like data, js, img or audio. An optional `mod.json` names it and lists its
 * scripts (same format as the game's `patches.json`):
 * `{ "name": "…", "version": "…", "author": "…", "description": "…",
 *    "scripts": [ { "file": "js/fix.js", "position": "afterPlugins" } ] }`.
 * Blocking (documents provider calls).
 */
object Mods {
    const val FOLDER = "mvplayer-mods"
    private const val TAG = "Mods"
    private const val MOD_JSON = "mod.json"
    private val GAME_DIRS = setOf("data", "js", "img", "audio", "movies", "fonts")
    /** Folders next to www that belong to the PC version or the game itself. */
    private val NOT_MODS = GAME_DIRS + setOf("www", "save", "icon", "locales", "swiftshader", "__macosx", UserPatches.FOLDER)

    fun find(root: GameNode, webRoot: GameNode): List<Mod> {
        val candidates = ArrayList<GameNode>()
        val rootChildren = root.children().filter { it.isDirectory }
        for (dir in rootChildren) {
            val lower = dir.name.lowercase()
            if (dir.name == webRoot.name && webRoot !== root || lower in NOT_MODS || lower.startsWith(".")) continue
            candidates += dir
        }
        val modFolders = rootChildren.filter { it.name.equals(FOLDER, ignoreCase = true) } +
            if (webRoot !== root) webRoot.children().filter { it.isDirectory && it.name.equals(FOLDER, ignoreCase = true) } else emptyList()
        modFolders.forEach { folder -> candidates += folder.children().filter { it.isDirectory && !it.name.startsWith(".") } }
        val mods = candidates.mapNotNull(::asMod).distinctBy { it.id.lowercase() }
        DebugLog.i(TAG, "Found ${mods.size} mods: ${mods.joinToString { it.id }}")
        return mods
    }

    private fun asMod(dir: GameNode): Mod? {
        val children = dir.children()
        val www = children.firstOrNull { it.isDirectory && it.name.equals("www", ignoreCase = true) }
        val content = www ?: dir
        val contentChildren = if (www != null) www.children() else children
        val isGame = contentChildren.any { child ->
            (child.isDirectory && child.name.lowercase() in GAME_DIRS) || child.name.equals("index.html", ignoreCase = true)
        }
        if (!isGame) return null
        val info = (children + contentChildren).firstOrNull { !it.isDirectory && it.name.equals(MOD_JSON, ignoreCase = true) }
            ?.let { runCatching { JSONObject(it.readText().removePrefix("\uFEFF")) }.getOrNull() }
        fun text(key: String) = info?.optString(key)?.trim()?.takeIf { it.isNotEmpty() }
        val scripts = scripts(dir.name, info?.optJSONArray("scripts"), stripWww = www != null)
        return Mod(dir.name, text("name") ?: dir.name, text("version"), text("author"), text("description"), content, dir, scripts)
    }

    /** `mod.json` "scripts"; whether the files exist is checked at game start ([existingScripts]). */
    private fun scripts(id: String, array: JSONArray?, stripWww: Boolean): List<UserPatch> {
        if (array == null) return emptyList()
        return (0 until array.length()).mapNotNull { i ->
            val entry = array.optJSONObject(i) ?: return@mapNotNull null
            var file = entry.optString("file").replace('\\', '/').trim('/')
            // A path written from the mod folder ("www/js/x.js") means the same file.
            if (stripWww && file.startsWith("www/", ignoreCase = true)) file = file.substring(4)
            val position = entry.optString("position").ifEmpty { PatchPosition.AFTER_PLUGINS.id }
                .let { p -> PatchPosition.entries.firstOrNull { it.id.equals(p, ignoreCase = true) } }
            when {
                file.isEmpty() || file.split('/').any { it == ".." } -> null.also { DebugLog.w(TAG, "$id: ignoring script entry '$file'") }
                position == null -> null.also { DebugLog.w(TAG, "$id: unknown position '${entry.optString("position")}' for $file") }
                else -> UserPatch(file, position)
            }
        }
    }

    /** Scripts of the active mods that exist in the layered [index], in mod order. */
    fun existingScripts(mods: List<Mod>, index: FileIndex): List<UserPatch> = mods.flatMap { mod ->
        mod.scripts.filter { script ->
            (index.resolve(script.path) != null).also { if (!it) DebugLog.w(TAG, "${mod.id}: script ${script.path} is missing") }
        }
    }

    /** The enabled mods in their order; ids without a folder are skipped and logged. */
    fun select(found: List<Mod>, enabled: List<String>): List<Mod> = enabled.mapNotNull { id ->
        found.firstOrNull { it.id.equals(id, ignoreCase = true) }
            ?: null.also { DebugLog.w(TAG, "Mod '$id' is switched on but its folder is gone") }
    }

    /**
     * Files that two or more of the active mods replace (case-insensitive like the file
     * index), with the mods top first: the first one is served. [layers] are the active mods
     * with their indexes, top first. A top-level mod.json is the mod's own file, not a game file.
     */
    fun conflicts(layers: List<Pair<Mod, FileIndex>>): List<ModConflict> {
        val owners = LinkedHashMap<String, MutableList<String>>()
        val names = HashMap<String, String>()
        for ((mod, index) in layers) {
            for (path in index.paths) {
                val key = path.lowercase()
                if (key == MOD_JSON) continue
                val list = owners.getOrPut(key) { ArrayList() }
                if (mod.id !in list) list += mod.id
                names.putIfAbsent(key, path)
            }
        }
        return owners.filterValues { it.size > 1 }
            .map { (key, ids) -> ModConflict(names.getValue(key), ids) }
            .sortedBy { it.path.lowercase() }
    }

    /** How many of the game's files the mod replaces. */
    fun replacedCount(mod: FileIndex, game: FileIndex): Int =
        mod.paths.count { !it.equals(MOD_JSON, ignoreCase = true) && game.resolve(it) != null }

    /** Deletes the mod's folder with everything in it; true on success. */
    fun remove(mod: Mod): Boolean {
        val deleted = mod.folder.deleteRecursively()
        if (deleted < 0) DebugLog.w(TAG, "Could not remove mod ${mod.id} completely")
        else DebugLog.i(TAG, "Removed mod ${mod.id} ($deleted files and folders)")
        return deleted >= 0
    }
}
