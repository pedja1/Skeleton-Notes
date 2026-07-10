package org.skynetsoftware.skeletonnotes.domain.model

import org.skynetsoftware.skeletonnotes.domain.model.nextcloud.NextcloudConnectionInfo

data class Settings(
    val nextcloudPeriodicSyncEnabled: Boolean,
    val nextcloudLastSyncTimestamp: Long,
    val nextcloudConnectionInfo: NextcloudConnectionInfo?,
    /** Sync interval in minutes. */
    val nextcloudSyncIntervalMinutes: Long,
    /** When true the periodic job only runs on unmetered (usually Wi-Fi) connections. */
    val nextcloudSyncOnlyOnUnmetered: Boolean,
)
