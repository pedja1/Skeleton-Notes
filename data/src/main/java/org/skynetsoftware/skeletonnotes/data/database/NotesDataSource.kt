package org.skynetsoftware.skeletonnotes.data.database

import org.skynetsoftware.skeletonnotes.domain.model.Note
import org.skynetsoftware.skeletonnotes.domain.model.NoteWithAttachments
import org.skynetsoftware.skeletonnotes.domain.model.Result

/**
 * Abstraction over the underlying SQLite database for notes.
 * Allows [org.skynetsoftware.skeletonnotes.data.repository.NotesRepositoryImpl] to be tested with mocked data sources.
 */
internal interface NotesDataSource {
    suspend fun getAllNotes(): Result<List<Note>>
    suspend fun getNoteById(id: Long): Result<NoteWithAttachments>
    suspend fun saveNote(noteWithAttachments: NoteWithAttachments): Result<Long>
    suspend fun deleteNote(id: Long): Result<Unit>
}
