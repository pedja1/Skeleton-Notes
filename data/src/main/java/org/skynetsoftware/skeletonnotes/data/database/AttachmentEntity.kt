package org.skynetsoftware.skeletonnotes.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "attachments")
data class AttachmentEntity(
    @PrimaryKey
    val id: Int = 0,
    val noteId: Int,
    val uri: String,
)