package org.skynetsoftware.skeletonnotes.domain.model

/**
 * Represents a [Note] combined with its associated list of [Attachment]s.
 */
data class NoteWithAttachments(
    val note: Note,
    val attachments: List<Attachment>,
)
