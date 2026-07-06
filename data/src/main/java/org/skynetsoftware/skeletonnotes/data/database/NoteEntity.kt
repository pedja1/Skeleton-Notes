package org.skynetsoftware.skeletonnotes.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "notes")
data class NoteEntity(
    @PrimaryKey
    val id: Int = 0,
    val title: String?,
    val content: String,
    val created: Long,
    val modified: Long,
    val tags: Set<String>,
    val attachments: Set<String>
)