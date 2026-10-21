// SPDX-FileCopyrightText: 2026 MircoDHD
// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.mircodhd.mvplayer.library

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import androidx.core.net.toUri
import io.github.mircodhd.mvplayer.debug.DebugLog
import io.github.mircodhd.mvplayer.storage.DocumentNode
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.UUID

/** Facts about a game shown in its info sheet; refreshed in the background. */
data class GameDetails(
    val engineVersion: String?,
    val pixiVersion: String?,
    val enabledPlugins: Int,
    val totalPlugins: Int,
    val sizeBytes: Long,
    val fileCount: Int,
    val encrypted: Boolean,
    val usesGreenworks: Boolean,
    val profiles: List<String>,
    /** The game's own patches, "file (position)". */
    val userPatches: List<String> = emptyList(),
)

data class GameEntry(
    val id: String,
    /** Tree URI of the folder the user picked (contains Game.exe or index.html). */
    val uri: String,
    val title: String,
    val addedAt: Long,
    val lastPlayedAt: Long,
    val playTimeMs: Long,
    val details: GameDetails?,
) {
    val treeUri: Uri get() = uri.toUri()

    /** Readable location such as "Games/Fear & Hunger". */
    val location: String get() = DocumentNode.describe(treeUri)
}

/**
 * The games the user has added, stored as JSON in the app's private files. All methods are
 * synchronized; reads return immutable snapshots.
 */
class GameLibrary private constructor(private val context: Context) {
    private val file = File(context.filesDir, "library.json")
    private var entries: List<GameEntry> = load()

    @Synchronized
    fun all(): List<GameEntry> = entries

    @Synchronized
    fun find(id: String): GameEntry? = entries.firstOrNull { it.id == id }

    @Synchronized
    fun findByUri(uri: String): GameEntry? = entries.firstOrNull { it.uri == uri }

    /** Returns the new entry, or null if this folder is already in the library. */
    @Synchronized
    fun add(uri: String, title: String): GameEntry? {
        if (findByUri(uri) != null) return null
        val entry = GameEntry(
            id = UUID.randomUUID().toString(),
            uri = uri,
            title = title,
            addedAt = System.currentTimeMillis(),
            lastPlayedAt = 0,
            playTimeMs = 0,
            details = null,
        )
        entries = entries + entry
        save()
        DebugLog.i(TAG, "Added '$title' (${entry.location})")
        return entry
    }

    @Synchronized
    fun update(id: String, transform: (GameEntry) -> GameEntry) {
        entries = entries.map { if (it.id == id) transform(it) else it }
        save()
    }

    @Synchronized
    fun remove(id: String) {
        val entry = find(id) ?: return
        entries = entries.filterNot { it.id == id }
        save()
        coverFile(id).delete()
        DebugLog.i(TAG, "Removed '${entry.title}' (${entry.location}); game files untouched")
    }

    /** Adds foreground time of a play session and marks the game as just played. */
    @Synchronized
    fun recordPlay(id: String, durationMs: Long) {
        val entry = find(id) ?: return
        update(entry.id) { it.copy(playTimeMs = it.playTimeMs + durationMs, lastPlayedAt = System.currentTimeMillis()) }
    }

    fun coverFile(id: String) = File(File(context.filesDir, "covers"), "$id.webp")

    private fun load(): List<GameEntry> {
        if (!file.isFile) return emptyList()
        return try {
            val array = JSONArray(file.readText())
            (0 until array.length()).map { fromJson(array.getJSONObject(it)) }
        } catch (e: Exception) {
            DebugLog.e(TAG, "Could not read $file, starting with an empty library", e)
            emptyList()
        }
    }

    private fun save() {
        try {
            val tmp = File(file.parentFile, "${file.name}.tmp")
            tmp.writeText(JSONArray(entries.map(::toJson)).toString(2))
            if (!tmp.renameTo(file)) throw IOException("rename failed")
        } catch (e: IOException) {
            DebugLog.e(TAG, "Could not write $file", e)
        }
    }

    private fun toJson(entry: GameEntry) = JSONObject()
        .put("id", entry.id)
        .put("uri", entry.uri)
        .put("title", entry.title)
        .put("addedAt", entry.addedAt)
        .put("lastPlayedAt", entry.lastPlayedAt)
        .put("playTimeMs", entry.playTimeMs)
        .put("details", entry.details?.let { d ->
            JSONObject()
                .put("engineVersion", d.engineVersion)
                .put("pixiVersion", d.pixiVersion)
                .put("enabledPlugins", d.enabledPlugins)
                .put("totalPlugins", d.totalPlugins)
                .put("sizeBytes", d.sizeBytes)
                .put("fileCount", d.fileCount)
                .put("encrypted", d.encrypted)
                .put("usesGreenworks", d.usesGreenworks)
                .put("profiles", JSONArray(d.profiles))
                .put("userPatches", JSONArray(d.userPatches))
        })

    private fun fromJson(json: JSONObject) = GameEntry(
        id = json.getString("id"),
        uri = json.getString("uri"),
        title = json.optString("title"),
        addedAt = json.optLong("addedAt"),
        lastPlayedAt = json.optLong("lastPlayedAt"),
        playTimeMs = json.optLong("playTimeMs"),
        details = json.optJSONObject("details")?.let { d ->
            GameDetails(
                engineVersion = d.optString("engineVersion").takeIf { it.isNotEmpty() },
                pixiVersion = d.optString("pixiVersion").takeIf { it.isNotEmpty() },
                enabledPlugins = d.optInt("enabledPlugins"),
                totalPlugins = d.optInt("totalPlugins"),
                sizeBytes = d.optLong("sizeBytes"),
                fileCount = d.optInt("fileCount"),
                encrypted = d.optBoolean("encrypted"),
                usesGreenworks = d.optBoolean("usesGreenworks"),
                profiles = d.optJSONArray("profiles")?.let { a -> (0 until a.length()).map { a.getString(it) } }.orEmpty(),
                userPatches = d.optJSONArray("userPatches")?.let { a -> (0 until a.length()).map { a.getString(it) } }.orEmpty(),
            )
        },
    )

    companion object {
        private const val TAG = "Library"

        // Holds the application context only, which lives as long as the process.
        @SuppressLint("StaticFieldLeak")
        @Volatile private var instance: GameLibrary? = null

        fun get(context: Context): GameLibrary =
            instance ?: synchronized(this) { instance ?: GameLibrary(context.applicationContext).also { instance = it } }
    }
}
