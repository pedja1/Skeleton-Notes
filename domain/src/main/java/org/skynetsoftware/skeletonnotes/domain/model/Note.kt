package org.skynetsoftware.skeletonnotes.domain.model

data class Note(
    val id: Long,
    val title: String?,
    val content: String,
    val createdAt: Long,
    val modifiedAt: Long,
    val tags: Set<String>,
)
