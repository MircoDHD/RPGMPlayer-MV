// SPDX-FileCopyrightText: 2026 MircoDHD
// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.mircodhd.mvplayer.input

import android.view.KeyEvent
import android.view.MotionEvent
import android.view.Window
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Waits for the next controller button (or axis direction) for the remapping screen.
 * Controls that were already held when capturing started are ignored until released.
 */
class InputCapture {
    private val input = GamepadInput(::onControls)
    private var baseline: Set<String> = emptySet()
    private var callback: ((String) -> Unit)? = null

    /** Observable for Compose: true while waiting for a button. */
    var isActive by mutableStateOf(false)
        private set

    fun start(onCaptured: (String) -> Unit) {
        baseline = input.activeControls
        callback = onCaptured
        isActive = true
    }

    fun cancel() {
        callback = null
        isActive = false
    }

    /** Swallows every key while capturing so nothing else reacts to the press. */
    fun dispatchKey(event: KeyEvent): Boolean {
        if (!isActive) return false
        input.handleKeyEvent(event)
        return true
    }

    fun dispatchMotion(event: MotionEvent): Boolean {
        if (!isActive) return false
        input.handleMotionEvent(event)
        return true
    }

    private fun onControls(controls: Set<String>) {
        baseline = baseline intersect controls
        val pressed = (controls - baseline).firstOrNull() ?: return
        baseline = controls
        val target = callback ?: return
        cancel()
        target(pressed)
    }
}

/**
 * While composed, routes key and generic motion events of [window] through [capture].
 * Works for activity windows and Compose dialog windows alike, so axes (d-pad hats) can
 * be captured inside dialogs too.
 */
@Composable
fun InterceptWindowInput(window: Window, capture: InputCapture) {
    DisposableEffect(window, capture) {
        val original = window.callback
        window.callback = object : Window.Callback by original {
            override fun dispatchKeyEvent(event: KeyEvent): Boolean =
                capture.dispatchKey(event) || original.dispatchKeyEvent(event)

            override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean =
                capture.dispatchMotion(event) || original.dispatchGenericMotionEvent(event)
        }
        onDispose { window.callback = original }
    }
}
