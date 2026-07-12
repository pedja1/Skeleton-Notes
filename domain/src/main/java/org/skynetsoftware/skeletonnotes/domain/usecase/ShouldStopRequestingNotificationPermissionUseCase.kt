package org.skynetsoftware.skeletonnotes.domain.usecase

import org.skynetsoftware.skeletonnotes.domain.repository.SettingsRepository

class ShouldStopRequestingNotificationPermissionUseCase(
    private val settingsRepository: SettingsRepository,
) {
    operator fun invoke(): Boolean = settingsRepository.shouldStopRequestingNotificationPermission()
}
