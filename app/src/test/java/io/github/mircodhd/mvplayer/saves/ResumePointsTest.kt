// SPDX-FileCopyrightText: 2026 MircoDHD
// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.mircodhd.mvplayer.saves

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ResumePointsTest {
    @get:Rule
    val temp = TemporaryFolder()

    private fun ResumePoints.addNamed(name: String): ResumePoint {
        Thread.sleep(2) // distinct creation times
        return add("data-$name", byteArrayOf(1, 2), "00:00:0$name", name)!!
    }

    @Test
    fun keepsTheThreeNewestAutomaticPoints() {
        val points = ResumePoints(temp.root, "game-1")
        (1..5).forEach { points.addNamed(it.toString()) }
        assertEquals(listOf("5", "4", "3"), points.list().map { it.location })
        // Files of dropped points are gone; only three saves, three thumbnails and the list remain.
        assertEquals(7, temp.root.resolve("resume/game-1").listFiles()!!.size)
    }

    @Test
    fun keptPointsSurviveAndAreLimited() {
        val points = ResumePoints(temp.root, "game-1")
        val first = points.addNamed("1")
        assertTrue(points.setKept(first.id, true))
        (2..5).forEach { points.addNamed(it.toString()) }
        assertEquals(listOf("5", "4", "3", "1"), points.list().map { it.location })
        val autos = points.list().filter { !it.kept }
        assertTrue(points.setKept(autos[0].id, true))
        assertTrue(points.setKept(autos[1].id, true))
        assertFalse(points.setKept(autos[2].id, true))
        assertEquals(3, points.list().count { it.kept })
        assertEquals("data-1", points.read(first.id))
        // Released, the oldest point counts as automatic again and is dropped first.
        points.addNamed("6")
        points.addNamed("7")
        points.setKept(first.id, false)
        assertFalse(points.list().any { it.id == first.id })
    }

    @Test
    fun deleteAndClear() {
        val points = ResumePoints(temp.root, "game-1")
        val a = points.addNamed("1")
        points.addNamed("2")
        points.delete(a.id)
        assertEquals(listOf("2"), points.list().map { it.location })
        points.clear()
        assertEquals(emptyList<ResumePoint>(), points.list())
    }

    @Test
    fun exportAndImportKeepTheLimitsAndTheGame() {
        val source = ResumePoints(temp.root, "source")
        val kept = source.addNamed("1")
        source.setKept(kept.id, true)
        source.addNamed("2")
        val zip = java.io.ByteArrayOutputStream()
        assertEquals(2, source.export(zip, "Termina"))

        val target = ResumePoints(temp.newFolder("other"), "target")
        assertEquals(-1, target.import(zip.toByteArray().inputStream(), "Fear & Hunger"))
        (3..5).forEach { target.addNamed(it.toString()) }
        assertEquals(1, target.import(zip.toByteArray().inputStream(), "Termina"))
        val points = target.list()
        // The kept one arrives kept; the imported automatic one is older than the three here.
        assertTrue(points.any { it.id == kept.id && it.kept })
        assertEquals(3, points.count { !it.kept })
        assertEquals("data-1", target.read(kept.id))
        // Importing again adds nothing.
        assertEquals(0, target.import(zip.toByteArray().inputStream(), "Termina"))
    }
}
