package org.skynetsoftware.skeletonnotes.data.database

import androidx.room.Embedded
import androidx.room.Relation

data class NoteWithAttachments(
    @Embedded val note: NoteEntity,
    @Relation(
          parentColumn = "id",
          entityColumn = "noteId"
    )
    val attachments: List<AttachmentEntity>
)