package org.skynetsoftware.skeletonnotes

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.skynetsoftware.skeletonnotes.domain.model.Note
import org.skynetsoftware.skeletonnotes.domain.model.Result
import org.skynetsoftware.skeletonnotes.domain.model.nextcloud.NextcloudConnectionInfo
import org.skynetsoftware.skeletonnotes.domain.model.nextcloud.NextcloudFileInfo
import org.skynetsoftware.skeletonnotes.domain.model.nextcloud.NextcloudInitiateLoginResult
import org.skynetsoftware.skeletonnotes.domain.model.nextcloud.NextcloudNote
import org.skynetsoftware.skeletonnotes.domain.model.nextcloud.NextcloudPollStatus
import org.skynetsoftware.skeletonnotes.domain.repository.BackupRepository
import org.skynetsoftware.skeletonnotes.domain.repository.ConflictResolution
import org.skynetsoftware.skeletonnotes.domain.repository.ImportSummary
import org.skynetsoftware.skeletonnotes.domain.repository.NextcloudRepository
import org.skynetsoftware.skeletonnotes.domain.repository.SettingsRepository
import org.skynetsoftware.skeletonnotes.domain.usecase.ExportNotesUseCase
import org.skynetsoftware.skeletonnotes.domain.usecase.GetSettingsUseCase
import org.skynetsoftware.skeletonnotes.domain.usecase.ImportNotesUseCase
import org.skynetsoftware.skeletonnotes.domain.usecase.InitiateNextcloudLoginUseCase
import org.skynetsoftware.skeletonnotes.domain.usecase.PollNextcloudLoginUseCase
import org.skynetsoftware.skeletonnotes.domain.usecase.SetPeriodicSyncEnabledUseCase
import org.skynetsoftware.skeletonnotes.domain.usecase.SetSyncIntervalUseCase
import org.skynetsoftware.skeletonnotes.domain.usecase.SetSyncOnlyOnUnmeteredUseCase
import org.skynetsoftware.skeletonnotes.settings.NextcloudLoginState
import org.skynetsoftware.skeletonnotes.settings.SettingsViewModel
import org.skynetsoftware.skeletonnotes.sync.NextcloudSyncScheduler
import java.io.InputStream
import java.io.OutputStream

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {
    @Test
    fun initialStateIsNotConnected() =
        runTest {
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
    fun loadedStateIsConnectedWhenConnectionInfoExists() =
        runTest {
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
    fun setPeriodicSyncEnabledCallsRepositoryAndReschedules() =
        runTest {
            val testDispatcher = UnconfinedTestDispatcher(testScheduler)
            Dispatchers.setMain(testDispatcher)
            try {
                var enabledValue = false
                val settingsRepo =
                    object : SettingsRepository by FakeSettingsRepo() {
                        override fun setPeriodicSyncEnabled(enabled: Boolean) {
                            enabledValue = enabled
                        }
                    }
                val fakeScheduler = FakeSyncScheduler()
                val viewModel = createViewModel(settingsRepository = settingsRepo, scheduler = fakeScheduler)

                viewModel.setPeriodicSyncEnabled(true)

                assertTrue(enabledValue)
                assertTrue(fakeScheduler.reschedulePeriodicSyncCalled)
            } finally {
                Dispatchers.resetMain()
            }
        }

    @Test
    fun setSyncIntervalCallsRepositoryAndReschedules() =
        runTest {
            val testDispatcher = UnconfinedTestDispatcher(testScheduler)
            Dispatchers.setMain(testDispatcher)
            try {
                var capturedMinutes = -1L
                val settingsRepo =
                    object : SettingsRepository by FakeSettingsRepo() {
                        override fun setSyncIntervalMinutes(minutes: Long) {
                            capturedMinutes = minutes
                        }
                    }
                val fakeScheduler = FakeSyncScheduler()
                val viewModel = createViewModel(settingsRepository = settingsRepo, scheduler = fakeScheduler)

                viewModel.setSyncInterval(60L)

                assertEquals(60L, capturedMinutes)
                assertTrue(fakeScheduler.reschedulePeriodicSyncCalled)
            } finally {
                Dispatchers.resetMain()
            }
        }

    @Test
    fun setSyncOnlyOnUnmeteredCallsRepositoryAndReschedules() =
        runTest {
            val testDispatcher = UnconfinedTestDispatcher(testScheduler)
            Dispatchers.setMain(testDispatcher)
            try {
                var capturedValue = false
                val settingsRepo =
                    object : SettingsRepository by FakeSettingsRepo() {
                        override fun setSyncOnlyOnUnmetered(onlyOnUnmetered: Boolean) {
                            capturedValue = onlyOnUnmetered
                        }
                    }
                val fakeScheduler = FakeSyncScheduler()
                val viewModel = createViewModel(settingsRepository = settingsRepo, scheduler = fakeScheduler)

                viewModel.setSyncOnlyOnUnmetered(true)

                assertTrue(capturedValue)
                assertTrue(fakeScheduler.reschedulePeriodicSyncCalled)
            } finally {
                Dispatchers.resetMain()
            }
        }

    @Test
    fun syncNowCallsSchedulerSyncNow() =
        runTest {
            val testDispatcher = UnconfinedTestDispatcher(testScheduler)
            Dispatchers.setMain(testDispatcher)
            try {
                val fakeScheduler = FakeSyncScheduler()
                val viewModel = createViewModel(scheduler = fakeScheduler)

                viewModel.syncNow()

                assertTrue(fakeScheduler.syncNowCalled)
            } finally {
                Dispatchers.resetMain()
            }
        }

    @Test
    fun initiateLoginTransitionsToWaitingForLogin() =
        runTest {
            val testDispatcher = UnconfinedTestDispatcher(testScheduler)
            Dispatchers.setMain(testDispatcher)
            try {
                val ncRepo =
                    FakeNextcloudRepoForSettings(
                        initiateLoginResult = Result.Success(NextcloudInitiateLoginResult("t", "e", "url")),
                        pollLoginResult = NextcloudPollStatus.Pending,
                    )
                val viewModel = createViewModel(nextcloudRepo = ncRepo)

                viewModel.initiateNextcloudLogin("https://cloud.example.com")
                assertEquals(NextcloudLoginState.WaitingForLogin, viewModel.nextcloudLoginState.value)

                testScheduler.advanceUntilIdle()
            } finally {
                Dispatchers.resetMain()
            }
        }

    @Test
    fun initiateLoginNormalizesSchemelessUrlToHttps() =
        runTest {
            val testDispatcher = UnconfinedTestDispatcher(testScheduler)
            Dispatchers.setMain(testDispatcher)
            try {
                val ncRepo =
                    FakeNextcloudRepoForSettings(
                        initiateLoginResult = Result.Success(NextcloudInitiateLoginResult("t", "e", "url")),
                        pollLoginResult = NextcloudPollStatus.Pending,
                    )
                val viewModel = createViewModel(nextcloudRepo = ncRepo)

                viewModel.initiateNextcloudLogin("cloud.example.com")

                assertEquals(NextcloudLoginState.WaitingForLogin, viewModel.nextcloudLoginState.value)
                assertEquals("https://cloud.example.com", viewModel.nextcloudServerUrl.value)

                testScheduler.advanceUntilIdle()
            } finally {
                Dispatchers.resetMain()
            }
        }

    @Test
    fun pollingSuccessTransitionsToConnected() =
        runTest {
            val testDispatcher = UnconfinedTestDispatcher(testScheduler)
            Dispatchers.setMain(testDispatcher)
            try {
                val connectionInfo = NextcloudConnectionInfo("https://cloud.example.com", "user")
                val ncRepo =
                    FakeNextcloudRepoForSettings(
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
    fun initiateLoginFailureSetsLoginError() =
        runTest {
            val testDispatcher = UnconfinedTestDispatcher(testScheduler)
            Dispatchers.setMain(testDispatcher)
            try {
                val ncRepo =
                    FakeNextcloudRepoForSettings(
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
    fun pollingTimeoutSetsLoginError() =
        runTest {
            val testDispatcher = UnconfinedTestDispatcher(testScheduler)
            Dispatchers.setMain(testDispatcher)
            try {
                val ncRepo =
                    FakeNextcloudRepoForSettings(
                        initiateLoginResult = Result.Success(NextcloudInitiateLoginResult("t", "e", "url")),
                        pollLoginResult = NextcloudPollStatus.Pending,
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
    fun importConflictRemainsPendingUntilResolved() =
        runTest {
            val testDispatcher = UnconfinedTestDispatcher(testScheduler)
            Dispatchers.setMain(testDispatcher)
            try {
                val existing = Note("n1", "Existing", "old", 1L, 1L, emptySet())
                val incoming = Note("n1", "Incoming", "new", 2L, 2L, emptySet())
                val viewModel = createViewModel()
                var resolution: ConflictResolution? = null
                val conflictJob =
                    launch {
                        resolution = viewModel.resolveConflict(existing, incoming)
                    }
                testScheduler.advanceUntilIdle()

                assertEquals(incoming, viewModel.pendingConflict.value?.incoming)
                assertEquals(existing, viewModel.pendingConflict.value?.existing)

                viewModel.onConflictResolved(ConflictResolution.OVERWRITE, applyToAll = false)
                testScheduler.advanceUntilIdle()

                assertEquals(ConflictResolution.OVERWRITE, resolution)
                assertEquals(null, viewModel.pendingConflict.value)
                conflictJob.cancel()
            } finally {
                Dispatchers.resetMain()
            }
        }

    private fun createViewModel(
        periodicSyncEnabled: Boolean = false,
        connectionInfo: NextcloudConnectionInfo? = null,
        settingsRepository: SettingsRepository = FakeSettingsRepo(periodicSyncEnabled),
        nextcloudRepo: NextcloudRepository = FakeNextcloudRepoForSettings(connectionInfo = connectionInfo),
        scheduler: NextcloudSyncScheduler = FakeSyncScheduler(),
        backupRepository: BackupRepository = FakeBackupRepository(),
    ): SettingsViewModel =
        SettingsViewModel(
            getSettings = GetSettingsUseCase(settingsRepository, nextcloudRepo),
            setPeriodicSyncEnabled = SetPeriodicSyncEnabledUseCase(settingsRepository),
            setSyncInterval = SetSyncIntervalUseCase(settingsRepository),
            setSyncOnlyOnUnmetered = SetSyncOnlyOnUnmeteredUseCase(settingsRepository),
            initiateNextcloudLogin = InitiateNextcloudLoginUseCase(nextcloudRepo),
            pollNextcloudLogin = PollNextcloudLoginUseCase(nextcloudRepo),
            scheduler = scheduler,
            exportNotes = ExportNotesUseCase(backupRepository),
            importNotes = ImportNotesUseCase(backupRepository),
        )

    private open class FakeBackupRepository : BackupRepository {
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

    private class FakeSettingsRepo(
        periodicSync: Boolean = false,
    ) : SettingsRepository {
        override val nextcloudPeriodicSync = flowOf(periodicSync)
        override val nextcloudLastSyncTimestamp = flowOf(0L)
        override val nextcloudSyncIntervalMinutes: Flow<Long> = flowOf(360L)
        override val nextcloudSyncOnlyOnUnmetered: Flow<Boolean> = flowOf(true)

        override fun setPeriodicSyncEnabled(enabled: Boolean) {}

        override fun setNextcloudLastSyncTimestamp(timestamp: Long) {}

        override fun setSyncIntervalMinutes(minutes: Long) {}

        override fun setSyncOnlyOnUnmetered(onlyOnUnmetered: Boolean) {}

        override fun shouldStopRequestingNotificationPermission() = false

        override fun setStopRequestingNotificationPermission() {}
    }

    private class FakeNextcloudRepoForSettings(
        private val connectionInfo: NextcloudConnectionInfo? = null,
        private val initiateLoginResult: Result<NextcloudInitiateLoginResult> =
            Result.Success(
                NextcloudInitiateLoginResult("", "", ""),
            ),
        private val pollLoginResult: NextcloudPollStatus = NextcloudPollStatus.Pending,
    ) : NextcloudRepository {
        override suspend fun initiateLogin(serverUrl: String) = initiateLoginResult

        override suspend fun pollLogin(
            token: String,
            endpoint: String,
        ) = pollLoginResult

        override fun connectionInfo(): Flow<NextcloudConnectionInfo?> = flowOf(connectionInfo)

        override fun logout() {}

        override suspend fun listFiles(): Result<List<NextcloudFileInfo>> = Result.Success(emptyList())

        override suspend fun downloadNote(uuid: String): Result<NextcloudNote> =
            Result.Failure(Exception("Not implemented"))

        override suspend fun uploadNote(note: NextcloudNote) = Result.Success(Unit)

        override suspend fun deleteRemoteNote(uuid: String) = Result.Success(Unit)

        override suspend fun uploadAttachment(
            noteId: String,
            attachmentId: String,
            filename: String,
            inputStream: InputStream,
            contentLength: Long,
        ) = Result.Success(Unit)

        override suspend fun downloadAttachment(
            noteId: String,
            attachmentId: String,
            filename: String,
        ): Result<String> = Result.Success("")

        override suspend fun deleteAttachment(
            noteId: String,
            attachmentId: String,
            filename: String,
        ) = Result.Success(Unit)

        override suspend fun deleteNoteDirectory(uuid: String) = Result.Success(Unit)
    }

    private class FakeSyncScheduler : NextcloudSyncScheduler {
        var reschedulePeriodicSyncCalled = false
        var syncNowCalled = false
        var cancelPeriodicSyncCalled = false

        override fun reschedulePeriodicSync() {
            reschedulePeriodicSyncCalled = true
        }

        override fun syncNow() {
            syncNowCalled = true
        }

        override fun cancelPeriodicSync() {
            cancelPeriodicSyncCalled = true
        }
    }
}
