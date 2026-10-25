// SPDX-FileCopyrightText: 2026 MircoDHD
// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.mircodhd.mvplayer.debug

/**
 * Multi-line console messages (warnings with a stack trace) are logged in full the first
 * time in a session; later copies keep only their first line and a count. A plugin's
 * deprecation warning printed on every map change would otherwise add its whole stack to
 * the log each time, minutes apart, so the one-minute repeat collapsing in [DebugLog]
 * does not catch it.
 */
class ConsoleLines {
    private val seen = HashMap<String, Int>()

    fun shorten(message: String): String {
        if ('\n' !in message) return message
        val count = synchronized(seen) {
            if (seen.size >= MAX_KEPT && message !in seen) return message
            (seen.getOrDefault(message, 0) + 1).also { seen[message] = it }
        }
        if (count == 1) return message
        return "${message.substringBefore('\n')} (same stack trace as before, ${count}x)"
    }

    private companion object {
        /** Distinct traces remembered per session, so a flood of new ones cannot grow it. */
        const val MAX_KEPT = 200
    }
}
