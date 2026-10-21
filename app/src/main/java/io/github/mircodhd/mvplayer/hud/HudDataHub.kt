// SPDX-FileCopyrightText: 2026 MircoDHD
// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.mircodhd.mvplayer.hud

import io.github.mircodhd.mvplayer.debug.DebugLog
import org.json.JSONObject

/**
 * Collects samples for the performance HUD: JavaScript stats arrive from the game at the
 * HUD's update rate; Android values come from the shared [DeviceSampler].
 * Call [onJsStats] on the main thread; it returns the new snapshot.
 */
class HudDataHub(
    private val device: DeviceSampler,
    /** Foreground play time of this session so far. */
    private val sessionMs: () -> Long,
) {
    private val history = FrameHistory()
    @Volatile private var lastDrawNanos = 0L

    fun onJsStats(json: String): HudSnapshot {
        val start = System.nanoTime()
        val js = try {
            JsStats.parse(JSONObject(json))
        } catch (e: Exception) {
            DebugLog.w(TAG, "Bad stats sample", e)
            null
        }
        if (js != null) history.add(js.frameGaps)
        val deviceStats = device.sample()
        val costMs = (System.nanoTime() - start + lastDrawNanos) / 1e6
        return HudSnapshot(js, deviceStats, history.snapshot(), System.currentTimeMillis(), sessionMs(), costMs)
    }

    /** The panel reports how long drawing took, shown with the next sample. */
    fun reportDrawNanos(nanos: Long) {
        lastDrawNanos = nanos
    }

    fun reset() {
        history.clear()
    }

    private companion object {
        const val TAG = "Hud"
    }
}
