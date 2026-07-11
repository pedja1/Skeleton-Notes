package org.skynetsoftware.skeletonnotes.domain.usecase

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import org.skynetsoftware.skeletonnotes.domain.model.Note
import org.skynetsoftware.skeletonnotes.domain.model.Result
import org.skynetsoftware.skeletonnotes.domain.repository.BackupRepository
import org.skynetsoftware.skeletonnotes.domain.repository.ConflictResolution
import org.skynetsoftware.skeletonnotes.domain.repository.ImportSummary
import java.io.InputStream
import java.io.OutputStream

class ExportNotesUseCaseTest {
    @Test
    fun `delegates to repository and returns its result`() =
        runTest {
            val expected = Result.Success(3)
            val stream = OutputStream.nullOutputStream()
            var received: OutputStream? = null
            val repository =
                object : StubBackupRepository() {
                    override suspend fun exportNotes(outputStream: OutputStream): Result<Int> {
                        received = outputStream
                        return expected
                    }
                }

            val result = ExportNotesUseCase(repository).invoke(stream)

            assertSame(expected, result)
            assertSame(stream, received)
        }

    @Test
    fun `propagates failure`() =
        runTest {
            val failure = Result.Failure<Int>(IllegalStateException("nope"))
            val repository =
                object : StubBackupRepository() {
                    override suspend fun exportNotes(outputStream: OutputStream): Result<Int> = failure
                }

            assertEquals(failure, ExportNotesUseCase(repository).invoke(OutputStream.nullOutputStream()))
        }

    private open class StubBackupRepository : BackupRepository {
        override suspend fun exportNotes(outputStream: OutputStream): Result<Int> = Result.Success(0)

        override suspend fun importNotes(
            inputStream: InputStream,
            onConflict: suspend (existing: Note, incoming: Note) -> ConflictResolution,
        ): Result<ImportSummary> = Result.Success(ImportSummary(0, 0, 0))
    }
}
