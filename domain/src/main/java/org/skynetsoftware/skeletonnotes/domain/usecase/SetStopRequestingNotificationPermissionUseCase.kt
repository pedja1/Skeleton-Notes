package org.skynetsoftware.skeletonnotes.domain.usecase

import org.skynetsoftware.skeletonnotes.domain.repository.SettingsRepository

class SetStopRequestingNotificationPermissionUseCase(
    private val settingsRepository: SettingsRepository,
) {
    operator fun invoke() = settingsRepository.setStopRequestingNotificationPermission()
}
