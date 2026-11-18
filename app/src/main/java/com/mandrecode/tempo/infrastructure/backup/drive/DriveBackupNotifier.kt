package com.mandrecode.tempo.infrastructure.backup.drive

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.mandrecode.tempo.MainActivity
import com.mandrecode.tempo.R
import com.mandrecode.tempo.features.backup.domain.model.DriveBackupFailure
import com.mandrecode.tempo.infrastructure.notifications.NotificationChannelManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * Tells the user when Drive backup has stopped for a reason only they can fix (#306): a backup
 * that silently fails for weeks is the failure that matters. Tapping opens the Google Drive backup
 * page in Settings, where the status offers the fix.
 */
class DriveBackupNotifier
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        fun notifyFailure(failure: DriveBackupFailure) {
            if (!NotificationChannelManager.canPostNotifications(context)) return
            ensureChannel()
            val message =
                when (failure) {
                    DriveBackupFailure.AUTHORIZATION_NEEDED -> R.string.drive_backup_failure_authorization
                    DriveBackupFailure.STORAGE_FULL -> R.string.drive_backup_failure_storage
                    DriveBackupFailure.PASSPHRASE_UNAVAILABLE -> R.string.drive_backup_failure_passphrase
                    DriveBackupFailure.NETWORK -> R.string.drive_backup_failure_network
                    DriveBackupFailure.UNEXPECTED -> R.string.drive_backup_failure_unexpected
                }
            val notification =
                NotificationCompat
                    .Builder(context, CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_file_upload)
                    .setContentTitle(context.getString(R.string.drive_backup_notification_title))
                    .setContentText(context.getString(message))
                    .setStyle(NotificationCompat.BigTextStyle().bigText(context.getString(message)))
                    .setContentIntent(openDriveBackupPage())
                    .setAutoCancel(true)
                    .build()
            @Suppress("MissingPermission") // checked by canPostNotifications above
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        }

        /**
         * A quiet, ongoing "backing up" notification while a run is under way, in its own
         * low-importance channel so it never sounds or pops up. Not a foreground service: a run is
         * seconds of work, and WorkManager already keeps it alive.
         */
        fun showProgress() {
            if (!NotificationChannelManager.canPostNotifications(context)) return
            ensureProgressChannel()
            val notification =
                NotificationCompat
                    .Builder(context, PROGRESS_CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_file_upload)
                    .setContentTitle(context.getString(R.string.drive_backup_progress_title))
                    .setProgress(0, 0, true)
                    .setOngoing(true)
                    .setOnlyAlertOnce(true)
                    .setSilent(true)
                    .setPriority(NotificationCompat.PRIORITY_LOW)
                    .setCategory(NotificationCompat.CATEGORY_PROGRESS)
                    .setContentIntent(openDriveBackupPage())
                    .build()
            @Suppress("MissingPermission") // checked by canPostNotifications above
            NotificationManagerCompat.from(context).notify(PROGRESS_NOTIFICATION_ID, notification)
        }

        /** Both notifications open the Google Drive backup page, where the status and its fix are. */
        private fun openDriveBackupPage(): PendingIntent =
            PendingIntent.getActivity(
                context,
                REQUEST_CODE,
                Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    putExtra(EXTRA_OPEN_SETTINGS, true)
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

        fun hideProgress() {
            NotificationManagerCompat.from(context).cancel(PROGRESS_NOTIFICATION_ID)
        }

        /** Withdraws the notice once backups work again. */
        fun clear() {
            NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
        }

        private fun ensureChannel() {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    context.getString(R.string.drive_backup_notification_channel_name),
                    NotificationManager.IMPORTANCE_DEFAULT,
                ).apply { description = context.getString(R.string.drive_backup_notification_channel_description) },
            )
        }

        private fun ensureProgressChannel() {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(
                NotificationChannel(
                    PROGRESS_CHANNEL_ID,
                    context.getString(R.string.drive_backup_progress_channel_name),
                    NotificationManager.IMPORTANCE_LOW,
                ).apply { description = context.getString(R.string.drive_backup_progress_channel_description) },
            )
        }

        companion object {
            const val CHANNEL_ID = "drive_backup_channel"
            const val PROGRESS_CHANNEL_ID = "drive_backup_progress_channel"
            const val EXTRA_OPEN_SETTINGS = "extra_open_settings"
            private const val NOTIFICATION_ID = 0x0D51
            private const val PROGRESS_NOTIFICATION_ID = 0x0D52
            private const val REQUEST_CODE = 0x0D51
        }
    }
