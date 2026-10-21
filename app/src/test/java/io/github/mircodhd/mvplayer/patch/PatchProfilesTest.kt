// SPDX-FileCopyrightText: 2026 MircoDHD
// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.mircodhd.mvplayer.patch

import io.github.mircodhd.mvplayer.game.FileIndex
import io.github.mircodhd.mvplayer.game.GameInfo
import io.github.mircodhd.mvplayer.storage.FileNode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.security.MessageDigest

class PatchProfilesTest {
    @get:Rule
    val temp = TemporaryFolder()

    private fun game(root: File, plugins: List<String> = emptyList(), greenworks: Boolean = false, engine: String? = "1.6.1") =
        FileNode(root).let { node ->
            GameInfo(
                webRoot = node, folderName = "Test", title = "Fear & Hunger", engineVersion = engine,
                enabledPlugins = plugins, totalPlugins = plugins.size, pixiVersion = null, titleImages = emptyList(),
                usesGreenworks = greenworks, audioExt = ".ogg", videoExt = ".webm", encryptionKey = null,
                index = FileIndex.build(node),
            )
        }

    @Test
    fun bundledProfilesParseAndBaseAlwaysApplies() {
        val profiles = PatchProfiles.parse(File("src/main/assets/patches/profiles.json").readText())
        val root = temp.newFolder("www")
        val plain = PatchProfiles.select(game(root), profiles)
        assertEquals(listOf("mv-base"), plain.profileIds)
        assertEquals("/__mvplayer/common/player.js", plain.patches.beforeEngine.single())
        assertTrue("/__mvplayer/mv/nwjs.js" in plain.patches.beforePlugins)
        val steam = PatchProfiles.select(game(root, greenworks = true), profiles)
        assertEquals(listOf("mv-base", "greenworks-stub"), steam.profileIds)
        // Every listed patch file exists.
        profiles.flatMap { it.scripts.values.flatten() }.forEach { assertTrue(it, File("src/main/assets/patches/$it").isFile) }
    }

    @Test
    fun matchRulesNeedTitlePluginsHashAndEngine() {
        val root = temp.newFolder("www")
        File(root, "js/plugins").mkdirs()
        val plugin = File(root, "js/plugins/Fix.js").apply { writeText("var x = 1;") }
        val hash = MessageDigest.getInstance("SHA-256").digest(plugin.readBytes()).joinToString("") { "%02x".format(it) }
        val json = """{"profiles":[
            {"id":"a","match":{"title":"hunger","plugins":["Fix"],"pluginSha256":{"Fix":"$hash"},"engine":{"min":"1.6.0","max":"1.6.2"}},"afterPlugins":["fixes/a.js"]},
            {"id":"b","match":{"pluginSha256":{"Fix":"00"}},"beforePlugins":["fixes/b.js"]},
            {"id":"c","match":{"engine":{"min":"1.6.2"}},"beforePlugins":["fixes/c.js"]}
        ]}"""
        val selection = PatchProfiles.select(game(root, plugins = listOf("Fix")), PatchProfiles.parse(json))
        assertEquals(listOf("a"), selection.profileIds)
        assertEquals(listOf("/__mvplayer/fixes/a.js"), selection.patches.afterPlugins)
        assertEquals(emptyList<String>(), PatchProfiles.select(game(root), PatchProfiles.parse(json)).profileIds)
    }

    @Test
    fun gamePatchesFollowTheirListOrRunAfterPlugins() {
        val root = temp.newFolder("www")
        val folder = File(root, "mvplayer-patches").apply { mkdirs() }
        File(folder, "b.js").writeText("")
        File(folder, "a.js").writeText("")
        File(folder, "readme.txt").writeText("")
        val unlisted = UserPatches.find(FileIndex.build(FileNode(root)))
        assertEquals(listOf("a.js", "b.js"), unlisted.map { it.file })
        assertTrue(unlisted.all { it.position == PatchPosition.AFTER_PLUGINS })

        File(folder, "patches.json").writeText(
            """{"patches":[{"file":"b.js","position":"beforePlugins"},{"file":"missing.js"},{"file":"../x.js"},{"file":"a.js","position":"nowhere"}]}""",
        )
        val listed = UserPatches.find(FileIndex.build(FileNode(root)))
        assertEquals(listOf(UserPatch("mvplayer-patches/b.js", PatchPosition.BEFORE_PLUGINS)), listed)
        assertEquals("/mvplayer-patches/b.js", listed.single().url)
    }

    @Test
    fun fileIndexListsFolders() {
        val root = temp.newFolder("www")
        File(root, "data").mkdirs()
        File(root, "data/Map001.json").writeText("{}")
        File(root, "img/faces").mkdirs()
        File(root, "img/faces/Actor1.png").writeText("x")
        val index = FileIndex.build(FileNode(root))
        assertEquals(mapOf("data" to true, "img" to true), index.list(""))
        assertEquals(mapOf("faces" to true), index.list("IMG"))
        assertEquals(mapOf("Map001.json" to false), index.list("data/"))
        assertNull(index.list("audio"))
    }
}
