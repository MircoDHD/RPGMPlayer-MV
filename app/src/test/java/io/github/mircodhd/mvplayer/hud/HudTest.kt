// SPDX-FileCopyrightText: 2026 MircoDHD
// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.mircodhd.mvplayer.hud

import io.github.mircodhd.mvplayer.settings.HudSettings
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HudTest {
    @Test
    fun parsesAPerfSample() {
        val json = JSONObject(
            """{"fps":59.8,"displayHz":120,"logicUps":60,"frameMs":16.72,"maxFrameMs":41.2,"slowFrames":2,
               "low1Fps":null,"workMs":3.1,"maxWorkMs":8,"gaps":[16.6,16.8,41.2],
               "heapMb":{"used":80,"total":120,"limit":512},"images":{"items":12,"megapixels":3.4},
               "renderer":{"requested":"webgl2","type":"webgl","gpu":"Some GPU","fallback":"WebGL 2 renderer could not be created"},
               "paused":true,"frameIntervalMs":33.333}""",
        )
        val stats = JsStats.parse(json)
        assertEquals(59.8, stats.fps!!, 0.001)
        assertEquals(120.0, stats.displayHz!!, 0.001)
        assertNull(stats.low1Fps)
        assertEquals(2, stats.slowFrames)
        assertEquals(listOf(16.6f, 16.8f, 41.2f), stats.frameGaps)
        assertEquals(80, stats.heapUsedMb)
        assertEquals(512, stats.heapLimitMb)
        assertEquals(12, stats.images)
        assertEquals("webgl", stats.rendererType)
        assertTrue(stats.rendererFallback)
        assertTrue(stats.paused)
        assertEquals(33.333, stats.frameIntervalMs, 0.001)
    }

    @Test
    fun emptySampleHasNoValues() {
        val stats = JsStats.parse(JSONObject("{}"))
        assertNull(stats.fps)
        assertNull(stats.heapUsedMb)
        assertFalse(stats.rendererFallback)
        assertTrue(stats.frameGaps.isEmpty())
    }

    @Test
    fun frameHistoryKeepsTheNewestValues() {
        val history = FrameHistory(capacity = 3)
        history.add(listOf(1f, 2f))
        history.add(listOf(3f, 4f))
        assertEquals(listOf(2f, 3f, 4f), history.snapshot())
        history.clear()
        assertTrue(history.snapshot().isEmpty())
    }

    @Test
    fun presetsAndRegistry() {
        val ids = HudRegistry.modules.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        assertEquals(HudRegistry.modulesOf(HudPreset.STANDARD), HudSettings.DEFAULT_MODULES)
        assertEquals(HudPreset.STANDARD, HudRegistry.presetOf(HudSettings.DEFAULT_MODULES))
        assertEquals(HudPreset.FULL, HudRegistry.presetOf(ids))
        assertNull(HudRegistry.presetOf(listOf("clock", "fps")))
        // Short tags are unique so every abbreviation maps to one module.
        val tags = HudRegistry.modules.map { it.shortLabel }
        assertEquals(tags.size, tags.toSet().size)
        // Unknown ids (from another app version) are skipped, the order is kept.
        assertEquals(listOf("clock", "fps"), HudRegistry.resolve(listOf("clock", "gone", "fps")).map { it.id })
    }

    @Test
    fun formatting() {
        assertEquals("0:42", duration(42_000))
        assertEquals("1:02:03", duration(3_723_000))
        assertEquals("WebGL 2", rendererName("webgl2"))
        assertNull(rendererName(null))
        assertEquals("Adreno 740", shortGpu("Adreno (TM) 740"))
        assertEquals("Mali-G715", shortGpu("ANGLE (ARM, Mali-G715, OpenGL ES 3.2)"))
    }
}
