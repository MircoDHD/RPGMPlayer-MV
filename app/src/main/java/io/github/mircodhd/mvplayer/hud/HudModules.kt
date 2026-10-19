// SPDX-FileCopyrightText: 2026 MircoDHD
// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.mircodhd.mvplayer.hud

import android.content.res.Resources
import android.os.PowerManager
import androidx.annotation.StringRes
import io.github.mircodhd.mvplayer.R
import java.util.Locale
import kotlin.math.roundToInt

/**
 * One line of the performance HUD. Adding a module means one more entry in
 * [HudRegistry.modules]; the panel, settings and presets pick it up from there.
 */
abstract class HudModule(
    val id: String,
    @StringRes val title: Int,
    @StringRes val summary: Int,
    /**
     * Tag for the abbreviated labels, in the style of PC overlays (MangoHud, Steam Deck):
     * short, upper case, the same in every language.
     */
    val shortLabel: String,
) {
    /** Text for the detailed layout, shown next to [title]; null while there is no data yet. */
    abstract fun value(data: HudSnapshot, res: Resources): String?

    /** Shorter value for the compact layout; [compact] puts the title in front of it. */
    open fun compactValue(data: HudSnapshot, res: Resources): String? = value(data, res)

    /** False where the value explains itself ("60 fps", a clock, the renderer name). */
    open val titleInCompact: Boolean get() = true

    /** False where the abbreviated line shows the bare value (frame time right after fps). */
    open val shortLabelInCompact: Boolean get() = titleInCompact

    /** The name in the detailed layout's label column. */
    fun label(res: Resources, short: Boolean): String = if (short) shortLabel else res.getString(title)

    /**
     * The module in the compact layout (one line with all modules), with the same name as
     * the detailed layout and the settings ([short]: the abbreviated tag).
     */
    fun compact(data: HudSnapshot, res: Resources, short: Boolean = false): String? =
        compactValue(data, res)?.let { value ->
            when {
                short && shortLabelInCompact -> "$shortLabel $value"
                !short && titleInCompact -> "${res.getString(title)} $value"
                else -> value
            }
        }

    /** Draws the frame time graph below the value in the detailed layout. */
    open val graph: Boolean get() = false

    /** Colour of the value; null = neutral (see [HudLevels]). */
    open fun level(data: HudSnapshot): HudLevel? = null
}

enum class HudPreset { MINIMAL, STANDARD, FULL }

