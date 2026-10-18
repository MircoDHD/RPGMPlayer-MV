// SPDX-FileCopyrightText: 2026 MircoDHD
// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.mircodhd.mvplayer.game

import io.github.mircodhd.mvplayer.storage.GameNode
import java.util.concurrent.Callable
import java.util.concurrent.Executors

/**
 * Snapshot of all files below a game's web root. Games are developed on Windows, where
 * paths are case-insensitive, so lookups fall back to a lowercase match.
 * Immutable after [build], safe to use from WebView's request threads.
 */
class FileIndex private constructor(
    private val exact: Map<String, GameNode>,
    private val lower: Map<String, GameNode>,
) {
    val size: Int get() = exact.size

    /** [path] is relative to the web root, using '/' separators and no leading slash. */
    fun resolve(path: String): GameNode? = exact[path] ?: lower[path.lowercase()]

    /** Sum of all indexed file sizes. */
    val totalBytes: Long get() = exact.values.sumOf { it.length }

    /** e.g. "rpgmvp=2074, rpgmvo=433, json=184", most common first. */
    fun extensionCounts(): String = lower.keys
        .groupingBy { it.substringAfterLast('/').substringAfterLast('.', "(none)") }
        .eachCount().entries.sortedByDescending { it.value }
        .joinToString { "${it.key}=${it.value}" }

    /** Real (case-preserved) paths whose lowercase form starts with [prefix], sorted. */
    fun pathsUnder(prefix: String): List<String> = exact.keys.filter { it.lowercase().startsWith(prefix) }.sorted()

    fun anyUnder(prefix: String, predicate: (String) -> Boolean): Boolean =
        lower.keys.any { it.startsWith(prefix) && predicate(it) }

    companion object {
        private val IGNORED_DIRS = setOf("__macosx", ".git")
        private val IGNORED_FILES = setOf(".ds_store", "thumbs.db", "desktop.ini")

        /** Folders are listed level by level, several at once: each listing is a provider query. */
        fun build(root: GameNode, parallelism: Int = 6): FileIndex {
            val exact = HashMap<String, GameNode>(8192)
            val lower = HashMap<String, GameNode>(8192)
            val pool = Executors.newFixedThreadPool(parallelism)
            try {
                var level = listOf(root to "")
                while (level.isNotEmpty()) {
                    val listings = pool.invokeAll(level.map { (dir, prefix) -> Callable { dir.children() to prefix } })
                    val next = ArrayList<Pair<GameNode, String>>()
                    for (future in listings) {
                        val (children, prefix) = future.get()
                        for (child in children) {
                            val name = child.name
                            val lowerName = name.lowercase()
                            if (child.isDirectory) {
                                if (lowerName !in IGNORED_DIRS) next += child to "$prefix$name/"
                            } else if (lowerName !in IGNORED_FILES && !name.startsWith("._")) {
                                val path = prefix + name
                                exact[path] = child
                                lower.putIfAbsent(path.lowercase(), child)
                            }
                        }
                    }
                    level = next
                }
            } finally {
                pool.shutdown()
            }
            return FileIndex(exact, lower)
        }
    }
}
