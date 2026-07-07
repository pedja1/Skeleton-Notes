package org.skynetsoftware.skeletonnotes.domain.usecase

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.skynetsoftware.skeletonnotes.domain.model.Attachment
import org.skynetsoftware.skeletonnotes.domain.model.Note
import org.skynetsoftware.skeletonnotes.domain.model.NoteWithAttachments
import org.skynetsoftware.skeletonnotes.domain.model.Result
import org.skynetsoftware.skeletonnotes.domain.repository.NotesRepository

class SaveNoteUseCaseTest {
    @Test
    fun savesNoteAndReturnsId() =
        runTest {
            val repository = FakeSaveRepository()
            val useCase = SaveNoteUseCase(repository)
            val note =
                Note(
                    id = 0,
                    title = "Test",
                    content = "Content",
                    createdAt = 1000L,
                    modifiedAt = 1000L,
                    tags = emptySet(),
                )
            val noteWithAttachments = NoteWithAttachments(note, emptyList())

            val result = useCase(noteWithAttachments)
            assertTrue(result is Result.Success)
            assertEquals(42L, (result as Result.Success).data)
            assertTrue(repository.saveCalled)
        }

    @Test
    fun savesNoteWithAttachments() =
        runTest {
            val repository = FakeSaveRepository()
            val useCase = SaveNoteUseCase(repository)
            val note =
                Note(
                    id = 0,
                    title = "With File",
                    content = "Content",
                    createdAt = 1000L,
                    modifiedAt = 1000L,
                    tags = emptySet(),
                )
            val attachments =
                listOf(
                    Attachment(id = 0, noteId = 0, uri = "file://test.txt"),
                )
            val noteWithAttachments = NoteWithAttachments(note, attachments)

            val result = useCase(noteWithAttachments)
            assertTrue(result is Result.Success)
            val saved = repository.savedNote
            assertEquals(1, saved?.attachments?.size)
            assertEquals("file://test.txt", saved?.attachments?.first()?.uri)
        }

    @Test
    fun returnsErrorWhenRepositoryFails() =
        runTest {
            val repository = FakeSaveRepository(shouldFail = true)
            val useCase = SaveNoteUseCase(repository)
            val note =
                Note(
                    id = 0,
                    title = "Test",
                    content = "Content",
                    createdAt = 1000L,
                    modifiedAt = 1000L,
                    tags = emptySet(),
                )

            val result = useCase(NoteWithAttachments(note, emptyList()))
            assertTrue(result is Result.Failure)
        }

    private class FakeSaveRepository(
        private val shouldFail: Boolean = false,
    ) : NotesRepository {
        var saveCalled = false
        var savedNote: NoteWithAttachments? = null

        override suspend fun getAllNotes(): Result<List<Note>> = Result.Success(emptyList())

        override suspend fun getNoteById(id: Long): Result<NoteWithAttachments> =
            Result.Success(
                NoteWithAttachments(
                    Note(id, "Test", "Content", 1000L, 1000L, emptySet()),
                    emptyList(),
                ),
            )

        override suspend fun saveNote(noteWithAttachments: NoteWithAttachments): Result<Long> {
            if (shouldFail) return Result.Failure(RuntimeException("DB error"))
            saveCalled = true
            savedNote = noteWithAttachments
            return Result.Success(42L)
        }

        override suspend fun deleteNote(id: Long): Result<Unit> = Result.Success(Unit)

        override suspend fun moveToTrash(id: Long): Result<Unit> = Result.Success(Unit)

        override suspend fun archiveNote(id: Long): Result<Unit> = Result.Success(Unit)
    }
}
