// SPDX-FileCopyrightText: 2026 MircoDHD
// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.mircodhd.mvplayer.fonts

import android.content.Context
import android.net.Uri
import io.github.mircodhd.mvplayer.debug.DebugLog
import java.io.File
import java.io.InputStream

/** A font the game text can use instead of the game's own. */
data class FontOption(
    val id: String,
    val name: String,
    val pixel: Boolean,
    /** Path in the APK assets for bundled fonts. */
    val asset: String? = null,
    /** File in the app's storage for fonts the player added. */
    val file: File? = null,
)

/**
 * Bundled fonts (SIL Open Font License, licence next to each file in `assets/fonts/`) and
 * fonts the player added, copied to `filesDir/fonts/`. Ids: [GAME] for the game's own
 * font, a bundled id, or "custom/<file name>".
 */
object FontLibrary {
    const val GAME = "game"
    private const val CUSTOM = "custom/"
    private const val TAG = "Fonts"
    private const val MAX_BYTES = 30L * 1024 * 1024
    private val SAFE_NAME = Regex("""[^A-Za-z0-9_.\- ]""")

    val BUNDLED = listOf(
        FontOption("pixelify-sans", "Pixelify Sans", pixel = true, asset = "fonts/pixelify-sans/PixelifySans.ttf"),
        FontOption("vt323", "VT323", pixel = true, asset = "fonts/vt323/VT323-Regular.ttf"),
        FontOption("press-start-2p", "Press Start 2P", pixel = true, asset = "fonts/press-start-2p/PressStart2P-Regular.ttf"),
        FontOption("atkinson-hyperlegible", "Atkinson Hyperlegible", pixel = false,
            asset = "fonts/atkinson-hyperlegible/AtkinsonHyperlegible-Regular.ttf"),
    )

    private fun customDir(context: Context) = File(context.filesDir, "fonts")

    fun custom(context: Context): List<FontOption> =
        customDir(context).listFiles()?.filter { it.isFile }?.sortedBy { it.name.lowercase() }?.map {
            FontOption(CUSTOM + it.name, it.nameWithoutExtension, pixel = false, file = it)
        }.orEmpty()

    fun all(context: Context): List<FontOption> = BUNDLED + custom(context)

    fun find(context: Context, id: String): FontOption? =
        BUNDLED.firstOrNull { it.id == id } ?: custom(context).firstOrNull { it.id == id }

    fun open(context: Context, id: String): InputStream? {
        val font = find(context, id) ?: return null
        return try {
            font.asset?.let { context.assets.open(it) } ?: font.file?.inputStream()
        } catch (e: Exception) {
            DebugLog.w(TAG, "Cannot open font $id", e)
            null
        }
    }

    /** Copies a font file the player picked; returns its id, or null if it is no font. */
    fun import(context: Context, uri: Uri, displayName: String?): String? {
        val name = (displayName ?: uri.lastPathSegment ?: "font.ttf").substringAfterLast('/').replace(SAFE_NAME, "_").take(80)
        val bytes = try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                val data = input.readBytes()
                if (data.size > MAX_BYTES) null else data
            }
        } catch (e: Exception) {
            DebugLog.w(TAG, "Cannot read picked font $uri", e)
            null
        } ?: return null
        if (!isFont(bytes)) {
            DebugLog.w(TAG, "Picked file $name is not a TrueType, OpenType or WOFF font")
            return null
        }
        val dir = customDir(context).apply { mkdirs() }
        val target = File(dir, name.ifBlank { "font.ttf" })
        target.writeBytes(bytes)
        DebugLog.i(TAG, "Added font ${target.name} (${bytes.size / 1024} KiB)")
        return CUSTOM + target.name
    }

    fun delete(context: Context, id: String): Boolean {
        if (!id.startsWith(CUSTOM)) return false
        val file = File(customDir(context), id.removePrefix(CUSTOM))
        return file.delete().also { if (it) DebugLog.i(TAG, "Removed font ${file.name}") }
    }

    /** TrueType (00 01 00 00 or "true"), OpenType ("OTTO"), WOFF and WOFF2 signatures. */
    fun isFont(bytes: ByteArray): Boolean {
        if (bytes.size < 12) return false
        val tag = String(bytes, 0, 4, Charsets.ISO_8859_1)
        return tag == "\u0000\u0001\u0000\u0000" || tag == "true" || tag == "OTTO" || tag == "wOFF" || tag == "wOF2"
    }
}
