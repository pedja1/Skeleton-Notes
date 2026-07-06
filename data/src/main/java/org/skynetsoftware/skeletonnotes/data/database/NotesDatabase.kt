package org.skynetsoftware.skeletonnotes.data.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [
        NoteEntity::class,
        AttachmentEntity::class,
    ],
    version = 1
)
@TypeConverters(Converters::class)
internal abstract class NotesDatabase : RoomDatabase() {
    abstract fun notesDao(): NotesDao
    abstract fun attachmentDao(): AttachmentDao
}