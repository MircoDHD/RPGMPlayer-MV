// SPDX-FileCopyrightText: 2026 MircoDHD
// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.mircodhd.mvplayer.web

object MimeTypes {
    private val byExtension = mapOf(
        "html" to "text/html",
        "htm" to "text/html",
        "js" to "text/javascript",
        "mjs" to "text/javascript",
        "json" to "application/json",
        "css" to "text/css",
        "txt" to "text/plain",
        "png" to "image/png",
        "jpg" to "image/jpeg",
        "jpeg" to "image/jpeg",
        "gif" to "image/gif",
        "webp" to "image/webp",
        "svg" to "image/svg+xml",
        "ico" to "image/x-icon",
        "ogg" to "audio/ogg",
        "m4a" to "audio/mp4",
        "mp3" to "audio/mpeg",
        "wav" to "audio/wav",
        "webm" to "video/webm",
        "mp4" to "video/mp4",
        "ttf" to "font/ttf",
        "otf" to "font/otf",
        "woff" to "font/woff",
        "woff2" to "font/woff2",
        "wasm" to "application/wasm",
    )

    private val textTypes = setOf("text/html", "text/javascript", "application/json", "text/css", "text/plain", "image/svg+xml")

    /** Encrypted MV assets (.rpgmvp/.rpgmvo/.rpgmvm) and unknown types are served as binary. */
    fun forPath(path: String): String =
        byExtension[path.substringAfterLast('.', "").lowercase()] ?: "application/octet-stream"

    fun isText(mime: String): Boolean = mime in textTypes
}
