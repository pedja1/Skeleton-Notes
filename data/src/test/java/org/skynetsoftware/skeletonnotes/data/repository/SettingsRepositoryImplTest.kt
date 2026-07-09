package org.skynetsoftware.skeletonnotes.data.repository

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.skynetsoftware.skeletonnotes.data.config.NextcloudConfigStore

class SettingsRepositoryImplTest {

    private lateinit var configStore: FakeConfigStore
    private lateinit var repository: SettingsRepositoryImpl

    @Before
    fun setUp() {
        configStore = FakeConfigStore()
        repository = SettingsRepositoryImpl(configStore)
    }

    @Test
    fun periodicSyncEnabledExposesConfigFlow() = runBlocking {
        configStore.periodicSyncEnabledFlow.value = true

        val value = repository.nextcloudPeriodicSync.first()

        assertTrue(value)
    }

    @Test
    fun lastSyncTimestampExposesConfigFlow() = runBlocking {
        configStore.lastSyncTimestampFlow.value = 5000L

        val value = repository.nextcloudLastSyncTimestamp.first()

        assertEquals(5000L, value)
    }

    @Test
    fun setPeriodicSyncEnabledDelegatesToStore() {
        repository.setPeriodicSyncEnabled(true)

        assertEquals(true, configStore.setPeriodicSyncEnabledValue)
    }

    @Test
    fun setPeriodicSyncEnabledDisableDelegatesToStore() {
        repository.setPeriodicSyncEnabled(false)

        assertEquals(false, configStore.setPeriodicSyncEnabledValue)
    }

    @Test
    fun setLastSyncTimestampDelegatesToStore() {
        repository.setNextcloudLastSyncTimestamp(3000L)

        assertEquals(3000L, configStore.setLastSyncTimestampValue)
    }

    private class FakeConfigStore : NextcloudConfigStore {
        val periodicSyncEnabledFlow = MutableStateFlow(false)
        val lastSyncTimestampFlow = MutableStateFlow(0L)

        var setPeriodicSyncEnabledValue: Boolean? = null
        var setLastSyncTimestampValue: Long? = null

        override val serverUrl = MutableStateFlow<String?>(null)
        override val username = MutableStateFlow<String?>(null)
        override val appPassword = MutableStateFlow<String?>(null)
        override val isConfigured = MutableStateFlow(false)
        override val periodicSyncEnabled = periodicSyncEnabledFlow
        override val lastSyncTimestamp = lastSyncTimestampFlow

        override fun setServerConfig(serverUrl: String, username: String, appPassword: String) {}
        override fun clearServerConfig() {}
        override fun setPeriodicSyncEnabled(periodicSyncEnabled: Boolean) {
            setPeriodicSyncEnabledValue = periodicSyncEnabled
        }
        override fun setLastSyncTimestamp(lastSyncTimestamp: Long) {
            setLastSyncTimestampValue = lastSyncTimestamp
        }
    }
}
