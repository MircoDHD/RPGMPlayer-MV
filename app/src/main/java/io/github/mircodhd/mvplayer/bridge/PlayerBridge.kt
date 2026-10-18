// SPDX-FileCopyrightText: 2026 MircoDHD
// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.mircodhd.mvplayer.bridge

import android.os.SystemClock
import android.webkit.JavascriptInterface
import io.github.mircodhd.mvplayer.debug.DebugLog
import io.github.mircodhd.mvplayer.storage.GameNode
import java.io.IOException

/**
 * Exposed to the game as `window.MvPlayerHost`. Methods run on WebView's JavaBridge thread
 * and are synchronous, matching MV's synchronous StorageManager API.
 *
 * Saves are stored exactly like the PC version does (www/save/<name>.rpgsave, content as
 * produced by the engine), so save folders can be copied between PC and Android.
 * The save folder is listed once; afterwards only this bridge changes it while the game
 * runs, so lookups use the cached listing instead of asking the documents provider.
 */
class PlayerBridge(
    private val webRoot: GameNode,
    private val host: Host,
) {
    /** Calls into the activity; invoked on the JavaBridge thread. */
    interface Host {
        fun exitGame()
        fun requestTextInput(current: String, maxLength: Int)
    }

    companion object {
        const val JS_NAME = "MvPlayerHost"
        private const val TAG = "Save"
        private val SAFE_NAME = Regex("""[A-Za-z0-9_\-][A-Za-z0-9_.\-]{0,127}""")
        private const val SAVE_DIR = "save"
    }

    private val lock = Any()
    private var saveDir: GameNode? = null
    /** Files in the save folder by lowercase name (Windows saves may differ in case). */
    private val files = HashMap<String, GameNode>()
    private var listed = false

    private fun ensureListed() {
        if (listed) return
        listed = true
        saveDir = webRoot.children().firstOrNull { it.isDirectory && it.name.equals(SAVE_DIR, ignoreCase = true) }
        saveDir?.children()?.filterNot { it.isDirectory }?.forEach { files[it.name.lowercase()] = it }
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
        if (!isSafe(name)) return null
        val start = SystemClock.elapsedRealtime()
        return synchronized(lock) {
            ensureListed()
            try {
                files[name!!.lowercase()]?.readText()
            } catch (e: Exception) {
                DebugLog.e(TAG, "Reading $name failed", e)
                null
            }
        }.also {
            DebugLog.d(TAG, "read $name: ${it?.length ?: "missing"} chars in ${SystemClock.elapsedRealtime() - start} ms")
        }
    }

    @JavascriptInterface
    fun saveWrite(name: String?, data: String?): Boolean {
        if (!isSafe(name) || data == null) return false
        val start = SystemClock.elapsedRealtime()
        return synchronized(lock) {
            ensureListed()
            try {
                val dir = saveDir ?: webRoot.createDirectory(SAVE_DIR)?.also { saveDir = it }
                    ?: throw IOException("cannot create the save folder")
                // Write a temp file first so a crash mid-write never destroys an existing save.
                val tmpName = "$name.tmp"
                files.remove(tmpName.lowercase())?.delete()
                val tmp = dir.createFile(tmpName) ?: throw IOException("cannot create $tmpName")
                tmp.openOutput().use { it.write(data.toByteArray()) }
                files.remove(name!!.lowercase())?.let { old ->
                    if (!old.delete()) throw IOException("cannot replace ${old.name}")
                }
                val saved = tmp.renameTo(name) ?: throw IOException("cannot rename $tmpName")
                files[name.lowercase()] = saved
                DebugLog.i(TAG, "wrote $name: ${data.length} chars in ${SystemClock.elapsedRealtime() - start} ms")
                true
            } catch (e: Exception) {
                DebugLog.e(TAG, "Writing $name failed", e)
                false
            }
        }
    }

    @JavascriptInterface
    fun saveExists(name: String?): Boolean {
        if (!isSafe(name)) return false
        return synchronized(lock) {
            ensureListed()
            name!!.lowercase() in files
        }.also { DebugLog.v(TAG, "exists $name: $it") }
    }

    @JavascriptInterface
    fun saveRemove(name: String?): Boolean {
        if (!isSafe(name)) return false
        return synchronized(lock) {
            ensureListed()
            files[name!!.lowercase()]?.let { node -> node.delete().also { if (it) files.remove(name.lowercase()) } } ?: true
        }.also { DebugLog.d(TAG, "remove $name: $it") }
    }

    @JavascriptInterface
    fun exitGame() {
        DebugLog.i("Lifecycle", "Game requested exit")
        host.exitGame()
    }

    /** Opens the native text field (soft keyboard) for name input scenes. */
    @JavascriptInterface
    fun requestTextInput(current: String?, maxLength: Int) {
        DebugLog.i("TextInput", "Game requested text input (max $maxLength)")
        host.requestTextInput(current.orEmpty(), maxLength.coerceIn(1, 64))
    }

    // Parameters are nullable because JavaScript can pass null for any of them.
    private fun isSafe(name: String?): Boolean {
        if (name != null && SAFE_NAME.matches(name)) return true
        DebugLog.w(TAG, "Rejected save file name '$name'")
        return false
    }
}
