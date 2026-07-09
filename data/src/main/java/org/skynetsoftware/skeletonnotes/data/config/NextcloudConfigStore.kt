package org.skynetsoftware.skeletonnotes.data.config

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Interface for persisting Nextcloud connection configuration.
 */
internal interface NextcloudConfigStore {
    val serverUrl: StateFlow<String?>
    val username: StateFlow<String?>
    val appPassword: StateFlow<String?>
    val isConfigured: Flow<Boolean>
    val periodicSyncEnabled: StateFlow<Boolean>
    val lastSyncTimestamp: StateFlow<Long>

    fun setServerConfig(serverUrl: String, username: String, appPassword: String)

    fun clearServerConfig()

    fun setPeriodicSyncEnabled(periodicSyncEnabled: Boolean)

    fun setLastSyncTimestamp(lastSyncTimestamp: Long)
}
