package org.skynetsoftware.skeletonnotes.settings

/**
 * One-shot events emitted by [SettingsViewModel] to drive the Nextcloud login
 * side effects that must fire exactly once (unlike re-emitted state).
 */
sealed interface NextcloudAuthEvent {
    /**
     * Open the Login Flow v2 [url] in a Custom Tab.
     */
    data class LaunchAuthUrl(
        val url: String,
    ) : NextcloudAuthEvent

    /**
     * Login completed; the activity should come to the foreground to dismiss
     * the Custom Tab.
     */
    data object LoginSucceeded : NextcloudAuthEvent
}
