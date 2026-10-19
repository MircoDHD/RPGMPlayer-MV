// SPDX-FileCopyrightText: 2026 MircoDHD
// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.mircodhd.mvplayer.hud

import org.json.JSONObject

/** One sample from `mv/perf.js` (MvPlayer.perf.setReporting). Missing values stay null. */
data class JsStats(
    val fps: Double?,
    val displayHz: Double?,
    val logicUps: Double?,
    val frameMs: Double?,
    val maxFrameMs: Double?,
    val low1Fps: Double?,
    val slowFrames: Int?,
    val workMs: Double?,
    val maxWorkMs: Double?,
    val heapUsedMb: Int?,
    val heapLimitMb: Int?,
    val images: Int?,
    val imageMegapixels: Double?,
    val rendererType: String?,
    val gpu: String?,
    val rendererFallback: Boolean,
    val paused: Boolean,
    /** Target gap between frames at the current frame rate cap; 0 = uncapped. */
    val frameIntervalMs: Double,
    /** Gaps between rendered frames in this sample, in ms. */
    val frameGaps: List<Float>,
) {
    companion object {
        fun parse(json: JSONObject): JsStats {
            val heap = json.optJSONObject("heapMb")
            val images = json.optJSONObject("images")
            val renderer = json.optJSONObject("renderer")
            val gaps = json.optJSONArray("gaps")
            return JsStats(
                fps = json.optDoubleOrNull("fps"),
                displayHz = json.optDoubleOrNull("displayHz"),
                logicUps = json.optDoubleOrNull("logicUps"),
                frameMs = json.optDoubleOrNull("frameMs"),
                maxFrameMs = json.optDoubleOrNull("maxFrameMs"),
                low1Fps = json.optDoubleOrNull("low1Fps"),
                slowFrames = json.optIntOrNull("slowFrames"),
                workMs = json.optDoubleOrNull("workMs"),
                maxWorkMs = json.optDoubleOrNull("maxWorkMs"),
                heapUsedMb = heap?.optIntOrNull("used"),
                heapLimitMb = heap?.optIntOrNull("limit"),
                images = images?.optIntOrNull("items"),
                imageMegapixels = images?.optDoubleOrNull("megapixels"),
                rendererType = renderer?.optStringOrNull("type"),
                gpu = renderer?.optStringOrNull("gpu"),
                rendererFallback = renderer?.optStringOrNull("fallback") != null,
                paused = json.optBoolean("paused", false),
                frameIntervalMs = json.optDoubleOrNull("frameIntervalMs") ?: 0.0,
                frameGaps = gaps?.let { a -> (0 until a.length()).map { a.optDouble(it).toFloat() }.filter { it > 0 } }.orEmpty(),
            )
        }

        private fun JSONObject.optDoubleOrNull(key: String): Double? =
            if (has(key) && !isNull(key)) optDouble(key).takeUnless { it.isNaN() } else null

        private fun JSONObject.optIntOrNull(key: String): Int? = if (has(key) && !isNull(key)) optInt(key) else null

        private fun JSONObject.optStringOrNull(key: String): String? =
            if (has(key) && !isNull(key)) optString(key).takeIf { it.isNotEmpty() } else null
    }
}

/** Values the app reads from Android itself. */
data class DeviceStats(
    val batteryPercent: Int? = null,
    val charging: Boolean? = null,
    val batteryTempC: Float? = null,
    /** PowerManager.THERMAL_STATUS_* */
    val thermalStatus: Int? = null,
    /** PowerManager.getThermalHeadroom: 1.0 = severe throttling expected. */
    val thermalHeadroom: Float? = null,
    /** Proportional set size of the app process (the WebView renderer runs separately). */
    val appPssMb: Int? = null,
    val displayHz: Float? = null,
)

/** Everything the HUD shows at one moment; immutable, replaced on every sample. */
data class HudSnapshot(
    val js: JsStats?,
    val device: DeviceStats,
    /** Recent frame gaps in ms, oldest first, for the frame time graph. */
    val frameHistory: List<Float>,
    val clockMillis: Long,
    val sessionMs: Long,
    /** Time the HUD itself took for its last update (parse, merge) and draw, in ms. */
    val costMs: Double?,
) {
    companion object {
        val EMPTY = HudSnapshot(null, DeviceStats(), emptyList(), 0, 0, null)
    }
}

/** Rolling window of frame gaps for the graph. */
class FrameHistory(private val capacity: Int = 180) {
    private val values = ArrayDeque<Float>(capacity)

    fun add(gaps: List<Float>) {
        for (gap in gaps) {
            if (values.size == capacity) values.removeFirst()
            values.addLast(gap)
        }
    }

    fun clear() = values.clear()

    fun snapshot(): List<Float> = values.toList()
}
