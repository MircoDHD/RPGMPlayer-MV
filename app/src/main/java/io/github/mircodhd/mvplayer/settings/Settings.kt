// SPDX-FileCopyrightText: 2026 MircoDHD
// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.mircodhd.mvplayer.settings

import io.github.mircodhd.mvplayer.input.ControllerMapping
import org.json.JSONArray
import org.json.JSONObject

/** When the on-screen controls are shown. */
enum class OverlayMode { AUTO, ALWAYS, NEVER }

/** Renderer PIXI uses; [id] is what `mv/renderer.js` reads from the config. */
enum class Renderer(val id: String) {
    WEBGL("webgl"),
    CANVAS("canvas"),
    WEBGL2("webgl2"),
}

enum class HudCorner { TOP_START, TOP_END, BOTTOM_START, BOTTOM_END }

enum class HudSize(val scale: Float) { SMALL(0.85f), MEDIUM(1f), LARGE(1.25f) }

/** Performance HUD drawn above the game; [modules] are module ids in display order. */
data class HudSettings(
    val enabled: Boolean = false,
    val corner: HudCorner = HudCorner.TOP_START,
    val size: HudSize = HudSize.MEDIUM,
    /** Opacity of the whole HUD (text and background), 0.2..1. */
    val opacity: Float = 0.9f,
    val detailed: Boolean = true,
    /** Short tags (FPS, HEAP, BATT …) instead of written-out names, to save space. */
    val shortLabels: Boolean = false,
    val intervalMs: Int = DEFAULT_INTERVAL_MS,
    val modules: List<String> = DEFAULT_MODULES,
    /** Widest the HUD may get, in percent of the screen width; the compact line wraps there. */
    val widthPercent: Int = 100,
    /** Where the player dragged it, in dp from [corner] (kept on screen when drawn). */
    val offsetX: Float = 0f,
    val offsetY: Float = 0f,
) {
    val moved: Boolean get() = offsetX != 0f || offsetY != 0f

    companion object {
        const val DEFAULT_INTERVAL_MS = 500
        val INTERVALS = listOf(250, 500, 1000)
        val WIDTHS = listOf(50, 75, 100)
        val OPACITY_RANGE = 0.2f..1f
        val DEFAULT_MODULES = listOf("fps", "frametime", "lows", "battery", "thermal")
    }
}

data class OverlaySettings(
    val mode: OverlayMode = OverlayMode.AUTO,
    /** 0..1 */
    val opacity: Float = 0.6f,
    /** Multiplies the size of every control. */
    val scale: Float = 1f,
    val haptics: Boolean = true,
    /** Left-handed: swaps the left and right sides of the layout. */
    val mirror: Boolean = false,
    /** Built-in preset or custom layout id. */
    val layoutId: String = DEFAULT_LAYOUT,
) {
    companion object {
        const val DEFAULT_LAYOUT = "console16"
        val OPACITY_RANGE = 0f..1f
        val SCALE_RANGE = 0.6f..1.6f
    }
}

/** Defaults for all games (Settings screen). */
data class GlobalSettings(
    /**
     * Highest frame rate the game renders at; 0 = display rate. MV advances game logic at
     * 60 steps per second but counts rendered frames as play time, so rendering at 120 Hz
     * doubles the play time and the GPU work without looking smoother.
     */
    val frameRateCap: Int = DEFAULT_FRAME_RATE_CAP,
    val mapping: ControllerMapping = ControllerMapping.DEFAULT,
    val overlay: OverlaySettings = OverlaySettings(),
    val renderer: Renderer = Renderer.WEBGL,
    val hud: HudSettings = HudSettings(),
    /** Unlocked by tapping the version ten times; allows [libraryTitle]. */
    val titleEditUnlocked: Boolean = false,
    /** Own name shown at the top of the library instead of the app name; null = app name. */
    val libraryTitle: String? = null,
) {
    companion object {
        const val DEFAULT_FRAME_RATE_CAP = 60
        /** Offered caps: battery saver, MV's own rate, the display rate (0). */
        val FRAME_RATE_CAPS = listOf(30, 60, 0)
        const val MAX_TITLE_LENGTH = 40
    }
}

