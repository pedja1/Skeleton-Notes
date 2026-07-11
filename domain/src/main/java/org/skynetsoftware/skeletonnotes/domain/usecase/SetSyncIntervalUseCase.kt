package org.skynetsoftware.skeletonnotes.domain.usecase

import org.skynetsoftware.skeletonnotes.domain.repository.SettingsRepository

/** Persists the Nextcloud periodic sync interval (in minutes). */
class SetSyncIntervalUseCase(
    private val settingsRepository: SettingsRepository,
) {
    operator fun invoke(minutes: Long) = settingsRepository.setSyncIntervalMinutes(minutes)
}
