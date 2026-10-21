// SPDX-FileCopyrightText: 2026 MircoDHD
// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.mircodhd.mvplayer.debug

import android.content.Context
import android.content.pm.ApplicationInfo
import androidx.core.content.edit

/**
 * Debug switches. Stored in SharedPreferences so a settings screen can toggle them later;
 * until then the defaults apply: everything on in debug builds, basic logging in release.
 */
data class DebugSettings(
    /** Write a session log file in addition to logcat. */
    val logToFile: Boolean,
    /** Log every served file, raw controller events, sound effects and periodic stats. */
    val verbose: Boolean,
    /**
     * Seconds between performance snapshots in the log (frame rate, frame times, memory,
     * battery, thermal); 0 disables them. One minute in release builds, so a log shows when
     * and why a game slowed down.
     */
    val statsIntervalSeconds: Int,
    /** Allow attaching chrome://inspect to the game WebView. */
    val remoteDebugging: Boolean,
) {
    companion object {
        private const val PREFS = "debug"
        private const val KEY_STATS = "stats_interval_2"

        fun load(context: Context): DebugSettings {
            val debuggable = context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            return DebugSettings(
                logToFile = prefs.getBoolean("log_to_file", true),
                verbose = prefs.getBoolean("verbose", debuggable),
                // New key: older versions stored 0 here whenever another switch was saved.
                statsIntervalSeconds = prefs.getInt(KEY_STATS, if (debuggable) 10 else 60),
                remoteDebugging = prefs.getBoolean("remote_debugging", debuggable),
            )
        }

        fun save(context: Context, settings: DebugSettings) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
                putBoolean("log_to_file", settings.logToFile)
                putBoolean("verbose", settings.verbose)
                putInt(KEY_STATS, settings.statsIntervalSeconds)
                putBoolean("remote_debugging", settings.remoteDebugging)
            }
        }
    }
}
