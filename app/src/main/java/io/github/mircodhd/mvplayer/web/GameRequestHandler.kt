// SPDX-FileCopyrightText: 2026 MircoDHD
// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.mircodhd.mvplayer.web

import android.content.res.AssetManager
import android.os.SystemClock
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import io.github.mircodhd.mvplayer.debug.DebugLog
import io.github.mircodhd.mvplayer.game.GameInfo
import io.github.mircodhd.mvplayer.game.MvDecrypter
import io.github.mircodhd.mvplayer.patch.PatchSet
import io.github.mircodhd.mvplayer.storage.GameNode
import java.io.ByteArrayInputStream
import java.io.FilterInputStream
import java.io.InputStream
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * Serves a game folder under a virtual HTTPS origin. Every request from the WebView ends
 * here: game files come from the [GameInfo.index], patches from the APK assets, and
 * anything else is refused so games never reach the network.
 * Called on WebView's IO threads; all state is immutable or lazily built once.
 */
class GameRequestHandler(
    private val assets: AssetManager,
    private val info: GameInfo,
    private val patches: PatchSet,
    private val configJson: String,
    /** Opens a font by FontLibrary id, for the font override (mv/fonts.js). */
    private val openFont: (String) -> InputStream?,
) {
    companion object {
        const val HOST = "game.local"
        const val START_URL = "https://$HOST/index.html"
        private const val PATCH_PREFIX = "__mvplayer/"
        const val FONT_PREFIX = "__mvplayer/font/"
        /** Read-only file system queries for the Node.js stand-ins (mv/nwjs.js). */
        private const val FS_PREFIX = "__mvplayer/fs/"
        private const val TAG = "Request"
        private val NO_CACHE = mapOf("Cache-Control" to "no-store")
    }

    private val indexHtml: ByteArray by lazy { buildIndexHtml() }

    private val requestCount = AtomicInteger()
    private val notFoundCount = AtomicInteger()
    private val decryptedCount = AtomicInteger()
    private val encryptedCount = AtomicInteger()
    private val repairedFiles: MutableSet<String> = java.util.concurrent.ConcurrentHashMap.newKeySet()
    private val bytesServed = AtomicLong()

    /** Totals since the game started, logged by the activity on pause. */
    fun stats() = "${requestCount.get()} requests, ${bytesServed.get() / 1024} KiB, " +
        "${notFoundCount.get()} not found, ${decryptedCount.get()} decrypted and ${encryptedCount.get()} encrypted twins"

    /** Null for a missing game file: WebView then fails the request (see [missing]). */
    fun handle(request: WebResourceRequest): WebResourceResponse? {
        val start = SystemClock.elapsedRealtime()
        val response = route(request)
        requestCount.incrementAndGet()
        if (response == null) {
            DebugLog.v(TAG, "${request.method} ${request.url.path} -> missing, failed as a network error")
            return null
        }
        val length = response.responseHeaders?.get("Content-Length")?.toLongOrNull() ?: 0L
        bytesServed.addAndGet(length)
        if (DebugLog.verbose) {
            val range = request.requestHeaders.entries.firstOrNull { it.key.equals("Range", true) }?.let { " [${it.value}]" } ?: ""
            DebugLog.v(TAG, "${request.method} ${request.url.path} -> ${response.statusCode} " +
                "${response.mimeType} ${length}B$range in ${SystemClock.elapsedRealtime() - start} ms")
        }
        return response
    }

    private fun route(request: WebResourceRequest): WebResourceResponse? {
        val url = request.url
        if (url.scheme != "https" || url.host != HOST) {
            DebugLog.w(TAG, "Blocked request to $url")
            return error(403, "Forbidden")
        }
        if (request.method != "GET" && request.method != "HEAD") return error(405, "Method Not Allowed")
        val path = normalize(url.path.orEmpty()) ?: return error(400, "Bad Request")
        return when {
            path.isEmpty() || path.equals("index.html", ignoreCase = true) -> serveIndex()
            path.startsWith(FONT_PREFIX) -> serveFont(path.removePrefix(FONT_PREFIX))
            path.startsWith(FS_PREFIX) -> serveFsQuery(path.removePrefix(FS_PREFIX))
            path.startsWith(PATCH_PREFIX) -> servePatch(path.removePrefix(PATCH_PREFIX))
            else -> serveFile(path, request.requestHeaders)
        }
    }

    private fun serveIndex() = inMemory("text/html", indexHtml)

    // Patches are small; reading them fully gives the response a Content-Length.
    private fun servePatch(name: String): WebResourceResponse = try {
        inMemory("text/javascript", assets.open("patches/$name").use { it.readBytes() })
    } catch (e: java.io.IOException) {
        DebugLog.e(TAG, "Missing patch $name", e)
        error(404, "Not Found")
    }

    /** "stat/<path>" → {exists, dir, size}; "list/<path>" → [{name, dir}] or 404. */
    private fun serveFsQuery(query: String): WebResourceResponse {
        val kind = query.substringBefore('/')
        val path = query.substringAfter('/', "")
        val json = when (kind) {
            "stat" -> {
                val file = info.index.resolve(path)
                val isDir = file == null && info.index.list(path) != null
                org.json.JSONObject().put("exists", file != null || isDir).put("dir", isDir).put("size", file?.length ?: 0)
            }
            "list" -> {
                val entries = info.index.list(path) ?: return error(404, "Not Found")
                org.json.JSONArray().apply {
                    entries.forEach { (name, dir) -> put(org.json.JSONObject().put("name", name).put("dir", dir)) }
                }
            }
            else -> return error(400, "Bad Request")
        }
        return inMemory("application/json", json.toString().toByteArray())
    }

    private fun serveFont(id: String): WebResourceResponse {
        val bytes = try {
            openFont(id)?.use { it.readBytes() }
        } catch (e: java.io.IOException) {
            null
        } ?: return error(404, "Not Found").also { DebugLog.w(TAG, "Missing font $id") }
        return WebResourceResponse("font/ttf", null, 200, "OK", NO_CACHE + ("Content-Length" to bytes.size.toString()), ByteArrayInputStream(bytes))
    }

    private fun serveFile(path: String, requestHeaders: Map<String, String>): WebResourceResponse? {
        val file = info.index.resolve(path)
        if (file == null) {
            serveDecryptedTwin(path)?.let { return it }
            serveEncryptedTwin(path)?.let { return it }
            notFoundCount.incrementAndGet()
            DebugLog.w(TAG, "Not found: $path")
            return missing()
        }
        if (isDataJson(path)) return serveDataJson(path, file)
        val mime = MimeTypes.forPath(file.name)
        val encoding = if (MimeTypes.isText(mime)) "UTF-8" else null
        val length = file.length
        val headers = mutableMapOf("Accept-Ranges" to "bytes", "Cache-Control" to "no-store")

        // <video> and some audio paths issue range requests; answer them properly.
        val range = requestHeaders.entries.firstOrNull { it.key.equals("Range", ignoreCase = true) }?.value
        parseRange(range, length)?.let { (start, end) ->
            val count = end - start + 1
            val stream = try {
                file.open(start)
            } catch (e: Exception) {
                return unreadable(path, e)
            }
            headers["Content-Range"] = "bytes $start-$end/$length"
            headers["Content-Length"] = count.toString()
            return WebResourceResponse(mime, encoding, 206, "Partial Content", headers, LimitedInputStream(stream, count))
        }
        headers["Content-Length"] = length.toString()
        val stream = try {
            file.open()
        } catch (e: Exception) {
            return unreadable(path, e)
        }
        return WebResourceResponse(mime, encoding, 200, "OK", headers, stream)
    }

    private fun isDataJson(path: String) =
        path.startsWith("data/", ignoreCase = true) && path.endsWith(".json", ignoreCase = true)

    /** Data files are read whole and repaired if MV's strict JSON.parse would reject them. */
    private fun serveDataJson(path: String, file: GameNode): WebResourceResponse? {
        val bytes = try {
            file.readBytes()
        } catch (e: Exception) {
            return unreadable(path, e)
        }
        val repaired = JsonRepair.repair(bytes)
        if (repaired != null && repairedFiles.add(path)) {
            DebugLog.w(TAG, "Repaired $path for JSON.parse: removed ${repaired.changes.joinToString()}")
        }
        return inMemory("application/json", repaired?.bytes ?: bytes)
    }

    // Exception, not IOException: a folder grant revoked while the game runs throws SecurityException.
    private fun unreadable(path: String, e: Exception): WebResourceResponse? {
        notFoundCount.incrementAndGet()
        DebugLog.w(TAG, "Cannot open $path", e)
        return missing()
    }

    /**
     * Plugins that load images before System.json is read (e.g. SRD_MenuBackgrounds) make
     * MV request "x.png" although only the encrypted "x.rpgmvp" exists. On PC a retry hides
     * this; here the encrypted twin is decrypted and served directly instead of racing.
     */
    private fun serveDecryptedTwin(path: String): WebResourceResponse? {
        val key = info.encryptionKey ?: return null
        val ext = path.substringAfterLast('.', "").lowercase()
        val encryptedExt = MvDecrypter.ENCRYPTED_EXTENSIONS[ext] ?: return null
        val twin = info.index.resolve(path.dropLast(ext.length) + encryptedExt) ?: return null
        val bytes = runCatching { MvDecrypter.decrypt(twin.readBytes(), key) }.getOrNull() ?: return null
        decryptedCount.incrementAndGet()
        DebugLog.i(TAG, "Serving decrypted ${twin.name} for $path")
        val headers = mapOf("Cache-Control" to "no-store", "Content-Length" to bytes.size.toString())
        return WebResourceResponse(MimeTypes.forPath(path), null, 200, "OK", headers, ByteArrayInputStream(bytes))
    }

    /**
     * Mods often add plain images or audio to an encrypted game; MV still asks for
     * "x.rpgmvp". The plain file is encrypted with the game's key, so MV's decrypter reads it.
     */
    private fun serveEncryptedTwin(path: String): WebResourceResponse? {
        val key = info.encryptionKey ?: return null
        val ext = path.substringAfterLast('.', "").lowercase()
        val plainExt = MvDecrypter.PLAIN_EXTENSIONS[ext] ?: return null
        val twin = info.index.resolve(path.dropLast(ext.length) + plainExt) ?: return null
        val bytes = runCatching { MvDecrypter.encrypt(twin.readBytes(), key) }.getOrNull() ?: return null
        encryptedCount.incrementAndGet()
        DebugLog.i(TAG, "Serving plain ${twin.name} encrypted for $path")
        val headers = mapOf("Cache-Control" to "no-store", "Content-Length" to bytes.size.toString())
        return WebResourceResponse(MimeTypes.forPath(path), null, 200, "OK", headers, ByteArrayInputStream(bytes))
    }

    private fun buildIndexHtml(): ByteArray {
        val original = info.index.resolve("index.html")?.readText().orEmpty()
        val engineTags = buildString {
            append("<script>window.MvPlayerConfig = ").append(configJson).append(";</script>\n")
            patches.beforeEngine.forEach { append(scriptTag(it)) }
        }
        val pluginTags = patches.beforePlugins.joinToString("") { scriptTag(it) }
        // afterPlugins scripts are appended by mv/afterplugins.js once MV has queued the plugins.

        var html = original
        html = insertBefore(html, Regex("<script", RegexOption.IGNORE_CASE), engineTags)
            ?: insertBefore(html, Regex("</head>", RegexOption.IGNORE_CASE), engineTags)
            ?: (engineTags + html)
        html = insertBefore(html, Regex("""<script[^>]*src\s*=\s*["']?js/plugins\.js""", RegexOption.IGNORE_CASE), pluginTags)
            ?: insertBefore(html, Regex("""<script[^>]*src\s*=\s*["']?js/main\.js""", RegexOption.IGNORE_CASE), pluginTags)
            ?: run {
                DebugLog.w(TAG, "index.html has no plugins.js/main.js script tag; appending patches")
                insertBefore(html, Regex("</body>", RegexOption.IGNORE_CASE), pluginTags) ?: (html + pluginTags)
            }
        DebugLog.d(TAG, "Injected patches: before engine ${patches.beforeEngine}, before plugins ${patches.beforePlugins}")
        return html.toByteArray()
    }

    private fun scriptTag(url: String) =
        "<script type=\"text/javascript\" src=\"$url\"></script>\n"

    private fun insertBefore(html: String, pattern: Regex, insert: String): String? =
        pattern.find(html)?.let { html.substring(0, it.range.first) + insert + html.substring(it.range.first) }

    /** Resolves "." and ".." and strips leading slashes; returns null if the path escapes the root. */
    private fun normalize(rawPath: String): String? {
        val parts = ArrayList<String>()
        for (segment in rawPath.split('/', '\\')) {
            when (segment) {
                "", "." -> Unit
                ".." -> if (parts.isEmpty()) return null else parts.removeAt(parts.lastIndex)
                else -> parts.add(segment)
            }
        }
        return parts.joinToString("/")
    }

    /** Parses a single "bytes=start-end" range; multi-range requests are answered in full. */
    private fun parseRange(header: String?, length: Long): Pair<Long, Long>? {
        if (header == null || length == 0L) return null
        val match = Regex("""bytes=(\d*)-(\d*)$""").matchEntire(header.trim()) ?: return null
        val (startText, endText) = match.destructured
        val (start, end) = when {
            startText.isEmpty() && endText.isEmpty() -> return null
            startText.isEmpty() -> (length - endText.toLong()).coerceAtLeast(0) to length - 1
            else -> startText.toLong() to (endText.toLongOrNull() ?: (length - 1)).coerceAtMost(length - 1)
        }
        return if (start in 0..end) start to end else null
    }

    private fun inMemory(mime: String, bytes: ByteArray) = WebResourceResponse(
        mime, "UTF-8", 200, "OK", NO_CACHE + ("Content-Length" to bytes.size.toString()), ByteArrayInputStream(bytes),
    )

    /**
     * A missing game file fails like on PC, where NW.js reports a network error for a file
     * that does not exist: MV's loaders only handle the error event and ignore a 404 load,
     * which left the game waiting forever. Without a response WebView tries the network,
     * which the app cannot use (no internet permission), so the request fails at once.
     */
    private fun missing(): WebResourceResponse? = null

    private fun error(status: Int, reason: String) =
        WebResourceResponse("text/plain", "UTF-8", status, reason, NO_CACHE, ByteArrayInputStream(ByteArray(0)))

    private class LimitedInputStream(input: InputStream, private var remaining: Long) : FilterInputStream(input) {
        override fun read(): Int {
            if (remaining <= 0) return -1
            return super.read().also { if (it >= 0) remaining-- }
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (remaining <= 0) return -1
            val n = super.read(b, off, minOf(len.toLong(), remaining).toInt())
            if (n > 0) remaining -= n
            return n
        }

        override fun available(): Int = minOf(super.available().toLong(), remaining).toInt()
    }
}
