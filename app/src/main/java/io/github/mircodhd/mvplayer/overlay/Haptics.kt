// SPDX-FileCopyrightText: 2026 MircoDHD
// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.mircodhd.mvplayer.overlay

import android.content.Context
import android.media.AudioAttributes
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import io.github.mircodhd.mvplayer.debug.DebugLog

/**
 * Short vibration for on-screen controls, played on the vibration motor directly.
 * View.performHapticFeedback depends on the system "touch feedback" setting, which many
 * phones ship turned off or very weak; game feedback follows the media vibration instead.
 */
object Haptics {
    private var logged = false

    fun click(context: Context) {
        val vibrator = vibrator(context) ?: return
        if (!vibrator.hasVibrator()) {
            logOnce("No vibrator on this device")
            return
        }
        // The tuned "click" where the device has one, else a short pulse.
        val effect = if (vibrator.areAllEffectsSupported(VibrationEffect.EFFECT_CLICK) == Vibrator.VIBRATION_EFFECT_SUPPORT_YES) {
            VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK)
        } else {
            VibrationEffect.createOneShot(PULSE_MS, VibrationEffect.DEFAULT_AMPLITUDE)
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                vibrator.vibrate(effect, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_MEDIA))
            } else {
                @Suppress("DEPRECATION") // the VibrationAttributes overload needs API 33
                vibrator.vibrate(effect, AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).build())
            }
            logOnce("Vibrating on the motor, amplitude control ${vibrator.hasAmplitudeControl()}")
        } catch (e: Exception) {
            DebugLog.w(TAG, "Vibration failed", e)
        }
    }

    private fun vibrator(context: Context): Vibrator? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION") // VibratorManager needs API 31
            context.getSystemService(Vibrator::class.java)
        }

    private fun logOnce(message: String) {
        if (logged) return
        logged = true
        DebugLog.i(TAG, message)
    }

    private const val TAG = "Haptics"
    private const val PULSE_MS = 20L
}
