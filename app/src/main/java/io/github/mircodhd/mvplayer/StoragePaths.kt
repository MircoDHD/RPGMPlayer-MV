// SPDX-FileCopyrightText: 2026 MircoDHD
// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.mircodhd.mvplayer

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.os.storage.StorageManager
import android.provider.DocumentsContract
import java.io.File

object StoragePaths {
    private const val EXTERNAL_STORAGE_AUTHORITY = "com.android.externalstorage.documents"

    /**
     * Maps a folder picked with ACTION_OPEN_DOCUMENT_TREE to a real path. With all-files
     * access the folder is then read through java.io.File, which is much faster than SAF
     * for thousands of small game files. Returns null for providers without a local path.
     */
    fun treeUriToFile(context: Context, uri: Uri): File? {
        if (uri.authority != EXTERNAL_STORAGE_AUTHORITY) return null
        val docId = DocumentsContract.getTreeDocumentId(uri)
        val volume = docId.substringBefore(':')
        val relative = docId.substringAfter(':', "")
        val base = when {
            volume.equals("primary", ignoreCase = true) -> Environment.getExternalStorageDirectory()
            volume.equals("home", ignoreCase = true) ->
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
            else -> context.getSystemService(StorageManager::class.java).storageVolumes
                .firstOrNull { it.uuid.equals(volume, ignoreCase = true) }
                ?.directory
        } ?: return null
        return if (relative.isEmpty()) base else File(base, relative)
    }
}
