// SPDX-FileCopyrightText: 2026 MircoDHD
// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.mircodhd.mvplayer.saves

import java.util.Base64

/**
 * The save file format of RPG Maker MV: the LZ-based "lz-string" compression (format by
 * pieroxy, MIT) in the variant MV ships, whose compressToBase64 writes the 16-bit
 * compressed characters as big-endian bytes in standard Base64. Written for this project
 * from the format; unit-tested against output of MV's own lz-string.js.
 */
object LzString {
    /** Same output as MV's `LZString.compressToBase64`. */
    fun compressToBase64(input: String): String {
        // Like MV's version, an empty text still gets the end marker ("QAA=").
        val chars = compress(input)
        val bytes = ByteArray(chars.length * 2)
        chars.forEachIndexed { i, c ->
            bytes[2 * i] = (c.code shr 8).toByte()
            bytes[2 * i + 1] = c.code.toByte()
        }
        return Base64.getEncoder().encodeToString(bytes)
    }

    /** Reads MV's (and current lz-string's) Base64 output; null if it is not valid. */
    fun decompressFromBase64(input: String): String? {
        if (input.isEmpty()) return ""
        val clean = input.filter { it.isLetterOrDigit() || it == '+' || it == '/' }
        val padded = clean + "=".repeat((4 - clean.length % 4) % 4)
        val bytes = runCatching { Base64.getDecoder().decode(padded) }.getOrNull() ?: return null
        val count = bytes.size / 2
        if (count == 0) return null
        val values = IntArray(count) { ((bytes[2 * it].toInt() and 0xff) shl 8) or (bytes[2 * it + 1].toInt() and 0xff) }
        return decompress(count, 32768) { values[it] }
    }

    /** The compressor, writing 16 bits per output character. */
    private fun compress(input: String): String {
        val dictionary = HashMap<String, Int>()
        val toCreate = HashSet<String>()
        val out = StringBuilder()
        var value = 0
        var position = 0
        var enlargeIn = 2
        var dictSize = 3
        var numBits = 2
        var w = ""

        fun writeBit(bit: Int) {
            value = (value shl 1) or bit
            if (position == 15) {
                position = 0
                out.append(value.toChar())
                value = 0
            } else {
                position++
            }
        }

        fun writeBits(count: Int, bits: Int) {
            var v = bits
            repeat(count) {
                writeBit(v and 1)
                v = v shr 1
            }
        }

        fun emit(word: String) {
            if (word in toCreate) {
                val code = word[0].code
                if (code < 256) {
                    writeBits(numBits, 0)
                    writeBits(8, code)
                } else {
                    writeBits(numBits, 1)
                    writeBits(16, code)
                }
                enlargeIn--
                if (enlargeIn == 0) {
                    enlargeIn = 1 shl numBits
                    numBits++
                }
                toCreate.remove(word)
            } else {
                writeBits(numBits, dictionary.getValue(word))
            }
            enlargeIn--
            if (enlargeIn == 0) {
                enlargeIn = 1 shl numBits
                numBits++
            }
        }

        for (ch in input) {
            val c = ch.toString()
            if (c !in dictionary) {
                dictionary[c] = dictSize++
                toCreate += c
            }
            val wc = w + c
            if (wc in dictionary) {
                w = wc
            } else {
                emit(w)
                dictionary[wc] = dictSize++
                w = c
            }
        }
        if (w.isNotEmpty()) emit(w)
        writeBits(numBits, 2)
        // Pad the last character with zero bits.
        while (true) {
            value = value shl 1
            if (position == 15) {
                out.append(value.toChar())
                break
            }
            position++
        }
        return out.toString()
    }

    private fun decompress(length: Int, resetValue: Int, next: (Int) -> Int): String? {
        val dictionary = ArrayList<String>(listOf("", "", ""))
        var enlargeIn = 4
        var numBits = 3
        var dataValue = next(0)
        var dataPosition = resetValue
        var dataIndex = 1

        fun readBits(count: Int): Int {
            var bits = 0
            var power = 1
            repeat(count) {
                val bit = dataValue and dataPosition
                dataPosition = dataPosition shr 1
                if (dataPosition == 0) {
                    dataPosition = resetValue
                    dataValue = if (dataIndex < length) next(dataIndex) else 0
                    dataIndex++
                }
                if (bit > 0) bits = bits or power
                power = power shl 1
            }
            return bits
        }

        val first = when (readBits(2)) {
            0 -> readBits(8).toChar().toString()
            1 -> readBits(16).toChar().toString()
            else -> return ""
        }
        dictionary += first
        var w = first
        val result = StringBuilder(first)
        while (true) {
            if (dataIndex > length) return null
            var code = readBits(numBits)
            when (code) {
                0, 1 -> {
                    dictionary += readBits(if (code == 0) 8 else 16).toChar().toString()
                    code = dictionary.size - 1
                    enlargeIn--
                }
                2 -> return result.toString()
            }
            if (enlargeIn == 0) {
                enlargeIn = 1 shl numBits
                numBits++
            }
            val entry = when {
                code < dictionary.size -> dictionary[code]
                code == dictionary.size -> w + w[0]
                else -> return null
            }
            result.append(entry)
            dictionary += w + entry[0]
            enlargeIn--
            w = entry
            if (enlargeIn == 0) {
                enlargeIn = 1 shl numBits
                numBits++
            }
        }
    }
}
