// SPDX-FileCopyrightText: 2026 MircoDHD
// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.mircodhd.mvplayer.input

import org.json.JSONArray
import org.json.JSONObject

/**
 * Which physical controls trigger which action. Each action has a list of bindings; a
 * binding is one control or a chord of several that must be held together.
 */
data class ControllerMapping(val bindings: Map<InputAction, List<List<String>>>) {

    fun bindingsFor(action: InputAction): List<List<String>> = bindings[action].orEmpty()

    fun withAdded(action: InputAction, binding: List<String>): ControllerMapping {
        val current = bindingsFor(action)
        if (binding in current) return this
        return copy(bindings = bindings + (action to current + listOf(binding)))
    }

    fun withRemoved(action: InputAction, binding: List<String>): ControllerMapping =
        copy(bindings = bindings + (action to bindingsFor(action).filterNot { it == binding }))

    /** Actions (other than [except]) that already use exactly this binding. */
    fun actionsUsing(binding: List<String>, except: InputAction? = null): List<InputAction> =
        bindings.filter { (action, list) -> action != except && binding in list }.keys.toList()

    fun toJson(): JSONObject = JSONObject().apply {
        for (action in InputAction.entries) {
            put(action.id, JSONArray(bindingsFor(action).map { JSONArray(it) }))
        }
    }

    companion object {
        private val k = Controls::key
        private fun axis(name: String, positive: Boolean) = Controls.axis(name, positive)

        /** Today's behaviour: face buttons by position, d-pad from keys, hat or left stick. */
        val DEFAULT = ControllerMapping(
            mapOf(
                InputAction.OK to listOf(listOf(k(Controls.KEY_BUTTON_A)), listOf(k(Controls.KEY_DPAD_CENTER))),
                InputAction.CANCEL to listOf(listOf(k(Controls.KEY_BUTTON_B)), listOf(k(Controls.KEY_BACK))),
                InputAction.SHIFT to listOf(listOf(k(Controls.KEY_BUTTON_X))),
                InputAction.MENU to listOf(listOf(k(Controls.KEY_BUTTON_Y)), listOf(k(Controls.KEY_BUTTON_START))),
                InputAction.PAGE_UP to listOf(listOf(k(Controls.KEY_BUTTON_L1))),
                InputAction.PAGE_DOWN to listOf(listOf(k(Controls.KEY_BUTTON_R1))),
                InputAction.UP to listOf(listOf(k(Controls.KEY_DPAD_UP)), listOf(axis("HAT_Y", false)), listOf(axis("Y", false))),
                InputAction.DOWN to listOf(listOf(k(Controls.KEY_DPAD_DOWN)), listOf(axis("HAT_Y", true)), listOf(axis("Y", true))),
                InputAction.LEFT to listOf(listOf(k(Controls.KEY_DPAD_LEFT)), listOf(axis("HAT_X", false)), listOf(axis("X", false))),
                InputAction.RIGHT to listOf(listOf(k(Controls.KEY_DPAD_RIGHT)), listOf(axis("HAT_X", true)), listOf(axis("X", true))),
                InputAction.QUICK_MENU to listOf(listOf(k(Controls.KEY_BUTTON_SELECT), k(Controls.KEY_BUTTON_START))),
            ),
        )

        /** Actions missing from the JSON keep their default bindings. */
        fun fromJson(json: JSONObject): ControllerMapping {
            val bindings = InputAction.entries.associateWith { action ->
                val array = json.optJSONArray(action.id) ?: return@associateWith DEFAULT.bindingsFor(action)
                (0 until array.length()).mapNotNull { i ->
                    val chord = array.optJSONArray(i) ?: return@mapNotNull null
                    (0 until chord.length()).map { chord.getString(it) }.takeIf { it.isNotEmpty() }
                }
            }
            return ControllerMapping(bindings)
        }
    }
}
