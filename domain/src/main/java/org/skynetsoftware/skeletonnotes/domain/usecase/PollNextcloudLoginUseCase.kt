package org.skynetsoftware.skeletonnotes.domain.usecase

import org.skynetsoftware.skeletonnotes.domain.model.nextcloud.NextcloudPollStatus
import org.skynetsoftware.skeletonnotes.domain.repository.NextcloudRepository

/**
 * Polls the Nextcloud server to check if the user has completed the
 * browser-based Login Flow v2 authentication.
 */
class PollNextcloudLoginUseCase(
    private val nextcloudRepository: NextcloudRepository,
) {
    /**
     * Makes a single poll for login completion. On
     * [NextcloudPollStatus.Authenticated] credentials are persisted internally;
     * [NextcloudPollStatus.Pending] means the caller should poll again after a delay.
     *
     * @param token the poll token from the initiate-login response
     * @param endpoint the poll endpoint from the initiate-login response
     */
    suspend operator fun invoke(
        token: String,
        endpoint: String,
    ): NextcloudPollStatus {
        return nextcloudRepository.pollLogin(token, endpoint)
    }
}
