package org.skynetsoftware.skeletonnotes.domain.usecase

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test
import org.skynetsoftware.skeletonnotes.domain.BaseFakeNotesRepository
import org.skynetsoftware.skeletonnotes.domain.model.Result

class ArchiveNoteUseCaseTest {
    @Test
    fun archivesSuccessfully() =
        runTest {
            val repository = FakeArchiveRepository()
            val useCase = ArchiveNoteUseCase(repository)

            val result = useCase("5")
            assertTrue(result is Result.Success)
        }

    @Test
    fun returnsErrorWhenRepositoryFails() =
        runTest {
            val repository = FakeArchiveRepository(shouldFail = true)
            val useCase = ArchiveNoteUseCase(repository)

            val result = useCase("5")
            assertTrue(result is Result.Failure)
        }

    @Test
    fun archivesAndCanBeCalledMultipleTimes() =
        runTest {
            val repository = FakeArchiveRepository()
            val useCase = ArchiveNoteUseCase(repository)

            val result1 = useCase("1")
            val result2 = useCase("2")
            assertTrue(result1 is Result.Success)
            assertTrue(result2 is Result.Success)
        }

    private class FakeArchiveRepository(
        private val shouldFail: Boolean = false,
    ) : BaseFakeNotesRepository() {
        override suspend fun archiveNote(id: String): Result<Unit> {
            if (shouldFail) return Result.Failure(RuntimeException("Archive error"))
            return Result.Success(Unit)
        }
    }
}
