package com.calmerge.app.ui

import java.net.URI

/**
 * Normalizes valid absolute URLs for duplicate detection.
 *
 * Invalid, relative, blank, or host-less input returns null so unrelated junk
 * cannot collide as the same feed.
 */
fun normalizeUrlOrNull(raw: String): String? {
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) return null

    return runCatching {
        val uri = URI(trimmed)
        val scheme = uri.scheme?.lowercase() ?: return null
        val authority = uri.rawAuthority?.lowercase() ?: return null
        if (uri.host.isNullOrBlank()) return null
        val path = uri.rawPath ?: ""
        val normalizedPath = if (path.endsWith("/") && path.length > 1) path.dropLast(1) else path
        val query = uri.rawQuery?.let { "?$it" } ?: ""
        "$scheme://$authority$normalizedPath$query"
    }.getOrNull()
}
