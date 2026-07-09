package org.skynetsoftware.skeletonnotes.domain.usecase

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test
import org.skynetsoftware.skeletonnotes.domain.BaseFakeNotesRepository
import org.skynetsoftware.skeletonnotes.domain.model.Result

class DeleteNoteUseCaseTest {
    @Test
    fun deletesNoteSuccessfully() =
        runTest {
            val repository = FakeDeleteRepository()
            val useCase = DeleteNoteUseCase(repository)

            val result = useCase("5")
            assertTrue(result is Result.Success)
        }

    @Test
    fun returnsErrorWhenRepositoryFails() =
        runTest {
            val repository = FakeDeleteRepository(shouldFail = true)
            val useCase = DeleteNoteUseCase(repository)

            val result = useCase("5")
            assertTrue(result is Result.Failure)
        }

    @Test
    fun deletesAndCanBeCalledMultipleTimes() =
        runTest {
            val repository = FakeDeleteRepository()
            val useCase = DeleteNoteUseCase(repository)

            val result1 = useCase("1")
            val result2 = useCase("2")
            assertTrue(result1 is Result.Success)
            assertTrue(result2 is Result.Success)
        }

    private class FakeDeleteRepository(
        private val shouldFail: Boolean = false,
    ) : BaseFakeNotesRepository() {
        override suspend fun deleteNote(id: String): Result<Unit> {
            if (shouldFail) return Result.Failure(RuntimeException("Delete error"))
            return Result.Success(Unit)
        }
    }
}
