package org.skynetsoftware.skeletonnotes.sync

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import org.skynetsoftware.skeletonnotes.R
import org.skynetsoftware.skeletonnotes.domain.usecase.SyncErrorReason
import org.skynetsoftware.skeletonnotes.settings.SettingsActivity

/**
 * Posts (and clears) the "sync failed" system notification so a background sync failure is visible
 * without opening the app. A single fixed-id notification is reused: a still-failing sync updates it
 * silently ([NotificationCompat.Builder.setOnlyAlertOnce]) and the next successful sync clears it.
 */
class SyncNotifier(
    private val context: Context,
) {
    private val notificationManager = NotificationManagerCompat.from(context)

    /** Shows or refreshes the failure notification for [reason]. No-op if notifications are disabled. */
    @SuppressLint("MissingPermission") // Guarded by areNotificationsEnabled(); notify() is a no-op otherwise.
    fun notifyFailure(reason: SyncErrorReason) {
        ensureChannel()
        if (!notificationManager.areNotificationsEnabled()) return

        val contentIntent =
            PendingIntent.getActivity(
                context,
                0,
                Intent(context, SettingsActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_IMMUTABLE,
            )

        val notification =
            NotificationCompat
                .Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_sync_error)
                .setContentTitle(context.getString(R.string.sync_notification_title))
                .setContentText(context.getString(reason.messageRes()))
                .setContentIntent(contentIntent)
                .setAutoCancel(true)
                .setOnlyAlertOnce(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .build()

        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    /** Dismisses the failure notification, e.g. after a later sync succeeds. */
    fun clear() {
        notificationManager.cancel(NOTIFICATION_ID)
    }

    private fun ensureChannel() {
        val channel =
            NotificationChannelCompat
                .Builder(CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_DEFAULT)
                .setName(context.getString(R.string.sync_notification_channel_name))
                .build()
        notificationManager.createNotificationChannel(channel)
    }

    private fun SyncErrorReason.messageRes(): Int =
        when (this) {
            SyncErrorReason.NO_CONNECTION -> R.string.sync_error_no_connection
            SyncErrorReason.AUTH_EXPIRED -> R.string.sync_error_auth_expired
            SyncErrorReason.SERVER_ERROR -> R.string.sync_error_server
            SyncErrorReason.UNKNOWN -> R.string.sync_error_unknown
        }

    companion object {
        private const val CHANNEL_ID = "sync"
        private const val NOTIFICATION_ID = 2001
    }
}
