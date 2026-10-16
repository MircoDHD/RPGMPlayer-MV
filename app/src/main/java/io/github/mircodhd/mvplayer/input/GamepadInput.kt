package io.github.mircodhd.mvplayer.input

import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import kotlin.math.abs

/**
 * Captures hardware controllers natively and reduces them to the W3C "standard gamepad"
 * button layout (0 = A/bottom ... 15 = d-pad right, 16 = home), packed into a bitmask.
 * The game side reads that mask instead of navigator.getGamepads(), whose support in
 * Android WebView is unreliable. All connected controllers are merged into one.
 */
class GamepadInput(private val onChange: (buttons: Int) -> Unit) {
    private class DeviceState {
        var keys = 0
        var axes = 0
    }

    private val devices = HashMap<Int, DeviceState>()
    private var lastSent = 0

    fun handleKeyEvent(event: KeyEvent): Boolean {
        if (!isController(event.device)) return false
        val button = buttonFor(event.keyCode) ?: return false
        if (event.action != KeyEvent.ACTION_DOWN && event.action != KeyEvent.ACTION_UP) return true
        val state = devices.getOrPut(event.deviceId) { DeviceState() }
        val bit = 1 shl button
        state.keys = if (event.action == KeyEvent.ACTION_DOWN) state.keys or bit else state.keys and bit.inv()
        publish()
        return true
    }

    fun handleMotionEvent(event: MotionEvent): Boolean {
        if (event.action != MotionEvent.ACTION_MOVE) return false
        if (!event.isFromSource(InputDevice.SOURCE_JOYSTICK) && !event.isFromSource(InputDevice.SOURCE_GAMEPAD)) return false
        val device = event.device ?: return false
        var bits = 0

        // D-pads are often reported as a hat axis instead of key events.
        val hatX = event.getAxisValue(MotionEvent.AXIS_HAT_X)
        val hatY = event.getAxisValue(MotionEvent.AXIS_HAT_Y)
        val stickX = centered(event, device, MotionEvent.AXIS_X)
        val stickY = centered(event, device, MotionEvent.AXIS_Y)
        if (hatY < -AXIS_THRESHOLD || stickY < -AXIS_THRESHOLD) bits = bits or (1 shl DPAD_UP)
        if (hatY > AXIS_THRESHOLD || stickY > AXIS_THRESHOLD) bits = bits or (1 shl DPAD_DOWN)
        if (hatX < -AXIS_THRESHOLD || stickX < -AXIS_THRESHOLD) bits = bits or (1 shl DPAD_LEFT)
        if (hatX > AXIS_THRESHOLD || stickX > AXIS_THRESHOLD) bits = bits or (1 shl DPAD_RIGHT)

        val leftTrigger = maxOf(event.getAxisValue(MotionEvent.AXIS_LTRIGGER), event.getAxisValue(MotionEvent.AXIS_BRAKE))
        val rightTrigger = maxOf(event.getAxisValue(MotionEvent.AXIS_RTRIGGER), event.getAxisValue(MotionEvent.AXIS_GAS))
        if (leftTrigger > AXIS_THRESHOLD) bits = bits or (1 shl L2)
        if (rightTrigger > AXIS_THRESHOLD) bits = bits or (1 shl R2)

        devices.getOrPut(event.deviceId) { DeviceState() }.axes = bits
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
        var combined = 0
        for (state in devices.values) combined = combined or state.keys or state.axes
        if (combined != lastSent) {
            lastSent = combined
            onChange(combined)
        }
    }

    private fun centered(event: MotionEvent, device: InputDevice, axis: Int): Float {
        val value = event.getAxisValue(axis)
        val flat = device.getMotionRange(axis, event.source)?.flat ?: 0f
        return if (abs(value) > flat) value else 0f
    }

    private fun isController(device: InputDevice?): Boolean {
        if (device == null || device.isVirtual) return false
        val sources = device.sources
        return sources and InputDevice.SOURCE_GAMEPAD == InputDevice.SOURCE_GAMEPAD ||
            sources and InputDevice.SOURCE_JOYSTICK == InputDevice.SOURCE_JOYSTICK
    }

    private fun buttonFor(keyCode: Int): Int? = when (keyCode) {
        KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_DPAD_CENTER -> 0
        // Some controllers report their B button as BACK.
        KeyEvent.KEYCODE_BUTTON_B, KeyEvent.KEYCODE_BACK -> 1
        KeyEvent.KEYCODE_BUTTON_X -> 2
        KeyEvent.KEYCODE_BUTTON_Y -> 3
        KeyEvent.KEYCODE_BUTTON_L1 -> 4
        KeyEvent.KEYCODE_BUTTON_R1 -> 5
        KeyEvent.KEYCODE_BUTTON_L2 -> L2
        KeyEvent.KEYCODE_BUTTON_R2 -> R2
        KeyEvent.KEYCODE_BUTTON_SELECT -> 8
        KeyEvent.KEYCODE_BUTTON_START -> 9
        KeyEvent.KEYCODE_BUTTON_THUMBL -> 10
        KeyEvent.KEYCODE_BUTTON_THUMBR -> 11
        KeyEvent.KEYCODE_DPAD_UP -> DPAD_UP
        KeyEvent.KEYCODE_DPAD_DOWN -> DPAD_DOWN
        KeyEvent.KEYCODE_DPAD_LEFT -> DPAD_LEFT
        KeyEvent.KEYCODE_DPAD_RIGHT -> DPAD_RIGHT
        KeyEvent.KEYCODE_BUTTON_MODE -> 16
        else -> null
    }

    private companion object {
        const val AXIS_THRESHOLD = 0.5f
        const val L2 = 6
        const val R2 = 7
        const val DPAD_UP = 12
        const val DPAD_DOWN = 13
        const val DPAD_LEFT = 14
        const val DPAD_RIGHT = 15
    }
}
