package org.skynetsoftware.skeletonnotes.nextcloud

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import org.skynetsoftware.skeletonnotes.domain.repository.SettingsRepository

/**
 * No-op implementation of [SettingsRepository] for the lite flavor.
 * All flows return defaults and setters are no-ops.
 */
class NoOpSettingsRepository : SettingsRepository {
    private val periodicSyncFlow = MutableStateFlow(false)
    private val lastSyncTimestampFlow = MutableStateFlow(0L)
    private val syncIntervalMinutesFlow = MutableStateFlow(360L)
    private val syncOnlyOnUnmeteredFlow = MutableStateFlow(true)

    override val nextcloudPeriodicSync: Flow<Boolean> = periodicSyncFlow
    override val nextcloudLastSyncTimestamp: Flow<Long> = lastSyncTimestampFlow
    override val nextcloudSyncIntervalMinutes: Flow<Long> = syncIntervalMinutesFlow
    override val nextcloudSyncOnlyOnUnmetered: Flow<Boolean> = syncOnlyOnUnmeteredFlow

    override fun setPeriodicSyncEnabled(enabled: Boolean) {}

    override fun setNextcloudLastSyncTimestamp(timestamp: Long) {}

    override fun setSyncIntervalMinutes(minutes: Long) {}

    override fun setSyncOnlyOnUnmetered(onlyOnUnmetered: Boolean) {}
}
