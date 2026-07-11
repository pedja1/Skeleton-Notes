package org.skynetsoftware.skeletonnotes.domain

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.skynetsoftware.skeletonnotes.domain.model.Note
import org.skynetsoftware.skeletonnotes.domain.model.NoteWithAttachments
import org.skynetsoftware.skeletonnotes.domain.model.Result
import org.skynetsoftware.skeletonnotes.domain.repository.NotesRepository

/**
 * Base fake implementation of [NotesRepository] providing default no-op
 * responses. Test-specific fakes can override only the methods they care about.
 */
open class BaseFakeNotesRepository : NotesRepository {
    override fun getAllNotes(): Result<List<Note>> = Result.Success(emptyList())

    override fun getAllNotesFlow(): Flow<Result<List<Note>>> = flowOf(Result.Success(emptyList()))

    override fun getAllNotesWithAttachmentsFlow(): Flow<Result<List<NoteWithAttachments>>> =
        flowOf(Result.Success(emptyList()))

    override fun getAllNotesWithAttachments(): Result<List<NoteWithAttachments>> = Result.Success(emptyList())

    override fun getNoteByIdFlow(id: String): Flow<Result<NoteWithAttachments>> =
        flowOf(Result.Success(NoteWithAttachments(Note(id, null, "", 0, 0, emptySet()), emptyList())))

    override fun getNoteById(id: String): Result<NoteWithAttachments> =
        Result.Success(NoteWithAttachments(Note(id, null, "", 0, 0, emptySet()), emptyList()))

    override suspend fun saveNote(noteWithAttachments: NoteWithAttachments): Result<Unit> = Result.Success(Unit)

    override suspend fun deleteNote(id: String): Result<Unit> = Result.Success(Unit)

    override suspend fun moveToTrash(id: String): Result<Unit> = Result.Success(Unit)

    override suspend fun archiveNote(id: String): Result<Unit> = Result.Success(Unit)
}
