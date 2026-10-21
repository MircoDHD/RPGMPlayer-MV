// SPDX-FileCopyrightText: 2026 MircoDHD
// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.mircodhd.mvplayer.saves

import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File

class LzStringTest {
    /** Generated with RPG Maker MV's own js/libs/lz-string.js (see the vectors file). */
    private val vectors = JSONArray(File("src/test/resources/lzstring-mv.json").readText())

    @Test
    fun matchesMvOutputExactly() {
        for (i in 0 until vectors.length()) {
            val v = vectors.getJSONObject(i)
            assertEquals("vector $i", v.getString("base64"), LzString.compressToBase64(v.getString("input")))
        }
    }

    @Test
    fun readsMvOutput() {
        for (i in 0 until vectors.length()) {
            val v = vectors.getJSONObject(i)
            assertEquals("vector $i", v.getString("input"), LzString.decompressFromBase64(v.getString("base64")))
        }
    }

    @Test
    fun rejectsGarbage() {
        assertNull(LzString.decompressFromBase64("!!!!"))
    }
}
