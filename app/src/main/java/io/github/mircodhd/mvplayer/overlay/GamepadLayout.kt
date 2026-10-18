// SPDX-FileCopyrightText: 2026 MircoDHD
// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.mircodhd.mvplayer.overlay

import io.github.mircodhd.mvplayer.input.InputAction
import org.json.JSONArray
import org.json.JSONObject

enum class ControlType { DPAD, STICK, BUTTON, PILL, SHOULDER }

enum class Anchor(val horizontal: Int, val vertical: Int) {
    // horizontal/vertical: -1 = start/top, 0 = center, 1 = end/bottom
    TOP_START(-1, -1), TOP_CENTER(0, -1), TOP_END(1, -1),
    CENTER_START(-1, 0), CENTER_END(1, 0),
    BOTTOM_START(-1, 1), BOTTOM_CENTER(0, 1), BOTTOM_END(1, 1);

    fun mirrored(): Anchor = entries.first { it.horizontal == -horizontal && it.vertical == vertical }
}

/**
 * One on-screen control. Positions and sizes are in units of the safe-area **height**
 * (the short side in landscape), so layouts keep their proportions on any aspect ratio:
 * the control's centre sits [x] and [y] units inward from its [anchor].
 */
data class OverlayControl(
    val id: String,
    val type: ControlType,
    val anchor: Anchor,
    val x: Float,
    val y: Float,
    val size: Float,
    val label: String? = null,
    /** For buttons; d-pads and sticks always produce the four directions. */
    val action: InputAction? = null,
    val hidden: Boolean = false,
) {
    /** Width and height relative to [size]. */
    val aspect: Pair<Float, Float>
        get() = when (type) {
            ControlType.PILL -> 1f to 0.42f
            ControlType.SHOULDER -> 1f to 0.5f
            else -> 1f to 1f
        }

    /** Centre in pixels for a safe area of [width] x [height]. */
    fun center(width: Float, height: Float): Pair<Float, Float> {
        val cx = when (anchor.horizontal) {
            -1 -> x * height
            1 -> width - x * height
            else -> width / 2 + x * height
        }
        val cy = when (anchor.vertical) {
            -1 -> y * height
            1 -> height - y * height
            else -> height / 2 + y * height
        }
        return cx to cy
    }

    fun mirrored(): OverlayControl = copy(anchor = anchor.mirrored(), x = if (anchor.horizontal == 0) -x else x)
}

data class GamepadLayout(
    val id: String,
    /** Display name for custom layouts; built-in presets use [nameKey] (a string resource name). */
    val name: String?,
    val nameKey: String?,
    val basedOn: String?,
    val controls: List<OverlayControl>,
    val builtIn: Boolean,
) {
    fun mirrored(): GamepadLayout = copy(controls = controls.map { it.mirrored() })

    val visibleControls: List<OverlayControl> get() = controls.filterNot { it.hidden }
}

object LayoutJson {
    fun parse(json: JSONObject, builtIn: Boolean): GamepadLayout {
        val array = json.getJSONArray("controls")
        val controls = (0 until array.length()).map { i ->
            val c = array.getJSONObject(i)
            OverlayControl(
                id = c.getString("id"),
                type = ControlType.valueOf(c.getString("type").uppercase()),
                anchor = Anchor.valueOf(c.getString("anchor").uppercase()),
                x = c.getDouble("x").toFloat(),
                y = c.getDouble("y").toFloat(),
                size = c.getDouble("size").toFloat(),
                label = c.optString("label").takeIf { it.isNotEmpty() },
                action = c.optString("action").takeIf { it.isNotEmpty() }?.let { id ->
                    InputAction.fromId(id) ?: throw IllegalArgumentException("Unknown action '$id'")
                },
                hidden = c.optBoolean("hidden", false),
            )
        }
        require(controls.map { it.id }.toSet().size == controls.size) { "Duplicate control ids" }
        controls.forEach { c ->
            require(c.type in setOf(ControlType.DPAD, ControlType.STICK) || c.action != null) { "Control '${c.id}' needs an action" }
        }
        return GamepadLayout(
            id = json.getString("id"),
            name = json.optString("name").takeIf { it.isNotEmpty() },
            nameKey = json.optString("nameKey").takeIf { it.isNotEmpty() },
            basedOn = json.optString("basedOn").takeIf { it.isNotEmpty() },
            controls = controls,
            builtIn = builtIn,
        )
    }

    fun toJson(layout: GamepadLayout): JSONObject = JSONObject().apply {
        put("version", 1)
        put("id", layout.id)
        layout.name?.let { put("name", it) }
        layout.nameKey?.let { put("nameKey", it) }
        layout.basedOn?.let { put("basedOn", it) }
        put("controls", JSONArray(layout.controls.map { c ->
            JSONObject().apply {
                put("id", c.id)
                put("type", c.type.name.lowercase())
                put("anchor", c.anchor.name.lowercase())
                put("x", c.x.toDouble())
                put("y", c.y.toDouble())
                put("size", c.size.toDouble())
                c.label?.let { put("label", it) }
                c.action?.let { put("action", it.id) }
                if (c.hidden) put("hidden", true)
            }
        }))
    }
}
