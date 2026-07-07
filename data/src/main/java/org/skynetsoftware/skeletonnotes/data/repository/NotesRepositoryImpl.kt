package org.skynetsoftware.skeletonnotes.data.repository

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import org.skynetsoftware.skeletonnotes.data.database.NotesDataSource
import org.skynetsoftware.skeletonnotes.domain.model.Note
import org.skynetsoftware.skeletonnotes.domain.model.NoteWithAttachments
import org.skynetsoftware.skeletonnotes.domain.model.Result
import org.skynetsoftware.skeletonnotes.domain.repository.NotesRepository

/**
 * Implementation of [NotesRepository] that delegates to a [NotesDataSource]
 * and switches to [Dispatchers.IO] for all operations.
 */
internal class NotesRepositoryImpl(private val notesDataSource: NotesDataSource) : NotesRepository {

    /**
     * Retrieves all notes on [Dispatchers.IO].
     */
    override fun getAllNotes(): Flow<Result<List<Note>>> = notesDataSource.getAllNotes()

    /**
     * Retrieves a note by [id] on [Dispatchers.IO].
     */
    override fun getNoteByIdFlow(id: Long): Flow<Result<NoteWithAttachments>> = notesDataSource.getNoteByIdFlow(id)

    /**
     * Retrieves a note by [id] on [Dispatchers.IO].
     */
    override fun getNoteById(id: Long): Result<NoteWithAttachments> = notesDataSource.getNoteById(id)

    /**
     * Saves a [noteWithAttachments] on [Dispatchers.IO].
     */
    override suspend fun saveNote(noteWithAttachments: NoteWithAttachments): Result<Long> = withContext(Dispatchers.IO) {
        notesDataSource.saveNote(noteWithAttachments)
    }

    /**
     * Deletes a note by [id] on [Dispatchers.IO].
     */
    override suspend fun deleteNote(id: Long): Result<Unit> = withContext(Dispatchers.IO) {
        notesDataSource.deleteNote(id)
    }

    /**
     * @see [NotesRepository.moveToTrash]
     */
    override suspend fun moveToTrash(id: Long): Result<Unit> = withContext(Dispatchers.IO) {
        notesDataSource.moveToTrash(id)
    }

    /**
     * @see [NotesRepository.archiveNote]
     */
    override suspend fun archiveNote(id: Long): Result<Unit> = withContext(Dispatchers.IO) {
        notesDataSource.archiveNote(id)
    }
}
