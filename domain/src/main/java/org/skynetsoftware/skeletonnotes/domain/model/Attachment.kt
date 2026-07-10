package org.skynetsoftware.skeletonnotes.domain.model

/**
 * Represents an attachment linked to a note.
 * @param uri local file path to the attachment
 * @param mimeType the attachment's MIME type (e.g. `image/jpeg`), or `null` when unknown
 * (e.g. legacy rows created before the type was recorded).
 */
data class Attachment(
    val id: String,
    val noteId: String,
    val uri: String,
    val mimeType: String? = null,
)
