package org.skynetsoftware.skeletonnotes

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.skynetsoftware.skeletonnotes.domain.model.Note
import org.skynetsoftware.skeletonnotes.domain.model.NoteWithAttachments
import org.skynetsoftware.skeletonnotes.domain.model.Result
import org.skynetsoftware.skeletonnotes.domain.repository.NotesRepository

class FakeNotesRepository(private val notes: List<Note>) : NotesRepository {
    override fun getAllNotes(): Flow<Result<List<Note>>> = flow {
        emit(Result.Success(notes))
    }

    override fun getNoteByIdFlow(id: Long): Flow<Result<NoteWithAttachments>> = flow {
        emit(getNoteById(id))
    }

    override fun getNoteById(id: Long): Result<NoteWithAttachments> =
        Result.Success(NoteWithAttachments(note = notes.first { it.id == id }, attachments = emptyList()))
    override suspend fun saveNote(noteWithAttachments: NoteWithAttachments): Result<Long> =
        Result.Success(noteWithAttachments.note.id)
    override suspend fun deleteNote(id: Long): Result<Unit> = Result.Success(Unit)
    override suspend fun moveToTrash(id: Long): Result<Unit> = Result.Success(Unit)
    override suspend fun archiveNote(id: Long): Result<Unit> = Result.Success(Unit)
}
