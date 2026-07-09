package org.skynetsoftware.skeletonnotes.domain.model.nextcloud

/**
 * Response from the initial POST to Nextcloud's Login Flow v2 endpoint.
 * Contains the data needed to start the login process and poll for completion.
 */
data class NextcloudInitiateLoginResult(
    val token: String,
    val endpoint: String,
    val loginUrl: String,
)
