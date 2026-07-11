package org.skynetsoftware.skeletonnotes.domain.model.nextcloud

/**
 * Info about a file found via WebDAV PROPFIND.
 */
data class NextcloudFileInfo(
    val filename: String,
    val lastModified: Long,
)
