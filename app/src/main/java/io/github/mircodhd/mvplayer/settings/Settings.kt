// SPDX-FileCopyrightText: 2026 MircoDHD
// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.mircodhd.mvplayer.settings

import io.github.mircodhd.mvplayer.input.ControllerMapping
import org.json.JSONObject

/** When the on-screen controls are shown. */
enum class OverlayMode { AUTO, ALWAYS, NEVER }

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
    /** Unlocked by tapping the version ten times; allows [libraryTitle]. */
    val titleEditUnlocked: Boolean = false,
    /** Own name shown at the top of the library instead of the app name; null = app name. */
    val libraryTitle: String? = null,
) {
    companion object {
        const val DEFAULT_FRAME_RATE_CAP = 60
        const val MAX_TITLE_LENGTH = 40
    }
}

/** Per-game overrides; null means "use the global default". */
data class GameSettings(
    val frameRateCap: Int? = null,
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
    val mapping: ControllerMapping,
    val overlay: OverlaySettings,
)

object SettingsResolver {
    fun resolve(global: GlobalSettings, game: GameSettings): EffectiveSettings = EffectiveSettings(
        frameRateCap = game.frameRateCap ?: global.frameRateCap,
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
        .put("titleEditUnlocked", s.titleEditUnlocked)
        .apply { s.libraryTitle?.let { put("libraryTitle", it) } }

    fun globalFromJson(json: JSONObject): GlobalSettings {
        val defaults = GlobalSettings()
        val o = json.optJSONObject("overlay") ?: JSONObject()
        val d = defaults.overlay
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
            titleEditUnlocked = json.optBoolean("titleEditUnlocked", false),
            libraryTitle = json.optString("libraryTitle").trim().take(GlobalSettings.MAX_TITLE_LENGTH).ifBlank { null },
        )
    }

    fun gameToJson(s: GameSettings): JSONObject = JSONObject().apply {
        put("version", 1)
        s.frameRateCap?.let { put("frameRateCap", it) }
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
        mapping = json.optJSONObject("mapping")?.let(ControllerMapping::fromJson),
        overlayMode = enumOrNull<OverlayMode>(json.optString("overlayMode")),
        overlayOpacity = json.optDoubleOrNull("overlayOpacity")?.toFloat()?.coerceIn(OverlaySettings.OPACITY_RANGE),
        overlayScale = json.optDoubleOrNull("overlayScale")?.toFloat()?.coerceIn(OverlaySettings.SCALE_RANGE),
        overlayHaptics = if (json.has("overlayHaptics")) json.optBoolean("overlayHaptics") else null,
        overlayMirror = if (json.has("overlayMirror")) json.optBoolean("overlayMirror") else null,
        overlayLayoutId = json.optString("overlayLayoutId").ifBlank { null },
    )

    private inline fun <reified T : Enum<T>> enumOrNull(name: String): T? =
        enumValues<T>().firstOrNull { it.name == name }

    private fun JSONObject.optIntOrNull(key: String): Int? = if (has(key)) optInt(key) else null

    private fun JSONObject.optDoubleOrNull(key: String): Double? =
        if (has(key)) optDouble(key).takeUnless { it.isNaN() } else null
}
