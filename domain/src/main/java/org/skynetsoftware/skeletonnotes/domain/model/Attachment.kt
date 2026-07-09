package org.skynetsoftware.skeletonnotes.domain.model

/**
 * Represents an attachment linked to a note.
 * @param uri local file path to the attachment
 */
data class Attachment(
    val id: String,
    val noteId: String,
    val uri: String,
)
