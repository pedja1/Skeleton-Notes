package org.skynetsoftware.skeletonnotes.domain.usecase

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.skynetsoftware.skeletonnotes.domain.attachment.AttachmentFileStorage
import org.skynetsoftware.skeletonnotes.domain.attachment.AttachmentWriteTarget
import org.skynetsoftware.skeletonnotes.domain.model.Note
import org.skynetsoftware.skeletonnotes.domain.model.NoteStatus
import org.skynetsoftware.skeletonnotes.domain.model.NoteWithAttachments
import org.skynetsoftware.skeletonnotes.domain.model.Result
import org.skynetsoftware.skeletonnotes.domain.model.nextcloud.NextcloudAttachment
import org.skynetsoftware.skeletonnotes.domain.model.nextcloud.NextcloudConnectionInfo
import org.skynetsoftware.skeletonnotes.domain.model.nextcloud.NextcloudFileInfo
import org.skynetsoftware.skeletonnotes.domain.model.nextcloud.NextcloudHttpException
import org.skynetsoftware.skeletonnotes.domain.model.nextcloud.NextcloudInitiateLoginResult
import org.skynetsoftware.skeletonnotes.domain.model.nextcloud.NextcloudNote
import org.skynetsoftware.skeletonnotes.domain.model.nextcloud.NextcloudPollStatus
import org.skynetsoftware.skeletonnotes.domain.repository.NextcloudRepository
import org.skynetsoftware.skeletonnotes.domain.repository.NotesRepository
import org.skynetsoftware.skeletonnotes.domain.repository.SettingsRepository
import java.io.File
import java.io.InputStream
import kotlin.coroutines.cancellation.CancellationException

class SyncNotesWithNextcloudUseCaseTest {
    @Test
    fun syncReturnsSuccessWhenNoRemoteChanges() =
        runTest {
            val notesRepo =
                FakeNotesRepo(
                    notes =
                        listOf(
                            Note(
                                id = "note1",
                                title = "Local",
                                content = "content",
                                createdAt = 1000L,
                                modifiedAt = 1000L,
                                tags = emptySet(),
                                remoteLastModified = 1000L,
                            ),
                        ),
                )
            val ncRepo =
                FakeNextcloudRepo(
                    remoteFiles =
                        listOf(
                            NextcloudFileInfo("note1.json", 1000L),
                        ),
                )
            val useCase = createUseCase(notesRepo, ncRepo)

            val result = useCase()

            assertTrue(result is Result.Success)
            assertTrue((result as Result.Success).data is SyncResult.Success)
        }

    @Test
    fun syncPullsNewRemoteNote() =
        runTest {
            val notesRepo = FakeNotesRepo(notes = emptyList())
            val ncRepo =
                FakeNextcloudRepo(
                    remoteFiles =
                        listOf(
                            NextcloudFileInfo("note1.json", 2000L),
                        ),
                    downloadedNote =
                        NextcloudNote(
                            id = "note1",
                            title = "Remote",
                            content = "remote content",
                            createdAt = 1000L,
                            modifiedAt = 2000L,
                            tags = emptySet(),
                            status = "ACTIVE",
                            attachments = emptyList(),
                        ),
                )
            val useCase = createUseCase(notesRepo, ncRepo)

            val result = useCase()

            assertTrue(result is Result.Success)
            assertTrue(notesRepo.savedNoteWithAttachments.isNotEmpty())
            assertEquals(
                "note1",
                notesRepo.savedNoteWithAttachments
                    .first()
                    .note.id,
            )
        }

    @Test
    fun syncDetectsConflict() =
        runTest {
            val notesRepo =
                FakeNotesRepo(
                    notes =
                        listOf(
                            Note(
                                id = "note1",
                                title = "Local",
                                content = "local content",
                                createdAt = 1000L,
                                modifiedAt = 3000L,
                                tags = emptySet(),
                                remoteLastModified = 2000L,
                            ),
                        ),
                )
            val ncRepo =
                FakeNextcloudRepo(
                    remoteFiles =
                        listOf(
                            NextcloudFileInfo("note1.json", 2500L),
                        ),
                )
            val useCase = createUseCase(notesRepo, ncRepo)

            val result = useCase()

            assertTrue(result is Result.Success)
            assertTrue((result as Result.Success).data is SyncResult.HasConflicts)
            val conflicts = (result.data as SyncResult.HasConflicts).conflicts
            assertEquals(1, conflicts.size)
            assertEquals("note1", conflicts[0].localNote.id)
        }

