// SPDX-FileCopyrightText: 2026 MircoDHD
// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.mircodhd.mvplayer.game

import io.github.mircodhd.mvplayer.settings.GameSettings
import io.github.mircodhd.mvplayer.settings.SettingsJson
import io.github.mircodhd.mvplayer.storage.FileNode
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ModsTest {
    @get:Rule
    val temp = TemporaryFolder()

    private fun file(root: File, path: String, text: String = "x") = File(root, path).apply { parentFile.mkdirs(); writeText(text) }

    /** A game folder like the PC version, with two mods next to www as players unpack them. */
    private fun game(): File {
        val root = temp.newFolder("Game")
        file(root, "www/index.html")
        file(root, "www/data/CommonEvents.json", "game")
        file(root, "www/js/rpg_objects.js", "game")
        file(root, "www/img/pictures/A.rpgmvp", "game")
        file(root, "locales/de.pak")
        file(root, "QoL Patch/www/data/CommonEvents.json", "qol")
        file(root, "QoL Patch/www/js/rpg_objects.js", "qol")
        file(root, "Bug Fix/www/data/CommonEvents.json", "bugfix")
        file(root, "Bug Fix/www/img/pictures/a.rpgmvp", "bugfix")
        file(root, "Bug Fix/mod.json", """{"name":"Bug Fix Mod","version":"2.0"}""")
        file(root, "Notes/readme.txt")
        file(root, "mvplayer-mods/Icons/img/system/IconSet.png", "icons")
        return root
    }

    @Test
    fun findsModsNextToWwwAndInTheModFolder() {
        val root = FileNode(game())
        val webRoot = root.children().first { it.name == "www" }
        val mods = Mods.find(root, webRoot)
        assertEquals(listOf("Bug Fix", "Icons", "QoL Patch"), mods.map { it.id }.sorted())
        val bugFix = mods.first { it.id == "Bug Fix" }
        assertEquals("Bug Fix Mod", bugFix.name)
        assertEquals("2.0", bugFix.version)
    }

    @Test
    fun theUpperModWinsAndTheGameFillsTheRest() {
        val root = FileNode(game())
        val webRoot = root.children().first { it.name == "www" }
        val mods = Mods.select(Mods.find(root, webRoot), listOf("QoL Patch", "Bug Fix", "Gone"))
        val index = FileIndex.layered(mods.map { FileIndex.build(it.content) } + FileIndex.build(webRoot))
        assertEquals("qol", index.resolve("data/CommonEvents.json")!!.readText())
        assertEquals("qol", index.resolve("js/rpg_objects.js")!!.readText())
        // Case-insensitive across layers: the mod's a.rpgmvp replaces the game's A.rpgmvp.
        assertEquals("bugfix", index.resolve("img/pictures/A.rpgmvp")!!.readText())
        assertEquals("x", index.resolve("index.html")!!.readText())
    }

    @Test
    fun conflictsListFilesOfSeveralActiveModsTopFirst() {
        val root = FileNode(game())
        val webRoot = root.children().first { it.name == "www" }
        val mods = Mods.select(Mods.find(root, webRoot), listOf("QoL Patch", "Bug Fix"))
        val layers = mods.map { it to FileIndex.build(it.content) }
        val conflicts = Mods.conflicts(layers)
        // Only CommonEvents.json is in both; rpg_objects.js is QoL only, the picture Bug Fix only.
        assertEquals(listOf(ModConflict("data/CommonEvents.json", listOf("QoL Patch", "Bug Fix"))), conflicts)
        val game = FileIndex.build(webRoot)
        assertEquals(2, Mods.replacedCount(layers[0].second, game))
        // a.rpgmvp replaces the game's A.rpgmvp (case-insensitive), mod.json is the mod's own file.
        assertEquals(2, Mods.replacedCount(layers[1].second, game))
    }

    @Test
    fun coreFilesAreMarked() {
        assertEquals(true, ModConflict("js/rpg_objects.js", emptyList()).isCore)
        assertEquals(true, ModConflict("JS/plugins.js", emptyList()).isCore)
        assertEquals(true, ModConflict("index.html", emptyList()).isCore)
        assertEquals(false, ModConflict("js/plugins/YEP_Core.js", emptyList()).isCore)
        assertEquals(false, ModConflict("data/Map001.json", emptyList()).isCore)
    }

    @Test
    fun modJsonScriptsArePositionedAndChecked() {
        val dir = game()
        file(dir, "Bug Fix/mod.json", """{"name":"Bug Fix Mod","scripts":[
            {"file":"js/fix.js","position":"beforePlugins"}, {"file":"www/js/after.js"},
            {"file":"../evil.js"}, {"file":"js/missing.js"}, {"file":"js/x.js","position":"sometime"}]}""")
        file(dir, "Bug Fix/www/js/fix.js")
        file(dir, "Bug Fix/www/js/after.js")
        val root = FileNode(dir)
        val webRoot = root.children().first { it.name == "www" }
        val mod = Mods.find(root, webRoot).first { it.id == "Bug Fix" }
        assertEquals(
            listOf("js/fix.js beforePlugins", "js/after.js afterPlugins", "js/missing.js afterPlugins"),
            mod.scripts.map { "${it.path} ${it.position.id}" },
        )
        val index = FileIndex.layered(listOf(FileIndex.build(mod.content), FileIndex.build(webRoot)))
        assertEquals(listOf("js/fix.js", "js/after.js"), Mods.existingScripts(listOf(mod), index).map { it.path })
    }

    @Test
    fun removeDeletesTheWholeModFolder() {
        val dir = game()
        val root = FileNode(dir)
        val webRoot = root.children().first { it.name == "www" }
        val mod = Mods.find(root, webRoot).first { it.id == "Bug Fix" }
        assertEquals(true, Mods.remove(mod))
        assertEquals(false, File(dir, "Bug Fix").exists())
        assertEquals(true, File(dir, "www/data/CommonEvents.json").exists())
    }

    @Test
    fun modOrderIsStoredPerGame() {
        val settings = GameSettings(mods = listOf("QoL Patch", "Bug Fix"))
        assertEquals(settings, SettingsJson.gameFromJson(JSONObject(SettingsJson.gameToJson(settings).toString())))
    }
}
