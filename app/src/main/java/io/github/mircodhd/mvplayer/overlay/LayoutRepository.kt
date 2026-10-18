// SPDX-FileCopyrightText: 2026 MircoDHD
// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.mircodhd.mvplayer.overlay

import android.content.Context
import io.github.mircodhd.mvplayer.debug.DebugLog
import io.github.mircodhd.mvplayer.settings.OverlaySettings
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.UUID

/**
 * Built-in presets from `assets/gamepad/presets/<id>.json` and custom layouts from
 * `filesDir/gamepad/custom/<id>.json`, in the same format.
 */
class LayoutRepository(private val context: Context) {
    private val customDir = File(context.filesDir, "gamepad/custom")

    fun all(): List<GamepadLayout> = presets() + custom()

    fun presets(): List<GamepadLayout> = PRESET_ORDER.mapNotNull { name ->
        runCatching {
            context.assets.open("$PRESET_DIR/$name.json").use { LayoutJson.parse(JSONObject(it.reader().readText()), builtIn = true) }
        }.onFailure { DebugLog.e(TAG, "Broken preset $name", it) }.getOrNull()
    }

    fun custom(): List<GamepadLayout> =
        customDir.listFiles { f -> f.name.endsWith(".json") }.orEmpty().sortedBy { it.name }.mapNotNull { file ->
            runCatching { LayoutJson.parse(JSONObject(file.readText()), builtIn = false) }
                .onFailure { DebugLog.w(TAG, "Skipping broken layout $file", it) }
                .getOrNull()
        }

    /** The layout with [id], falling back to the default preset. */
    fun find(id: String): GamepadLayout =
        all().let { list -> list.firstOrNull { it.id == id } ?: list.firstOrNull { it.id == OverlaySettings.DEFAULT_LAYOUT } ?: list.first() }

    /** Saves a customised copy and returns it (a new id unless [layout] is already custom). */
    fun saveCustom(layout: GamepadLayout, name: String): GamepadLayout {
        val saved = layout.copy(
            id = if (layout.builtIn) "custom-${UUID.randomUUID()}" else layout.id,
            name = name,
            nameKey = null,
            basedOn = layout.basedOn ?: layout.id.takeIf { layout.builtIn },
            builtIn = false,
        )
        try {
            customDir.mkdirs()
            File(customDir, "${saved.id}.json").writeText(LayoutJson.toJson(saved).toString(2))
            DebugLog.i(TAG, "Saved custom layout ${saved.id} '$name'")
        } catch (e: IOException) {
            DebugLog.e(TAG, "Could not save layout ${saved.id}", e)
        }
        return saved
    }

    fun deleteCustom(id: String) {
        if (!id.startsWith("custom-")) return
        File(customDir, "$id.json").delete()
        DebugLog.i(TAG, "Deleted custom layout $id")
    }

    companion object {
        private const val TAG = "Layouts"
        private const val PRESET_DIR = "gamepad/presets"
        val PRESET_ORDER = listOf("handheld", "console16", "arcade6", "dualanalog", "minimal")
    }
}
