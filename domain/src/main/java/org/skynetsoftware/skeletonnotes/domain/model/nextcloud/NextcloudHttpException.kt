package org.skynetsoftware.skeletonnotes.domain.model.nextcloud

/**
 * Raised when a Nextcloud WebDAV request returns a non-success HTTP status.
 *
 * Carries the [code] so callers can classify the failure (for example, 401/403 as an expired login
 * versus 5xx as a server error). Lives in the domain module so both the data layer (which throws it)
 * and the domain layer (which classifies it) can reference it.
 */
class NextcloudHttpException(
    val code: Int,
) : Exception("Server returned $code")
