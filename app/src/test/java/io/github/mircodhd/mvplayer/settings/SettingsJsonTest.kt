// SPDX-FileCopyrightText: 2026 MircoDHD
// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.mircodhd.mvplayer.settings

import io.github.mircodhd.mvplayer.input.ControllerMapping
import io.github.mircodhd.mvplayer.input.Controls
import io.github.mircodhd.mvplayer.input.InputAction
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsJsonTest {
    @Test
    fun globalRoundTrip() {
        val mapping = ControllerMapping.DEFAULT.withAdded(InputAction.OK, listOf(Controls.key(Controls.KEY_BUTTON_X)))
        val settings = GlobalSettings(
            frameRateCap = 0,
            mapping = mapping,
            overlay = OverlaySettings(OverlayMode.NEVER, 0.3f, 1.2f, haptics = false, mirror = true, layoutId = "minimal"),
            renderer = Renderer.WEBGL2,
            hud = HudSettings(
                enabled = true, corner = HudCorner.BOTTOM_END, size = HudSize.LARGE, opacity = 0.4f,
                detailed = false, shortLabels = true, intervalMs = 250, modules = listOf("clock", "fps"), widthPercent = 50, offsetX = 12.5f, offsetY = -40f,
            ),
            titleEditUnlocked = true,
            libraryTitle = "My games",
        )
        val parsed = SettingsJson.globalFromJson(JSONObject(SettingsJson.globalToJson(settings).toString()))
        assertEquals(settings, parsed)
    }

    @Test
    fun gameRoundTripKeepsNullsAsDefault() {
        val settings = GameSettings(frameRateCap = 60, renderer = Renderer.CANVAS, overlayMirror = false)
        val parsed = SettingsJson.gameFromJson(JSONObject(SettingsJson.gameToJson(settings).toString()))
        assertEquals(settings, parsed)
        assertNull(parsed.mapping)
        assertNull(parsed.overlayHaptics)
    }

    @Test
    fun invalidValuesFallBackToDefaults() {
        val parsed = SettingsJson.globalFromJson(JSONObject("""{"frameRateCap":-5,"overlay":{"mode":"BOGUS","opacity":7}}"""))
        assertEquals(0, parsed.frameRateCap)
        assertEquals(OverlayMode.AUTO, parsed.overlay.mode)
        assertEquals(1f, parsed.overlay.opacity)
    }

    @Test
    fun resolverPrefersGameOverrides() {
        val global = GlobalSettings(frameRateCap = 60, overlay = OverlaySettings(opacity = 0.5f))
        val effective = SettingsResolver.resolve(global, GameSettings(frameRateCap = 0, overlayOpacity = 0.9f))
        assertEquals(0, effective.frameRateCap)
        assertEquals(0.9f, effective.overlay.opacity)
        assertEquals(global.overlay.scale, effective.overlay.scale)
        assertEquals(global.mapping, effective.mapping)
    }

    @Test
    fun emptyGameSettingsAreDetected() {
        assertTrue(GameSettings().isEmpty)
    }

    @Test
    fun mappingMissingActionsKeepDefaults() {
        val parsed = ControllerMapping.fromJson(JSONObject("""{"ok":[["k99"]]}"""))
        assertEquals(listOf(listOf("k99")), parsed.bindingsFor(InputAction.OK))
        assertEquals(ControllerMapping.DEFAULT.bindingsFor(InputAction.CANCEL), parsed.bindingsFor(InputAction.CANCEL))
    }

    @Test
    fun invalidHudValuesFallBackToDefaults() {
        val parsed = SettingsJson.globalFromJson(
            JSONObject("""{"renderer":"vulkan","hud":{"corner":"MIDDLE","intervalMs":123,"opacity":5,"modules":["fps","fps",""]}}"""),
        )
        assertEquals(Renderer.WEBGL, parsed.renderer)
        assertEquals(HudCorner.TOP_START, parsed.hud.corner)
        assertEquals(HudSettings.DEFAULT_INTERVAL_MS, parsed.hud.intervalMs)
        assertEquals(1f, parsed.hud.opacity)
        assertEquals(listOf("fps"), parsed.hud.modules)
    }
}
