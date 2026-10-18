// SPDX-FileCopyrightText: 2026 MircoDHD
// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.mircodhd.mvplayer.settings

import io.github.mircodhd.mvplayer.overlay.Anchor
import io.github.mircodhd.mvplayer.overlay.ControlType
import io.github.mircodhd.mvplayer.overlay.GamepadLayout
import io.github.mircodhd.mvplayer.overlay.OverlayControl
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsBackupTest {
    private val layout = GamepadLayout(
        id = "custom-1234",
        name = "Mine",
        nameKey = null,
        basedOn = "console16",
        controls = listOf(OverlayControl("dpad", ControlType.DPAD, Anchor.BOTTOM_START, 0.3f, 0.3f, 0.38f)),
        builtIn = false,
    )

    @Test
    fun exportRoundTrip() {
        val contents = SettingsBackup.Contents(
            global = GlobalSettings(frameRateCap = 0, libraryTitle = "Shelf", titleEditUnlocked = true),
            layouts = listOf(layout),
            games = listOf(SettingsBackup.GameRef("Fear & Hunger", "Games/Fear & Hunger", GameSettings(overlayOpacity = 0.25f))),
        )
        val json = JSONObject(SettingsBackup.toJson("0.3.3", contents).toString())
        assertEquals(contents, SettingsBackup.fromJson(json))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsOtherFiles() {
        SettingsBackup.fromJson(JSONObject("""{"format":"something-else"}"""))
    }

    @Test
    fun matchesByLocationThenTitle() {
        val imported = listOf(
            SettingsBackup.GameRef("Fear & Hunger", "Games/Fear & Hunger", GameSettings(frameRateCap = 0)),
            SettingsBackup.GameRef("TERMINA", "Old/TERMINA", GameSettings(overlayMirror = true)),
            SettingsBackup.GameRef("Missing", "Games/Missing", GameSettings(overlayHaptics = false)),
        )
        val library = listOf(
            Triple("a", "Fear and Hunger (renamed)", "Games/Fear & Hunger"),
            Triple("b", "Termina", "Games/Termina"),
        )
        val (matches, unmatched) = SettingsBackup.match(imported, library)
        assertEquals(GameSettings(frameRateCap = 0), matches["a"])
        assertEquals(GameSettings(overlayMirror = true), matches["b"])
        assertEquals(listOf("Missing"), unmatched)
    }
}