    @Test
    fun syncPushesNewLocalNote() =
        runTest {
            val notesRepo =
                FakeNotesRepo(
                    notes =
                        listOf(
                            Note(
                                id = "note1",
                                title = "Local",
                                content = "content",
                                createdAt = 1000L,
                                modifiedAt = 3000L,
                                tags = emptySet(),
                                remoteLastModified = 0L,
                            ),
                        ),
                )
            val ncRepo =
                FakeNextcloudRepo(
                    remoteFiles = emptyList(),
                    afterUploadFiles =
                        listOf(
                            NextcloudFileInfo("note1.json", 3000L),
                        ),
                )
            val useCase = createUseCase(notesRepo, ncRepo)

            val result = useCase()

            assertTrue(result is Result.Success)
            assertTrue(ncRepo.uploadedNotes.isNotEmpty())
            assertEquals("note1", ncRepo.uploadedNotes.first().id)
            // A brand-new note (remoteLastModified == 0) must not be treated as remotely
            // deleted and trashed on the same sync that first pushes it.
            assertTrue(notesRepo.trashedNoteIds.isEmpty())
            // Its remoteLastModified is refreshed to the server's post-upload mtime.
            assertEquals(
                3000L,
                notesRepo.savedNoteWithAttachments
                    .last()
                    .note.remoteLastModified,
            )
        }

    @Test
    fun pushedNoteRecordsServerMtimeAndDoesNotReconflict() =
        runTest {
            val notesRepo =
                FakeNotesRepo(
                    notes =
                        listOf(
                            Note(
                                id = "note1",
                                title = "Local",
                                content = "edited content",
                                createdAt = 1000L,
                                modifiedAt = 3000L,
                                tags = emptySet(),
                                remoteLastModified = 2000L,
                            ),
                        ),
                )
            val ncRepo =
                FakeNextcloudRepo(
                    // Note already exists remotely with the previously-synced mtime.
                    remoteFiles = listOf(NextcloudFileInfo("note1.json", 2000L)),
                    // After the push the server reports a newer mtime for the file.
                    afterUploadFiles = listOf(NextcloudFileInfo("note1.json", 4000L)),
                )
            val useCase = createUseCase(notesRepo, ncRepo)

            val result = useCase()

            assertTrue(result is Result.Success)
            assertTrue(ncRepo.uploadedNotes.isNotEmpty())
            // The saved remoteLastModified must be the server's post-upload mtime, not the
            // stale pre-upload value, otherwise the next sync would see a spurious conflict.
            assertEquals(
                4000L,
                notesRepo.savedNoteWithAttachments
                    .last()
                    .note.remoteLastModified,
            )
        }

    @Test
    fun syncReturnsErrorWhenRemoteListingFails() =
        runTest {
            val notesRepo = FakeNotesRepo(notes = emptyList())
            val ncRepo = FakeNextcloudRepo(listFails = true)
            val useCase = createUseCase(notesRepo, ncRepo)

            val result = useCase()

            assertTrue(result is Result.Success)
            assertTrue((result as Result.Success).data is SyncResult.Error)
        }

    @Test
    fun syncSkipsAttachmentWhenDownloadFails() =
        runTest {
            val notesRepo = FakeNotesRepo(notes = emptyList())
            val ncRepo =
                FakeNextcloudRepo(
                    remoteFiles = listOf(NextcloudFileInfo("note1.json", 2000L)),
                    downloadedNote =
                        NextcloudNote(
                            id = "note1",
                            title = "Remote",
                            content = "remote content",
                            createdAt = 1000L,
                            modifiedAt = 2000L,
                            tags = emptySet(),
                            status = "ACTIVE",
                            attachments = listOf(NextcloudAttachment(id = "att1", filename = "photo.png")),
                        ),
                    attachmentDownloadFails = true,
                )
            val useCase = createUseCase(notesRepo, ncRepo)

            val result = useCase()

            assertTrue(result is Result.Success)
            assertTrue((result as Result.Success).data is SyncResult.Success)
            assertTrue(notesRepo.savedNoteWithAttachments.isNotEmpty())
            assertTrue(
                notesRepo.savedNoteWithAttachments
                    .first()
                    .attachments
                    .isEmpty(),
            )
        }

