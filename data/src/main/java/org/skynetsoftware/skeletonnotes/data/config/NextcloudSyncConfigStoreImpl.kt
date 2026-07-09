package org.skynetsoftware.skeletonnotes.data.config

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine

/**
 * Default [NextcloudConfigStore] implementation backed by [SharedPreferences].
 */
@SuppressLint("UseKtx")
internal class NextcloudConfigStoreImpl(private val prefs: SharedPreferences) : NextcloudConfigStore {

    companion object {
        private const val PREFS_NAME = "nextcloud"
        private const val KEY_SERVER_URL = "serverUrl"
        private const val KEY_USERNAME = "username"
        private const val KEY_APP_PASSWORD = "appPassword"
        private const val KEY_PERIODIC_SYNC_ENABLED = "periodicSyncEnabled"
        private const val KEY_LAST_SYNC_TIMESTAMP = "lastSyncTimestamp"

        fun from(context: Context): NextcloudConfigStoreImpl =
            NextcloudConfigStoreImpl(context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE))
    }

    private val _serverUrl = MutableStateFlow(prefs.getString(KEY_SERVER_URL, null))
    override val serverUrl: StateFlow<String?> get() = _serverUrl.asStateFlow()

    private val _username = MutableStateFlow(prefs.getString(KEY_USERNAME, null))
    override val username: StateFlow<String?> get() = _username.asStateFlow()

    private val _appPassword = MutableStateFlow(prefs.getString(KEY_APP_PASSWORD, null))
    override val appPassword: StateFlow<String?> = _appPassword.asStateFlow()

    override val isConfigured: Flow<Boolean> = combine(serverUrl, username, appPassword) { serverUrl, username, appPassword ->
        serverUrl != null && username != null && appPassword != null
    }

    private val _periodicSyncEnabled = MutableStateFlow(prefs.getBoolean(KEY_PERIODIC_SYNC_ENABLED, false))
    override val periodicSyncEnabled: StateFlow<Boolean> get() = _periodicSyncEnabled.asStateFlow()

    private val _lastSyncTimestamp = MutableStateFlow(prefs.getLong(KEY_LAST_SYNC_TIMESTAMP, 0L))
    override val lastSyncTimestamp: StateFlow<Long> get() = _lastSyncTimestamp.asStateFlow()

    override fun setServerConfig(serverUrl: String, username: String, appPassword: String) {
        prefs.edit()
            .putString(KEY_SERVER_URL, serverUrl)
            .putString(KEY_USERNAME, username)
            .putString(KEY_APP_PASSWORD, appPassword)
            .apply()
        _serverUrl.value = serverUrl
        _username.value = username
        _appPassword.value = appPassword
    }

    override fun clearServerConfig() {
        prefs.edit()
            .putString(KEY_SERVER_URL, null)
            .putString(KEY_USERNAME, null)
            .putString(KEY_APP_PASSWORD, null)
            .apply()
        _serverUrl.value = null
        _username.value = null
        _appPassword.value = null
    }

    override fun setPeriodicSyncEnabled(periodicSyncEnabled: Boolean) {
        prefs.edit()
            .putBoolean(KEY_PERIODIC_SYNC_ENABLED, periodicSyncEnabled)
            .apply()
        _periodicSyncEnabled.value = periodicSyncEnabled
    }

    override fun setLastSyncTimestamp(lastSyncTimestamp: Long) {
        prefs.edit()
            .putLong(KEY_LAST_SYNC_TIMESTAMP, lastSyncTimestamp)
            .apply()
        _lastSyncTimestamp.value = lastSyncTimestamp
    }
}
