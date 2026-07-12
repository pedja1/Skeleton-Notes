package org.skynetsoftware.skeletonnotes.nextcloud

import org.skynetsoftware.skeletonnotes.domain.sync.NextcloudSyncScheduler

/**
 * No-op implementation of [NextcloudSyncScheduler] for the lite flavor.
 */
class NoOpNextcloudSyncScheduler : NextcloudSyncScheduler {
    override fun reschedulePeriodicSync() {}

    override fun syncNow() {}

    override fun cancelPeriodicSync() {}
}
