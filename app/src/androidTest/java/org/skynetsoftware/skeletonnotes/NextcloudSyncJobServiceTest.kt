package org.skynetsoftware.skeletonnotes

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.skynetsoftware.skeletonnotes.di.AppDi
import org.skynetsoftware.skeletonnotes.di.AppGraph
import org.skynetsoftware.skeletonnotes.di.ProductionAppGraph
import org.skynetsoftware.skeletonnotes.domain.attachment.AttachmentFileStorage
import org.skynetsoftware.skeletonnotes.domain.attachment.AttachmentWriteTarget
import org.skynetsoftware.skeletonnotes.domain.model.Note
import org.skynetsoftware.skeletonnotes.domain.model.NoteWithAttachments
import org.skynetsoftware.skeletonnotes.domain.model.Result
import org.skynetsoftware.skeletonnotes.domain.repository.NotesRepository
import org.skynetsoftware.skeletonnotes.domain.usecase.SyncNotesWithNextcloudUseCase
import org.skynetsoftware.skeletonnotes.domain.usecase.SyncResult
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Instrumented test that drives [org.skynetsoftware.skeletonnotes.sync.NextcloudSyncJobService]
 * end to end through the real Android `JobScheduler`, verifying that a scheduled one-off sync
 * actually invokes the sync use case and completes without crashing (i.e. `jobFinished` is
 * reached).
 */
@RunWith(AndroidJUnit4::class)
class NextcloudSyncJobServiceTest {
    private lateinit var useCase: RecordingSyncUseCase

    @Before
    fun setUp() {
        useCase = RecordingSyncUseCase()
        AppDi.install(SyncJobAppGraph(ProductionAppGraph(testApplication, inMemoryDatabase = true), useCase))
    }

    @After
    fun tearDown() {
        AppDi.install(ProductionAppGraph(testApplication, inMemoryDatabase = true))
    }

    @Test
    fun scheduledOneOffSyncInvokesSyncUseCase() {
        AppDi.nextcloudSyncScheduler.syncNow()

        assertTrue(
            "Sync use case was not invoked by the scheduled job",
            useCase.started.await(TIMEOUT_SECONDS, TimeUnit.SECONDS),
        )
        assertTrue(useCase.invocations.get() >= 1)
    }

    private companion object {
        const val TIMEOUT_SECONDS = 60L
    }

    /**
     * [AppGraph] that delegates everything to [delegate] except the sync use case, which is
     * replaced by a controllable [RecordingSyncUseCase]. The real scheduler from [delegate] is
     * kept so jobs are actually submitted to the platform `JobScheduler`.
     */
    private class SyncJobAppGraph(
        delegate: AppGraph,
        private val syncUseCase: SyncNotesWithNextcloudUseCase,
    ) : AppGraph by delegate {
        override val syncNotesWithNextcloudUseCase: SyncNotesWithNextcloudUseCase get() = syncUseCase
    }

    /**
     * Sync use case that records invocations and signals when it starts, without performing any
     * real work.
     */
    private class RecordingSyncUseCase :
        SyncNotesWithNextcloudUseCase(
            NoOpNotesRepository(),
            FakeSettingsNextcloudRepository(),
            FakeSettingsRepository(),
            NoOpAttachmentFileStorage(),
        ) {
        val started = CountDownLatch(1)
        val invocations = AtomicInteger(0)

        override suspend fun invoke(): Result<SyncResult> {
            invocations.incrementAndGet()
            started.countDown()
            return Result.Success(SyncResult.Success)
        }
    }

    /** No-op [NotesRepository] used only to satisfy the sync use case constructor. */
    private class NoOpNotesRepository : NotesRepository {
        override fun getAllNotesFlow(): Flow<Result<List<Note>>> = flowOf(Result.Success(emptyList()))

        override suspend fun getAllNotes(): Result<List<Note>> = Result.Success(emptyList())

        override fun getAllNotesWithAttachmentsFlow(): Flow<Result<List<NoteWithAttachments>>> =
            flowOf(Result.Success(emptyList()))

        override suspend fun getAllNotesWithAttachments(): Result<List<NoteWithAttachments>> =
            Result.Success(emptyList())

        override fun getNoteByIdFlow(id: String): Flow<Result<NoteWithAttachments>> =
            flowOf(Result.Failure(Exception("not found")))

        override suspend fun getNoteById(id: String): Result<NoteWithAttachments> =
            Result.Failure(Exception("not found"))

        override suspend fun saveNote(noteWithAttachments: NoteWithAttachments): Result<Unit> = Result.Success(Unit)

        override suspend fun saveNotes(notesWithAttachments: List<NoteWithAttachments>): Result<Unit> =
            Result.Success(Unit)

        override suspend fun deleteNote(id: String): Result<Unit> = Result.Success(Unit)

        override suspend fun moveToTrash(id: String): Result<Unit> = Result.Success(Unit)

        override suspend fun archiveNote(id: String): Result<Unit> = Result.Success(Unit)

        override suspend fun restoreNote(id: String): Result<Unit> = Result.Success(Unit)
    }

    /** No-op [org.skynetsoftware.skeletonnotes.domain.attachment.AttachmentFileStorage] used only to satisfy the sync use case constructor. */
    private class NoOpAttachmentFileStorage : AttachmentFileStorage {
        override fun copyToStorage(
            source: String,
            attachmentId: String,
            mimeType: String?,
        ): String = ""

        override fun writeStream(
            attachmentId: String,
            inputStream: InputStream,
        ): String = ""

        override fun openWriteStream(
            attachmentId: String,
            extension: String?,
        ): AttachmentWriteTarget = AttachmentWriteTarget("", ByteArrayOutputStream())

        override fun getFile(attachmentId: String): File = File("")

        override fun deleteFile(attachmentId: String) {}
    }
}