/** Per-game overrides; null means "use the global default". */
data class GameSettings(
    val frameRateCap: Int? = null,
    val renderer: Renderer? = null,
    val mapping: ControllerMapping? = null,
    val overlayMode: OverlayMode? = null,
    val overlayOpacity: Float? = null,
    val overlayScale: Float? = null,
    val overlayHaptics: Boolean? = null,
    val overlayMirror: Boolean? = null,
    val overlayLayoutId: String? = null,
) {
    val isEmpty: Boolean get() = this == GameSettings()
}

/** What a running game actually uses. */
data class EffectiveSettings(
    val frameRateCap: Int,
    val renderer: Renderer,
    val hud: HudSettings,
    val mapping: ControllerMapping,
    val overlay: OverlaySettings,
)

object SettingsResolver {
    fun resolve(global: GlobalSettings, game: GameSettings): EffectiveSettings = EffectiveSettings(
        frameRateCap = game.frameRateCap ?: global.frameRateCap,
        renderer = game.renderer ?: global.renderer,
        hud = global.hud,
        mapping = game.mapping ?: global.mapping,
        overlay = OverlaySettings(
            mode = game.overlayMode ?: global.overlay.mode,
            opacity = game.overlayOpacity ?: global.overlay.opacity,
            scale = game.overlayScale ?: global.overlay.scale,
            haptics = game.overlayHaptics ?: global.overlay.haptics,
            mirror = game.overlayMirror ?: global.overlay.mirror,
            layoutId = game.overlayLayoutId ?: global.overlay.layoutId,
        ),
    )
}

/** JSON (de)serialisation; unknown or invalid values fall back to the defaults. */
object SettingsJson {
    fun globalToJson(s: GlobalSettings): JSONObject = JSONObject()
        .put("version", 1)
        .put("frameRateCap", s.frameRateCap)
        .put("mapping", s.mapping.toJson())
        .put("overlay", JSONObject()
            .put("mode", s.overlay.mode.name)
            .put("opacity", s.overlay.opacity.toDouble())
            .put("scale", s.overlay.scale.toDouble())
            .put("haptics", s.overlay.haptics)
            .put("mirror", s.overlay.mirror)
            .put("layoutId", s.overlay.layoutId))
        .put("renderer", s.renderer.id)
        .put("hud", JSONObject()
            .put("enabled", s.hud.enabled)
            .put("corner", s.hud.corner.name)
            .put("size", s.hud.size.name)
            .put("opacity", s.hud.opacity.toDouble())
            .put("detailed", s.hud.detailed)
            .put("shortLabels", s.hud.shortLabels)
            .put("intervalMs", s.hud.intervalMs)
            .put("modules", JSONArray(s.hud.modules))
            .put("widthPercent", s.hud.widthPercent)
            .put("offsetX", s.hud.offsetX.toDouble())
            .put("offsetY", s.hud.offsetY.toDouble()))
        .put("titleEditUnlocked", s.titleEditUnlocked)
        .apply { s.libraryTitle?.let { put("libraryTitle", it) } }