object HudRegistry {
    val modules: List<HudModule> = listOf(
        object : HudModule("fps", R.string.hud_mod_fps, R.string.hud_mod_fps_summary, "FPS") {
            override fun level(data: HudSnapshot) = data.js?.fps?.let { HudLevels.fps(it, HudLevels.targetMs(data.js?.frameIntervalMs)) }
            override val titleInCompact get() = false
            override fun value(data: HudSnapshot, res: Resources) = data.js?.fps?.let { "${it.roundToInt()} fps" }
        },
        object : HudModule("frametime", R.string.hud_mod_frametime, R.string.hud_mod_frametime_summary, "FT") {
            override fun level(data: HudSnapshot) = data.js?.frameMs?.let { HudLevels.frameTime(it, HudLevels.targetMs(data.js?.frameIntervalMs)) }
            override val graph get() = true
            // Abbreviated: "60 fps 16,7 ms", as overlays show fps and frame time together.
            override val shortLabelInCompact get() = false
            override fun value(data: HudSnapshot, res: Resources) =
                data.js?.frameMs?.let { "${f1(it)} ms · max ${f1(data.js.maxFrameMs ?: it)}" }
            override fun compactValue(data: HudSnapshot, res: Resources) = data.js?.frameMs?.let { "${f1(it)} ms" }
        },
        object : HudModule("lows", R.string.hud_mod_lows, R.string.hud_mod_lows_summary, "1%") {
            override fun level(data: HudSnapshot) = data.js?.low1Fps?.let { HudLevels.fps(it, HudLevels.targetMs(data.js?.frameIntervalMs)) }
            override fun value(data: HudSnapshot, res: Resources): String? {
                val js = data.js ?: return null
                val low = js.low1Fps?.let { "${it.roundToInt()} fps" } ?: "–"
                return "$low · ${res.getString(R.string.hud_slow_frames, js.slowFrames ?: 0)}"
            }
            override fun compactValue(data: HudSnapshot, res: Resources) = data.js?.low1Fps?.let { "${it.roundToInt()} fps" }
        },
        object : HudModule("timing", R.string.hud_mod_timing, R.string.hud_mod_timing_summary, "HZ") {
            override fun level(data: HudSnapshot) = data.js?.logicUps?.let(HudLevels::logic)
            override val shortLabelInCompact get() = false
            override fun value(data: HudSnapshot, res: Resources): String? {
                val js = data.js ?: return null
                val logic = js.logicUps?.roundToInt() ?: return null
                val raf = js.displayHz?.roundToInt()
                val panel = data.device.displayHz?.roundToInt()
                return "$logic/s · ${raf ?: "–"} Hz" + (panel?.let { " (${res.getString(R.string.hud_panel_hz, it)})" } ?: "")
            }
            override fun compactValue(data: HudSnapshot, res: Resources) =
                data.js?.let { js -> js.logicUps?.let { "${it.roundToInt()}/s · ${js.displayHz?.roundToInt() ?: "–"} Hz" } }
        },
        object : HudModule("jswork", R.string.hud_mod_jswork, R.string.hud_mod_jswork_summary, "JS") {
            override fun level(data: HudSnapshot) = data.js?.workMs?.let { HudLevels.jsTime(it, HudLevels.targetMs(data.js?.frameIntervalMs)) }
            override fun value(data: HudSnapshot, res: Resources) =
                data.js?.workMs?.let { "${f1(it)} ms · max ${f1(data.js.maxWorkMs ?: it)}" }
            override fun compactValue(data: HudSnapshot, res: Resources) = data.js?.workMs?.let { "${f1(it)} ms" }
        },
        object : HudModule("heap", R.string.hud_mod_heap, R.string.hud_mod_heap_summary, "HEAP") {
            override fun level(data: HudSnapshot) = data.js?.heapUsedMb?.let { HudLevels.memory(it, data.js.heapLimitMb) }
            override fun value(data: HudSnapshot, res: Resources) =
                data.js?.heapUsedMb?.let { used -> data.js.heapLimitMb?.let { "$used / $it MB" } ?: "$used MB" }
            override fun compactValue(data: HudSnapshot, res: Resources) = data.js?.heapUsedMb?.let { "$it MB" }
        },
        object : HudModule("images", R.string.hud_mod_images, R.string.hud_mod_images_summary, "IMG") {
            override fun value(data: HudSnapshot, res: Resources) =
                data.js?.images?.let { "$it · ${f1(data.js.imageMegapixels ?: 0.0)} MP" }
            override fun compactValue(data: HudSnapshot, res: Resources) = data.js?.images?.toString()
        },
        object : HudModule("appram", R.string.hud_mod_appram, R.string.hud_mod_appram_summary, "RAM") {
            override fun value(data: HudSnapshot, res: Resources) = data.device.appPssMb?.let { "$it MB" }
        },
        object : HudModule("battery", R.string.hud_mod_battery, R.string.hud_mod_battery_summary, "BATT") {
            override fun level(data: HudSnapshot) = HudLevels.battery(data.device.batteryPercent, data.device.charging, data.device.batteryTempC)
            override fun value(data: HudSnapshot, res: Resources): String? {
                val d = data.device
                val percent = d.batteryPercent ?: return null
                val parts = mutableListOf("$percent %")
                if (d.charging == true) parts += res.getString(R.string.hud_charging)
                d.batteryTempC?.let { parts += "${f1(it.toDouble())} °C" }
                return parts.joinToString(" · ")
            }
            override fun compactValue(data: HudSnapshot, res: Resources): String? {
                val d = data.device
                val percent = d.batteryPercent ?: return null
                return "$percent %" + (if (d.charging == true) "+" else "") + (d.batteryTempC?.let { " ${it.roundToInt()} °C" } ?: "")
            }
        },
        object : HudModule("thermal", R.string.hud_mod_thermal, R.string.hud_mod_thermal_summary, "THRM") {
            override fun level(data: HudSnapshot) = HudLevels.thermal(data.device.thermalStatus, data.device.thermalHeadroom)
            override fun value(data: HudSnapshot, res: Resources): String? {
                val status = data.device.thermalStatus ?: return null
                val heat = data.device.thermalHeadroom?.let { " · " + res.getString(R.string.hud_thermal_heat, (it * 100).roundToInt()) } ?: ""
                return thermalName(status, res) + heat
            }
            override fun compactValue(data: HudSnapshot, res: Resources) = data.device.thermalStatus?.let { thermalName(it, res) }
        },
        object : HudModule("renderer", R.string.hud_mod_renderer, R.string.hud_mod_renderer_summary, "GL") {
            override val titleInCompact get() = false
            override fun value(data: HudSnapshot, res: Resources): String? {
                val js = data.js ?: return null
                val type = rendererName(js.rendererType) ?: return null
                val fallback = if (js.rendererFallback) " (${res.getString(R.string.hud_renderer_fallback)})" else ""
                return type + fallback + (js.gpu?.let { " · ${shortGpu(it)}" } ?: "")
            }
            override fun compactValue(data: HudSnapshot, res: Resources) = rendererName(data.js?.rendererType)
        },
        object : HudModule("clock", R.string.hud_mod_clock, R.string.hud_mod_clock_summary, "TIME") {
            override val titleInCompact get() = false
            override fun value(data: HudSnapshot, res: Resources): String? {
                if (data.clockMillis == 0L) return null
                return "${clock(data.clockMillis)} · ${res.getString(R.string.hud_session, duration(data.sessionMs))}"
            }
            override fun compactValue(data: HudSnapshot, res: Resources) = if (data.clockMillis == 0L) null else clock(data.clockMillis)
        },
        object : HudModule("hud", R.string.hud_mod_cost, R.string.hud_mod_cost_summary, "HUD") {
            override fun value(data: HudSnapshot, res: Resources) = data.costMs?.let { "${f2(it)} ms" }
        },
    )

