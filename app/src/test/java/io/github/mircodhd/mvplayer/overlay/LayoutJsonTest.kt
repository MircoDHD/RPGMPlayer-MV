// SPDX-FileCopyrightText: 2026 MircoDHD
// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.mircodhd.mvplayer.overlay

import io.github.mircodhd.mvplayer.input.InputAction
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class LayoutJsonTest {
    private val presetDir = File("src/main/assets/gamepad/presets")

    private fun preset(name: String) = LayoutJson.parse(JSONObject(File(presetDir, "$name.json").readText()), builtIn = true)

    @Test
    fun allPresetsParseAndAreUsable() {
        for (name in LayoutRepository.PRESET_ORDER) {
            val layout = preset(name)
            assertEquals(name, layout.id)
            assertTrue("$name needs a name key", layout.nameKey != null)
            assertTrue("$name needs OK", layout.controls.any { it.action == InputAction.OK })
            assertTrue("$name needs Cancel", layout.controls.any { it.action == InputAction.CANCEL })
            assertTrue("$name needs Menu", layout.controls.any { it.action == InputAction.MENU })
            assertTrue("$name needs the quick menu", layout.controls.any { it.action == InputAction.QUICK_MENU })
            assertTrue("$name needs directions", layout.controls.any { it.type == ControlType.DPAD || it.type == ControlType.STICK })
        }
    }

    @Test
    fun presetsStayOnScreenAtCommonAspectRatios() {
        for (name in LayoutRepository.PRESET_ORDER) {
            for (aspect in listOf(16f / 9f, 20f / 9f, 4f / 3f)) {
                val height = 1000f
                val width = height * aspect
                for (c in preset(name).controls) {
                    val (cx, cy) = c.center(width, height)
                    val half = c.size * height / 2
                    assertTrue("$name/${c.id} off screen at $aspect", cx - half >= 0 && cx + half <= width)
                    assertTrue("$name/${c.id} off screen at $aspect", cy - half * c.aspect.second >= 0 && cy + half * c.aspect.second <= height)
                }
            }
        }
    }

    @Test
    fun roundTripKeepsEverything() {
        val layout = preset("dualanalog")
        assertEquals(layout, LayoutJson.parse(JSONObject(LayoutJson.toJson(layout).toString()), builtIn = true))
    }

    @Test
    fun mirroringSwapsSides() {
        val dpad = preset("console16").mirrored().controls.first { it.id == "dpad" }
        assertEquals(Anchor.BOTTOM_END, dpad.anchor)
        val (cx, _) = dpad.center(2000f, 1000f)
        assertTrue(cx > 1000f)
    }

    @Test(expected = IllegalArgumentException::class)
    fun buttonsWithoutActionAreRejected() {
        LayoutJson.parse(
            JSONObject("""{"id":"x","controls":[{"id":"a","type":"button","anchor":"bottom_end","x":0.1,"y":0.1,"size":0.1}]}"""),
            builtIn = false,
        )
    }
}
