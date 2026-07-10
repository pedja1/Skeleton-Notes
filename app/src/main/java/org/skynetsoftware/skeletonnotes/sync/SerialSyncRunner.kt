package org.skynetsoftware.skeletonnotes.sync

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch

/**
 * Runs a suspending [sync] action while guaranteeing that at most one execution is ever in
 * flight.
 *
 * When [start] is called while a previous run is still active, that run is cancelled and
 * awaited (via [Job.cancelAndJoin]) before the new one begins, so two runs never overlap and
 * the new run observes the latest state. Cancelling the previous run only takes effect if
 * [sync] is cooperative (checks for cancellation / rethrows [kotlin.coroutines.cancellation.CancellationException]).
 *
 * All calls are expected to happen on a single thread (the owning `JobService`'s main thread),
 * so [currentJob] is accessed without additional synchronization and is never mutated from the
 * coroutine itself.
 *
 * @param scope the scope the sync runs in; its lifetime is owned by the caller.
 * @param sync the suspending sync action to execute.
 */
class SerialSyncRunner(
    private val scope: CoroutineScope,
    private val sync: suspend () -> Unit,
) {
    private var currentJob: Job? = null

    /**
     * Starts a new [sync] run, first preempting and awaiting any in-flight run.
     *
     * @param onComplete invoked after [sync] finishes normally; not invoked if the run is
     * cancelled (by [stop] or by a subsequent [start]).
     */
    fun start(onComplete: () -> Unit) {
        val previous = currentJob
        currentJob =
            scope.launch {
                previous?.cancelAndJoin()
                sync()
                onComplete()
            }
    }

    /** Cancels the current run, if any. Safe to call when nothing is running. */
    fun stop() {
        currentJob?.cancel()
    }
}
