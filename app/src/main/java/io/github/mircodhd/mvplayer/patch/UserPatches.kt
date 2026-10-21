// SPDX-FileCopyrightText: 2026 MircoDHD
// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.mircodhd.mvplayer.patch

import io.github.mircodhd.mvplayer.debug.DebugLog
import io.github.mircodhd.mvplayer.game.FileIndex
import org.json.JSONObject

/**
 * The game's own patches: scripts in `mvplayer-patches/` next to the game's index.html, so
 * they travel with the game folder. `patches.json` lists them with their position
 * (docs/patches.md):
 * ```
 * { "patches": [ { "file": "fix.js", "position": "beforePlugins" }, { "file": "after.js" } ] }
 * ```
 * Without it, every .js file directly in the folder runs after the plugins, sorted by name.
 * They run with the same rights as plugins.
 */
object UserPatches {
    const val FOLDER = "mvplayer-patches"
    private const val TAG = "Patches"

    fun find(index: FileIndex): List<UserPatch> {
        val files = index.pathsUnder("$FOLDER/")
        if (files.isEmpty()) return emptyList()
        val folder = files.first().substringBefore('/')
        val list = files.firstOrNull { it.equals("$folder/patches.json", ignoreCase = true) }
        val patches = if (list != null) fromList(index, folder, list) else files
            .filter { it.count { c -> c == '/' } == 1 && it.endsWith(".js", ignoreCase = true) }
            .sortedBy { it.lowercase() }
            .map { UserPatch(it, PatchPosition.AFTER_PLUGINS) }
        if (patches.isNotEmpty()) DebugLog.i(TAG, "Game patches: " + patches.joinToString { "${it.file} (${it.position.id})" })
        return patches
    }

    private fun fromList(index: FileIndex, folder: String, list: String): List<UserPatch> {
        val json = runCatching { JSONObject(index.resolve(list)!!.readText().removePrefix("\uFEFF")) }
            .onFailure { DebugLog.w(TAG, "Cannot read $list; no game patches used", it) }
            .getOrNull() ?: return emptyList()
        val array = json.optJSONArray("patches") ?: return emptyList()
        return (0 until array.length()).mapNotNull { i ->
            val entry = array.optJSONObject(i) ?: return@mapNotNull null
            val file = entry.optString("file").replace('\\', '/').trim('/')
            val position = entry.optString("position").ifEmpty { PatchPosition.AFTER_PLUGINS.id }
                .let { id -> PatchPosition.entries.firstOrNull { it.id.equals(id, ignoreCase = true) } }
            val path = "$folder/$file"
            when {
                file.isEmpty() || file.split('/').any { it == ".." } -> null.also { DebugLog.w(TAG, "Ignoring patch entry '$file'") }
                position == null -> null.also { DebugLog.w(TAG, "Unknown position '${entry.optString("position")}' for $file") }
                index.resolve(path) == null -> null.also { DebugLog.w(TAG, "Patch file $path is missing") }
                else -> UserPatch(path, position)
            }
        }
    }
}
