package org.skynetsoftware.skeletonnotes.sync

import android.app.job.JobParameters
import android.app.job.JobService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.skynetsoftware.skeletonnotes.di.AppDi

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

    override fun onStartJob(params: JobParameters): Boolean {
        runner.start { jobFinished(params, false) }
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean {
        runner.stop()
        return true
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }
}
