// SPDX-FileCopyrightText: 2026 MircoDHD
// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.mircodhd.mvplayer.input

import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import io.github.mircodhd.mvplayer.debug.DebugLog
import kotlin.math.abs

/**
 * Captures hardware controllers natively and reports the set of physical controls that
 * are held (see [Controls]); [InputRouter] turns them into game input via the mapping.
 * navigator.getGamepads() is not used, its support in Android WebView is unreliable.
 * All connected controllers are merged into one.
 */
class GamepadInput(private val onChange: (controls: Set<String>) -> Unit) {
    private class DeviceState {
        val keys = HashSet<String>()
        var axes: Set<String> = emptySet()
    }

    private val devices = HashMap<Int, DeviceState>()
    private var lastSent: Set<String> = emptySet()

    val activeControls: Set<String> get() = lastSent

    fun handleKeyEvent(event: KeyEvent): Boolean {
        if (!isController(event.device) || !isControllerKey(event.keyCode)) return false
        if (event.action != KeyEvent.ACTION_DOWN && event.action != KeyEvent.ACTION_UP) return true
        val control = Controls.key(event.keyCode)
        if (event.repeatCount == 0) {
            DebugLog.d(TAG, "${KeyEvent.keyCodeToString(event.keyCode)} " +
                "${if (event.action == KeyEvent.ACTION_DOWN) "down" else "up"} from '${event.device?.name}' (id ${event.deviceId})")
        }
        val state = devices.getOrPut(event.deviceId) { DeviceState() }
        if (event.action == KeyEvent.ACTION_DOWN) state.keys += control else state.keys -= control
        publish()
        return true
    }

    fun handleMotionEvent(event: MotionEvent): Boolean {
        if (event.action != MotionEvent.ACTION_MOVE) return false
        if (!event.isFromSource(InputDevice.SOURCE_JOYSTICK) && !event.isFromSource(InputDevice.SOURCE_GAMEPAD)) return false
        val device = event.device ?: return false
        val axes = HashSet<String>()
        for ((axis, name) in STICK_AXES) {
            val value = centered(event, device, axis)
            if (value < -AXIS_THRESHOLD) axes += Controls.axis(name, false)
            if (value > AXIS_THRESHOLD) axes += Controls.axis(name, true)
        }
        // Triggers rest at 0; some controllers report them as brake/gas instead.
        if (maxOf(event.getAxisValue(MotionEvent.AXIS_LTRIGGER), event.getAxisValue(MotionEvent.AXIS_BRAKE)) > AXIS_THRESHOLD) {
            axes += Controls.axis("LTRIGGER", true)
        }
        if (maxOf(event.getAxisValue(MotionEvent.AXIS_RTRIGGER), event.getAxisValue(MotionEvent.AXIS_GAS)) > AXIS_THRESHOLD) {
            axes += Controls.axis("RTRIGGER", true)
        }
        val state = devices.getOrPut(event.deviceId) { DeviceState() }
        if (state.axes != axes && DebugLog.verbose) DebugLog.v(TAG, "axes from '${device.name}': ${axes.sorted()}")
        state.axes = axes
        publish()
        return true
    }

    fun onDeviceRemoved(deviceId: Int) {
        if (devices.remove(deviceId) != null) publish()
    }

    /** Releases everything, e.g. when the activity loses focus mid-press. */
    fun reset() {
        devices.clear()
        publish()
    }

    private fun publish() {
        val combined = HashSet<String>()
        for (state in devices.values) {
            combined += state.keys
            combined += state.axes
        }
        if (combined != lastSent) {
            lastSent = combined
            DebugLog.d(TAG, "controls = ${if (combined.isEmpty()) "none" else combined.sorted().joinToString("+") { ControlNames.short(it) }}")
            onChange(combined)
        }
    }

    private fun centered(event: MotionEvent, device: InputDevice, axis: Int): Float {
        val value = event.getAxisValue(axis)
        val flat = device.getMotionRange(axis, event.source)?.flat ?: 0f
        return if (abs(value) > flat) value else 0f
    }

    companion object {
        private const val TAG = "Input"
        private const val AXIS_THRESHOLD = 0.5f

        /** Axes reported as directions: d-pad hat, left stick, right stick (Z/RZ or RX/RY). */
        private val STICK_AXES = listOf(
            MotionEvent.AXIS_HAT_X to "HAT_X",
            MotionEvent.AXIS_HAT_Y to "HAT_Y",
            MotionEvent.AXIS_X to "X",
            MotionEvent.AXIS_Y to "Y",
            MotionEvent.AXIS_Z to "Z",
            MotionEvent.AXIS_RZ to "RZ",
            MotionEvent.AXIS_RX to "RX",
            MotionEvent.AXIS_RY to "RY",
        )

        fun isController(device: InputDevice?): Boolean {
            if (device == null || device.isVirtual) return false
            val sources = device.sources
            return sources and InputDevice.SOURCE_GAMEPAD == InputDevice.SOURCE_GAMEPAD ||
                sources and InputDevice.SOURCE_JOYSTICK == InputDevice.SOURCE_JOYSTICK
        }

        /** Gamepad buttons, d-pad keys and BACK (some controllers report B as BACK). */
        private fun isControllerKey(keyCode: Int) = KeyEvent.isGamepadButton(keyCode) || keyCode in listOf(
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_LEFT,
            KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_BACK,
        )
    }
}

/** Human-readable names for controls, shown in the mapping screen and the log. */
object ControlNames {
    fun short(control: String): String {
        Controls.keyCodeOf(control)?.let { code ->
            return when (code) {
                KeyEvent.KEYCODE_DPAD_UP -> "D-pad ↑"
                KeyEvent.KEYCODE_DPAD_DOWN -> "D-pad ↓"
                KeyEvent.KEYCODE_DPAD_LEFT -> "D-pad ←"
                KeyEvent.KEYCODE_DPAD_RIGHT -> "D-pad →"
                KeyEvent.KEYCODE_DPAD_CENTER -> "D-pad center"
                KeyEvent.KEYCODE_BACK -> "Back"
                KeyEvent.KEYCODE_BUTTON_THUMBL -> "L3"
                KeyEvent.KEYCODE_BUTTON_THUMBR -> "R3"
                KeyEvent.KEYCODE_BUTTON_MODE -> "Home"
                else -> KeyEvent.keyCodeToString(code).removePrefix("KEYCODE_BUTTON_").removePrefix("KEYCODE_")
                    .lowercase().replaceFirstChar { it.uppercase() }
                    .let { if (it.length <= 2) it.uppercase() else it }
            }
        }
        val (axis, positive) = Controls.axisOf(control) ?: return control
        val sign = { neg: String, pos: String -> if (positive) pos else neg }
        return when (axis) {
            "HAT_X" -> "D-pad ${sign("←", "→")}"
            "HAT_Y" -> "D-pad ${sign("↑", "↓")}"
            "X" -> "Left stick ${sign("←", "→")}"
            "Y" -> "Left stick ${sign("↑", "↓")}"
            "Z", "RX" -> "Right stick ${sign("←", "→")}"
            "RZ", "RY" -> "Right stick ${sign("↑", "↓")}"
            "LTRIGGER" -> "L2"
            "RTRIGGER" -> "R2"
            else -> "$axis ${if (positive) "+" else "−"}"
        }
    }

    fun binding(binding: List<String>): String = binding.joinToString(" + ") { short(it) }
}
