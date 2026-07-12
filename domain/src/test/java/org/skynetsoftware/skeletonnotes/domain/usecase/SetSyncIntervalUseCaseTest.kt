package org.skynetsoftware.skeletonnotes.domain.usecase

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Test
import org.skynetsoftware.skeletonnotes.domain.repository.SettingsRepository

class SetSyncIntervalUseCaseTest {
    @Test
    fun invokeDelegatesToRepository() {
        val repo = FakeSettingsRepo()
        val useCase = SetSyncIntervalUseCase(repo)

        useCase(120L)

        assertEquals(120L, repo.capturedMinutes)
    }

    @Test
    fun invokeUpdatesCapturingDifferentValues() {
        val repo = FakeSettingsRepo()
        val useCase = SetSyncIntervalUseCase(repo)

        useCase(15L)
        assertEquals(15L, repo.capturedMinutes)

        useCase(1440L)
        assertEquals(1440L, repo.capturedMinutes)
    }

    private class FakeSettingsRepo : SettingsRepository {
        var capturedMinutes: Long = -1L

        override val nextcloudPeriodicSync: Flow<Boolean> = flowOf(false)

        override val nextcloudLastSyncTimestamp: Flow<Long> = flowOf(0L)

        override val nextcloudSyncIntervalMinutes: Flow<Long> = flowOf(360L)

        override val nextcloudSyncOnlyOnUnmetered: Flow<Boolean> = flowOf(true)

        override fun setPeriodicSyncEnabled(enabled: Boolean) {}

        override fun setNextcloudLastSyncTimestamp(timestamp: Long) {}

        override fun setSyncIntervalMinutes(minutes: Long) {
            capturedMinutes = minutes
        }

        override fun setSyncOnlyOnUnmetered(onlyOnUnmetered: Boolean) {}

        override fun shouldStopRequestingNotificationPermission() = false

        override fun setStopRequestingNotificationPermission() {}
    }
}
