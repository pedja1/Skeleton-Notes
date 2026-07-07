package org.skynetsoftware.skeletonnotes.data.database

import org.skynetsoftware.skeletonnotes.domain.model.Note
import org.skynetsoftware.skeletonnotes.domain.model.NoteWithAttachments
import org.skynetsoftware.skeletonnotes.domain.model.Result

/**
 * Abstraction over the underlying SQLite database for notes.
 * Allows [org.skynetsoftware.skeletonnotes.data.repository.NotesRepositoryImpl] to be tested with mocked data sources.
 */
internal interface NotesDataSource {

    /**
     * Retrieves all notes.
     */
    suspend fun getAllNotes(): Result<List<Note>>

    /**
     * Retrieves a single note with its attachments by [id].
     */
    suspend fun getNoteById(id: Long): Result<NoteWithAttachments>

    /**
     * Saves the given [noteWithAttachments] and returns the note's ID.
     */
    suspend fun saveNote(noteWithAttachments: NoteWithAttachments): Result<Long>

    /**
     * Deletes the note identified by [id] along with its attachments.
     */
    suspend fun deleteNote(id: Long): Result<Unit>
    suspend fun moveToTrash(id: Long): Result<Unit>
    suspend fun archiveNote(id: Long): Result<Unit>
}
