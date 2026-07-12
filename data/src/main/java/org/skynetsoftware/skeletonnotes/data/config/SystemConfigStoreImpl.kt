package org.skynetsoftware.skeletonnotes.data.config

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences

/**
 * Default [SystemConfigStore] implementation backed by [SharedPreferences].
 */
@SuppressLint("UseKtx")
internal class SystemConfigStoreImpl(
    private val prefs: SharedPreferences,
) : SystemConfigStore {
    companion object {
        private const val PREFS_NAME = "system"
        private const val KEY_STOP_REQUESTING_NOTIFICATION_PERMISSION = "stopRequestingNotificationPermission"

        fun from(context: Context): SystemConfigStore =
            SystemConfigStoreImpl(context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE))
    }

    /**
     * @see [SystemConfigStore.shouldStopRequestingNotificationPermission]
     * Reads the value from shared preferences
     */
    override fun shouldStopRequestingNotificationPermission() =
        prefs.getBoolean(KEY_STOP_REQUESTING_NOTIFICATION_PERMISSION, false)

    /**
     * @see [SystemConfigStore.setStopRequestingNotificationPermission]
     * Stores the value in shared preferences
     */
    override fun setStopRequestingNotificationPermission() =
        prefs.edit().putBoolean(KEY_STOP_REQUESTING_NOTIFICATION_PERMISSION, true).apply()
}
