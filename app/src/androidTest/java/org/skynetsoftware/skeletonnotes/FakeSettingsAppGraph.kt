package org.skynetsoftware.skeletonnotes

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import org.skynetsoftware.skeletonnotes.di.AppGraph
import org.skynetsoftware.skeletonnotes.domain.model.Result
import org.skynetsoftware.skeletonnotes.domain.model.nextcloud.NextcloudConnectionInfo
import org.skynetsoftware.skeletonnotes.domain.model.nextcloud.NextcloudFileInfo
import org.skynetsoftware.skeletonnotes.domain.model.nextcloud.NextcloudInitiateLoginResult
import org.skynetsoftware.skeletonnotes.domain.model.nextcloud.NextcloudNote
import org.skynetsoftware.skeletonnotes.domain.model.nextcloud.NextcloudPollStatus
import org.skynetsoftware.skeletonnotes.domain.repository.NextcloudRepository
import org.skynetsoftware.skeletonnotes.domain.repository.SettingsRepository
import org.skynetsoftware.skeletonnotes.domain.usecase.GetSettingsUseCase
import org.skynetsoftware.skeletonnotes.domain.usecase.InitiateNextcloudLoginUseCase
import org.skynetsoftware.skeletonnotes.domain.usecase.PollNextcloudLoginUseCase
import org.skynetsoftware.skeletonnotes.domain.usecase.SetPeriodicSyncEnabledUseCase
import org.skynetsoftware.skeletonnotes.domain.usecase.SetSyncIntervalUseCase
import org.skynetsoftware.skeletonnotes.domain.usecase.SetSyncOnlyOnUnmeteredUseCase
import org.skynetsoftware.skeletonnotes.sync.NextcloudSyncScheduler
import java.io.InputStream

/**
 * [SettingsRepository] implementation for instrumented tests that allows
 * controlling the settings flows and tracking write operations.
 */
class FakeSettingsRepository(
    periodicSync: Boolean = false,
    lastSyncTimestamp: Long = 0L,
    syncIntervalMinutes: Long = 360L,
    syncOnlyOnUnmetered: Boolean = true,
) : SettingsRepository {
    private val periodicSyncFlow = MutableStateFlow(periodicSync)
    private val lastSyncTimestampFlow = MutableStateFlow(lastSyncTimestamp)
    private val syncIntervalMinutesFlow = MutableStateFlow(syncIntervalMinutes)
    private val syncOnlyOnUnmeteredFlow = MutableStateFlow(syncOnlyOnUnmetered)

    override val nextcloudPeriodicSync: Flow<Boolean> = periodicSyncFlow

    override val nextcloudLastSyncTimestamp: Flow<Long> = lastSyncTimestampFlow

    override val nextcloudSyncIntervalMinutes: Flow<Long> = syncIntervalMinutesFlow

    override val nextcloudSyncOnlyOnUnmetered: Flow<Boolean> = syncOnlyOnUnmeteredFlow

    var periodicSyncEnabled: Boolean = periodicSync
        private set

    var nextcloudLastSyncTimestampValue: Long = lastSyncTimestamp
        private set

    var shouldStopPermission: Boolean = false
        private set

    override fun setPeriodicSyncEnabled(enabled: Boolean) {
        periodicSyncEnabled = enabled
        periodicSyncFlow.value = enabled
    }

    override fun setNextcloudLastSyncTimestamp(timestamp: Long) {
        nextcloudLastSyncTimestampValue = timestamp
        lastSyncTimestampFlow.value = timestamp
    }

    override fun setSyncIntervalMinutes(minutes: Long) {
        syncIntervalMinutesFlow.value = minutes
    }

    override fun setSyncOnlyOnUnmetered(onlyOnUnmetered: Boolean) {
        syncOnlyOnUnmeteredFlow.value = onlyOnUnmetered
    }

    override fun shouldStopRequestingNotificationPermission() = shouldStopPermission

    override fun setStopRequestingNotificationPermission() {
        shouldStopPermission = true
    }
}

/**
 * [NextcloudRepository] implementation for instrumented tests that allows
 * controlling login flow results and connection state. An optional
 * [suspendBlocker] can be used to suspend [initiateLogin] indefinitely,
 * allowing tests to observe intermediate login states.
 */
class FakeSettingsNextcloudRepository(
    connection: NextcloudConnectionInfo? = null,
    private val initiateResult: Result<NextcloudInitiateLoginResult> =
        Result.Success(
            NextcloudInitiateLoginResult("", "", ""),
        ),
    private val pollResult: NextcloudPollStatus =
        NextcloudPollStatus.Authenticated(
            NextcloudConnectionInfo("", ""),
        ),
    var suspendBlocker: CompletableDeferred<Unit>? = null,
) : NextcloudRepository {
    private val connectionFlow = MutableStateFlow(connection)

    override fun connectionInfo(): Flow<NextcloudConnectionInfo?> = connectionFlow

    override suspend fun initiateLogin(serverUrl: String): Result<NextcloudInitiateLoginResult> {
        suspendBlocker?.await()
        return initiateResult
    }

    override suspend fun pollLogin(
        token: String,
        endpoint: String,
    ): NextcloudPollStatus = pollResult

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

/**
 * Test [AppGraph] that delegates all use cases to a real [delegate] graph
 * except settings-related ones, which are backed by controllable
 * [FakeSettingsRepository] and [FakeSettingsNextcloudRepository] instances.
 */
class FakeSettingsAppGraph(
    val delegate: AppGraph,
    settingsRepository: FakeSettingsRepository = FakeSettingsRepository(),
    nextcloudRepository: FakeSettingsNextcloudRepository = FakeSettingsNextcloudRepository(),
) : AppGraph by delegate {
    override val getSettingsUseCase: GetSettingsUseCase =
        GetSettingsUseCase(settingsRepository, nextcloudRepository)

    override val setPeriodicSyncEnabledUseCase: SetPeriodicSyncEnabledUseCase =
        SetPeriodicSyncEnabledUseCase(settingsRepository)

    override val setSyncIntervalUseCase: SetSyncIntervalUseCase =
        SetSyncIntervalUseCase(settingsRepository)

    override val setSyncOnlyOnUnmeteredUseCase: SetSyncOnlyOnUnmeteredUseCase =
        SetSyncOnlyOnUnmeteredUseCase(settingsRepository)

    override val initiateNextcloudLoginUseCase: InitiateNextcloudLoginUseCase =
        InitiateNextcloudLoginUseCase(nextcloudRepository)

    override val pollNextcloudLoginUseCase: PollNextcloudLoginUseCase =
        PollNextcloudLoginUseCase(nextcloudRepository)

    override val nextcloudSyncScheduler: NextcloudSyncScheduler = NoOpSyncScheduler()
}

/** [NextcloudSyncScheduler] that does nothing, used in UI tests to avoid real job scheduling. */
class NoOpSyncScheduler : NextcloudSyncScheduler {
    override fun reschedulePeriodicSync() {}

    override fun syncNow() {}

    override fun cancelPeriodicSync() {}
}
