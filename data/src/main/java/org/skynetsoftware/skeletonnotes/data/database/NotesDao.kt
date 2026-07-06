package org.skynetsoftware.skeletonnotes.data.database

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface NotesDao {
    @Query("SELECT * FROM notes")
    fun getAll(): Flow<List<NoteEntity>>

    @Transaction
    @Query("SELECT * FROM notes")
    fun getAllWithAttachments(): Flow<List<NoteWithAttachments>>
}