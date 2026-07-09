package org.skynetsoftware.skeletonnotes.domain.repository

import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    val nextcloudPeriodicSync: Flow<Boolean>
    val nextcloudLastSyncTimestamp: Flow<Long>

    fun setPeriodicSyncEnabled(enabled: Boolean)

    fun setNextcloudLastSyncTimestamp(timestamp: Long)
}