    @Test
    fun syncMapsInvalidRemoteStatusToActive() =
        runTest {
            val notesRepo = FakeNotesRepo(notes = emptyList())
            val ncRepo =
                FakeNextcloudRepo(
                    remoteFiles = listOf(NextcloudFileInfo("note1.json", 2000L)),
                    downloadedNote =
                        NextcloudNote(
                            id = "note1",
                            title = "Remote",
                            content = "remote content",
                            createdAt = 1000L,
                            modifiedAt = 2000L,
                            tags = emptySet(),
                            status = "NOT_A_REAL_STATUS",
                            attachments = emptyList(),
                        ),
                )
            val useCase = createUseCase(notesRepo, ncRepo)

            val result = useCase()

            assertTrue(result is Result.Success)
            assertTrue(notesRepo.savedNoteWithAttachments.isNotEmpty())
            assertEquals(
                NoteStatus.ACTIVE,
                notesRepo.savedNoteWithAttachments
                    .first()
                    .note.status,
            )
        }

    @Test
    fun syncPullsRemoteNoteWhenOnlyRemoteChanged() =
        runTest {
            val notesRepo =
                FakeNotesRepo(
                    notes =
                        listOf(
                            Note(
                                id = "note1",
                                title = "Local",
                                content = "local content",
                                createdAt = 1000L,
                                modifiedAt = 2000L,
                                tags = emptySet(),
                                remoteLastModified = 2000L,
                            ),
                        ),
                )
            val ncRepo =
                FakeNextcloudRepo(
                    remoteFiles = listOf(NextcloudFileInfo("note1.json", 3000L)),
                    downloadedNote =
                        NextcloudNote(
                            id = "note1",
                            title = "Remote",
                            content = "remote updated",
                            createdAt = 1000L,
                            modifiedAt = 3000L,
                            tags = emptySet(),
                            status = "ACTIVE",
                            attachments = emptyList(),
                        ),
                )
            val useCase = createUseCase(notesRepo, ncRepo)

            val result = useCase()

            assertTrue(result is Result.Success)
            assertTrue((result as Result.Success).data is SyncResult.Success)
            assertTrue(notesRepo.savedNoteWithAttachments.isNotEmpty())
            assertEquals(
                "remote updated",
                notesRepo.savedNoteWithAttachments
                    .first()
                    .note.content,
            )
        }

    @Test
    fun pulledNoteRecordsRemoteFileMtime() =
        runTest {
            val notesRepo = FakeNotesRepo(notes = emptyList())
            val fileMtime = 5000L
            val ncRepo =
                FakeNextcloudRepo(
                    remoteFiles = listOf(NextcloudFileInfo("note1.json", fileMtime)),
                    downloadedNote =
                        NextcloudNote(
                            id = "note1",
                            title = "Remote",
                            content = "content",
                            createdAt = 1000L,
                            modifiedAt = 3000L,
                            tags = emptySet(),
                            status = "ACTIVE",
                            attachments = emptyList(),
                        ),
                )
            val useCase = createUseCase(notesRepo, ncRepo)

            useCase()

            val saved = notesRepo.savedNoteWithAttachments.first()
            assertEquals(fileMtime, saved.note.remoteLastModified)
        }

    @Test
    fun pullNoteKeepsExistingAttachmentUris() =
        runTest {
            val notesRepo =
                FakeNotesRepo(
                    notes =
                        listOf(
                            Note(
                                id = "note1",
                                title = "Local",
                                content = "content",
                                createdAt = 1000L,
                                modifiedAt = 2000L,
                                tags = emptySet(),
                                remoteLastModified = 2000L,
                            ),
                        ),
                )
            val ncRepo =
                FakeNextcloudRepo(
                    remoteFiles = listOf(NextcloudFileInfo("note1.json", 3000L)),
                    downloadedNote =
                        NextcloudNote(
                            id = "note1",
                            title = "Remote",
                            content = "updated content",
                            createdAt = 1000L,
                            modifiedAt = 3000L,
                            tags = emptySet(),
                            status = "ACTIVE",
                            attachments = listOf(NextcloudAttachment(id = "att1", filename = "photo.png")),
                        ),
                )
            val useCase = createUseCase(notesRepo, ncRepo)

            useCase()

            val saved = notesRepo.savedNoteWithAttachments.first()
            assertEquals(1, saved.attachments.size)
            assertTrue(
                saved.attachments
                    .first()
                    .uri
                    .isNotBlank(),
            )
            assertEquals(1, ncRepo.downloadAttachmentStreamCallCount)
        }

