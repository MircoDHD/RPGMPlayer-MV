// SPDX-FileCopyrightText: 2026 MircoDHD
// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.mircodhd.mvplayer.patch

import android.content.Context
import android.net.Uri
import io.github.mircodhd.mvplayer.debug.DebugLog
import io.github.mircodhd.mvplayer.game.GameInfoCache
import io.github.mircodhd.mvplayer.game.GameInspector
import io.github.mircodhd.mvplayer.storage.DocumentNode
import io.github.mircodhd.mvplayer.storage.GameNode

/**
 * The `mvplayer-patches` folder of one game, for the patch guide: shows what is there,
 * creates the folder and copies picked files into it. Blocking (documents provider calls),
 * so call it off the main thread.
 */
object PatchFolder {
    private const val TAG = "Patches"
    private const val README = "README.txt"
    private val SAFE_NAME = Regex("""[^A-Za-z0-9_.\- ]""")

    /** Files in the folder, or null when the folder does not exist (or the game is unreachable). */
    fun files(context: Context, treeUri: Uri): List<String>? = folder(context, treeUri, create = false)
        ?.children()?.filterNot { it.isDirectory }?.map { it.name }?.sortedBy { it.lowercase() }

    /** Creates the folder with a short README; true when it exists afterwards. */
    fun create(context: Context, treeUri: Uri): Boolean {
        val folder = folder(context, treeUri, create = true) ?: return false
        if (folder.children().none { it.name.equals(README, ignoreCase = true) }) {
            runCatching {
                folder.createFile(README)?.openOutput()?.use { it.write(readme().toByteArray()) }
            }.onFailure { DebugLog.w(TAG, "Cannot write $README", it) }
        }
        changed(treeUri)
        return true
    }

    /** Copies a picked file into the folder (creating it); returns the stored name. */
    fun add(context: Context, treeUri: Uri, source: Uri, displayName: String?): String? = runCatching {
        val folder = folder(context, treeUri, create = true) ?: return null
        val name = (displayName ?: "patch.js").replace(SAFE_NAME, "_").take(80)
        val bytes = context.contentResolver.openInputStream(source)?.use { it.readBytes() } ?: return null
        folder.children().firstOrNull { it.name.equals(name, ignoreCase = true) }?.delete()
        val file = folder.createFile(name) ?: return null
        file.openOutput().use { it.write(bytes) }
        DebugLog.i(TAG, "Added game patch $name (${bytes.size} bytes)")
        changed(treeUri)
        name
    }.onFailure { DebugLog.w(TAG, "Cannot add a game patch", it) }.getOrNull()

    private fun folder(context: Context, treeUri: Uri, create: Boolean): GameNode? {
        val root = DocumentNode.root(context, treeUri) ?: return null
        val webRoot = GameInspector.detect(root).webRoot ?: return null
        webRoot.children().firstOrNull { it.isDirectory && it.name.equals(UserPatches.FOLDER, ignoreCase = true) }?.let { return it }
        return if (create) webRoot.createDirectory(UserPatches.FOLDER) else null
    }

    /** The cached scan would not list the new files; the next start scans again. */
    private fun changed(treeUri: Uri) {
        GameInfoCache.remove(treeUri.toString())
    }

    private fun readme() = """
        RPGM Player - MV: game patches
        ================================
        JavaScript files (.js) in this folder run with the game, like plugins.
        Without patches.json every .js file here runs after the plugins, in
        alphabetical order. A patches.json chooses the order and the moment:

        { "patches": [
            { "file": "early.js", "position": "beforePlugins" },
            { "file": "late.js" }
        ] }

        Positions: beforeEngine, beforePlugins, afterPlugins (the default).
        Patches can change anything in the game: only use patches you trust.
    """.trimIndent() + "\n"
}
