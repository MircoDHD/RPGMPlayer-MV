// SPDX-FileCopyrightText: 2026 MircoDHD
// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.mircodhd.mvplayer.debug

import org.junit.Assert.assertEquals
import org.junit.Test

class ConsoleLinesTest {
    private val trace = "    at Lightmask.initialize (TerraxLighting.js:701:11)\n    at new Lightmask (TerraxLighting.js:694:22)"

    @Test
    fun singleLineMessagesStayAsTheyAre() {
        val lines = ConsoleLines()
        repeat(3) { assertEquals("Steamworks failed", lines.shorten("Steamworks failed")) }
    }

    @Test
    fun aRepeatedTraceIsShortenedAfterTheFirstTime() {
        val lines = ConsoleLines()
        assertEquals(trace, lines.shorten(trace))
        assertEquals("    at Lightmask.initialize (TerraxLighting.js:701:11) (same stack trace as before, 2x)", lines.shorten(trace))
        assertEquals("    at Lightmask.initialize (TerraxLighting.js:701:11) (same stack trace as before, 3x)", lines.shorten(trace))
    }

    @Test
    fun differentTracesAreCountedSeparately() {
        val lines = ConsoleLines()
        val other = "Error: x\n    at foo (a.js:1:1)"
        lines.shorten(trace)
        assertEquals(other, lines.shorten(other))
    }
}
