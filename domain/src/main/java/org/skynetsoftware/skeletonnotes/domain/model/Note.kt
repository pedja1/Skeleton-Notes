package org.skynetsoftware.skeletonnotes.domain.model

data class Note(
    val id: Long = 0,
    val title: String? = null,
    val content: String,
    val createdAt: Long = System.currentTimeMillis(),
    val modifiedAt: Long = System.currentTimeMillis()
)
