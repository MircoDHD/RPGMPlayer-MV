// SPDX-FileCopyrightText: 2026 MircoDHD
// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.mircodhd.mvplayer.saves

import android.content.Context
import android.net.Uri
import io.github.mircodhd.mvplayer.game.GameInspector
import io.github.mircodhd.mvplayer.storage.DocumentNode
import org.json.JSONArray
import org.json.JSONObject

/** What MV's global.rpgsave says about one save slot. */
data class SlotInfo(val playtime: String, val timestamp: Long) {
    companion object {
        fun fromJson(json: JSONObject?): SlotInfo? = json?.let { SlotInfo(it.optString("playtime"), it.optLong("timestamp")) }

        /** Slot infos from a global.rpgsave text, by slot number (1 = file1.rpgsave). */
        fun fromGlobal(globalText: String?): Map<Int, JSONObject> {
            val json = globalText?.let(LzString::decompressFromBase64) ?: return emptyMap()
            val array = runCatching { JSONArray(json) }.getOrNull() ?: return emptyMap()
            return (1 until array.length()).mapNotNull { i -> array.optJSONObject(i)?.let { i to it } }.toMap()
        }
    }
}

/** One numbered save slot of the game. */
data class SaveSlot(val number: Int, val file: String, val info: SlotInfo?, val sizeBytes: Long)

/**
 * The game's own saves in `www/save/` (made with the game's save menu), shown in the
 * library. Read only. Blocking; call off the main thread.
 */
class GameSaves(private val context: Context, private val treeUri: Uri) {
    fun slots(): List<SaveSlot> {
        val root = DocumentNode.root(context, treeUri) ?: return emptyList()
        val webRoot = GameInspector.detect(root).webRoot ?: return emptyList()
        val dir = webRoot.children().firstOrNull { it.isDirectory && it.name.equals("save", ignoreCase = true) } ?: return emptyList()
        val files = dir.children().filterNot { it.isDirectory }
        val global = files.firstOrNull { it.name.equals("global.rpgsave", ignoreCase = true) }?.let { runCatching { it.readText() }.getOrNull() }
        val infos = SlotInfo.fromGlobal(global)
        return files.mapNotNull { node ->
            val number = SLOT_FILE.matchEntire(node.name)?.groupValues?.get(1)?.toIntOrNull() ?: return@mapNotNull null
            SaveSlot(number, node.name, SlotInfo.fromJson(infos[number]), node.length)
        }.sortedBy { it.number }
    }

    companion object {
        /** Numbered save slots (global.rpgsave and config.rpgsave are not slots). */
        val SLOT_FILE = Regex("""file(\d+)\.rpgsave""", RegexOption.IGNORE_CASE)
    }
}
