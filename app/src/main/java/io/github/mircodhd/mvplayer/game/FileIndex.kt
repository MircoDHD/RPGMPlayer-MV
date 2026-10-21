// SPDX-FileCopyrightText: 2026 MircoDHD
// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.mircodhd.mvplayer.game

import io.github.mircodhd.mvplayer.debug.DebugLog
import io.github.mircodhd.mvplayer.storage.GameNode
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.text.Normalizer
import java.util.concurrent.Callable
import java.util.concurrent.Executors

/**
 * Snapshot of all files below a game's web root. Games are developed on Windows, where
 * paths are case-insensitive, so lookups fall back to a lowercase match, then to the
 * Unicode NFC form (macOS archives store "ä" as "a" plus a combining mark) and finally to
 * names garbled by unpacking with the wrong code page ([MojibakeNames]).
 * Immutable after [build], safe to use from WebView's request threads.
 */
class FileIndex private constructor(
    private val exact: Map<String, GameNode>,
    private val lower: Map<String, GameNode>,
) {
    val size: Int get() = exact.size

    /** NFC lowercase keys; only differs from [lower] for non-ASCII names. */
    private val normalized: Map<String, GameNode> by lazy {
        lower.entries.filter { (path) -> path.any { it.code > 0x7F } }
            .associate { (path, node) -> nfc(path) to node }
    }

    /** Repaired names of garbled files, keyed like [normalized]; built on the first miss. */
    private val repaired: Map<String, Pair<String, GameNode>> by lazy {
        val result = HashMap<String, Pair<String, GameNode>>()
        for ((path, node) in exact) {
            if (path.none { it.code > 0x7F }) continue
            for (candidate in MojibakeNames.repairs(path)) result.putIfAbsent(nfc(candidate).lowercase(), path to node)
        }
        result
    }

    /** [path] is relative to the web root, using '/' separators and no leading slash. */
    fun resolve(path: String): GameNode? {
        exact[path]?.let { return it }
        val lowerPath = path.lowercase()
        lower[lowerPath]?.let { return it }
        // Normalised and repaired names always contain non-ASCII characters.
        if (path.all { it.code <= 0x7F }) return null
        val key = nfc(lowerPath)
        normalized[key]?.let { return it }
        return repaired[key]?.let { (stored, node) ->
            DebugLog.i(TAG, "Using garbled file name '$stored' for '$path'")
            node
        }
    }

    /** Sum of all indexed file sizes. */
    val totalBytes: Long get() = exact.values.sumOf { it.length }

    /** e.g. "rpgmvp=2074, rpgmvo=433, json=184", most common first. */
    fun extensionCounts(): String = lower.keys
        .groupingBy { it.substringAfterLast('/').substringAfterLast('.', "(none)") }
        .eachCount().entries.sortedByDescending { it.value }
        .joinToString { "${it.key}=${it.value}" }

    /** Real (case-preserved) paths whose lowercase form starts with [prefix], sorted. */
    fun pathsUnder(prefix: String): List<String> = exact.keys.filter { it.lowercase().startsWith(prefix) }.sorted()

    /**
     * Entries directly inside folder [dir] ("" for the web root), case-insensitive like
     * [resolve]: name to whether it is a folder. Null if there is no such folder.
     */
    fun list(dir: String): Map<String, Boolean>? {
        val prefix = if (dir.isEmpty()) "" else dir.lowercase().trimEnd('/') + "/"
        val result = LinkedHashMap<String, Boolean>()
        for (path in exact.keys) {
            if (!path.lowercase().startsWith(prefix)) continue
            val rest = path.substring(prefix.length)
            val slash = rest.indexOf('/')
            if (slash < 0) result[rest] = false else result.putIfAbsent(rest.substring(0, slash), true)
        }
        return if (result.isEmpty() && dir.isNotEmpty()) null else result
    }

    fun anyUnder(prefix: String, predicate: (String) -> Boolean): Boolean =
        lower.keys.any { it.startsWith(prefix) && predicate(it) }

    companion object {
        private const val TAG = "Files"
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

        private fun nfc(text: String): String = Normalizer.normalize(text, Normalizer.Form.NFC)
    }
}

/**
 * Zip tools that ignore the archive's encoding turn non-ASCII names into "mojibake": the
 * UTF-8 bytes of "Ä.png" decoded as Windows-1252 become "Ã„.png", and Japanese
 * Shift-JIS names decoded as CP437 or 1252 become runs of box-drawing or accented letters.
 * [repairs] undoes this: it re-encodes a stored name with each wrong code page and decodes
 * the bytes with the code page they really were in; only strict, error-free round trips
 * count.
 */
object MojibakeNames {
    private val WRONG = listOf("IBM437", "windows-1252").mapNotNull(::charsetOrNull)
    private val ORIGINAL = listOf("UTF-8", "Shift_JIS", "GBK").mapNotNull(::charsetOrNull)

    fun repairs(name: String): Set<String> {
        val result = LinkedHashSet<String>()
        for (wrong in WRONG) {
            val bytes = encode(name, wrong) ?: continue
            for (original in ORIGINAL) {
                val repaired = decode(bytes, original) ?: continue
                if (repaired != name) result += repaired
            }
        }
        return result
    }

    private fun charsetOrNull(name: String): Charset? = runCatching { Charset.forName(name) }.getOrNull()

    private fun encode(text: String, charset: Charset): ByteArray? = try {
        val buffer = charset.newEncoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .encode(CharBuffer.wrap(text))
        ByteArray(buffer.remaining()).also { buffer.get(it) }
    } catch (e: java.nio.charset.CharacterCodingException) {
        null
    }

    private fun decode(bytes: ByteArray, charset: Charset): String? = try {
        charset.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes)).toString()
    } catch (e: java.nio.charset.CharacterCodingException) {
        null
    }
}
