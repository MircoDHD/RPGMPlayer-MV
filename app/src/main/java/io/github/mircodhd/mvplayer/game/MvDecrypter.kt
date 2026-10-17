// SPDX-FileCopyrightText: 2026 MircoDHD
// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.mircodhd.mvplayer.game

/**
 * Same algorithm as MV's Decrypter.decryptArrayBuffer: verify the 16-byte "RPGMV" header,
 * drop it, and XOR the first 16 bytes of the payload with the key from System.json.
 */
object MvDecrypter {
    private const val HEADER_LENGTH = 16
    private val HEADER = byteArrayOf(0x52, 0x50, 0x47, 0x4D, 0x56, 0, 0, 0, 0, 3, 1, 0, 0, 0, 0, 0)

    /** Encrypted counterparts of plain asset extensions. */
    val ENCRYPTED_EXTENSIONS = mapOf("png" to "rpgmvp", "ogg" to "rpgmvo", "m4a" to "rpgmvm")

    /** Parses System.json's encryptionKey (32 hex digits). */
    fun parseKey(hex: String?): ByteArray? {
        if (hex == null || hex.length != 32) return null
        return runCatching { ByteArray(16) { hex.substring(it * 2, it * 2 + 2).toInt(16).toByte() } }.getOrNull()
    }

    fun decrypt(data: ByteArray, key: ByteArray): ByteArray? {
        if (data.size < HEADER_LENGTH) return null
        for (i in 0 until HEADER_LENGTH) if (data[i] != HEADER[i]) return null
        val out = data.copyOfRange(HEADER_LENGTH, data.size)
        for (i in 0 until minOf(HEADER_LENGTH, out.size, key.size)) {
            out[i] = (out[i].toInt() xor key[i].toInt()).toByte()
        }
        return out
    }
}
