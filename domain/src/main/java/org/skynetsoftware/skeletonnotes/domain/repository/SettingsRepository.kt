package org.skynetsoftware.skeletonnotes.domain.repository

import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    val nextcloudPeriodicSync: Flow<Boolean>
    val nextcloudLastSyncTimestamp: Flow<Long>

    /** Sync interval in minutes. */
    val nextcloudSyncIntervalMinutes: Flow<Long>

    /** Whether the periodic sync job requires an unmetered connection. */
    val nextcloudSyncOnlyOnUnmetered: Flow<Boolean>

    fun setPeriodicSyncEnabled(enabled: Boolean)

    fun setNextcloudLastSyncTimestamp(timestamp: Long)

    fun setSyncIntervalMinutes(minutes: Long)

    fun setSyncOnlyOnUnmetered(onlyOnUnmetered: Boolean)
}
