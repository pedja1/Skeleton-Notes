package org.skynetsoftware.skeletonnotes.data.repository

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.skynetsoftware.skeletonnotes.data.database.NotesDataSource
import org.skynetsoftware.skeletonnotes.domain.model.Note
import org.skynetsoftware.skeletonnotes.domain.model.NoteWithAttachments
import org.skynetsoftware.skeletonnotes.domain.model.Result
import org.skynetsoftware.skeletonnotes.domain.repository.NotesRepository

internal class NotesRepositoryImpl(private val notesDataSource: NotesDataSource) : NotesRepository {

    override suspend fun getAllNotes(): Result<List<Note>> = withContext(Dispatchers.IO) {
        notesDataSource.getAllNotes()
    }

    override suspend fun getNoteById(id: Long): Result<NoteWithAttachments> = withContext(Dispatchers.IO) {
        notesDataSource.getNoteById(id)
    }

    override suspend fun saveNote(noteWithAttachments: NoteWithAttachments): Result<Long> = withContext(Dispatchers.IO) {
        notesDataSource.saveNote(noteWithAttachments)
    }

    override suspend fun deleteNote(id: Long): Result<Unit> = withContext(Dispatchers.IO) {
        notesDataSource.deleteNote(id)
    }

    override suspend fun moveToTrash(id: Long): Result<Unit> = withContext(Dispatchers.IO) {
        notesDataSource.moveToTrash(id)
    }

    override suspend fun archiveNote(id: Long): Result<Unit> = withContext(Dispatchers.IO) {
        notesDataSource.archiveNote(id)
    }
}
