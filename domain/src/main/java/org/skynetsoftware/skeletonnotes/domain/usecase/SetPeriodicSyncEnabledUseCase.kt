package org.skynetsoftware.skeletonnotes.domain.usecase

import org.skynetsoftware.skeletonnotes.domain.repository.SettingsRepository

class SetPeriodicSyncEnabledUseCase(
    private val settingsRepository: SettingsRepository,
) {
    operator fun invoke(enabled: Boolean) = settingsRepository.setPeriodicSyncEnabled(enabled)
}
