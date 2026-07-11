package org.skynetsoftware.skeletonnotes.domain.model.nextcloud

/**
 * Result of a single Nextcloud Login Flow v2 poll request.
 *
 * The poll endpoint is polled repeatedly until the user completes the
 * browser-based login. Every response that is not the final one-time
 * success (including transient network errors and reverse-proxy redirects)
 * is reported as [Pending] so the caller keeps polling.
 */
sealed interface NextcloudPollStatus {
    /**
     * Login completed. Credentials have been persisted internally and the
     * (password-free) [info] describes the established connection.
     */
    data class Authenticated(
        val info: NextcloudConnectionInfo,
    ) : NextcloudPollStatus

    /**
     * Login not completed yet; the caller should poll again after a delay.
     */
    data object Pending : NextcloudPollStatus
}
