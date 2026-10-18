// SPDX-FileCopyrightText: 2026 MircoDHD
// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.mircodhd.mvplayer.ui

/**
 * One version of the changelog, parsed from the Markdown in `assets/changelog/<lang>.md`
 * (the English file is CHANGELOG.md, copied at build time). Only the subset used there is
 * understood: "## version – date" headings, "### Section" headings and "- " bullets whose
 * continuation lines are indented.
 */
data class ChangelogEntry(val version: String, val heading: String, val blocks: List<ChangelogBlock>)

sealed interface ChangelogBlock {
    data class Section(val title: String) : ChangelogBlock
    data class Bullet(val text: String) : ChangelogBlock
    data class Paragraph(val text: String) : ChangelogBlock
}

object Changelog {
    fun parse(markdown: String): List<ChangelogEntry> {
        val entries = ArrayList<ChangelogEntry>()
        var heading: String? = null
        var blocks = ArrayList<ChangelogBlock>()
        fun finish() {
            val h = heading ?: return
            entries += ChangelogEntry(h.substringBefore(' ').trim(), h, blocks)
        }
        for (raw in markdown.lines()) {
            val line = raw.trimEnd()
            when {
                line.startsWith("## ") -> {
                    finish()
                    heading = line.removePrefix("## ").trim()
                    blocks = ArrayList()
                }
                heading == null || line.isBlank() -> Unit
                line.startsWith("### ") -> blocks += ChangelogBlock.Section(line.removePrefix("### ").trim())
                line.startsWith("- ") -> blocks += ChangelogBlock.Bullet(line.removePrefix("- ").trim())
                raw.startsWith("  ") && blocks.lastOrNull() is ChangelogBlock.Bullet -> {
                    val last = blocks.removeAt(blocks.lastIndex) as ChangelogBlock.Bullet
                    blocks += ChangelogBlock.Bullet(last.text + " " + line.trim())
                }
                else -> blocks += ChangelogBlock.Paragraph(line.trim())
            }
        }
        finish()
        return entries
    }

    /** Entries after [lastSeen] up to and including [current], newest first. */
    fun between(entries: List<ChangelogEntry>, lastSeen: String, current: String): List<ChangelogEntry> =
        entries.filter { compareVersions(it.version, lastSeen) > 0 && compareVersions(it.version, current) <= 0 }

    /** Compares "major.minor.patch" numerically; missing or non-numeric parts count as 0. */
    fun compareVersions(a: String, b: String): Int {
        val pa = a.split('.').map { it.toIntOrNull() ?: 0 }
        val pb = b.split('.').map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(pa.size, pb.size)) {
            val diff = pa.getOrElse(i) { 0 } - pb.getOrElse(i) { 0 }
            if (diff != 0) return diff
        }
        return 0
    }
}
