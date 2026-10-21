// SPDX-FileCopyrightText: 2026 MircoDHD
// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.mircodhd.mvplayer.saves

import io.github.mircodhd.mvplayer.debug.DebugLog
import org.json.JSONArray
import org.json.JSONObject
import io.github.mircodhd.mvplayer.storage.SyncedOutputStream
import io.github.mircodhd.mvplayer.storage.writeSynced
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import java.io.IOException
import java.util.UUID
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/** One app save ("Stand"): MV save data plus what the list shows. */
data class ResumePoint(
    val id: String,
    /** Kept points are never replaced by new automatic ones. */
    val kept: Boolean,
    val createdAt: Long,
    /** MV's play time text, e.g. "01:23:45". */
    val playtime: String,
    /** Map name, or empty. */
    val location: String,
    val sizeBytes: Long,
)

/**
 * App saves of one game in `filesDir/resume/<gameId>/`, outside the game folder: the game
 * creates one automatically every 15 minutes of play; at most [MAX_AUTO] automatic ones
 * are kept (the oldest goes), and the player can keep up to [MAX_KEPT] of them for good.
 * Each point is `<id>.json.gz` (MV's save contents as JsonEx JSON, gzip-compressed here
 * because compressing in JavaScript would stall the game for up to a second on slow
 * devices) and `<id>.webp` (a small screenshot); `points.json` lists them. Synchronized; the bridge
 * calls in from the JavaBridge thread, the UI from the main thread.
 */
class ResumePoints(filesDir: File, gameId: String) {
    private val dir = File(File(filesDir, "resume"), gameId.filter { it.isLetterOrDigit() || it == '-' })
    private val indexFile = File(dir, "points.json")

    @Synchronized
    fun list(): List<ResumePoint> = load().sortedByDescending { it.createdAt }

    /** Stores a new automatic point and drops the oldest automatic ones beyond [MAX_AUTO]. */
    @Synchronized
    fun add(json: String, thumbnail: ByteArray?, playtime: String, location: String): ResumePoint? = try {
        dir.mkdirs()
        val id = UUID.randomUUID().toString()
        val data = File(dir, "$id.json.gz")
        // Synced: app saves exist for the case that the device goes off suddenly.
        GZIPOutputStream(SyncedOutputStream(FileOutputStream(data)).buffered()).use { it.write(json.toByteArray()) }
        thumbnail?.let { File(dir, "$id.webp").writeSynced(it) }
        val point = ResumePoint(id, kept = false, createdAt = System.currentTimeMillis(), playtime = playtime, location = location,
            sizeBytes = data.length() + (thumbnail?.size ?: 0))
        val points = load() + point
        val autos = points.filter { !it.kept }.sortedByDescending { it.createdAt }
        val dropped = autos.drop(MAX_AUTO)
        dropped.forEach(::deleteFiles)
        save(points - dropped.toSet())
        DebugLog.i(TAG, "Saved app save $id at $location ($playtime, ${point.sizeBytes / 1024} KiB)" +
            if (dropped.isEmpty()) "" else ", removed ${dropped.size} older")
        point
    } catch (e: IOException) {
        DebugLog.e(TAG, "Could not store an app save", e)
        null
    }

    /** Keeps or releases a point; keeping fails when [MAX_KEPT] are kept already. */
    @Synchronized
    fun setKept(id: String, kept: Boolean): Boolean {
        val points = load()
        if (kept && points.count { it.kept } >= MAX_KEPT) return false
        val updated = points.map { if (it.id == id) it.copy(kept = kept) else it }
        // A released point is automatic again and may push the oldest automatic one out.
        val dropped = updated.filter { !it.kept }.sortedByDescending { it.createdAt }.drop(MAX_AUTO)
        dropped.forEach(::deleteFiles)
        save(updated - dropped.toSet())
        DebugLog.i(TAG, (if (kept) "Kept" else "Released") + " app save $id")
        return true
    }

    @Synchronized
    fun delete(id: String) {
        val points = load()
        points.filter { it.id == id }.forEach(::deleteFiles)
        save(points.filterNot { it.id == id })
        DebugLog.i(TAG, "Deleted app save $id")
    }

    @Synchronized
    fun read(id: String): String? = runCatching {
        GZIPInputStream(File(dir, "$id.json.gz").inputStream().buffered()).use { it.readBytes().toString(Charsets.UTF_8) }
    }.getOrNull()

    fun thumbnail(id: String): File = File(dir, "$id.webp")

    /** All points of the game as a zip, tagged with [gameTitle]; returns how many. */
    @Synchronized
    fun export(out: OutputStream, gameTitle: String): Int {
        val points = load()
        ZipOutputStream(out).use { zip ->
            zip.putNextEntry(ZipEntry(MANIFEST))
            zip.write(JSONObject().put("format", 1).put("game", gameTitle).put("points", toJson(points)).toString(1).toByteArray())
            zip.closeEntry()
            for (point in points) {
                for (name in listOf("${point.id}.json.gz", "${point.id}.webp")) {
                    val file = File(dir, name).takeIf { it.isFile } ?: continue
                    zip.putNextEntry(ZipEntry(name))
                    file.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                }
            }
        }
        DebugLog.i(TAG, "Exported ${points.size} app saves")
        return points.size
    }

