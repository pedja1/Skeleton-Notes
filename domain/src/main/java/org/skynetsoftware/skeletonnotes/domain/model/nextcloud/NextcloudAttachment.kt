package org.skynetsoftware.skeletonnotes.domain.model.nextcloud

/**
 * Represents an attachment reference inside a [NextcloudNote] JSON document.
 */
data class NextcloudAttachment(
    val id: String,
    val filename: String,
    val mimeType: String? = null,
)
