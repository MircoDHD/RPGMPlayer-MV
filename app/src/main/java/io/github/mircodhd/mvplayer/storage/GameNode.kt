// SPDX-FileCopyrightText: 2026 MircoDHD
// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.mircodhd.mvplayer.storage

import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/**
 * A file or folder inside a game folder. On the device this is a Storage Access Framework
 * document ([DocumentNode]), so the app needs no broad storage permission; unit tests use
 * plain files ([FileNode]).
 */
interface GameNode {
    val name: String
    val isDirectory: Boolean
    /** Size in bytes; 0 for folders. */
    val length: Long

    /** Direct children; empty for files or when the folder cannot be read. */
    fun children(): List<GameNode>

    fun child(name: String): GameNode? = children().firstOrNull { it.name == name }

    /** Opens the file for reading, positioned at [offset]. */
    fun open(offset: Long = 0): InputStream

    /** Opens the file for writing; replaces its content. */
    fun openOutput(): OutputStream

    fun createDirectory(name: String): GameNode?

    /** Creates an empty file named exactly [name]; returns null if that fails. */
    fun createFile(name: String): GameNode?

    fun delete(): Boolean

    /** Returns the renamed node, or null if renaming failed. */
    fun renameTo(name: String): GameNode?

    fun readBytes(): ByteArray = open().use { it.readBytes() }

    fun readText(): String = readBytes().toString(Charsets.UTF_8)

    /** Reads at most [maxChars] characters from the start, e.g. to find a version banner. */
    fun readHead(maxChars: Int): String = open().bufferedReader().use { reader ->
        val buffer = CharArray(maxChars)
        String(buffer, 0, reader.read(buffer).coerceAtLeast(0))
    }

    /** Total size of all files below this node. */
    fun totalSize(): Long = if (isDirectory) children().sumOf { it.totalSize() } else length
}

/** [GameNode] backed by java.io.File; used by unit tests. */
class FileNode(val file: File) : GameNode {
    override val name: String get() = file.name
    override val isDirectory: Boolean get() = file.isDirectory
    override val length: Long get() = if (file.isFile) file.length() else 0

    override fun children(): List<GameNode> = file.listFiles()?.map(::FileNode).orEmpty()

    override fun child(name: String): GameNode? = File(file, name).takeIf { it.exists() }?.let(::FileNode)

    override fun open(offset: Long): InputStream = FileInputStream(file).apply { if (offset > 0) channel.position(offset) }

    override fun openOutput(): OutputStream = SyncedOutputStream(java.io.FileOutputStream(file))

    override fun createDirectory(name: String): GameNode? =
        File(file, name).takeIf { it.mkdirs() || it.isDirectory }?.let(::FileNode)

    override fun createFile(name: String): GameNode? = try {
        File(file, name).takeIf { it.createNewFile() }?.let(::FileNode)
    } catch (e: IOException) {
        null
    }

    override fun delete(): Boolean = file.delete()

    override fun renameTo(name: String): GameNode? {
        val target = File(file.parentFile, name)
        return if (file.renameTo(target)) FileNode(target) else null
    }
}

/**
 * Forces the written data onto the storage when closed. Without it a crash or power loss
 * soon after saving can leave an empty file, because Android writes it out later; with
 * the temp-file-then-rename pattern that would replace a good save with an empty one.
 */
class SyncedOutputStream(private val out: java.io.FileOutputStream, private val onClose: () -> Unit = {}) :
    java.io.FilterOutputStream(out) {
    override fun write(b: ByteArray, off: Int, len: Int) = out.write(b, off, len)

    override fun close() {
        try {
            out.flush()
            runCatching { out.fd.sync() } // some providers' descriptors cannot sync
        } finally {
            out.close()
            onClose()
        }
    }
}

/** Writes [bytes] and forces them onto the storage (see [SyncedOutputStream]). */
fun java.io.File.writeSynced(bytes: ByteArray) {
    SyncedOutputStream(java.io.FileOutputStream(this)).use { it.write(bytes) }
}
