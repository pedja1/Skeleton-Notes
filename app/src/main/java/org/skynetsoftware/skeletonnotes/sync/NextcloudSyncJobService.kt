package org.skynetsoftware.skeletonnotes.sync

import android.app.job.JobParameters
import android.app.job.JobService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.skynetsoftware.skeletonnotes.di.AppDi
import org.skynetsoftware.skeletonnotes.domain.model.Result
import org.skynetsoftware.skeletonnotes.domain.usecase.SyncResult

/**
 * [JobService] that executes a full Nextcloud sync cycle.
 *
 * The sync use case already handles the not-configured case (returns [SyncResult.Error]) and
 * updates the last-sync timestamp on success, so no guarding is needed here.
 *
 * Coroutine orchestration is delegated to [SerialSyncRunner], which guarantees at most one sync
 * runs at a time: a new job preempts and awaits any in-flight run before starting (see
 * [onStartJob]). The service-scoped [scope] lives for the whole service lifetime and is
 * cancelled in [onDestroy]; individual runs are cancelled in [onStopJob]. All [JobService]
 * callbacks run on the main thread, so no additional synchronization is required.
 */
class NextcloudSyncJobService : JobService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val runner = SerialSyncRunner(scope) { AppDi.syncNotesWithNextcloudUseCase() }
    private val syncNotifier by lazy { SyncNotifier(applicationContext) }

    /**
     * The [JobParameters] of the in-flight run. Needed because a preempted run's `onComplete`
     * never fires (see [SerialSyncRunner.start]), so its job must be finished explicitly when a
     * new job takes over, and [onStopJob] must only cancel the run belonging to its own params.
     * Written on the main thread (all [JobService] callbacks) and cleared from the completion
     * callback on an IO thread, hence volatile.
     */
    @Volatile
    private var currentParams: JobParameters? = null

    override fun onStartJob(params: JobParameters): Boolean {
        currentParams?.let { preempted ->
            // The runner is about to cancel the preempted run, whose onComplete (the only other
            // jobFinished call) will never fire; without this, JobScheduler keeps the preempted
            // job running (holding its wakelock) until the execution limit.
            jobFinished(preempted, false)
        }
        currentParams = params
        runner.start { result ->
            handleSyncResult(result)
            if (currentParams === params) {
                currentParams = null
            }
            jobFinished(params, false)
        }
        return true
    }

    /**
     * Surfaces the sync outcome: a failure posts the "sync failed" notification (the reason is also
     * persisted by the use case for the Settings screen), while a success or conflicts-only result
     * clears any stale notification. Cancelled/preempted runs never reach here (see [SerialSyncRunner]).
     */
    private fun handleSyncResult(result: Result<SyncResult>) {
        val syncResult = (result as? Result.Success)?.data ?: return
        when (syncResult) {
            is SyncResult.Error -> syncNotifier.notifyFailure(syncResult.reason)
            SyncResult.Success, is SyncResult.HasConflicts -> syncNotifier.clear()
        }
    }

    override fun onStopJob(params: JobParameters): Boolean {
        // Only stop the runner when the stopped job is the one currently running: a late stop for
        // an already-preempted job must not cancel the newer run that replaced it.
        if (currentParams === params) {
            runner.stop()
            currentParams = null
            return true
        }
        return false
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }
}
