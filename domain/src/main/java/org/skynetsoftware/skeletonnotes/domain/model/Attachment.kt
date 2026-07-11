package org.skynetsoftware.skeletonnotes.domain.model

/**
 * Represents an attachment linked to a note.
 * @param uri local file path to the attachment
 * @param mimeType the attachment's MIME type (e.g. `image/jpeg`), or `null` when unknown
 * (e.g. legacy rows created before the type was recorded).
 * @param filename the original display name of the attachment (e.g. `report.pdf`), or `null` for
 * legacy rows created before the name was recorded. The on-disk file is always named `{id}.{ext}`,
 * independent of this value.
 */
data class Attachment(
    val id: String,
    val noteId: String,
    val uri: String,
    val mimeType: String? = null,
    val filename: String? = null,
)
