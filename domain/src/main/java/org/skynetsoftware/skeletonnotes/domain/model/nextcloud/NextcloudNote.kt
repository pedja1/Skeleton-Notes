package org.skynetsoftware.skeletonnotes.domain.model.nextcloud

/**
 * Data model for a note stored as a JSON file on Nextcloud via WebDAV.
 * Mirrors [org.skynetsoftware.skeletonnotes.domain.model.Note] properties for serialization.
 */
data class NextcloudNote(
    val id: String,
    val title: String?,
    val content: String,
    val createdAt: Long,
    val modifiedAt: Long,
    val tags: Set<String>,
    val status: String,
    val attachments: List<NextcloudAttachment>,
)