    fun globalFromJson(json: JSONObject): GlobalSettings {
        val defaults = GlobalSettings()
        val o = json.optJSONObject("overlay") ?: JSONObject()
        val d = defaults.overlay
        val h = json.optJSONObject("hud") ?: JSONObject()
        val dh = defaults.hud
        return GlobalSettings(
            frameRateCap = json.optInt("frameRateCap", defaults.frameRateCap).coerceAtLeast(0),
            mapping = json.optJSONObject("mapping")?.let(ControllerMapping::fromJson) ?: defaults.mapping,
            overlay = OverlaySettings(
                mode = enumOrNull<OverlayMode>(o.optString("mode")) ?: d.mode,
                opacity = o.optDouble("opacity", d.opacity.toDouble()).toFloat().coerceIn(OverlaySettings.OPACITY_RANGE),
                scale = o.optDouble("scale", d.scale.toDouble()).toFloat().coerceIn(OverlaySettings.SCALE_RANGE),
                haptics = o.optBoolean("haptics", d.haptics),
                mirror = o.optBoolean("mirror", d.mirror),
                layoutId = o.optString("layoutId").ifBlank { d.layoutId },
            ),
            renderer = rendererOrNull(json.optString("renderer")) ?: defaults.renderer,
            hud = HudSettings(
                enabled = h.optBoolean("enabled", dh.enabled),
                corner = enumOrNull<HudCorner>(h.optString("corner")) ?: dh.corner,
                size = enumOrNull<HudSize>(h.optString("size")) ?: dh.size,
                opacity = h.optDouble("opacity", dh.opacity.toDouble()).toFloat().coerceIn(HudSettings.OPACITY_RANGE),
                detailed = h.optBoolean("detailed", dh.detailed),
                shortLabels = h.optBoolean("shortLabels", dh.shortLabels),
                intervalMs = h.optInt("intervalMs", dh.intervalMs).takeIf { it in HudSettings.INTERVALS } ?: dh.intervalMs,
                modules = h.optJSONArray("modules")?.let { a -> (0 until a.length()).map { a.optString(it) }.filter { it.isNotBlank() }.distinct() }
                    ?: dh.modules,
                widthPercent = h.optInt("widthPercent", dh.widthPercent).takeIf { it in HudSettings.WIDTHS } ?: dh.widthPercent,
                offsetX = h.optDouble("offsetX", 0.0).toFloat().takeUnless { it.isNaN() } ?: 0f,
                offsetY = h.optDouble("offsetY", 0.0).toFloat().takeUnless { it.isNaN() } ?: 0f,
            ),
            titleEditUnlocked = json.optBoolean("titleEditUnlocked", false),
            libraryTitle = json.optString("libraryTitle").trim().take(GlobalSettings.MAX_TITLE_LENGTH).ifBlank { null },
        )
    }

    fun gameToJson(s: GameSettings): JSONObject = JSONObject().apply {
        put("version", 1)
        s.frameRateCap?.let { put("frameRateCap", it) }
        s.renderer?.let { put("renderer", it.id) }
        s.mapping?.let { put("mapping", it.toJson()) }
        s.overlayMode?.let { put("overlayMode", it.name) }
        s.overlayOpacity?.let { put("overlayOpacity", it.toDouble()) }
        s.overlayScale?.let { put("overlayScale", it.toDouble()) }
        s.overlayHaptics?.let { put("overlayHaptics", it) }
        s.overlayMirror?.let { put("overlayMirror", it) }
        s.overlayLayoutId?.let { put("overlayLayoutId", it) }
    }

    fun gameFromJson(json: JSONObject) = GameSettings(
        frameRateCap = json.optIntOrNull("frameRateCap")?.coerceAtLeast(0),
        renderer = rendererOrNull(json.optString("renderer")),
        mapping = json.optJSONObject("mapping")?.let(ControllerMapping::fromJson),
        overlayMode = enumOrNull<OverlayMode>(json.optString("overlayMode")),
        overlayOpacity = json.optDoubleOrNull("overlayOpacity")?.toFloat()?.coerceIn(OverlaySettings.OPACITY_RANGE),
        overlayScale = json.optDoubleOrNull("overlayScale")?.toFloat()?.coerceIn(OverlaySettings.SCALE_RANGE),
        overlayHaptics = if (json.has("overlayHaptics")) json.optBoolean("overlayHaptics") else null,
        overlayMirror = if (json.has("overlayMirror")) json.optBoolean("overlayMirror") else null,
        overlayLayoutId = json.optString("overlayLayoutId").ifBlank { null },
    )

    private fun rendererOrNull(id: String): Renderer? = Renderer.entries.firstOrNull { it.id == id }

    private inline fun <reified T : Enum<T>> enumOrNull(name: String): T? =
        enumValues<T>().firstOrNull { it.name == name }

    private fun JSONObject.optIntOrNull(key: String): Int? = if (has(key)) optInt(key) else null

    private fun JSONObject.optDoubleOrNull(key: String): Double? =
        if (has(key)) optDouble(key).takeUnless { it.isNaN() } else null
}
