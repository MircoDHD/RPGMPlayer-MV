// SPDX-FileCopyrightText: 2026 MircoDHD
// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.mircodhd.mvplayer.library

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.SystemClock
import androidx.core.graphics.get
import androidx.core.graphics.scale
import io.github.mircodhd.mvplayer.debug.DebugLog
import io.github.mircodhd.mvplayer.game.GameInfo
import io.github.mircodhd.mvplayer.game.GameInspector
import io.github.mircodhd.mvplayer.game.MvDecrypter
import io.github.mircodhd.mvplayer.patch.PatchProfiles
import io.github.mircodhd.mvplayer.storage.GameNode
import java.io.File

/** Reads library details and the cover image from a game folder. Call off the main thread. */
object GameScanner {
    private const val TAG = "Scanner"
    private const val COVER_MAX_WIDTH = 640

    fun scan(folder: GameNode, coverOut: File): Pair<GameInfo, GameDetails>? {
        val start = SystemClock.elapsedRealtime()
        val webRoot = GameInspector.detect(folder).webRoot ?: return null
        val info = GameInspector.inspectMv(webRoot, folder.name)
        val selection = PatchProfiles.select(info)
        val details = GameDetails(
            engineVersion = info.engineVersion,
            pixiVersion = info.pixiVersion,
            enabledPlugins = info.enabledPlugins.size,
            totalPlugins = info.totalPlugins,
            // The web root is already indexed; only files next to it (Game.exe, DLLs) need a walk.
            sizeBytes = info.index.totalBytes +
                if (webRoot === folder) 0 else folder.children().filterNot { it.name == webRoot.name }.sumOf { it.totalSize() },
            fileCount = info.index.size,
            encrypted = info.encryptionKey != null,
            usesGreenworks = info.usesGreenworks,
            profiles = selection.profileIds,
            userPatches = selection.userPatches.map { "${it.file} (${it.position.id})" },
        )
        if (!coverOut.isFile) extractCover(info, coverOut)
        DebugLog.d(TAG, "Scanned ${info.title} in ${SystemClock.elapsedRealtime() - start} ms: $details")
        return info to details
    }

    /**
     * Uses the title screen image as cover. Prefers the images named in System.json, then
     * anything in img/titles1 and img/titles2, then the window icon. Opaque images win over
     * transparent overlays such as logos.
     */
    private fun extractCover(info: GameInfo, out: File) {
        val imageExtensions = listOf(".rpgmvp", ".png")
        val folderImages = (info.index.pathsUnder("img/titles1/") + info.index.pathsUnder("img/titles2/"))
            .filter { path -> imageExtensions.any { path.endsWith(it, ignoreCase = true) } }
            .map { it.substringBeforeLast('.') }
        val candidates = (info.titleImages + folderImages).distinct()
        var fallback: Bitmap? = null
        for (base in candidates) {
            val bitmap = decode(info, base) ?: continue
            if (isOpaque(bitmap)) {
                save(bitmap, out)
                return
            }
            if (fallback == null) fallback = bitmap
        }
        val icon = fallback ?: info.index.resolve("icon/icon.png")?.let(::decodePlain)
        if (icon != null) save(icon, out) else DebugLog.i(TAG, "No cover image found for ${info.title}")
    }

    /** [base] is a web-root path without extension; tries the encrypted and plain variants. */
    private fun decode(info: GameInfo, base: String): Bitmap? {
        info.index.resolve("$base.rpgmvp")?.let { file ->
            val key = info.encryptionKey ?: return@let
            val bytes = MvDecrypter.decrypt(file.readBytes(), key) ?: return@let
            return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        }
        return info.index.resolve("$base.png")?.let(::decodePlain)
    }

    private fun decodePlain(node: GameNode): Bitmap? = runCatching { node.open().use(BitmapFactory::decodeStream) }.getOrNull()

    private fun isOpaque(bitmap: Bitmap): Boolean {
        if (!bitmap.hasAlpha()) return true
        val steps = 12
        for (y in 0 until steps) for (x in 0 until steps) {
            val pixel = bitmap[x * (bitmap.width - 1) / (steps - 1), y * (bitmap.height - 1) / (steps - 1)]
            if (Color.alpha(pixel) < 250) return false
        }
        return true
    }

    private fun save(bitmap: Bitmap, out: File) {
        val scaled = if (bitmap.width > COVER_MAX_WIDTH) {
            bitmap.scale(COVER_MAX_WIDTH, bitmap.height * COVER_MAX_WIDTH / bitmap.width)
        } else {
            bitmap
        }
        out.parentFile?.mkdirs()
        out.outputStream().use { scaled.compress(Bitmap.CompressFormat.WEBP_LOSSY, 85, it) }
        DebugLog.d(TAG, "Saved cover ${scaled.width}x${scaled.height} to $out")
    }
}
