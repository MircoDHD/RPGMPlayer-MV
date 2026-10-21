// SPDX-FileCopyrightText: 2026 MircoDHD
// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.mircodhd.mvplayer.game

import io.github.mircodhd.mvplayer.storage.FileNode
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
        val index = FileIndex.build(FileNode(root))
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
    fun encryptedPlainFilesDecryptAgain() {
        val key = MvDecrypter.parseKey("00112233445566778899aabbccddeeff")!!
        val plain = ByteArray(100) { (it * 7).toByte() }
        assertArrayEquals(plain, MvDecrypter.decrypt(MvDecrypter.encrypt(plain, key), key))
        assertArrayEquals(ByteArray(3) { 1 }, MvDecrypter.decrypt(MvDecrypter.encrypt(ByteArray(3) { 1 }, key), key))
    }

    @Test
    fun fontFilesAreRecognisedBySignature() {
        val ttf = byteArrayOf(0, 1, 0, 0) + ByteArray(20)
        assertEquals(true, io.github.mircodhd.mvplayer.fonts.FontLibrary.isFont(ttf))
        assertEquals(true, io.github.mircodhd.mvplayer.fonts.FontLibrary.isFont("wOF2".toByteArray() + ByteArray(20)))
        assertEquals(false, io.github.mircodhd.mvplayer.fonts.FontLibrary.isFont("PK\u0003\u0004".toByteArray() + ByteArray(20)))
    }

    @Test
    fun decrypterRejectsWrongHeader() {
        val key = MvDecrypter.parseKey("000102030405060708090a0b0c0d0e0f")!!
        assertNull(MvDecrypter.decrypt(ByteArray(32), key))
        assertNull(MvDecrypter.parseKey("abc"))
    }

    @Test
    fun fileIndexFindsDecomposedAndGarbledNames() {
        val root = temp.newFolder("www")
        File(root, "img/pictures").mkdirs()
        // macOS archive: "ä" stored as "a" + combining diaeresis
        File(root, "img/pictures/Ka\u0308fer.png").writeText("x")
        // UTF-8 "Ölfass" unpacked as Windows-1252
        File(root, "img/pictures/Ã–lfass.png").writeText("x")
        // Shift-JIS "タイトル" unpacked as CP437
        val garbled = String("タイトル.png".toByteArray(charset("Shift_JIS")), charset("IBM437"))
        File(root, "img/pictures/$garbled").writeText("x")
        val index = FileIndex.build(FileNode(root))
        assertEquals("Ka\u0308fer.png", index.resolve("img/pictures/Käfer.png")?.name)
        assertEquals("Ã–lfass.png", index.resolve("img/pictures/Ölfass.png")?.name)
        assertEquals(garbled, index.resolve("img/pictures/タイトル.png")?.name)
        assertNull(index.resolve("img/pictures/Missing.png"))
    }

    @Test
    fun mojibakeRepairNeedsAStrictRoundTrip() {
        assertEquals(emptySet<String>(), MojibakeNames.repairs("plain.png"))
        assertEquals(true, "Ölfass.png" in MojibakeNames.repairs("Ã–lfass.png"))
    }
}
