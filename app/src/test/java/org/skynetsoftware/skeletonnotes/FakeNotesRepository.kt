package org.skynetsoftware.skeletonnotes

import org.skynetsoftware.skeletonnotes.domain.model.Note
import org.skynetsoftware.skeletonnotes.domain.model.NoteWithAttachments
import org.skynetsoftware.skeletonnotes.domain.model.Result
import org.skynetsoftware.skeletonnotes.domain.repository.NotesRepository

class FakeNotesRepository(private val notes: List<Note>) : NotesRepository {
    override suspend fun getAllNotes(): Result<List<Note>> = Result.Success(notes)
    override suspend fun getNoteById(id: Long): Result<NoteWithAttachments> =
        Result.Success(NoteWithAttachments(note = notes.first { it.id == id }, attachments = emptyList()))
    override suspend fun saveNote(noteWithAttachments: NoteWithAttachments): Result<Long> =
        Result.Success(noteWithAttachments.note.id)
    override suspend fun deleteNote(id: Long): Result<Unit> = Result.Success(Unit)
    override suspend fun moveToTrash(id: Long): Result<Unit> = Result.Success(Unit)
    override suspend fun archiveNote(id: Long): Result<Unit> = Result.Success(Unit)
}
