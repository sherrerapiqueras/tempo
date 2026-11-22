package com.mandrecode.tempo.infrastructure.liveactivity

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.mandrecode.tempo.MainActivity
import com.mandrecode.tempo.R
import com.mandrecode.tempo.core.data.preferences.ActiveLiveActivityPreferences
import com.mandrecode.tempo.features.routines.domain.model.HabitChain
import com.mandrecode.tempo.infrastructure.notifications.NotificationChannelManager
import com.mandrecode.tempo.infrastructure.notifications.NotificationSyncManager
import com.mandrecode.tempo.infrastructure.notifications.RequestCodeGenerator
import com.mandrecode.tempo.infrastructure.reminders.receivers.CompleteHabitReceiver
import com.mandrecode.tempo.infrastructure.reminders.receivers.DismissLiveActivityReceiver
import com.mandrecode.tempo.infrastructure.reminders.receivers.HabitReminderReceiver
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Clock

/**
 * Manager for creating and updating Live Activities (ongoing notifications) for habit chains.
 * Uses Android 16+ ProgressStyle to denote "phases" (habits) in the chain.
 */
@Singleton
class HabitChainLiveActivityManager
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val notificationSyncManager: NotificationSyncManager,
        private val activeLiveActivityPreferences: ActiveLiveActivityPreferences,
        private val clock: Clock,
    ) {
        private val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        /** Chains with a live activity currently posted, mapped to the date their progress is for. */
        private val activeChains =
            java.util.concurrent
                .ConcurrentHashMap<Long, LocalDate>()
                .apply { putAll(activeLiveActivityPreferences.getActiveChains()) }

        init {
            NotificationChannelManager.ensureLiveActivityChannel(context, notificationManager)
        }

        /**
         * Start or update a live activity for a habit chain.
         * Uses ProgressStyle on API 36+ to show habits as milestones/points.
         */
        fun updateLiveActivity(
            chain: HabitChain,
            completedCount: Int,
            totalCount: Int,
            currentHabitId: Long? = null,
            currentHabitTitle: String? = null,
            fromNotification: Boolean = false,
            scheduledDate: LocalDate? = null,
        ) {
            if (totalCount == 0) return

            // Determine whether the live activity should be silently dismissed:
            // 1. All habits unchecked and the chain's reminder is still in the future,
            //    AND the user did not explicitly trigger this from a notification action
            //    — the scheduled alarm will re-trigger the reminder later. We must honor
            //    explicit "Start chain" taps even when periodicReminder has already been
            //    advanced to the next future occurrence by HabitReminderReceiver.
            // 2. All habits completed from within the app (not from a notification action).
            val allUncheckedWithFutureReminder =
                !fromNotification &&
                    completedCount == 0 &&
                    chain.periodicReminder?.let {
                        it > clock.now().toLocalDateTime(TimeZone.currentSystemDefault())
                    } ?: false
            val allCompletedFromApp = completedCount == totalCount && !fromNotification

            if (allUncheckedWithFutureReminder || allCompletedFromApp) {
                dismissLiveActivity(chain.id)
                if (allCompletedFromApp) {
                    notificationSyncManager.dismissHabitChainNotification(chain.id)
                }
                return
            }

            if (!NotificationChannelManager.canPostNotifications(context)) {
                dismissLiveActivity(chain.id)
            } else {
                // Record the date this live activity belongs to so recovery can tell a
                // still-current session from a leftover of a previous day.
                val activeDate = scheduledDate ?: today()
                if (activeChains.put(chain.id, activeDate) != activeDate) {
                    activeLiveActivityPreferences.setActiveChain(chain.id, activeDate)
                }

                // The live activity supersedes the chain reminder notification — dismiss it
                // regardless of whether the live activity was started from the notification
                // action or from in-app habit completion.
                notificationSyncManager.dismissHabitChainNotification(chain.id)

                val contentIntent =
                    Intent(context, MainActivity::class.java).apply {
                        putExtra(HabitReminderReceiver.EXTRA_HABIT_CHAIN_ID, chain.id)
                        putExtra(HabitReminderReceiver.EXTRA_OPEN_ROUTINES, true)
                        scheduledDate?.let {
                            putExtra(HabitReminderReceiver.EXTRA_SCHEDULED_DATE, it.toString())
                        }
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    }

                val pendingIntent =
                    PendingIntent.getActivity(
                        context,
                        RequestCodeGenerator.forLiveActivity(chain.id),
                        contentIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                    )

                val progress = (completedCount.toFloat() / totalCount.toFloat() * 100).toInt()
                val allCompleted = completedCount == totalCount

                // Create "Complete current" action
                val completeAction =
                    if (!allCompleted && currentHabitId != null) {
                        val intent =
                            Intent(context, CompleteHabitReceiver::class.java).apply {
                                putExtra(CompleteHabitReceiver.EXTRA_HABIT_ID, currentHabitId)
                                putExtra(CompleteHabitReceiver.EXTRA_HABIT_CHAIN_ID, chain.id)
                                scheduledDate?.let {
                                    putExtra(CompleteHabitReceiver.EXTRA_SCHEDULED_DATE, it.toString())
                                }
                            }
                        val pendingActionIntent =
                            PendingIntent.getBroadcast(
                                context,
                                RequestCodeGenerator.forHabit(currentHabitId),
                                intent,
                                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                            )
                        NotificationCompat.Action
                            .Builder(
                                R.drawable.ic_check_circle, // Assuming this exists or using a generic one
                                context.getString(R.string.habit_chain_complete_current),
                                pendingActionIntent,
                            ).build()
                    } else {
                        null
                    }

                val contentText =
                    when {
                        allCompleted -> context.getString(R.string.habit_chain_all_done)
                        currentHabitTitle != null ->
                            context.getString(
                                R.string.habit_chain_next_habit,
                                currentHabitTitle,
                            )

                        else ->
                            context.resources.getQuantityString(
                                R.plurals.habit_chain_progress_text,
                                totalCount,
                                completedCount,
                                totalCount,
                            )
                    }

                // Build the base notification using NotificationCompat for backward compatibility
                val builder =
                    NotificationCompat
                        .Builder(context, NotificationChannelManager.HABIT_CHAIN_LIVE_ACTIVITY_CHANNEL_ID)
                        .setSmallIcon(R.drawable.ic_track_changes)
                        .setContentTitle(
                            if (allCompleted) {
                                context.getString(R.string.habit_chain_completed_title, chain.title)
                            } else {
                                context.getString(R.string.habit_chain_progress_title, chain.title)
                            },
                        ).setContentText(contentText)
                        .setSubText(
                            context.resources.getQuantityString(
                                R.plurals.habit_chain_progress_text,
                                totalCount,
                                completedCount,
                                totalCount,
                            ),
                        ).setOngoing(!allCompleted)
                        .setAutoCancel(allCompleted)
                        .setContentIntent(pendingIntent)
                        .setDeleteIntent(dismissIntent(chain.id))
                        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                        .setCategory(NotificationCompat.CATEGORY_PROGRESS)
                        .setRequestPromotedOngoing(!allCompleted)

                if (completeAction != null) {
                    builder.addAction(completeAction)
                }

                // Apply ProgressStyle for "Phases" on Android 16+
                if (Build.VERSION.SDK_INT >= 36) {
                    val progressStyle = Notification.ProgressStyle()
                    progressStyle.setProgress(progress)

                    // Add a point for each habit in the chain to represent phases
                    for (i in 1..totalCount) {
                        val pointProgress = (i.toFloat() / totalCount.toFloat() * 100).toInt()
                        val point = Notification.ProgressStyle.Point(pointProgress)
                        progressStyle.addProgressPoint(point)
                    }

                    // Convert to platform builder to set the style
                    val platformBuilder = Notification.Builder.recoverBuilder(context, builder.build())
                    platformBuilder.setStyle(progressStyle)

                    // Set short critical text for status chips
                    if (!allCompleted) {
                        platformBuilder.setShortCriticalText("$completedCount/$totalCount")
                    }

                    notificationManager.notify(getNotificationId(chain.id), platformBuilder.build())
                } else {
                    // Fallback for older versions
                    builder.setProgress(100, progress, false)
                    builder.setStyle(NotificationCompat.BigTextStyle())

                    if (Build.VERSION.SDK_INT >= 35 && !allCompleted) {
                        builder.setShortCriticalText("$completedCount/$totalCount")
                    }

                    notificationManager.notify(getNotificationId(chain.id), builder.build())
                }

                if (allCompleted) {
                    activeChains.remove(chain.id)
                    activeLiveActivityPreferences.removeActiveChainId(chain.id)
                }
            }
        }

        fun dismissLiveActivity(chainId: Long) {
            activeChains.remove(chainId)
            activeLiveActivityPreferences.removeActiveChainId(chainId)
            notificationManager.cancel(getNotificationId(chainId))
        }

        fun hasActiveLiveActivity(chainId: Long): Boolean = activeChains.containsKey(chainId)

        private fun today(): LocalDate = clock.now().toLocalDateTime(TimeZone.currentSystemDefault()).date

        /**
         * Delete intent so a user swipe clears the persisted record. Unlike a missing notification,
         * this fires only on deliberate dismissal — never on reboot, force-stop, or an app-initiated
         * cancel — which is what keeps recovery from resurrecting a discarded live activity.
         */
        private fun dismissIntent(chainId: Long): PendingIntent {
            val intent =
                Intent(context, DismissLiveActivityReceiver::class.java).apply {
                    putExtra(DismissLiveActivityReceiver.EXTRA_HABIT_CHAIN_ID, chainId)
                }
            return PendingIntent.getBroadcast(
                context,
                RequestCodeGenerator.forLiveActivityDismiss(chainId),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }

        private fun getNotificationId(chainId: Long): Int = RequestCodeGenerator.forLiveActivity(chainId)
    }
