package org.skynetsoftware.skeletonnotes.domain.usecase

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Test
import org.skynetsoftware.skeletonnotes.domain.repository.SettingsRepository

class SetSyncOnlyOnUnmeteredUseCaseTest {
    @Test
    fun invokeDelegatesToRepository() {
        val repo = FakeSettingsRepo()
        val useCase = SetSyncOnlyOnUnmeteredUseCase(repo)

        useCase(true)

        assertEquals(true, repo.capturedValue)
    }

    @Test
    fun invokeFalseDelegatesToRepository() {
        val repo = FakeSettingsRepo()
        val useCase = SetSyncOnlyOnUnmeteredUseCase(repo)

        useCase(false)

        assertEquals(false, repo.capturedValue)
    }

    private class FakeSettingsRepo : SettingsRepository {
        var capturedValue: Boolean? = null

        override val nextcloudPeriodicSync: Flow<Boolean> = flowOf(false)

        override val nextcloudLastSyncTimestamp: Flow<Long> = flowOf(0L)

        override val nextcloudSyncIntervalMinutes: Flow<Long> = flowOf(360L)

        override val nextcloudSyncOnlyOnUnmetered: Flow<Boolean> = flowOf(true)

        override fun setPeriodicSyncEnabled(enabled: Boolean) {}

        override fun setNextcloudLastSyncTimestamp(timestamp: Long) {}

        override fun setSyncIntervalMinutes(minutes: Long) {}

        override fun setSyncOnlyOnUnmetered(onlyOnUnmetered: Boolean) {
            capturedValue = onlyOnUnmetered
        }

        override fun shouldStopRequestingNotificationPermission() = false

        override fun setStopRequestingNotificationPermission() {}
    }
}
