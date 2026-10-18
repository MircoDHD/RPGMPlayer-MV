// SPDX-FileCopyrightText: 2026 MircoDHD
// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.mircodhd.mvplayer.ui

import android.content.Context
import android.os.Environment
import android.text.format.DateUtils
import android.text.format.Formatter
import io.github.mircodhd.mvplayer.R

object Format {
    fun playTime(context: Context, ms: Long): String {
        val minutes = ms / 60_000
        return when {
            minutes < 1 -> context.getString(R.string.fmt_playtime_under_minute)
            minutes < 60 -> context.getString(R.string.fmt_playtime_minutes, minutes)
            else -> context.getString(R.string.fmt_playtime_hours, minutes / 60, minutes % 60)
        }
    }

    fun lastPlayed(context: Context, time: Long): String =
        if (time <= 0) {
            context.getString(R.string.lib_never_played)
        } else {
            context.getString(
                R.string.lib_last_played,
                DateUtils.getRelativeTimeSpanString(time, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS),
            )
        }

    fun date(context: Context, time: Long): String =
        DateUtils.formatDateTime(context, time, DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_YEAR)

    /** "/storage/emulated/0/Games/Fear & Hunger" -> "Games/Fear & Hunger" */
    fun path(path: String): String {
        val root = Environment.getExternalStorageDirectory().absolutePath
        return if (path.startsWith(root)) path.removePrefix(root).trimStart('/') else path
    }

    /** "2026-10-18_19-30" for file names. */
    fun fileStamp(): String = java.text.SimpleDateFormat("yyyy-MM-dd_HH-mm", java.util.Locale.US).format(java.util.Date())

    fun size(context: Context, bytes: Long): String = Formatter.formatFileSize(context, bytes)
}
