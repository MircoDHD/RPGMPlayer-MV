// SPDX-FileCopyrightText: 2026 MircoDHD
// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.mircodhd.mvplayer.hud

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Debug
import android.os.PowerManager
import android.os.SystemClock
import io.github.mircodhd.mvplayer.debug.DebugLog
import org.json.JSONObject
import java.util.concurrent.Executors

/**
 * Collects samples for the performance HUD: JavaScript stats arrive from the game at the
 * HUD's update rate, Android values are read alongside at their own, slower pace (battery
 * every sample, thermal every 2 s, process memory every 5 s on a background thread).
 * Call [onJsStats] on the main thread; it returns the new snapshot.
 */
class HudDataHub(
    private val context: Context,
    /** Foreground play time of this session so far. */
    private val sessionMs: () -> Long,
    private val displayHz: () -> Float?,
) {
    private val powerManager = context.getSystemService(PowerManager::class.java)
    private val history = FrameHistory()
    private val memoryThread = Executors.newSingleThreadExecutor { r -> Thread(r, "HudMemory").apply { isDaemon = true } }

    private var thermalAt = 0L
    private var thermalStatus: Int? = null
    private var thermalHeadroom: Float? = null
    private var memoryAt = 0L
    @Volatile private var appPssMb: Int? = null
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
        val device = readDevice()
        val costMs = (System.nanoTime() - start + lastDrawNanos) / 1e6
        return HudSnapshot(js, device, history.snapshot(), System.currentTimeMillis(), sessionMs(), costMs)
    }

    /** The panel reports how long drawing took, shown with the next sample. */
    fun reportDrawNanos(nanos: Long) {
        lastDrawNanos = nanos
    }

    fun reset() {
        history.clear()
    }

    fun close() {
        memoryThread.shutdownNow()
    }

    private fun readDevice(): DeviceStats {
        val now = SystemClock.elapsedRealtime()
        if (now - thermalAt >= THERMAL_PERIOD_MS) {
            thermalAt = now
            thermalStatus = powerManager?.currentThermalStatus
            // Android rate-limits this and answers NaN when asked too often or unsupported.
            thermalHeadroom = powerManager?.getThermalHeadroom(THERMAL_FORECAST_S)?.takeUnless { it.isNaN() }
        }
        if (now - memoryAt >= MEMORY_PERIOD_MS) {
            memoryAt = now
            memoryThread.execute { appPssMb = (Debug.getPss() / 1024).toInt() }
        }
        // Sticky broadcast: registering without a receiver just returns the last value.
        val battery = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = battery?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = battery?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val status = battery?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val temp = battery?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE) ?: Int.MIN_VALUE
        return DeviceStats(
            batteryPercent = if (level >= 0 && scale > 0) level * 100 / scale else null,
            charging = if (status >= 0) status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL else null,
            batteryTempC = if (temp != Int.MIN_VALUE) temp / 10f else null,
            thermalStatus = thermalStatus,
            thermalHeadroom = thermalHeadroom,
            appPssMb = appPssMb,
            displayHz = displayHz(),
        )
    }

    private companion object {
        const val TAG = "Hud"
        const val THERMAL_PERIOD_MS = 2_000L
        const val MEMORY_PERIOD_MS = 5_000L
        const val THERMAL_FORECAST_S = 10
    }
}
