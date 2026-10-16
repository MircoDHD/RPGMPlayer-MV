package io.github.mircodhd.mvplayer.game

import java.io.File

/**
 * Snapshot of all files below a game's web root. Games are developed on Windows, where
 * paths are case-insensitive, so lookups fall back to a lowercase match.
 * Immutable after [build], safe to use from WebView's request threads.
 */
class FileIndex private constructor(
    private val exact: Map<String, File>,
    private val lower: Map<String, File>,
) {
    val size: Int get() = exact.size

    /** [path] is relative to the web root, using '/' separators and no leading slash. */
    fun resolve(path: String): File? = exact[path] ?: lower[path.lowercase()]

    /** e.g. "rpgmvp=2074, rpgmvo=433, json=184", most common first. */
    fun extensionCounts(): String = lower.keys
        .groupingBy { it.substringAfterLast('/').substringAfterLast('.', "(none)") }
        .eachCount().entries.sortedByDescending { it.value }
        .joinToString { "${it.key}=${it.value}" }

    fun anyUnder(prefix: String, predicate: (String) -> Boolean): Boolean =
        lower.keys.any { it.startsWith(prefix) && predicate(it) }

    companion object {
        private val IGNORED_DIRS = setOf("__macosx", ".git")
        private val IGNORED_FILES = setOf(".ds_store", "thumbs.db", "desktop.ini")

        fun build(root: File): FileIndex {
            val exact = HashMap<String, File>(8192)
            val lower = HashMap<String, File>(8192)
            fun walk(dir: File, prefix: String) {
                val children = dir.listFiles() ?: return
                for (child in children) {
                    val name = child.name
                    val lowerName = name.lowercase()
                    if (child.isDirectory) {
                        if (lowerName !in IGNORED_DIRS) walk(child, "$prefix$name/")
                    } else if (lowerName !in IGNORED_FILES && !name.startsWith("._")) {
                        val path = prefix + name
                        exact[path] = child
                        lower.putIfAbsent(path.lowercase(), child)
                    }
                }
            }
            walk(root, "")
            return FileIndex(exact, lower)
        }
    }
}
