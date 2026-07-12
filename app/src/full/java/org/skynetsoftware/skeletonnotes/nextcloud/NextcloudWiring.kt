package org.skynetsoftware.skeletonnotes.nextcloud

import android.app.Application
import org.skynetsoftware.skeletonnotes.data.di.DataDi
import org.skynetsoftware.skeletonnotes.domain.repository.NextcloudRepository
import org.skynetsoftware.skeletonnotes.domain.repository.SettingsRepository
import org.skynetsoftware.skeletonnotes.domain.sync.NextcloudSyncScheduler
import org.skynetsoftware.skeletonnotes.domain.usecase.GetSettingsUseCase
import org.skynetsoftware.skeletonnotes.nextcloud.di.NextcloudDi

/**
 * Wires production Nextcloud dependencies for the `full` flavor.
 */
object NextcloudWiring {
    fun wire(application: Application): Triple<NextcloudRepository, SettingsRepository, NextcloudSyncScheduler> {
        NextcloudDi.init(application)
        val ncRepo = NextcloudDi.createNextcloudRepository(DataDi.attachmentFileStorage)
        val settingsRepo = NextcloudDi.createSettingsRepository()
        val getSettingsUseCase = GetSettingsUseCase(settingsRepo, ncRepo)
        val scheduler = NextcloudDi.createSyncScheduler(getSettingsUseCase)
        return Triple(ncRepo, settingsRepo, scheduler)
    }
}
