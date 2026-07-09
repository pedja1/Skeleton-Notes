package org.skynetsoftware.skeletonnotes.domain.repository

import kotlinx.coroutines.flow.Flow
import org.skynetsoftware.skeletonnotes.domain.model.Note
import org.skynetsoftware.skeletonnotes.domain.model.NoteWithAttachments
import org.skynetsoftware.skeletonnotes.domain.model.Result

/**
 * Repository interface for note persistence operations including CRUD.
 */
interface NotesRepository {
    /**
     * Retrieves all notes as Flow.
     */
    fun getAllNotesFlow(): Flow<Result<List<Note>>>

    /**
     * Retrieves all notes.
     */
    fun getAllNotes(): Result<List<Note>>

    /**
     * Retrieves a single note with its attachments by [id].
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
     * Moves the note with the given [id] to trash by setting its status to [org.skynetsoftware.skeletonnotes.domain.model.NoteStatus.TRASH].
     *
     * @param id the ID of the note to trash
     * @return [Result.Success] on success, or [Result.Failure] on error
     */
    suspend fun moveToTrash(id: String): Result<Unit>

    /**
     * Archives the note with the given [id] by setting its status to [org.skynetsoftware.skeletonnotes.domain.model.NoteStatus.ARCHIVE].
     *
     * @param id the ID of the note to archive
     * @return [Result.Success] on success, or [Result.Failure] on error
     */
    suspend fun archiveNote(id: String): Result<Unit>
}
