// SPDX-FileCopyrightText: 2026 MircoDHD
// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.mircodhd.mvplayer.storage

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import io.github.mircodhd.mvplayer.debug.DebugLog
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.InputStream
import java.io.OutputStream

/**
 * A document below a folder the user picked with ACTION_OPEN_DOCUMENT_TREE. The app keeps a
 * persistable read/write grant for that folder, which replaces all-files access.
 * Listing a folder is one query to the documents provider; files are opened as real file
 * descriptors, so seeking (range requests) stays cheap.
 */
class DocumentNode private constructor(
    private val resolver: ContentResolver,
    private val treeUri: Uri,
    private val documentId: String,
    override val name: String,
    private val mimeType: String,
    override val length: Long,
) : GameNode {
    override val isDirectory: Boolean get() = mimeType == Document.MIME_TYPE_DIR

    val uri: Uri get() = DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId)

    override fun children(): List<GameNode> {
        if (!isDirectory) return emptyList()
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, documentId)
        val result = ArrayList<GameNode>()
        try {
            resolver.query(childrenUri, PROJECTION, null, null, null)?.use { cursor ->
                while (cursor.moveToNext()) {
                    result += DocumentNode(
                        resolver, treeUri,
                        documentId = cursor.getString(0),
                        name = cursor.getString(1).orEmpty(),
                        mimeType = cursor.getString(2).orEmpty(),
                        length = if (cursor.isNull(3)) 0 else cursor.getLong(3),
                    )
                }
            }
        } catch (e: Exception) {
            // SecurityException when the grant was revoked, IllegalArgumentException for a stale id.
            DebugLog.w(TAG, "Cannot list $name", e)
        }
        return result
    }

    override fun open(offset: Long): InputStream {
        val pfd = resolver.openFileDescriptor(uri, "r") ?: throw FileNotFoundException(name)
        return ParcelFileDescriptor.AutoCloseInputStream(pfd).apply {
            if (offset > 0) (this as FileInputStream).channel.position(offset)
        }
    }

    override fun openOutput(): OutputStream = resolver.openOutputStream(uri, "wt") ?: throw FileNotFoundException(name)

    override fun createDirectory(name: String): GameNode? = create(Document.MIME_TYPE_DIR, name)

    override fun createFile(name: String): GameNode? = create(OCTET_STREAM, name)

    private fun create(mime: String, childName: String): GameNode? = try {
        val created = DocumentsContract.createDocument(resolver, uri, mime, childName) ?: return null
        val node = DocumentNode(resolver, treeUri, DocumentsContract.getDocumentId(created), childName, mime, 0)
        // Providers add " (1)" or an extension when the name is taken or unusual; callers need the exact name.
        if (queryName(created) == childName) {
            node
        } else {
            DocumentsContract.deleteDocument(resolver, created)
            DebugLog.w(TAG, "Provider renamed new document '$childName'; removed it again")
            null
        }
    } catch (e: Exception) {
        DebugLog.w(TAG, "Cannot create '$childName' in $name", e)
        null
    }

    override fun delete(): Boolean = try {
        DocumentsContract.deleteDocument(resolver, uri)
    } catch (e: Exception) {
        DebugLog.w(TAG, "Cannot delete $name", e)
        false
    }

    override fun renameTo(name: String): GameNode? = try {
        val renamed = DocumentsContract.renameDocument(resolver, uri, name) ?: uri
        DocumentNode(resolver, treeUri, DocumentsContract.getDocumentId(renamed), name, mimeType, length)
            .takeIf { queryName(renamed) == name }
    } catch (e: Exception) {
        DebugLog.w(TAG, "Cannot rename ${this.name} to $name", e)
        null
    }

    private fun queryName(documentUri: Uri): String? =
        resolver.query(documentUri, arrayOf(Document.COLUMN_DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }

    companion object {
        private const val TAG = "Storage"
        private const val OCTET_STREAM = "application/octet-stream"
        private val PROJECTION = arrayOf(
            Document.COLUMN_DOCUMENT_ID,
            Document.COLUMN_DISPLAY_NAME,
            Document.COLUMN_MIME_TYPE,
            Document.COLUMN_SIZE,
        )
        const val GRANT_FLAGS = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION

        /** The picked folder itself, or null if the grant is missing or the folder is gone. */
        fun root(context: Context, treeUri: Uri): DocumentNode? {
            val resolver = context.contentResolver
            val rootId = runCatching { DocumentsContract.getTreeDocumentId(treeUri) }.getOrNull() ?: return null
            val rootUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, rootId)
            return try {
                resolver.query(rootUri, PROJECTION, null, null, null)?.use { cursor ->
                    if (!cursor.moveToFirst()) return null
                    DocumentNode(resolver, treeUri, cursor.getString(0), cursor.getString(1).orEmpty(),
                        cursor.getString(2).orEmpty(), 0).takeIf { it.isDirectory }
                }
            } catch (e: Exception) {
                DebugLog.w(TAG, "Cannot open picked folder $treeUri", e)
                null
            }
        }

        fun hasGrant(context: Context, treeUri: Uri): Boolean = context.contentResolver.persistedUriPermissions
            .any { it.uri == treeUri && it.isReadPermission && it.isWritePermission }

        /**
         * Human-readable location of a picked folder, e.g. "Games/Fear & Hunger" for the
         * internal storage or "1A2B-3C4D: Games/X" for an SD card.
         */
        fun describe(treeUri: Uri): String {
            val id = runCatching { DocumentsContract.getTreeDocumentId(treeUri) }.getOrNull() ?: return treeUri.toString()
            val volume = id.substringBefore(':', "")
            val path = id.substringAfter(':')
            return when {
                treeUri.authority != "com.android.externalstorage.documents" -> id
                volume.equals("primary", ignoreCase = true) || volume.isEmpty() -> path.ifEmpty { "/" }
                volume.equals("home", ignoreCase = true) -> "Documents/$path".trimEnd('/')
                else -> "$volume: $path"
            }
        }
    }
}
