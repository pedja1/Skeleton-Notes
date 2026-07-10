package org.skynetsoftware.skeletonnotes.domain.usecase

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import org.skynetsoftware.skeletonnotes.domain.model.Settings
import org.skynetsoftware.skeletonnotes.domain.repository.NextcloudRepository
import org.skynetsoftware.skeletonnotes.domain.repository.SettingsRepository

class GetSettingsUseCase(
    private val settingsRepository: SettingsRepository,
    private val nextcloudRepository: NextcloudRepository,
) {
    operator fun invoke(): Flow<Settings> =
        combine(
            combine(
                settingsRepository.nextcloudPeriodicSync,
                settingsRepository.nextcloudLastSyncTimestamp,
                nextcloudRepository.connectionInfo(),
            ) { periodicSync, lastSync, connectionInfo -> Triple(periodicSync, lastSync, connectionInfo) },
            settingsRepository.nextcloudSyncIntervalMinutes,
            settingsRepository.nextcloudSyncOnlyOnUnmetered,
        ) { (periodicSync, lastSync, connectionInfo), intervalMinutes, onlyOnUnmetered ->
            Settings(
                nextcloudPeriodicSyncEnabled = periodicSync,
                nextcloudLastSyncTimestamp = lastSync,
                nextcloudConnectionInfo = connectionInfo,
                nextcloudSyncIntervalMinutes = intervalMinutes,
                nextcloudSyncOnlyOnUnmetered = onlyOnUnmetered,
            )
        }
}