    @Test
    fun pushNoteUploadsAttachments() =
        runTest {
            val notesRepo =
                FakeNotesRepo(
                    notes =
                        listOf(
                            Note(
                                id = "note1",
                                title = "Local",
                                content = "content",
                                createdAt = 1000L,
                                modifiedAt = 3000L,
                                tags = emptySet(),
                                remoteLastModified = 0L,
                            ),
                        ),
                    attachments =
                        listOf(
                            org.skynetsoftware.skeletonnotes.domain.model.Attachment(
                                id = "att1",
                                noteId = "note1",
                                uri = "/data/note1/att1_photo.jpg",
                            ),
                        ),
                )
            val ncRepo = FakeNextcloudRepo(remoteFiles = emptyList())
            val storage = FakeAttachmentFileStorage()
            val settingsRepository = FakeSettingsRepository()
            val useCase = SyncNotesWithNextcloudUseCase(notesRepo, ncRepo, settingsRepository, storage)

            useCase()

            assertTrue(ncRepo.uploadedAttachments.isNotEmpty())
            assertEquals("note1", ncRepo.uploadedAttachments.first().first)
            assertEquals("att1", ncRepo.uploadedAttachments.first().second)
            assertEquals(1, ncRepo.uploadAttachmentStreamCallCount)
        }

    @Test
    fun trashNoteDeletesRemote() =
        runTest {
            val notesRepo =
                FakeNotesRepo(
                    notes =
                        listOf(
                            Note(
                                id = "note1",
                                title = "Trash Note",
                                content = "content",
                                createdAt = 1000L,
                                modifiedAt = 2000L,
                                tags = emptySet(),
                                status = NoteStatus.TRASH,
                                remoteLastModified = 1000L,
                            ),
                        ),
                )
            val ncRepo =
                FakeNextcloudRepo(
                    remoteFiles = listOf(NextcloudFileInfo("note1.json", 2000L)),
                )
            val useCase = createUseCase(notesRepo, ncRepo)

            useCase()

            assertTrue(ncRepo.deletedDirectories.contains("note1"))
        }

    @Test
    fun remoteDeletedNoteGetsTrashed() =
        runTest {
            val notesRepo =
                FakeNotesRepo(
                    notes =
                        listOf(
                            Note(
                                id = "note1",
                                title = "Remote Deleted",
                                content = "content",
                                createdAt = 1000L,
                                modifiedAt = 1000L,
                                tags = emptySet(),
                                remoteLastModified = 1000L,
                            ),
                        ),
                )
            val ncRepo = FakeNextcloudRepo(remoteFiles = emptyList())
            val useCase = createUseCase(notesRepo, ncRepo)

            useCase()

            assertTrue(notesRepo.trashedNoteIds.contains("note1"))
        }

    @Test
    fun syncReturnsErrorAndKeepsTimestampWhenUploadFails() =
        runTest {
            val notesRepo =
                FakeNotesRepo(
                    notes =
                        listOf(
                            Note(
                                id = "note1",
                                title = "Local",
                                content = "content",
                                createdAt = 1000L,
                                modifiedAt = 3000L,
                                tags = emptySet(),
                                remoteLastModified = 0L,
                            ),
                        ),
                )
            val ncRepo = FakeNextcloudRepo(remoteFiles = emptyList(), uploadNoteFails = true)
            val settingsRepository = FakeSettingsRepository()
            val useCase =
                SyncNotesWithNextcloudUseCase(notesRepo, ncRepo, settingsRepository, FakeAttachmentFileStorage())

            val result = useCase()

            assertTrue(result is Result.Success)
            assertTrue((result as Result.Success).data is SyncResult.Error)
            assertEquals(0L, (settingsRepository.nextcloudLastSyncTimestamp as MutableStateFlow).value)
        }

    @Test
    fun syncPersistsAuthExpiredReasonWhenServerReturns401() =
        runTest {
            val ncRepo =
                object : NextcloudRepository by FakeNextcloudRepo() {
                    override suspend fun listFiles(): Result<List<NextcloudFileInfo>> =
                        Result.Failure(NextcloudHttpException(401))
                }
            val settingsRepository = FakeSettingsRepository()
            val useCase =
                SyncNotesWithNextcloudUseCase(FakeNotesRepo(), ncRepo, settingsRepository, FakeAttachmentFileStorage())

            val result = useCase()

            val syncResult = (result as Result.Success).data
            assertTrue(syncResult is SyncResult.Error)
            assertEquals(SyncErrorReason.AUTH_EXPIRED, (syncResult as SyncResult.Error).reason)
        }

