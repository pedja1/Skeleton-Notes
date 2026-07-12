package org.skynetsoftware.skeletonnotes.domain.sync

/**
 * Manages scheduling of Nextcloud sync jobs via the Android [android.app.job.JobScheduler].
 *
 * Implementations are expected to be idempotent: calling [reschedulePeriodicSync] multiple times
 * converges to the correct scheduler state without leaking jobs.
 */
interface NextcloudSyncScheduler {
    /**
     * Reads the current settings and either schedules or cancels the periodic sync job
     * accordingly. Call whenever a setting that affects the job changes (toggle, interval,
     * network preference) or on app launch (to survive app-update job clearing).
     */
    fun reschedulePeriodicSync()

    /** Enqueues a one-off immediate sync job on any network. */
    fun syncNow()

    /** Cancels the periodic sync job if one is pending. */
    fun cancelPeriodicSync()
}
