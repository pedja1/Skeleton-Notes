package org.skynetsoftware.skeletonnotes.domain.model

/**
 * Represents a note entity with content, metadata, and optional tags.
 */
data class Note(
    val id: Long,
    val title: String?,
    val content: String,
    val createdAt: Long,
    val modifiedAt: Long,
    val tags: Set<String>,
    val status: NoteStatus = NoteStatus.ACTIVE,
)