    @Test
    fun syncPropagatesCancellationExceptionInsteadOfReturningError() =
        runTest {
            val entered = CompletableDeferred<Unit>()
            // A repo that suspends indefinitely inside the first remote call so the sync can be
            // cancelled while in flight.
            val ncRepo =
                object : NextcloudRepository by FakeNextcloudRepo() {
                    override suspend fun listFiles(): Result<List<NextcloudFileInfo>> {
                        entered.complete(Unit)
                        awaitCancellation()
                    }
                }
            val useCase =
                SyncNotesWithNextcloudUseCase(
                    FakeNotesRepo(),
                    ncRepo,
                    FakeSettingsRepository(),
                    FakeAttachmentFileStorage(),
                )

            var caught: Throwable? = null
            var result: Result<SyncResult>? = null
            val job =
                launch(start = CoroutineStart.UNDISPATCHED) {
                    try {
                        result = useCase()
                    } catch (e: CancellationException) {
                        caught = e
                        throw e
                    }
                }

            entered.await()
            job.cancelAndJoin()

            // Cancellation must propagate cooperatively, not be swallowed into SyncResult.Error.
            assertTrue(caught is CancellationException)
            assertNull(result)
        }

    private fun createUseCase(
        notesRepo: FakeNotesRepo,
        ncRepo: FakeNextcloudRepo,
    ): SyncNotesWithNextcloudUseCase =
        SyncNotesWithNextcloudUseCase(notesRepo, ncRepo, FakeSettingsRepository(), FakeAttachmentFileStorage())

    private class FakeNotesRepo(
        private val notes: List<Note> = emptyList(),
        private val attachments: List<org.skynetsoftware.skeletonnotes.domain.model.Attachment> = emptyList(),
    ) : NotesRepository {
        val savedNoteWithAttachments = mutableListOf<NoteWithAttachments>()
        val trashedNoteIds = mutableListOf<String>()

        override fun getAllNotesFlow() = flowOf(Result.Success(notes))

        override suspend fun getAllNotes(): Result<List<Note>> = Result.Success(notes)

        override fun getAllNotesWithAttachmentsFlow() =
            flowOf(Result.Success(notes.map { NoteWithAttachments(it, attachments.filter { a -> a.noteId == it.id }) }))

        override suspend fun getAllNotesWithAttachments(): Result<List<NoteWithAttachments>> =
            Result.Success(notes.map { NoteWithAttachments(it, attachments.filter { a -> a.noteId == it.id }) })

        override fun getNoteByIdFlow(id: String) =
            flowOf(
                Result.Success(
                    NoteWithAttachments(
                        Note(id, null, "", 0, 0, emptySet()),
                        emptyList(),
                    ),
                ),
            )

        override suspend fun getNoteById(id: String): Result<NoteWithAttachments> =
            notes.find { it.id == id }?.let {
                Result.Success(NoteWithAttachments(it, attachments.filter { a -> a.noteId == id }))
            } ?: Result.Failure(Exception("not found"))

        override suspend fun saveNote(noteWithAttachments: NoteWithAttachments): Result<Unit> {
            savedNoteWithAttachments.add(noteWithAttachments)
            return Result.Success(Unit)
        }

        override suspend fun saveNotes(notesWithAttachments: List<NoteWithAttachments>): Result<Unit> {
            savedNoteWithAttachments.addAll(notesWithAttachments)
            return Result.Success(Unit)
        }

        override suspend fun deleteNote(id: String) = Result.Success(Unit)

        override suspend fun moveToTrash(id: String): Result<Unit> {
            trashedNoteIds.add(id)
            return Result.Success(Unit)
        }

        override suspend fun archiveNote(id: String) = Result.Success(Unit)

        override suspend fun restoreNote(id: String) = Result.Success(Unit)
    }

