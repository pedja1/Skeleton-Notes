package org.skynetsoftware.skeletonnotes.data.database

import kotlinx.coroutines.flow.Flow
import org.skynetsoftware.skeletonnotes.domain.model.Note
import org.skynetsoftware.skeletonnotes.domain.model.NoteWithAttachments
import org.skynetsoftware.skeletonnotes.domain.model.Result

/**
 * Abstraction over the underlying SQLite database for notes.
 * Allows [org.skynetsoftware.skeletonnotes.data.repository.NotesRepositoryImpl] to be tested with mocked data sources.
 */
internal interface NotesDataSource {

    /**
     * Retrieves all notes as Flow.
     */
    fun getAllNotesFlow(): Flow<Result<List<Note>>>

    /**
     * Retrieves all notes.
     */
    fun getAllNotes(): Result<List<Note>>

    /**
     * Retrieves a single note with its attachments by [id] as Flow
     */
    fun getNoteByIdFlow(id: String): Flow<Result<NoteWithAttachments>>

    /**
     * Retrieves a single note with its attachments by [id].
     */
    fun getNoteById(id: String): Result<NoteWithAttachments>

    /**
     * Saves the given [noteWithAttachments] and returns the note's ID.
     */
    suspend fun saveNote(noteWithAttachments: NoteWithAttachments): Result<Unit>

    /**
     * Deletes the note identified by [id] along with its attachments.
     */
    suspend fun deleteNote(id: String): Result<Unit>

    /**
     * Move note to trash. Set status to [NoteStatus.TRASH]
     */
    suspend fun moveToTrash(id: String): Result<Unit>

    /**
     * Archive note. Set status to [NoteStatus.ARCHIVED]
     */
    suspend fun archiveNote(id: String): Result<Unit>
}
