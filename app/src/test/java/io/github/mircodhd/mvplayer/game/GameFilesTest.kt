// SPDX-FileCopyrightText: 2026 MircoDHD
// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.mircodhd.mvplayer.game

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class GameFilesTest {
    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun fileIndexFallsBackToCaseInsensitiveLookup() {
        val root = temp.newFolder("www")
        File(root, "img/sv_actors").mkdirs()
        File(root, "img/sv_actors/Penance1_1.rpgmvp").writeText("x")
        File(root, "__MACOSX").mkdirs()
        File(root, "__MACOSX/junk.png").writeText("x")
        File(root, ".DS_Store").writeText("x")
        val index = FileIndex.build(root)
        assertEquals(1, index.size)
        assertEquals("Penance1_1.rpgmvp", index.resolve("img/sv_actors/penance1_1.rpgmvp")?.name)
        assertNull(index.resolve("__MACOSX/junk.png"))
    }

    @Test
    fun decrypterRestoresThePayload() {
        val key = MvDecrypter.parseKey("000102030405060708090a0b0c0d0e0f")!!
        val payload = ByteArray(40) { it.toByte() }
        val header = byteArrayOf(0x52, 0x50, 0x47, 0x4D, 0x56, 0, 0, 0, 0, 3, 1, 0, 0, 0, 0, 0)
        val encrypted = header + payload.mapIndexed { i, b -> if (i < 16) (b.toInt() xor key[i].toInt()).toByte() else b }
        assertArrayEquals(payload, MvDecrypter.decrypt(encrypted, key))
    }

    @Test
    fun decrypterRejectsWrongHeader() {
        val key = MvDecrypter.parseKey("000102030405060708090a0b0c0d0e0f")!!
        assertNull(MvDecrypter.decrypt(ByteArray(32), key))
        assertNull(MvDecrypter.parseKey("abc"))
    }
}
