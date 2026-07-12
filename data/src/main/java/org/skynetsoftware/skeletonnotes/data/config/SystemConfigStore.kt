package org.skynetsoftware.skeletonnotes.data.config

/**
 * Interface for persisting application configuration/preferences.
 */
internal interface SystemConfigStore {
    /**
     * @return if true, notification permission should not be requested again
     */
    fun shouldStopRequestingNotificationPermission(): Boolean

    /**
     * Persist value that notification permission should not be requested again
     */
    fun setStopRequestingNotificationPermission()
}
