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
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.io.OutputStream

class ImportNotesUseCaseTest {
    @Test
    fun `delegates stream and conflict handler to repository`() =
        runTest {
            val expected = Result.Success(ImportSummary(imported = 2, overwritten = 1, skipped = 0))
            val stream = ByteArrayInputStream(ByteArray(0))
            val handler: suspend (Note, Note) -> ConflictResolution = { _, _ -> ConflictResolution.KEEP_BOTH }
            var receivedStream: InputStream? = null
            var receivedHandler: Any? = null
            val repository =
                object : StubBackupRepository() {
                    override suspend fun importNotes(
                        inputStream: InputStream,
                        onConflict: suspend (existing: Note, incoming: Note) -> ConflictResolution,
                        onProgress: (current: Int, total: Int) -> Unit,
                    ): Result<ImportSummary> {
                        receivedStream = inputStream
                        receivedHandler = onConflict
                        return expected
                    }
                }

            val result = ImportNotesUseCase(repository).invoke(stream, handler)

            assertSame(expected, result)
            assertSame(stream, receivedStream)
            assertSame(handler, receivedHandler)
        }

    @Test
    fun `propagates failure`() =
        runTest {
            val failure = Result.Failure<ImportSummary>(IllegalStateException("bad archive"))
            val repository =
                object : StubBackupRepository() {
                    override suspend fun importNotes(
                        inputStream: InputStream,
                        onConflict: suspend (existing: Note, incoming: Note) -> ConflictResolution,
                        onProgress: (current: Int, total: Int) -> Unit,
                    ): Result<ImportSummary> = failure
                }

            val result =
                ImportNotesUseCase(repository).invoke(
                    ByteArrayInputStream(ByteArray(0)),
                    onConflict = { _, _ -> ConflictResolution.KEEP_EXISTING },
                )

            assertEquals(failure, result)
        }

    private open class StubBackupRepository : BackupRepository {
        override suspend fun exportNotes(
            outputStream: OutputStream,
            onProgress: (current: Int, total: Int) -> Unit,
        ): Result<Int> = Result.Success(0)

        override suspend fun importNotes(
            inputStream: InputStream,
            onConflict: suspend (existing: Note, incoming: Note) -> ConflictResolution,
            onProgress: (current: Int, total: Int) -> Unit,
        ): Result<ImportSummary> = Result.Success(ImportSummary(0, 0, 0))
    }
}
