package org.skynetsoftware.skeletonnotes.domain.model

data class Attachment(
    val id: Long,
    val noteId: Long,
    val uri: String,
)