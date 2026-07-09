package org.skynetsoftware.skeletonnotes.domain.usecase

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test
import org.skynetsoftware.skeletonnotes.domain.BaseFakeNotesRepository
import org.skynetsoftware.skeletonnotes.domain.model.Result

class MoveToTrashUseCaseTest {
    @Test
    fun movesToTrashSuccessfully() =
        runTest {
            val repository = FakeMoveToTrashRepository()
            val useCase = MoveToTrashUseCase(repository)

            val result = useCase("5")
            assertTrue(result is Result.Success)
        }

    @Test
    fun returnsErrorWhenRepositoryFails() =
        runTest {
            val repository = FakeMoveToTrashRepository(shouldFail = true)
            val useCase = MoveToTrashUseCase(repository)

            val result = useCase("5")
            assertTrue(result is Result.Failure)
        }

    @Test
    fun movesToTrashAndCanBeCalledMultipleTimes() =
        runTest {
            val repository = FakeMoveToTrashRepository()
            val useCase = MoveToTrashUseCase(repository)

            val result1 = useCase("1")
            val result2 = useCase("2")
            assertTrue(result1 is Result.Success)
            assertTrue(result2 is Result.Success)
        }

    private class FakeMoveToTrashRepository(
        private val shouldFail: Boolean = false,
    ) : BaseFakeNotesRepository() {
        override suspend fun moveToTrash(id: String): Result<Unit> {
            if (shouldFail) return Result.Failure(RuntimeException("Trash error"))
            return Result.Success(Unit)
        }

        override suspend fun archiveNote(id: String): Result<Unit> = Result.Success(Unit)
    }
}
