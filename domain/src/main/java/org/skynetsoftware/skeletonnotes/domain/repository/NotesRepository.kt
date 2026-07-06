package org.skynetsoftware.skeletonnotes.domain.repository

import org.skynetsoftware.skeletonnotes.domain.model.Note
import org.skynetsoftware.skeletonnotes.domain.model.NoteWithAttachments
import org.skynetsoftware.skeletonnotes.domain.model.Result

interface NotesRepository {
    suspend fun getAllNotes(): Result<List<Note>>

    suspend fun getNoteById(id: Long): Result<NoteWithAttachments>

    suspend fun saveNote(noteWithAttachments: NoteWithAttachments): Result<Long>

    suspend fun deleteNote(id: Long): Result<Unit>
}
