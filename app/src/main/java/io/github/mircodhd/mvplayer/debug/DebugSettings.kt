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
    /** Seconds between JS stats snapshots (FPS, heap, audio); 0 disables them. */
    val statsIntervalSeconds: Int,
    /** Allow attaching chrome://inspect to the game WebView. */
    val remoteDebugging: Boolean,
) {
    companion object {
        private const val PREFS = "debug"

        fun load(context: Context): DebugSettings {
            val debuggable = context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            return DebugSettings(
                logToFile = prefs.getBoolean("log_to_file", true),
                verbose = prefs.getBoolean("verbose", debuggable),
                statsIntervalSeconds = prefs.getInt("stats_interval", if (debuggable) 10 else 0),
                remoteDebugging = prefs.getBoolean("remote_debugging", debuggable),
            )
        }

        fun save(context: Context, settings: DebugSettings) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
                putBoolean("log_to_file", settings.logToFile)
                putBoolean("verbose", settings.verbose)
                putInt("stats_interval", settings.statsIntervalSeconds)
                putBoolean("remote_debugging", settings.remoteDebugging)
            }
        }
    }
}
