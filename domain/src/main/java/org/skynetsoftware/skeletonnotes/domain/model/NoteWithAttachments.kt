package org.skynetsoftware.skeletonnotes.domain.model

data class NoteWithAttachments(
    val note: Note,
    val attachments: List<Attachment>
)