    private class FakeNextcloudRepo(
        private val remoteFiles: List<NextcloudFileInfo> = emptyList(),
        private val downloadedNote: NextcloudNote? = null,
        private val afterUploadFiles: List<NextcloudFileInfo> = emptyList(),
        private val listFails: Boolean = false,
        private val attachmentDownloadFails: Boolean = false,
        private val uploadNoteFails: Boolean = false,
    ) : NextcloudRepository {
        val uploadedNotes = mutableListOf<NextcloudNote>()
        val uploadedAttachments = mutableListOf<Triple<String, String, String>>()
        val deletedDirectories = mutableListOf<String>()
        var uploadAttachmentStreamCallCount = 0
        var downloadAttachmentStreamCallCount = 0
        private var uploadCalled = false

        override suspend fun initiateLogin(serverUrl: String) = Result.Success(NextcloudInitiateLoginResult("", "", ""))

        override suspend fun pollLogin(
            token: String,
            endpoint: String,
        ) = NextcloudPollStatus.Authenticated(
            NextcloudConnectionInfo("", ""),
        )

        override fun connectionInfo() = flowOf(null)

        override fun logout() {}

        override suspend fun listFiles(): Result<List<NextcloudFileInfo>> {
            if (listFails) return Result.Failure(Exception("listing failed"))
            return if (uploadCalled) Result.Success(afterUploadFiles) else Result.Success(remoteFiles)
        }

        override suspend fun downloadNote(uuid: String): Result<NextcloudNote> =
            if (downloadedNote != null) {
                Result.Success(downloadedNote)
            } else {
                Result.Failure(Exception("Not found"))
            }

        override suspend fun uploadNote(note: NextcloudNote): Result<Unit> {
            uploadedNotes.add(note)
            uploadCalled = true
            return if (uploadNoteFails) Result.Failure(Exception("upload failed")) else Result.Success(Unit)
        }

        override suspend fun deleteRemoteNote(uuid: String) = Result.Success(Unit)

        override suspend fun uploadAttachment(
            noteId: String,
            attachmentId: String,
            filename: String,
            inputStream: InputStream,
            contentLength: Long,
        ): Result<Unit> {
            uploadAttachmentStreamCallCount++
            inputStream.readBytes()
            uploadedAttachments.add(Triple(noteId, attachmentId, filename))
            return Result.Success(Unit)
        }

        override suspend fun downloadAttachment(
            noteId: String,
            attachmentId: String,
            filename: String,
        ): Result<String> {
            downloadAttachmentStreamCallCount++
            return if (attachmentDownloadFails) {
                Result.Failure(Exception("attachment download failed"))
            } else {
                Result.Success("/fake/$attachmentId")
            }
        }

        override suspend fun deleteAttachment(
            noteId: String,
            attachmentId: String,
            filename: String,
        ) = Result.Success(
            Unit,
        )

        override suspend fun deleteNoteDirectory(uuid: String): Result<Unit> {
            deletedDirectories.add(uuid)
            return Result.Success(Unit)
        }
    }

    private class FakeAttachmentFileStorage : AttachmentFileStorage {
        private val files = mutableMapOf<String, ByteArray>()

        override fun copyToStorage(
            source: String,
            attachmentId: String,
            mimeType: String?,
        ): String = "/fake/$attachmentId"

        override fun writeStream(
            attachmentId: String,
            inputStream: InputStream,
        ): String {
            files[attachmentId] = inputStream.readBytes()
            return "/fake/$attachmentId"
        }

        override fun openWriteStream(
            attachmentId: String,
            extension: String?,
        ): AttachmentWriteTarget {
            val outputStream =
                object : java.io.ByteArrayOutputStream() {
                    override fun close() {
                        files[attachmentId] = toByteArray()
                        super.close()
                    }
                }
            return AttachmentWriteTarget("/fake/$attachmentId", outputStream)
        }

        override fun getFile(attachmentId: String): File {
            val file = File.createTempFile("test_", ".tmp")
            file.deleteOnExit()
            files[attachmentId]?.let { file.writeBytes(it) }
            return file
        }

        override fun deleteFile(attachmentId: String) {
            files.remove(attachmentId)
        }
    }

    private class FakeSettingsRepository : SettingsRepository {
        override val nextcloudPeriodicSync: Flow<Boolean> = MutableStateFlow(false)
        override val nextcloudLastSyncTimestamp: Flow<Long> = MutableStateFlow(0L)
        override val nextcloudSyncIntervalMinutes: Flow<Long> = MutableStateFlow(360L)
        override val nextcloudSyncOnlyOnUnmetered: Flow<Boolean> = MutableStateFlow(true)

        override fun setPeriodicSyncEnabled(enabled: Boolean) {
            (nextcloudPeriodicSync as MutableStateFlow).value = enabled
        }

        override fun setNextcloudLastSyncTimestamp(timestamp: Long) {
            (nextcloudLastSyncTimestamp as MutableStateFlow).value = timestamp
        }

        override fun setSyncIntervalMinutes(minutes: Long) {}

        override fun setSyncOnlyOnUnmetered(onlyOnUnmetered: Boolean) {}

        override fun shouldStopRequestingNotificationPermission() = false

        override fun setStopRequestingNotificationPermission() {}
    }
}