    private val byId = modules.associateBy { it.id }

    fun find(id: String): HudModule? = byId[id]

    /** The given ids in order, unknown ids (from newer or older versions) skipped. */
    fun resolve(ids: List<String>): List<HudModule> = ids.mapNotNull(byId::get)

    fun modulesOf(preset: HudPreset): List<String> = when (preset) {
        HudPreset.MINIMAL -> listOf("fps")
        HudPreset.STANDARD -> listOf("fps", "frametime", "lows", "battery", "thermal")
        HudPreset.FULL -> modules.map { it.id }
    }

    /** The preset whose modules match exactly, or null for a custom selection. */
    fun presetOf(ids: List<String>): HudPreset? = HudPreset.entries.firstOrNull { modulesOf(it) == ids }
}

private fun f1(value: Double) = String.format(Locale.getDefault(), "%.1f", value)

private fun f2(value: Double) = String.format(Locale.getDefault(), "%.2f", value)

private fun clock(millis: Long) = String.format(Locale.getDefault(), "%tR", millis)

/** h:mm:ss or m:ss. */
internal fun duration(ms: Long): String {
    val s = ms / 1000
    return if (s >= 3600) {
        String.format(Locale.ROOT, "%d:%02d:%02d", s / 3600, s / 60 % 60, s % 60)
    } else {
        String.format(Locale.ROOT, "%d:%02d", s / 60, s % 60)
    }
}

internal fun rendererName(type: String?): String? = when (type) {
    "webgl2" -> "WebGL 2"
    "webgl" -> "WebGL"
    "canvas" -> "Canvas"
    else -> null
}

/** "Adreno (TM) 740" → "Adreno 740"; ANGLE strings keep their inner part. */
internal fun shortGpu(gpu: String): String {
    val inner = Regex("""ANGLE \([^,]*, ([^,(]+)""").find(gpu)?.groupValues?.get(1) ?: gpu
    return inner.replace("(TM)", "").replace("(R)", "").replace(Regex("\\s+"), " ").trim().take(32)
}

private fun thermalName(status: Int, res: Resources): String = res.getString(
    when (status) {
        PowerManager.THERMAL_STATUS_NONE -> R.string.hud_thermal_none
        PowerManager.THERMAL_STATUS_LIGHT -> R.string.hud_thermal_light
        PowerManager.THERMAL_STATUS_MODERATE -> R.string.hud_thermal_moderate
        PowerManager.THERMAL_STATUS_SEVERE -> R.string.hud_thermal_severe
        PowerManager.THERMAL_STATUS_CRITICAL -> R.string.hud_thermal_critical
        PowerManager.THERMAL_STATUS_EMERGENCY -> R.string.hud_thermal_emergency
        PowerManager.THERMAL_STATUS_SHUTDOWN -> R.string.hud_thermal_shutdown
        else -> R.string.hud_thermal_none
    },
)
