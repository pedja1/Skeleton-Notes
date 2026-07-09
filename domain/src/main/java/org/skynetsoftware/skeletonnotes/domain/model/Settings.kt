package org.skynetsoftware.skeletonnotes.domain.model

import org.skynetsoftware.skeletonnotes.domain.model.nextcloud.NextcloudConnectionInfo

data class Settings(
    val nextcloudPeriodicSyncEnabled: Boolean,
    val nextcloudLastSyncTimestamp: Long,
    val nextcloudConnectionInfo: NextcloudConnectionInfo?,
)
