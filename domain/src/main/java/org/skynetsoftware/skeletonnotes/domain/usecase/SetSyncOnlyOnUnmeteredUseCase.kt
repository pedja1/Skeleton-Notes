package org.skynetsoftware.skeletonnotes.domain.usecase

import org.skynetsoftware.skeletonnotes.domain.repository.SettingsRepository

/** Persists whether the periodic sync job should require an unmetered network connection. */
class SetSyncOnlyOnUnmeteredUseCase(private val settingsRepository: SettingsRepository) {
    operator fun invoke(onlyOnUnmetered: Boolean) = settingsRepository.setSyncOnlyOnUnmetered(onlyOnUnmetered)
}
