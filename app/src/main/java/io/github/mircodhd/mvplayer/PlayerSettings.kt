// SPDX-FileCopyrightText: 2026 MircoDHD
// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.mircodhd.mvplayer

import android.content.Context
import androidx.core.content.edit

/** Player options. Stored in SharedPreferences so a settings screen can change them later. */
data class PlayerSettings(
    /**
     * Highest frame rate the game renders at; 0 = display rate. MV advances game logic at
     * 60 steps per second but counts rendered frames for the play time, so rendering at
     * 120 Hz doubles the play time and the GPU work without looking any smoother.
     */
    val frameRateCap: Int,
) {
    companion object {
        private const val PREFS = "player_settings"
        const val DEFAULT_FRAME_RATE_CAP = 60

        fun load(context: Context): PlayerSettings {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            return PlayerSettings(frameRateCap = prefs.getInt("frame_rate_cap", DEFAULT_FRAME_RATE_CAP))
        }

        fun save(context: Context, settings: PlayerSettings) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
                putInt("frame_rate_cap", settings.frameRateCap)
            }
        }
    }
}
