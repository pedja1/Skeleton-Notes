package org.skynetsoftware.skeletonnotes.domain.usecase

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test
import org.skynetsoftware.skeletonnotes.domain.model.Note
import org.skynetsoftware.skeletonnotes.domain.model.NoteWithAttachments
import org.skynetsoftware.skeletonnotes.domain.model.Result
import org.skynetsoftware.skeletonnotes.domain.repository.NotesRepository

class MoveToTrashUseCaseTest {
    @Test
    fun movesToTrashSuccessfully() =
        runTest {
            val repository = FakeMoveToTrashRepository()
            val useCase = MoveToTrashUseCase(repository)

            val result = useCase(5)
            assertTrue(result is Result.Success)
        }

    @Test
    fun returnsErrorWhenRepositoryFails() =
        runTest {
            val repository = FakeMoveToTrashRepository(shouldFail = true)
            val useCase = MoveToTrashUseCase(repository)

            val result = useCase(5)
            assertTrue(result is Result.Failure)
        }

    @Test
    fun movesToTrashAndCanBeCalledMultipleTimes() =
        runTest {
            val repository = FakeMoveToTrashRepository()
            val useCase = MoveToTrashUseCase(repository)

            val result1 = useCase(1)
            val result2 = useCase(2)
            assertTrue(result1 is Result.Success)
            assertTrue(result2 is Result.Success)
        }

    private class FakeMoveToTrashRepository(
        private val shouldFail: Boolean = false,
    ) : NotesRepository {
        override fun getAllNotes(): Flow<Result<List<Note>>> =
            flow {
                emit(Result.Success(emptyList()))
            }

        override fun getNoteByIdFlow(id: Long): Flow<Result<NoteWithAttachments>> =
            flow {
                emit(getNoteById(id))
            }

        override fun getNoteById(id: Long): Result<NoteWithAttachments> =
            Result.Success(
                NoteWithAttachments(
                    Note(id, "Test", "Content", 1000L, 1000L, emptySet()),
                    emptyList(),
                ),
            )

        override suspend fun saveNote(noteWithAttachments: NoteWithAttachments): Result<Long> = Result.Success(1L)

        override suspend fun deleteNote(id: Long): Result<Unit> = Result.Success(Unit)

        override suspend fun moveToTrash(id: Long): Result<Unit> {
            if (shouldFail) return Result.Failure(RuntimeException("Trash error"))
            return Result.Success(Unit)
        }

        override suspend fun archiveNote(id: Long): Result<Unit> = Result.Success(Unit)
    }
}
