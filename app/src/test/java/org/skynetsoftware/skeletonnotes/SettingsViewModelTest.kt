package org.skynetsoftware.skeletonnotes

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.skynetsoftware.skeletonnotes.domain.model.Note
import org.skynetsoftware.skeletonnotes.domain.model.Result
import org.skynetsoftware.skeletonnotes.domain.model.nextcloud.NextcloudConnectionInfo
import org.skynetsoftware.skeletonnotes.domain.model.nextcloud.NextcloudInitiateLoginResult
import org.skynetsoftware.skeletonnotes.domain.model.nextcloud.NextcloudNote
import org.skynetsoftware.skeletonnotes.domain.model.nextcloud.NextcloudPollStatus
import org.skynetsoftware.skeletonnotes.domain.repository.AttachmentFileStorage
import org.skynetsoftware.skeletonnotes.domain.repository.NextcloudRepository
import org.skynetsoftware.skeletonnotes.domain.repository.RemoteFileInfo
import org.skynetsoftware.skeletonnotes.domain.repository.SettingsRepository
import org.skynetsoftware.skeletonnotes.domain.usecase.GetSettingsUseCase
import org.skynetsoftware.skeletonnotes.domain.usecase.InitiateNextcloudLoginUseCase
import org.skynetsoftware.skeletonnotes.domain.usecase.PollNextcloudLoginUseCase
import org.skynetsoftware.skeletonnotes.domain.usecase.SetPeriodicSyncEnabledUseCase
import org.skynetsoftware.skeletonnotes.domain.usecase.SyncNotesWithNextcloudUseCase
import org.skynetsoftware.skeletonnotes.domain.usecase.SyncResult
import org.skynetsoftware.skeletonnotes.settings.NextcloudLoginState
import org.skynetsoftware.skeletonnotes.settings.SettingsViewModel
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    @Test
    fun initialStateIsNotConnected() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        Dispatchers.setMain(testDispatcher)
        try {
            val viewModel = createViewModel(connectionInfo = null)
            testScheduler.advanceUntilIdle()
            assertEquals(NextcloudLoginState.NotConnected, viewModel.nextcloudLoginState.value)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun loadedStateIsConnectedWhenConnectionInfoExists() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        Dispatchers.setMain(testDispatcher)
        try {
            val connectionInfo = NextcloudConnectionInfo("https://cloud.example.com", "user")
            val viewModel = createViewModel(connectionInfo = connectionInfo)
            testScheduler.advanceUntilIdle()
            assertEquals(NextcloudLoginState.Connected(connectionInfo), viewModel.nextcloudLoginState.value)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun setPeriodicSyncEnabledCallsRepository() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        Dispatchers.setMain(testDispatcher)
        try {
            var enabledValue = false
            val settingsRepo = object : SettingsRepository by FakeSettingsRepo() {
                override fun setPeriodicSyncEnabled(enabled: Boolean) {
                    enabledValue = enabled
                }
            }
            val viewModel = createViewModel(settingsRepository = settingsRepo)

            viewModel.setPeriodicSyncEnabled(true)

            assertTrue(enabledValue)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun initiateLoginTransitionsToWaitingForLogin() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        Dispatchers.setMain(testDispatcher)
        try {
            val ncRepo = FakeNextcloudRepoForSettings(
                initiateLoginResult = Result.Success(NextcloudInitiateLoginResult("t", "e", "url")),
                pollLoginResult = NextcloudPollStatus.Pending,
            )
            val viewModel = createViewModel(nextcloudRepo = ncRepo)

            viewModel.initiateNextcloudLogin("https://cloud.example.com")
            // The poll stays pending, so the browser-waiting state is observable
            // before the loop eventually times out.
            assertEquals(NextcloudLoginState.WaitingForLogin, viewModel.nextcloudLoginState.value)

            // Drain the polling loop so no coroutine is left running past resetMain().
            testScheduler.advanceUntilIdle()
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun pollingSuccessTransitionsToConnected() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        Dispatchers.setMain(testDispatcher)
        try {
            val connectionInfo = NextcloudConnectionInfo("https://cloud.example.com", "user")
            val ncRepo = FakeNextcloudRepoForSettings(
                initiateLoginResult = Result.Success(NextcloudInitiateLoginResult("t", "e", "url")),
                pollLoginResult = NextcloudPollStatus.Authenticated(connectionInfo),
            )
            val viewModel = createViewModel(nextcloudRepo = ncRepo)

            viewModel.initiateNextcloudLogin("https://cloud.example.com")
            testScheduler.advanceUntilIdle()

            assertEquals(NextcloudLoginState.Connected(connectionInfo), viewModel.nextcloudLoginState.value)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun syncNowCallsSyncUseCase() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        Dispatchers.setMain(testDispatcher)
        try {
            var syncCalled = false
            val syncUseCase = FakeSyncUseCase(onInvoke = { syncCalled = true })
            val viewModel = createViewModel(syncUseCase = syncUseCase)

            viewModel.syncNow()
            testScheduler.advanceUntilIdle()

            assertTrue(syncCalled)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun syncNowPropagatesSyncResult() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        Dispatchers.setMain(testDispatcher)
        try {
            val conflictNote = Note(
                id = "note1", title = "Conflict", content = "content",
                createdAt = 1000L, modifiedAt = 2000L, tags = emptySet(),
            )
            val syncResult = SyncResult.HasConflicts(
                listOf(org.skynetsoftware.skeletonnotes.domain.usecase.NoteConflict(conflictNote, "note1"))
            )
            val syncUseCase = FakeSyncUseCase(result = Result.Success(syncResult))
            val viewModel = createViewModel(syncUseCase = syncUseCase)

            viewModel.syncNow()
            testScheduler.advanceUntilIdle()

            val result = viewModel.syncResult.value
            assertNotNull(result)
            assertTrue(result is SyncResult.HasConflicts)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun initiateLoginFailureSetsLoginError() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        Dispatchers.setMain(testDispatcher)
        try {
            val ncRepo = FakeNextcloudRepoForSettings(
                initiateLoginResult = Result.Failure(Exception("network error")),
            )
            val viewModel = createViewModel(nextcloudRepo = ncRepo)
            viewModel.initiateNextcloudLogin("https://cloud.example.com")
            testScheduler.advanceUntilIdle()
            assertEquals(NextcloudLoginState.LoginError, viewModel.nextcloudLoginState.value)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun pollingTimeoutSetsLoginError() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        Dispatchers.setMain(testDispatcher)
        try {
            val ncRepo = FakeNextcloudRepoForSettings(
                initiateLoginResult = Result.Success(NextcloudInitiateLoginResult("t", "e", "url")),
                pollLoginResult = NextcloudPollStatus.Pending,
            )
            val viewModel = createViewModel(nextcloudRepo = ncRepo)
            viewModel.initiateNextcloudLogin("https://cloud.example.com")
            // Advance past the polling timeout; login never completes.
            testScheduler.advanceUntilIdle()
            assertEquals(NextcloudLoginState.LoginError, viewModel.nextcloudLoginState.value)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun syncNowFailurePropagatesSyncError() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        Dispatchers.setMain(testDispatcher)
        try {
            val error = Exception("sync failed")
            val syncUseCase = FakeSyncUseCase(result = Result.Failure(error))
            val viewModel = createViewModel(syncUseCase = syncUseCase)
            viewModel.syncNow()
            testScheduler.advanceUntilIdle()
            val result = viewModel.syncResult.value
            assertNotNull(result)
            assertTrue(result is SyncResult.Error)
            assertEquals(error, (result as SyncResult.Error).throwable)
        } finally {
            Dispatchers.resetMain()
        }
    }

    private fun createViewModel(
        periodicSyncEnabled: Boolean = false,
        connectionInfo: NextcloudConnectionInfo? = null,
        settingsRepository: SettingsRepository = FakeSettingsRepo(periodicSyncEnabled),
        nextcloudRepo: NextcloudRepository = FakeNextcloudRepoForSettings(connectionInfo = connectionInfo),
        syncUseCase: FakeSyncUseCase = FakeSyncUseCase(),
    ): SettingsViewModel {
        return SettingsViewModel(
            getSettings = GetSettingsUseCase(settingsRepository, nextcloudRepo),
            setPeriodicSyncEnabled = SetPeriodicSyncEnabledUseCase(settingsRepository),
            initiateNextcloudLogin = InitiateNextcloudLoginUseCase(nextcloudRepo),
            pollNextcloudLogin = PollNextcloudLoginUseCase(nextcloudRepo),
            syncNotesWithNextcloud = syncUseCase,
        )
    }

    private class FakeSettingsRepo(
        private val periodicSync: Boolean = false,
    ) : SettingsRepository {
        override val nextcloudPeriodicSync = flowOf(periodicSync)
        override val nextcloudLastSyncTimestamp = flowOf(0L)
        override fun setPeriodicSyncEnabled(enabled: Boolean) {}
        override fun setNextcloudLastSyncTimestamp(timestamp: Long) {}
    }

    private class FakeNextcloudRepoForSettings(
        private val connectionInfo: NextcloudConnectionInfo? = null,
        private val initiateLoginResult: Result<NextcloudInitiateLoginResult> = Result.Success(NextcloudInitiateLoginResult("", "", "")),
        private val pollLoginResult: NextcloudPollStatus = NextcloudPollStatus.Pending,
    ) : NextcloudRepository {
        override suspend fun initiateLogin(serverUrl: String) = initiateLoginResult
        override suspend fun pollLogin(token: String, endpoint: String) = pollLoginResult
        override fun connectionInfo(): Flow<NextcloudConnectionInfo?> = flowOf(connectionInfo)
        override fun logout() {}
        override suspend fun listRemoteFiles(): Result<List<RemoteFileInfo>> = Result.Success(emptyList())
        override suspend fun downloadNote(uuid: String): Result<NextcloudNote> = Result.Failure(Exception("Not implemented"))
        override suspend fun uploadNote(note: NextcloudNote) = Result.Success(Unit)
        override suspend fun deleteRemoteNote(uuid: String) = Result.Success(Unit)
        override suspend fun uploadAttachment(noteId: String, attachmentId: String, filename: String, bytes: ByteArray) = Result.Success(Unit)
        override suspend fun downloadAttachment(noteId: String, attachmentId: String, filename: String): Result<ByteArray> = Result.Success(ByteArray(0))
        override suspend fun deleteRemoteAttachment(noteId: String, attachmentId: String, filename: String) = Result.Success(Unit)
        override suspend fun deleteRemoteNoteDirectory(uuid: String) = Result.Success(Unit)
    }

    private class FakeSyncUseCase(
        private val onInvoke: () -> Unit = {},
        private val result: Result<SyncResult> = Result.Success(SyncResult.Success),
    ) : SyncNotesWithNextcloudUseCase(
        TestNotesRepo(),
        FakeNextcloudRepoForSettings(),
        FakeAttachmentFileStorage(),
        FakeSettingsRepo()
    ) {
        override suspend fun invoke(): Result<SyncResult> {
            onInvoke()
            return result
        }
    }

    private class TestNotesRepo : BaseFakeNotesRepository()

    private class FakeAttachmentFileStorage : AttachmentFileStorage {
        override fun writeBytes(attachmentId: String, bytes: ByteArray): String = "/fake/$attachmentId"
        override fun getFile(attachmentId: String): File = File.createTempFile("test_", ".tmp").also { it.deleteOnExit() }
        override fun deleteFile(attachmentId: String) {}
    }
}
