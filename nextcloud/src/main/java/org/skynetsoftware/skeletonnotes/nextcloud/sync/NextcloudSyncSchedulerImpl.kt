package org.skynetsoftware.skeletonnotes.nextcloud.sync

import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.ComponentName
import android.content.Context
import android.os.Build
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.skynetsoftware.skeletonnotes.domain.sync.NextcloudSyncScheduler
import org.skynetsoftware.skeletonnotes.domain.usecase.GetSettingsUseCase

/**
 * Production [NextcloudSyncScheduler] that submits jobs to [JobScheduler].
 *
 * [reschedulePeriodicSync] is idempotent: it reads the current settings snapshot, then either
 * schedules or cancels the periodic job so the scheduler state always matches the persisted
 * settings.
 */
class NextcloudSyncSchedulerImpl(
    context: Context,
    private val getSettings: GetSettingsUseCase,
) : NextcloudSyncScheduler {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val jobScheduler = context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler
    private val serviceComponent = ComponentName(context, NextcloudSyncJobService::class.java)

    companion object {
        /** Job ID for the repeating periodic sync. Must be unique within the app. */
        const val PERIODIC_JOB_ID = 1001

        /** Job ID for the user-triggered one-off sync. */
        const val ONE_OFF_JOB_ID = 1002

        /** Android's minimum enforced job period (15 minutes). */
        private const val MIN_PERIOD_MS = 15L * 60 * 1000
    }

    override fun reschedulePeriodicSync() {
        scope.launch {
            val settings = getSettings().first()
            if (settings.nextcloudPeriodicSyncEnabled && settings.nextcloudConnectionInfo != null) {
                val networkType =
                    if (settings.nextcloudSyncOnlyOnUnmetered) {
                        JobInfo.NETWORK_TYPE_UNMETERED
                    } else {
                        JobInfo.NETWORK_TYPE_ANY
                    }
                val periodMs = (settings.nextcloudSyncIntervalMinutes * 60 * 1000).coerceAtLeast(MIN_PERIOD_MS)
                val jobInfo =
                    JobInfo
                        .Builder(PERIODIC_JOB_ID, serviceComponent)
                        .setPeriodic(periodMs)
                        .setRequiredNetworkType(networkType)
                        .setPersisted(true)
                        .build()
                jobScheduler.schedule(jobInfo)
            } else {
                jobScheduler.cancel(PERIODIC_JOB_ID)
            }
        }
    }

    override fun syncNow() {
        val builder =
            JobInfo
                .Builder(ONE_OFF_JOB_ID, serviceComponent)
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setExpedited(true)
        } else {
            builder.setOverrideDeadline(0)
        }
        jobScheduler.schedule(builder.build())
    }

    override fun cancelPeriodicSync() {
        jobScheduler.cancel(PERIODIC_JOB_ID)
    }
}
