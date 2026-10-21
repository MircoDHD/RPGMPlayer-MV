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
import java.util.concurrent.Executors

/**
 * Reads battery, thermal and memory values from Android, each at its own pace: battery on
 * every call (sticky broadcast, cheap), thermal status and headroom every 2 s (Android
 * rate-limits the headroom), process memory every 5 s on a background thread. Used by the
 * performance HUD and by the periodic performance line in the log. Call [sample] on one
 * thread (the main thread).
 */
class DeviceSampler(private val context: Context, private val displayHz: () -> Float?) {
    private val powerManager = context.getSystemService(PowerManager::class.java)
    private val memoryThread = Executors.newSingleThreadExecutor { r -> Thread(r, "DeviceMemory").apply { isDaemon = true } }

    private var thermalAt = 0L
    private var thermalStatus: Int? = null
    private var thermalHeadroom: Float? = null
    private var memoryAt = 0L
    @Volatile private var appPssMb: Int? = null

    fun sample(): DeviceStats {
        val now = SystemClock.elapsedRealtime()
        if (thermalAt == 0L || now - thermalAt >= THERMAL_PERIOD_MS) {
            thermalAt = now
            thermalStatus = powerManager?.currentThermalStatus
            // NaN when asked too often or when the device does not support it.
            thermalHeadroom = powerManager?.getThermalHeadroom(THERMAL_FORECAST_S)?.takeUnless { it.isNaN() }
        }
        if (memoryAt == 0L || now - memoryAt >= MEMORY_PERIOD_MS) {
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

    fun close() {
        memoryThread.shutdownNow()
    }

    private companion object {
        const val THERMAL_PERIOD_MS = 2_000L
        const val MEMORY_PERIOD_MS = 5_000L
        const val THERMAL_FORECAST_S = 10
    }
}