    /**
     * Adds the points of an exported zip that are not here yet. Returns how many were added,
     * or -1 when the zip belongs to another game. The limits still apply: points beyond
     * [MAX_KEPT] kept ones count as automatic, and only the [MAX_AUTO] newest automatic stay.
     */
    @Synchronized
    fun import(input: InputStream, gameTitle: String): Int {
        val staged = HashMap<String, ByteArray>()
        var manifest: JSONObject? = null
        ZipInputStream(input).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                val name = entry.name.substringAfterLast('/')
                when {
                    name == MANIFEST -> manifest = JSONObject(zip.readBytes().toString(Charsets.UTF_8))
                    IMPORTED.matches(name) -> staged[name] = zip.readBytes()
                }
            }
        }
        val meta = manifest ?: return 0
        if (meta.optString("game") != gameTitle) {
            DebugLog.w(TAG, "Refused app saves of '${meta.optString("game")}' for '$gameTitle'")
            return -1
        }
        val existing = load()
        val incoming = fromJson(meta.optJSONArray("points") ?: JSONArray())
            .filter { p -> existing.none { it.id == p.id } && staged.containsKey("${p.id}.json.gz") }
        dir.mkdirs()
        for (point in incoming) {
            File(dir, "${point.id}.json.gz").writeSynced(staged.getValue("${point.id}.json.gz"))
            staged["${point.id}.webp"]?.let { File(dir, "${point.id}.webp").writeSynced(it) }
        }
        var keptRoom = MAX_KEPT - existing.count { it.kept }
        val merged = existing + incoming.sortedByDescending { it.createdAt }.map { p ->
            if (p.kept && keptRoom > 0) p.also { keptRoom-- } else p.copy(kept = false)
        }
        val dropped = merged.filter { !it.kept }.sortedByDescending { it.createdAt }.drop(MAX_AUTO)
        dropped.forEach(::deleteFiles)
        save(merged - dropped.toSet())
        val added = incoming.count { p -> dropped.none { it.id == p.id } }
        DebugLog.i(TAG, "Imported $added app saves (${incoming.size - added} older ones did not fit)")
        return added
    }

    /** Removes every point of the game, e.g. when it leaves the library. */
    @Synchronized
    fun clear() {
        dir.deleteRecursively()
    }

    private fun deleteFiles(point: ResumePoint) {
        File(dir, "${point.id}.json.gz").delete()
        File(dir, "${point.id}.webp").delete()
    }

    private fun load(): List<ResumePoint> {
        if (!indexFile.isFile) return emptyList()
        return runCatching { fromJson(JSONArray(indexFile.readText())).filter { File(dir, "${it.id}.json.gz").length() > 0 } }
            .onFailure { DebugLog.e(TAG, "Cannot read $indexFile", it) }.getOrDefault(emptyList())
    }

    private fun fromJson(array: JSONArray): List<ResumePoint> = (0 until array.length()).mapNotNull { i ->
        val o = array.optJSONObject(i) ?: return@mapNotNull null
        val id = o.optString("id").takeIf { ID.matches(it) } ?: return@mapNotNull null
        ResumePoint(
            id = id,
            kept = o.optBoolean("kept"),
            createdAt = o.optLong("createdAt"),
            playtime = o.optString("playtime"),
            location = o.optString("location"),
            sizeBytes = o.optLong("sizeBytes"),
        )
    }

    private fun toJson(points: List<ResumePoint>) = JSONArray().apply {
        points.forEach {
            put(JSONObject().put("id", it.id).put("kept", it.kept).put("createdAt", it.createdAt)
                .put("playtime", it.playtime).put("location", it.location).put("sizeBytes", it.sizeBytes))
        }
    }

    private fun save(points: List<ResumePoint>) {
        val array = toJson(points)
        try {
            dir.mkdirs()
            val tmp = File(dir, "points.json.tmp")
            tmp.writeSynced(array.toString(1).toByteArray())
            if (!tmp.renameTo(indexFile)) throw IOException("rename failed")
        } catch (e: IOException) {
            DebugLog.e(TAG, "Could not write $indexFile", e)
        }
    }

    companion object {
        const val MAX_AUTO = 3
        const val MAX_KEPT = 3
        const val INTERVAL_MINUTES = 15
        private const val TAG = "AppSaves"
        private const val MANIFEST = "autosaves.json"
        private val ID = Regex("""[0-9a-f-]{36}""")
        private val IMPORTED = Regex("""[0-9a-f-]{36}\.(json\.gz|webp)""")
    }
}
