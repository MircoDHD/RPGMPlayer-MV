// SPDX-FileCopyrightText: 2026 MircoDHD
// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.mircodhd.mvplayer.game

import android.os.SystemClock
import java.util.concurrent.ConcurrentHashMap

/**
 * Inspected games (file index included) by folder URI, kept for [MAX_AGE_MS] in this
 * process. The library scans a game when the app opens; starting it soon after reuses the
 * result instead of listing thousands of files through the documents provider again
 * (about 2 s on slow devices). Save files are not part of it; the save bridge lists them
 * itself on every start.
 */
object GameInfoCache {
    private const val MAX_AGE_MS = 30 * 60 * 1000L

    private class Entry(val info: GameInfo, val at: Long)

    private val entries = ConcurrentHashMap<String, Entry>()

    fun put(folderUri: String, info: GameInfo) {
        entries[folderUri] = Entry(info, SystemClock.elapsedRealtime())
    }

    /** The cached game and its age in ms, or null when there is none or it is too old. */
    fun get(folderUri: String): Pair<GameInfo, Long>? {
        val entry = entries[folderUri] ?: return null
        val age = SystemClock.elapsedRealtime() - entry.at
        if (age > MAX_AGE_MS) {
            entries.remove(folderUri)
            return null
        }
        return entry.info to age
    }

    fun remove(folderUri: String) {
        entries.remove(folderUri)
    }
}
