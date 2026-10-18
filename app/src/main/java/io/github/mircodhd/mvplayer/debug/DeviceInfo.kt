// SPDX-FileCopyrightText: 2026 MircoDHD
// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.mircodhd.mvplayer.debug

import android.app.ActivityManager
import android.content.Context
import android.hardware.display.DisplayManager
import android.os.Build
import android.view.Display
import android.webkit.WebView

/** One-off facts about the device and app, written at the top of every session log. */
object DeviceInfo {
    fun log(context: Context) {
        @Suppress("DEPRECATION") // the flags overload needs API 33
        val app = runCatching { context.packageManager.getPackageInfo(context.packageName, 0) }.getOrNull()
        val webView = WebView.getCurrentWebViewPackage()
        val memory = ActivityManager.MemoryInfo().also {
            context.getSystemService(ActivityManager::class.java).getMemoryInfo(it)
        }
        val metrics = context.resources.displayMetrics
        val display = context.getSystemService(DisplayManager::class.java).getDisplay(Display.DEFAULT_DISPLAY)

        DebugLog.i("Session", "${context.packageName} ${app?.versionName} (${app?.longVersionCode})")
        DebugLog.i("Session", "Device: ${Build.MANUFACTURER} ${Build.MODEL} (${Build.DEVICE}), " +
            "Android ${Build.VERSION.RELEASE} / SDK ${Build.VERSION.SDK_INT}, patch ${Build.VERSION.SECURITY_PATCH}")
        val soc = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) "${Build.SOC_MANUFACTURER} ${Build.SOC_MODEL}" else Build.HARDWARE
        DebugLog.i("Session", "ABIs: ${Build.SUPPORTED_ABIS.joinToString()}, SoC: $soc")
        DebugLog.i("Session", "WebView: ${webView?.packageName} ${webView?.versionName}")
        DebugLog.i("Session", "RAM: ${memory.totalMem / MB} MB total, ${memory.availMem / MB} MB free, " +
            "low=${memory.lowMemory}, app heap limit ${Runtime.getRuntime().maxMemory() / MB} MB")
        DebugLog.i("Session", "Display: ${metrics.widthPixels}x${metrics.heightPixels} @${metrics.densityDpi}dpi, " +
            "${display?.refreshRate?.let { "%.0f".format(it) }} Hz")
        DebugLog.i("Session", "Log settings: ${DebugLog.settings}")
    }

    private const val MB = 1024 * 1024
}
