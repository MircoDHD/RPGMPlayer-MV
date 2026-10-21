// SPDX-FileCopyrightText: 2026 MircoDHD
// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.mircodhd.mvplayer.web

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class JsonRepairTest {
    private fun repaired(text: String) = JsonRepair.repair(text.toByteArray())?.bytes?.toString(Charsets.UTF_8)

    @Test
    fun validFilesAreLeftAlone() {
        assertNull(JsonRepair.repair("""{"a":[1,2],"url":"http://x/y","t":"a, ]"}""".toByteArray()))
    }

    @Test
    fun removesBomCommentsAndTrailingCommas() {
        val text = "\uFEFF{\n  // note\n  \"a\": [1, 2, ],\n  /* block */ \"b\": \"x // not a comment\",\n}"
        val result = JsonRepair.repair(text.toByteArray())!!
        val json = JSONObject(result.bytes.toString(Charsets.UTF_8))
        assertEquals(2, json.getJSONArray("a").length())
        assertEquals("x // not a comment", json.getString("b"))
        assertEquals(listOf("byte order mark", "comments", "trailing commas"), result.changes)
    }

    @Test
    fun keepsEscapedQuotesAndUtf8() {
        assertEquals("""{"n":"Kä\"fer,]"}""", repaired("""{"n":"Kä\"fer,]",}"""))
    }
}
