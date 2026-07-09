package org.skynetsoftware.skeletonnotes.domain.model.nextcloud

/**
 * Data model containing nextcloud connection metadata.
 */
data class NextcloudConnectionInfo(
    val serverUrl: String,
    val username: String,
)
