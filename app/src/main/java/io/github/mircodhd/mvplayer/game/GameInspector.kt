package io.github.mircodhd.mvplayer.game

import android.os.SystemClock
import io.github.mircodhd.mvplayer.debug.DebugLog
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

enum class Engine { MV, MZ, RGSS, RPG2K, UNKNOWN }

/** Result of a cheap check that only looks at a few marker files. */
data class EngineMatch(val engine: Engine, val webRoot: File?)

/** Everything the player needs to know about an MV game before it starts. */
data class GameInfo(
    val webRoot: File,
    val title: String,
    val engineVersion: String?,
    val enabledPlugins: List<String>,
    val usesGreenworks: Boolean,
    val audioExt: String,
    val videoExt: String,
    /** Asset key from System.json, null if the game is not encrypted. */
    val encryptionKey: ByteArray?,
    val index: FileIndex,
)

object GameInspector {
    private const val TAG = "Inspector"
    private const val BOM = "\uFEFF"

    /** Finds the folder that holds index.html and identifies the engine. */
    fun detect(dir: File): EngineMatch = detectEngine(dir).also {
        DebugLog.i(TAG, "Detected ${it.engine} in $dir (web root ${it.webRoot})")
    }

    private fun detectEngine(dir: File): EngineMatch {
        val webRoot = listOf(File(dir, "www"), dir).firstOrNull { File(it, "index.html").isFile }
        if (webRoot != null) {
            if (File(webRoot, "js/rpg_core.js").isFile) return EngineMatch(Engine.MV, webRoot)
            if (File(webRoot, "js/rmmz_core.js").isFile) return EngineMatch(Engine.MZ, webRoot)
        }
        val names = dir.list()?.map { it.lowercase() }?.toSet().orEmpty()
        if ("rpg_rt.ldb" in names || "rpg_rt.lmt" in names) return EngineMatch(Engine.RPG2K, null)
        val rgssArchives = setOf("game.rgssad", "game.rgss2a", "game.rgss3a")
        if ("game.ini" in names && (names.any { it in rgssArchives } || "data" in names)) {
            return EngineMatch(Engine.RGSS, null)
        }
        return EngineMatch(Engine.UNKNOWN, null)
    }

    /** Full inspection of an MV game. Reads plugin files, so call it off the main thread. */
    fun inspectMv(webRoot: File): GameInfo {
        val start = SystemClock.elapsedRealtime()
        val index = FileIndex.build(webRoot)
        DebugLog.i(TAG, "Indexed ${index.size} files under $webRoot in ${SystemClock.elapsedRealtime() - start} ms")
        val plugins = readEnabledPlugins(webRoot)
        val usesGreenworks = plugins.any { name ->
            val file = index.resolve("js/plugins/$name.js") ?: return@any false
            val text = runCatching { file.readText() }.getOrDefault("")
            text.contains("greenworks", ignoreCase = true)
        }
        val system = readSystem(webRoot)
        return GameInfo(
            webRoot = webRoot,
            title = system?.optString("gameTitle")?.takeIf { it.isNotBlank() }
                ?: webRoot.absoluteFile.parentFile?.name ?: webRoot.name,
            engineVersion = readCoreVersion(webRoot),
            enabledPlugins = plugins,
            usesGreenworks = usesGreenworks,
            audioExt = pickAudioExt(index),
            videoExt = pickVideoExt(index),
            encryptionKey = MvDecrypter.parseKey(system?.optString("encryptionKey")),
            index = index,
        ).also {
            DebugLog.i(TAG, "${it.title}: MV ${it.engineVersion}, ${plugins.size} plugins, " +
                "greenworks=${it.usesGreenworks}, audio=${it.audioExt}, video=${it.videoExt}, " +
                "encrypted=${it.encryptionKey != null}, inspected in ${SystemClock.elapsedRealtime() - start} ms")
            DebugLog.i(TAG, "Enabled plugins: ${plugins.joinToString()}")
            DebugLog.d(TAG, "File types: ${index.extensionCounts()}")
        }
    }

    private fun readSystem(webRoot: File): JSONObject? = runCatching {
        JSONObject(File(webRoot, "data/System.json").readText().removePrefix(BOM))
    }.onFailure { DebugLog.w(TAG, "Could not read data/System.json", it) }.getOrNull()

    private fun readCoreVersion(webRoot: File): String? = runCatching {
        val head = File(webRoot, "js/rpg_core.js").bufferedReader().use { r ->
            CharArray(16 * 1024).let { buf -> String(buf, 0, r.read(buf).coerceAtLeast(0)) }
        }
        Regex("""RPGMAKER_VERSION\s*=\s*["']([^"']+)""").find(head)?.groupValues?.get(1)
            ?: Regex("""rpg_core\.js v([\d.]+)""").find(head)?.groupValues?.get(1)
    }.getOrNull()

    private fun readEnabledPlugins(webRoot: File): List<String> = runCatching {
        val text = File(webRoot, "js/plugins.js").readText()
        val array = JSONArray(text.substring(text.indexOf('['), text.lastIndexOf(']') + 1))
        (0 until array.length())
            .map { array.getJSONObject(it) }
            .filter { it.optBoolean("status") }
            .map { it.optString("name") }
            .filter { it.isNotBlank() }
    }.getOrElse {
        DebugLog.w(TAG, "Could not read js/plugins.js", it)
        emptyList()
    }

    // Utils.isMobileDevice() makes MV ask for .m4a/.mp4; serve whatever the game actually ships.
    private fun pickAudioExt(index: FileIndex): String {
        val hasOgg = index.anyUnder("audio/") { it.endsWith(".ogg") || it.endsWith(".rpgmvo") }
        val hasM4a = index.anyUnder("audio/") { it.endsWith(".m4a") || it.endsWith(".rpgmvm") }
        return if (!hasOgg && hasM4a) ".m4a" else ".ogg"
    }

    private fun pickVideoExt(index: FileIndex): String {
        val hasWebm = index.anyUnder("movies/") { it.endsWith(".webm") }
        val hasMp4 = index.anyUnder("movies/") { it.endsWith(".mp4") }
        return if (!hasWebm && hasMp4) ".mp4" else ".webm"
    }
}
