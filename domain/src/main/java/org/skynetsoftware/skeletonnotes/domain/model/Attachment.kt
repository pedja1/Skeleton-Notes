package org.skynetsoftware.skeletonnotes.domain.model

/**
 * Represents an attachment linked to a note.
 * @param uri any supported uri, like file://, http://, content://
 */
data class Attachment(
    val id: Long,
    val noteId: Long,
    val uri: String,
)
