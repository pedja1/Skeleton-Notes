package org.skynetsoftware.skeletonnotes.domain.repository

import org.skynetsoftware.skeletonnotes.domain.model.Note
import org.skynetsoftware.skeletonnotes.domain.model.NoteWithAttachments
import org.skynetsoftware.skeletonnotes.domain.model.Result

/**
 * Repository interface for note persistence operations including CRUD.
 */
interface NotesRepository {
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

    /**
     * Moves the note with the given [id] to trash by setting its status to [NoteStatus.TRASH].
     *
     * @param id the ID of the note to trash
     * @return [Result.Success] on success, or [Result.Failure] on error
     */
    suspend fun moveToTrash(id: Long): Result<Unit>

    /**
     * Archives the note with the given [id] by setting its status to [NoteStatus.ARCHIVE].
     *
     * @param id the ID of the note to archive
     * @return [Result.Success] on success, or [Result.Failure] on error
     */
    suspend fun archiveNote(id: Long): Result<Unit>
}
