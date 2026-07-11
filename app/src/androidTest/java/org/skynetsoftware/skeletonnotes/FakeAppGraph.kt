package org.skynetsoftware.skeletonnotes

import android.app.Application
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.skynetsoftware.skeletonnotes.di.AppGraph
import org.skynetsoftware.skeletonnotes.domain.model.Note
import org.skynetsoftware.skeletonnotes.domain.model.NoteWithAttachments
import org.skynetsoftware.skeletonnotes.domain.model.Result
import org.skynetsoftware.skeletonnotes.domain.repository.NotesRepository
import org.skynetsoftware.skeletonnotes.domain.usecase.GetAllNotesUseCase

/**
 * Test [AppGraph] that delegates every dependency to a real [delegate] graph, except
 * [getAllNotesUseCase] which is backed by a repository that always fails. Used to drive the
 * main screen into its error state without corrupting the (in-memory) database.
 */
class FakeAppGraph(private val delegate: AppGraph) : AppGraph by delegate {
    override val getAllNotesUseCase: GetAllNotesUseCase =
        GetAllNotesUseCase(FailingNotesRepository())
}

/**
 * [NotesRepository] whose read flow always emits a failure, simulating an unreadable database.
 */
private class FailingNotesRepository : NotesRepository {
    override fun getAllNotesFlow(): Flow<Result<List<Note>>> =
        flowOf(Result.Failure(IllegalStateException("database corrupted")))

    override fun getAllNotes(): Result<List<Note>> =
        Result.Failure(IllegalStateException("database corrupted"))

    override fun getAllNotesWithAttachmentsFlow(): Flow<Result<List<NoteWithAttachments>>> =
        flowOf(Result.Failure(IllegalStateException("database corrupted")))

    override fun getAllNotesWithAttachments(): Result<List<NoteWithAttachments>> =
        Result.Failure(IllegalStateException("database corrupted"))

    override fun getNoteByIdFlow(id: String): Flow<Result<NoteWithAttachments>> =
        flowOf(Result.Failure(IllegalStateException("database corrupted")))

    override fun getNoteById(id: String): Result<NoteWithAttachments> =
        Result.Failure(IllegalStateException("database corrupted"))

    override suspend fun saveNote(noteWithAttachments: NoteWithAttachments): Result<Unit> =
        Result.Failure(IllegalStateException("database corrupted"))

    override suspend fun deleteNote(id: String): Result<Unit> =
        Result.Failure(IllegalStateException("database corrupted"))

    override suspend fun moveToTrash(id: String): Result<Unit> =
        Result.Failure(IllegalStateException("database corrupted"))

    override suspend fun archiveNote(id: String): Result<Unit> =
        Result.Failure(IllegalStateException("database corrupted"))

    override suspend fun restoreNote(id: String): Result<Unit> =
        Result.Failure(IllegalStateException("database corrupted"))
}

/**
 * The [Application] under test, useful for building a [org.skynetsoftware.skeletonnotes.di.ProductionAppGraph].
 */
val testApplication: Application
    get() = androidx.test.core.app.ApplicationProvider.getApplicationContext()
