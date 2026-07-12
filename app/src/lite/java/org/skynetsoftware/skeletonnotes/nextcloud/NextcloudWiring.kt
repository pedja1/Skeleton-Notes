package org.skynetsoftware.skeletonnotes.nextcloud

import android.app.Application
import org.skynetsoftware.skeletonnotes.domain.repository.NextcloudRepository
import org.skynetsoftware.skeletonnotes.domain.repository.SettingsRepository
import org.skynetsoftware.skeletonnotes.domain.sync.NextcloudSyncScheduler

/**
 * Wires no-op Nextcloud dependencies for the `lite` flavor.
 */
object NextcloudWiring {
    fun wire(application: Application): Triple<NextcloudRepository, SettingsRepository, NextcloudSyncScheduler> =
        Triple(NoOpNextcloudRepository(), NoOpSettingsRepository(), NoOpNextcloudSyncScheduler())
}
