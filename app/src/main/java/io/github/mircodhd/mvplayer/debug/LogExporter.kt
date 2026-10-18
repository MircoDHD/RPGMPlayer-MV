// SPDX-FileCopyrightText: 2026 MircoDHD
// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.mircodhd.mvplayer.debug

import android.content.ClipData
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Packs all session logs into one zip and builds a share intent for it. */
object LogExporter {
    private const val SHARE_DIR = "shared"

    /** Call off the main thread. Returns null if there are no logs yet. */
    fun createShareIntent(context: Context, chooserTitle: CharSequence): Intent? {
        DebugLog.flush()
        val logs = DebugLog.logFiles()
        if (logs.isEmpty()) return null

        val dir = File(context.cacheDir, SHARE_DIR)
        dir.mkdirs()
        dir.listFiles()?.forEach { it.delete() } // only the latest export is kept
        val stamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(Date())
        val zip = File(dir, "rpgm-player-logs_$stamp.zip")
        ZipOutputStream(zip.outputStream().buffered()).use { out ->
            for (log in logs) {
                out.putNextEntry(ZipEntry(log.name))
                log.inputStream().use { it.copyTo(out) }
                out.closeEntry()
            }
        }
        DebugLog.i("Log", "Exported ${logs.size} logs (${zip.length() / 1024} KiB) to $zip")

        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", zip)
        val send = Intent(Intent.ACTION_SEND)
            .setType("application/zip")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .putExtra(Intent.EXTRA_SUBJECT, zip.name)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        send.clipData = ClipData.newRawUri(zip.name, uri)
        return Intent.createChooser(send, chooserTitle)
    }
}
