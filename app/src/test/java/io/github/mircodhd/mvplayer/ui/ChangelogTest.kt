// SPDX-FileCopyrightText: 2026 MircoDHD
// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.mircodhd.mvplayer.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ChangelogTest {
    private val sample = """
        # Changelog
        Intro text is ignored.

        ## 0.3.3 – 2026-10-18
        ### Added
        - **Bold** start and a long line
          that continues here.
        - Second

        ## 0.3.2 – 2026-10-18
        ### Fixed
        - A fix

        ## 0.1.0 – 2026-10-17 (first)
        Plain paragraph.
    """.trimIndent()

    @Test
    fun parsesHeadingsSectionsAndContinuedBullets() {
        val entries = Changelog.parse(sample)
        assertEquals(listOf("0.3.3", "0.3.2", "0.1.0"), entries.map { it.version })
        assertEquals(
            listOf(
                ChangelogBlock.Section("Added"),
                ChangelogBlock.Bullet("**Bold** start and a long line that continues here."),
                ChangelogBlock.Bullet("Second"),
            ),
            entries.first().blocks,
        )
        assertEquals(listOf(ChangelogBlock.Paragraph("Plain paragraph.")), entries.last().blocks)
    }

    @Test
    fun selectsVersionsSinceTheLastSeenOne() {
        val entries = Changelog.parse(sample)
        assertEquals(listOf("0.3.3", "0.3.2"), Changelog.between(entries, "0.3.1", "0.3.3").map { it.version })
        assertEquals(listOf("0.3.2"), Changelog.between(entries, "0.1.0", "0.3.2").map { it.version })
        assertTrue(Changelog.compareVersions("0.10.0", "0.9.9") > 0)
    }

    /** The German box must list the same versions as CHANGELOG.md. */
    @Test
    fun germanChangelogCoversEveryVersion() {
        val english = Changelog.parse(File("../CHANGELOG.md").readText()).map { it.version }
        val german = Changelog.parse(File("src/main/assets/changelog/de.md").readText()).map { it.version }
        assertEquals(english, german)
    }
}
