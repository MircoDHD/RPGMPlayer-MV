// SPDX-FileCopyrightText: 2026 MircoDHD
// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.mircodhd.mvplayer.bridge

import android.os.SystemClock
import android.webkit.JavascriptInterface
import io.github.mircodhd.mvplayer.debug.DebugLog
import java.io.File
import java.io.IOException

/**
 * Exposed to the game as `window.MvPlayerHost`. Methods run on WebView's JavaBridge thread
 * and are synchronous, matching MV's synchronous StorageManager API.
 *
 * Saves are stored exactly like the PC version does (www/save/<name>.rpgsave, content as
 * produced by the engine), so save folders can be copied between PC and Android.
 */
class PlayerBridge(
    private val saveDir: File,
    private val onExit: () -> Unit,
) {
    companion object {
        const val JS_NAME = "MvPlayerHost"
        private const val TAG = "Save"
        private val SAFE_NAME = Regex("""[A-Za-z0-9_\-][A-Za-z0-9_.\-]{0,127}""")
    }

    /** Log sink for MvPlayer.log in the patches. [level] is one of v, d, i, w, e. */
    @JavascriptInterface
    fun log(level: String?, category: String?, message: String?) {
        val lvl = when (level) {
            "v" -> DebugLog.Level.VERBOSE
            "d" -> DebugLog.Level.DEBUG
            "w" -> DebugLog.Level.WARN
            "e" -> DebugLog.Level.ERROR
            else -> DebugLog.Level.INFO
        }
        DebugLog.log(lvl, "JS/${category ?: "?"}", message.orEmpty())
    }

    @JavascriptInterface
    fun saveRead(name: String?): String? {
        val file = saveFile(name) ?: return null
        val start = SystemClock.elapsedRealtime()
        return try {
            (if (file.isFile) file.readText() else null).also {
                DebugLog.d(TAG, "read $name: ${it?.length ?: "missing"} chars in ${SystemClock.elapsedRealtime() - start} ms")
            }
        } catch (e: IOException) {
            DebugLog.e(TAG, "Reading $name failed", e)
            null
        }
    }

    @JavascriptInterface
    fun saveWrite(name: String?, data: String?): Boolean {
        val file = saveFile(name) ?: return false
        if (data == null) return false
        val start = SystemClock.elapsedRealtime()
        return try {
            saveDir.mkdirs()
            // Write to a temp file first so a crash mid-write never destroys an existing save.
            val tmp = File(saveDir, "${file.name}.tmp")
            tmp.writeText(data)
            if (!tmp.renameTo(file)) {
                file.delete()
                if (!tmp.renameTo(file)) throw IOException("rename failed")
            }
            DebugLog.i(TAG, "wrote $name: ${data.length} chars to $file in ${SystemClock.elapsedRealtime() - start} ms")
            true
        } catch (e: IOException) {
            DebugLog.e(TAG, "Writing $name to $saveDir failed", e)
            false
        }
    }

    @JavascriptInterface
    fun saveExists(name: String?): Boolean =
        (saveFile(name)?.isFile == true).also { DebugLog.v(TAG, "exists $name: $it") }

    @JavascriptInterface
    fun saveRemove(name: String?): Boolean {
        val file = saveFile(name) ?: return false
        return (!file.exists() || file.delete()).also { DebugLog.d(TAG, "remove $name: $it") }
    }

    @JavascriptInterface
    fun exitGame() {
        DebugLog.i("Lifecycle", "Game requested exit")
        onExit()
    }

    // Parameters are nullable because JavaScript can pass null for any of them.
    private fun saveFile(name: String?): File? {
        if (name == null || !SAFE_NAME.matches(name)) {
            DebugLog.w(TAG, "Rejected save file name '$name'")
            return null
        }
        return File(saveDir, name)
    }
}
