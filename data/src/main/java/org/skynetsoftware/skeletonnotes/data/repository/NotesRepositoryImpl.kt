package org.skynetsoftware.skeletonnotes.data.repository

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import org.skynetsoftware.skeletonnotes.data.database.NotesDataSource
import org.skynetsoftware.skeletonnotes.domain.model.Note
import org.skynetsoftware.skeletonnotes.domain.model.NoteWithAttachments
import org.skynetsoftware.skeletonnotes.domain.model.Result
import org.skynetsoftware.skeletonnotes.domain.repository.NotesRepository

/**
 * Implementation of [NotesRepository] that delegates to a [NotesDataSource]
 * and switches to [Dispatchers.IO] for all operations.
 */
internal class NotesRepositoryImpl(
    private val notesDataSource: NotesDataSource,
) : NotesRepository {
    /**
     * @see NotesRepository.getNoteByIdFlow
     * @see NotesDataSource.getAllNotesFlow
     */
    override fun getAllNotesFlow(): Flow<Result<List<Note>>> = notesDataSource.getAllNotesFlow()

    /**
     * @see NotesRepository.getAllNotes
     * @see NotesDataSource.getAllNotes
     */
    override suspend fun getAllNotes(): Result<List<Note>> = notesDataSource.getAllNotes()

    /**
     * @see NotesRepository.getAllNotesWithAttachmentsFlow
     * @see NotesDataSource.getAllNotesWithAttachmentsFlow
     */
    override fun getAllNotesWithAttachmentsFlow(): Flow<Result<List<NoteWithAttachments>>> =
        notesDataSource.getAllNotesWithAttachmentsFlow()

    /**
     * @see NotesRepository.getAllNotesWithAttachments
     * @see NotesDataSource.getAllNotesWithAttachments
     */
    override suspend fun getAllNotesWithAttachments(): Result<List<NoteWithAttachments>> =
        notesDataSource.getAllNotesWithAttachments()

    /**
     * @see NotesRepository.getNoteByIdFlow
     * @see NotesDataSource.getNoteByIdFlow
     */
    override fun getNoteByIdFlow(id: String): Flow<Result<NoteWithAttachments>> = notesDataSource.getNoteByIdFlow(id)

    /**
     * @see NotesRepository.getNoteById
     * @see NotesDataSource.getNoteById
     */
    override suspend fun getNoteById(id: String): Result<NoteWithAttachments> = notesDataSource.getNoteById(id)

    /**
     * @see NotesRepository.saveNote
     * @see NotesDataSource.saveNote
     */
    override suspend fun saveNote(noteWithAttachments: NoteWithAttachments): Result<Unit> =
        notesDataSource.saveNote(noteWithAttachments)

    /**
     * @see NotesRepository.saveNotes
     * @see NotesDataSource.saveNotes
     */
    override suspend fun saveNotes(notesWithAttachments: List<NoteWithAttachments>): Result<Unit> =
        notesDataSource.saveNotes(notesWithAttachments)

    /**
     * @see NotesRepository.deleteNote
     * @see NotesDataSource.deleteNote
     */
    override suspend fun deleteNote(id: String): Result<Unit> = notesDataSource.deleteNote(id)

    /**
     * @see NotesRepository.moveToTrash
     * @see NotesDataSource.moveToTrash
     */
    override suspend fun moveToTrash(id: String): Result<Unit> = notesDataSource.moveToTrash(id)

    /**
     * @see NotesRepository.archiveNote
     * @see NotesDataSource.archiveNote
     */
    override suspend fun archiveNote(id: String): Result<Unit> = notesDataSource.archiveNote(id)

    /**
     * @see NotesRepository.restoreNote
     * @see NotesDataSource.restoreNote
     */
    override suspend fun restoreNote(id: String): Result<Unit> = notesDataSource.restoreNote(id)
}
