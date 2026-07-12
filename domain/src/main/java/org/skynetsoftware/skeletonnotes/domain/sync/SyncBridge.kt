package org.skynetsoftware.skeletonnotes.domain.sync

/**
 * Bridge used by the Nextcloud sync [android.app.job.JobService] to invoke the sync
 * action provided by the application layer. Set during DI initialization.
 */
object SyncBridge {
    var action: (suspend () -> Unit)? = null
}
