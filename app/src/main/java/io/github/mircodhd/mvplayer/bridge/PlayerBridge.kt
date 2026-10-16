package io.github.mircodhd.mvplayer.bridge

import android.util.Log
import android.webkit.JavascriptInterface
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
        private const val TAG = "PlayerBridge"
        private val SAFE_NAME = Regex("""[A-Za-z0-9_\-][A-Za-z0-9_.\-]{0,127}""")
    }

    @JavascriptInterface
    fun saveRead(name: String?): String? {
        val file = saveFile(name) ?: return null
        return try {
            if (file.isFile) file.readText() else null
        } catch (e: IOException) {
            Log.e(TAG, "Reading $name failed", e)
            null
        }
    }

    @JavascriptInterface
    fun saveWrite(name: String?, data: String?): Boolean {
        val file = saveFile(name) ?: return false
        if (data == null) return false
        return try {
            saveDir.mkdirs()
            // Write to a temp file first so a crash mid-write never destroys an existing save.
            val tmp = File(saveDir, "${file.name}.tmp")
            tmp.writeText(data)
            if (!tmp.renameTo(file)) {
                file.delete()
                if (!tmp.renameTo(file)) throw IOException("rename failed")
            }
            true
        } catch (e: IOException) {
            Log.e(TAG, "Writing $name failed", e)
            false
        }
    }

    @JavascriptInterface
    fun saveExists(name: String?): Boolean = saveFile(name)?.isFile == true

    @JavascriptInterface
    fun saveRemove(name: String?): Boolean {
        val file = saveFile(name) ?: return false
        return !file.exists() || file.delete()
    }

    @JavascriptInterface
    fun exitGame() = onExit()

    // Parameters are nullable because JavaScript can pass null for any of them.
    private fun saveFile(name: String?): File? {
        if (name == null || !SAFE_NAME.matches(name)) {
            Log.w(TAG, "Rejected save file name '$name'")
            return null
        }
        return File(saveDir, name)
    }
}
