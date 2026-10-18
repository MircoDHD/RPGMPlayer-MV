// SPDX-FileCopyrightText: 2026 MircoDHD
// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.mircodhd.mvplayer.input

/**
 * Everything a button can trigger. MV actions carry the standard-gamepad bit that
 * `mv/input.js` maps to MV's Input names, so the JS side does not need to know about
 * remapping. App actions are handled natively.
 */
enum class InputAction(val id: String, val mvBit: Int?) {
    OK("ok", 0),
    CANCEL("cancel", 1),
    SHIFT("shift", 2),
    MENU("menu", 3),
    PAGE_UP("pageup", 4),
    PAGE_DOWN("pagedown", 5),
    UP("up", 12),
    DOWN("down", 13),
    LEFT("left", 14),
    RIGHT("right", 15),
    QUICK_MENU("quickmenu", null);

    val isAppAction: Boolean get() = mvBit == null

    companion object {
        fun fromId(id: String): InputAction? = entries.firstOrNull { it.id == id }

        /** Standard-gamepad bitmask for the MV actions in [actions]. */
        fun bitsOf(actions: Collection<InputAction>): Int =
            actions.fold(0) { bits, action -> action.mvBit?.let { bits or (1 shl it) } ?: bits }
    }
}

/**
 * Physical controls are plain strings so mappings can be stored and compared easily:
 * `k<keycode>` for buttons, `a:<axis>:<+|->` for an axis pushed past the threshold.
 */
object Controls {
    // android.view.KeyEvent constants, duplicated so this file stays free of Android types.
    const val KEY_BACK = 4
    const val KEY_DPAD_UP = 19
    const val KEY_DPAD_DOWN = 20
    const val KEY_DPAD_LEFT = 21
    const val KEY_DPAD_RIGHT = 22
    const val KEY_DPAD_CENTER = 23
    const val KEY_BUTTON_A = 96
    const val KEY_BUTTON_B = 97
    const val KEY_BUTTON_X = 99
    const val KEY_BUTTON_Y = 100
    const val KEY_BUTTON_L1 = 102
    const val KEY_BUTTON_R1 = 103
    const val KEY_BUTTON_START = 108
    const val KEY_BUTTON_SELECT = 109

    fun key(keyCode: Int) = "k$keyCode"

    fun axis(name: String, positive: Boolean) = "a:$name:${if (positive) "+" else "-"}"

    /** Key code of a `k…` control, or null for axes. */
    fun keyCodeOf(control: String): Int? = control.takeIf { it.startsWith("k") }?.drop(1)?.toIntOrNull()

    /** (axis name, positive) of an `a:…` control, or null for keys. */
    fun axisOf(control: String): Pair<String, Boolean>? {
        val parts = control.split(':')
        if (parts.size != 3 || parts[0] != "a") return null
        return parts[1] to (parts[2] == "+")
    }
}
