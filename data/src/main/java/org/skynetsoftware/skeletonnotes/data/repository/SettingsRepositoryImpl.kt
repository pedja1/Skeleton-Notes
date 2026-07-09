package org.skynetsoftware.skeletonnotes.data.repository

import kotlinx.coroutines.flow.Flow
import org.skynetsoftware.skeletonnotes.data.config.NextcloudConfigStore
import org.skynetsoftware.skeletonnotes.domain.repository.SettingsRepository

internal class SettingsRepositoryImpl(private val nextcloudConfigStore: NextcloudConfigStore) : SettingsRepository {
    override val nextcloudPeriodicSync: Flow<Boolean> = nextcloudConfigStore.periodicSyncEnabled
    override val nextcloudLastSyncTimestamp: Flow<Long> = nextcloudConfigStore.lastSyncTimestamp

    override fun setPeriodicSyncEnabled(enabled: Boolean) {
        nextcloudConfigStore.setPeriodicSyncEnabled(enabled)
    }

    override fun setNextcloudLastSyncTimestamp(timestamp: Long) {
        nextcloudConfigStore.setLastSyncTimestamp(timestamp)
    }
}
