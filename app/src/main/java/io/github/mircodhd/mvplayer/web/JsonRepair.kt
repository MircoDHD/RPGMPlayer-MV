// SPDX-FileCopyrightText: 2026 MircoDHD
// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.mircodhd.mvplayer.web

/**
 * Makes hand-edited data files readable for MV's strict `JSON.parse`: a UTF-8 byte order
 * mark, `//` and `/* */` comments and trailing commas before `]` or `}` are removed, all
 * outside strings. Works on UTF-8 bytes (multi-byte characters never contain ASCII
 * delimiters). [repair] returns null when nothing needed fixing, so valid files are served
 * unchanged after a single scan.
 */
object JsonRepair {
    data class Result(val bytes: ByteArray, val changes: List<String>)

    fun repair(input: ByteArray): Result? {
        val changes = LinkedHashSet<String>()
        var start = 0
        if (input.size >= 3 && input[0] == 0xEF.toByte() && input[1] == 0xBB.toByte() && input[2] == 0xBF.toByte()) {
            start = 3
            changes += "byte order mark"
        }
        if (start == 0 && !needsRepair(input)) return null
        val out = java.io.ByteArrayOutputStream(input.size)
        var i = start
        var inString = false
        while (i < input.size) {
            val b = input[i].toInt()
            if (inString) {
                out.write(b)
                if (b == '\\'.code && i + 1 < input.size) {
                    out.write(input[i + 1].toInt())
                    i += 2
                    continue
                }
                if (b == '"'.code) inString = false
                i++
                continue
            }
            when {
                b == '"'.code -> {
                    inString = true
                    out.write(b)
                    i++
                }
                b == '/'.code && i + 1 < input.size && input[i + 1] == '/'.code.toByte() -> {
                    changes += "comments"
                    while (i < input.size && input[i] != '\n'.code.toByte()) i++
                }
                b == '/'.code && i + 1 < input.size && input[i + 1] == '*'.code.toByte() -> {
                    changes += "comments"
                    i += 2
                    while (i + 1 < input.size && !(input[i] == '*'.code.toByte() && input[i + 1] == '/'.code.toByte())) i++
                    i += 2
                }
                b == ','.code && closesNext(input, i + 1) -> {
                    changes += "trailing commas"
                    i++
                }
                else -> {
                    out.write(b)
                    i++
                }
            }
        }
        return Result(out.toByteArray(), changes.toList())
    }

    /** One fast pass: is there a comment or a trailing comma outside strings? */
    private fun needsRepair(input: ByteArray): Boolean {
        var inString = false
        var i = 0
        while (i < input.size) {
            val b = input[i].toInt()
            if (inString) {
                if (b == '\\'.code) i++ else if (b == '"'.code) inString = false
            } else when (b) {
                '"'.code -> inString = true
                '/'.code -> return true
                ','.code -> if (closesNext(input, i + 1)) return true
            }
            i++
        }
        return false
    }

    /** Whether the next non-whitespace (and non-comment-start) byte closes an array or object. */
    private fun closesNext(input: ByteArray, from: Int): Boolean {
        var i = from
        while (i < input.size) {
            when (input[i].toInt()) {
                ' '.code, '\t'.code, '\r'.code, '\n'.code -> i++
                ']'.code, '}'.code -> return true
                else -> return false
            }
        }
        return false
    }
}
