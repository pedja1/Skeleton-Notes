package org.skynetsoftware.skeletonnotes.nextcloud.repository

import kotlinx.coroutines.flow.Flow
import org.skynetsoftware.skeletonnotes.domain.repository.SettingsRepository
import org.skynetsoftware.skeletonnotes.nextcloud.config.NextcloudConfigStore

internal class SettingsRepositoryImpl(
    private val nextcloudConfigStore: NextcloudConfigStore,
) : SettingsRepository {
    override val nextcloudPeriodicSync: Flow<Boolean> = nextcloudConfigStore.periodicSyncEnabled
    override val nextcloudLastSyncTimestamp: Flow<Long> = nextcloudConfigStore.lastSyncTimestamp
    override val nextcloudSyncIntervalMinutes: Flow<Long> = nextcloudConfigStore.syncIntervalMinutes
    override val nextcloudSyncOnlyOnUnmetered: Flow<Boolean> = nextcloudConfigStore.syncOnlyOnUnmetered

    override fun setPeriodicSyncEnabled(enabled: Boolean) {
        nextcloudConfigStore.setPeriodicSyncEnabled(enabled)
    }

    override fun setNextcloudLastSyncTimestamp(timestamp: Long) {
        nextcloudConfigStore.setLastSyncTimestamp(timestamp)
    }

    override fun setSyncIntervalMinutes(minutes: Long) {
        nextcloudConfigStore.setSyncIntervalMinutes(minutes)
    }

    override fun setSyncOnlyOnUnmetered(onlyOnUnmetered: Boolean) {
        nextcloudConfigStore.setSyncOnlyOnUnmetered(onlyOnUnmetered)
    }
}
