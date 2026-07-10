package org.skynetsoftware.skeletonnotes

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.skynetsoftware.skeletonnotes.domain.model.Note
import org.skynetsoftware.skeletonnotes.domain.model.NoteWithAttachments
import org.skynetsoftware.skeletonnotes.domain.model.Result

class FakeNotesRepository(private val notes: List<Note>) : BaseFakeNotesRepository() {
    override fun getAllNotesFlow(): Flow<Result<List<Note>>> = flow {
        emit(Result.Success(notes))
    }

    override fun getAllNotesWithAttachmentsFlow(): Flow<Result<List<NoteWithAttachments>>> = flow {
        emit(Result.Success(notes.map { NoteWithAttachments(it, emptyList()) }))
    }

    override fun getNoteById(id: String): Result<NoteWithAttachments> =
        Result.Success(NoteWithAttachments(note = notes.first { it.id == id }, attachments = emptyList()))
}
