package org.skynetsoftware.skeletonnotes.domain.usecase

import org.skynetsoftware.skeletonnotes.domain.model.Result
import org.skynetsoftware.skeletonnotes.domain.model.nextcloud.NextcloudInitiateLoginResult
import org.skynetsoftware.skeletonnotes.domain.repository.NextcloudRepository

/**
 * Initiates the Nextcloud Login Flow v2 by posting to the server and returning
 * the data needed to complete the browser-based authentication.
 */
class InitiateNextcloudLoginUseCase(
    private val nextcloudRepository: NextcloudRepository,
) {
    /**
     * Initiates login for the given [serverUrl].
     *
     * @param serverUrl the Nextcloud server URL (e.g. "https://cloud.example.com")
     * @return [Result] containing [NextcloudInitiateLoginResult] with token, endpoint, and login URL
     */
    suspend operator fun invoke(serverUrl: String): Result<NextcloudInitiateLoginResult> =
        nextcloudRepository.initiateLogin(serverUrl)
}
