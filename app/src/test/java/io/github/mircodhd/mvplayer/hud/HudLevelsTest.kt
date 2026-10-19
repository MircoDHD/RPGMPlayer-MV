// SPDX-FileCopyrightText: 2026 MircoDHD
// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.mircodhd.mvplayer.hud

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HudLevelsTest {
    private val at60 = HudLevels.targetMs(1000.0 / 60)
    private val at30 = HudLevels.targetMs(1000.0 / 30)

    @Test
    fun frameRateIsJudgedAgainstTheCap() {
        assertEquals(HudLevel.GOOD, HudLevels.fps(30.0, at30)) // 30 fps is fine when 30 is the goal
        assertEquals(HudLevel.GOOD, HudLevels.fps(24.0, at30))
        assertEquals(HudLevel.WARN, HudLevels.fps(23.0, at30))
        assertEquals(HudLevel.BAD, HudLevels.fps(14.0, at30))
        assertEquals(HudLevel.GOOD, HudLevels.fps(60.0, at60))
        assertEquals(HudLevel.WARN, HudLevels.fps(40.0, at60))
        assertEquals(HudLevel.BAD, HudLevels.fps(29.0, at60))
        // Uncapped is judged like 60 fps.
        assertEquals(HudLevels.targetMs(1000.0 / 60), HudLevels.targetMs(0.0), 1e-9)
    }

    @Test
    fun frameTimeUnderTwentyMillisecondsIsGreenAt60() {
        assertEquals(HudLevel.GOOD, HudLevels.frameTime(16.7, at60))
        assertEquals(HudLevel.GOOD, HudLevels.frameTime(20.0, at60))
        assertEquals(HudLevel.WARN, HudLevels.frameTime(25.0, at60))
        assertEquals(HudLevel.BAD, HudLevels.frameTime(34.0, at60))
        assertEquals(HudLevel.GOOD, HudLevels.frameTime(33.4, at30))
    }

    @Test
    fun otherValuesStayNeutralUntilTheyMatter() {
        assertNull(HudLevels.logic(60.0))
        assertEquals(HudLevel.WARN, HudLevels.logic(50.0))
        assertNull(HudLevels.jsTime(4.0, at60))
        assertEquals(HudLevel.BAD, HudLevels.jsTime(17.0, at60))
        assertNull(HudLevels.memory(100, 1000))
        assertEquals(HudLevel.WARN, HudLevels.memory(750, 1000))
        assertNull(HudLevels.memory(750, null))
        assertNull(HudLevels.battery(80, false, 30f))
        assertEquals(HudLevel.WARN, HudLevels.battery(25, false, 30f))
        assertNull(HudLevels.battery(10, true, 30f)) // charging: a low level is not a warning
        assertEquals(HudLevel.BAD, HudLevels.battery(80, false, 46f))
        assertNull(HudLevels.thermal(0, 0.3f))
        assertEquals(HudLevel.WARN, HudLevels.thermal(0, 0.8f))
        assertEquals(HudLevel.BAD, HudLevels.thermal(3, 0.3f))
    }
}
