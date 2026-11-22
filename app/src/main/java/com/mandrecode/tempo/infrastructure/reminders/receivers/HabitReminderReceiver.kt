package com.mandrecode.tempo.infrastructure.reminders.receivers

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.annotation.VisibleForTesting
import androidx.core.app.NotificationCompat
import com.mandrecode.tempo.MainActivity
import com.mandrecode.tempo.R
import com.mandrecode.tempo.core.di.IoDispatcher
import com.mandrecode.tempo.core.domain.model.VacationPeriod
import com.mandrecode.tempo.core.domain.repository.VacationModeRepository
import com.mandrecode.tempo.features.routines.domain.model.Habit
import com.mandrecode.tempo.features.routines.domain.model.HabitChain
import com.mandrecode.tempo.features.routines.domain.model.HabitType
import com.mandrecode.tempo.features.routines.domain.repository.HabitChainRepository
import com.mandrecode.tempo.features.routines.domain.repository.HabitRepository
import com.mandrecode.tempo.features.routines.domain.scheduler.HabitReminderScheduler
import com.mandrecode.tempo.features.routines.domain.util.HabitReminderDateUtil
import com.mandrecode.tempo.infrastructure.notifications.NotificationChannelManager
import com.mandrecode.tempo.infrastructure.notifications.NotificationSyncManager
import com.mandrecode.tempo.infrastructure.notifications.RequestCodeGenerator
import com.mandrecode.tempo.infrastructure.receivers.launchReceiverWork
import com.mandrecode.tempo.util.CompletionHistoryUtil
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import javax.inject.Inject
import kotlin.time.Clock

@AndroidEntryPoint
class HabitReminderReceiver : BroadcastReceiver() {
    @Inject
    lateinit var habitRepository: HabitRepository

    @Inject
    lateinit var habitChainRepository: HabitChainRepository

    @Inject
    lateinit var habitReminderScheduler: HabitReminderScheduler

    @Inject
    lateinit var vacationModeRepository: VacationModeRepository

    @Inject
    @IoDispatcher
    lateinit var ioDispatcher: CoroutineDispatcher

    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        val isChain = intent.getBooleanExtra(EXTRA_IS_CHAIN, false)

        launchReceiverWork(ioDispatcher, "HabitReminderReceiver") {
            if (isChain) {
                handleChainReminder(context, intent)
            } else {
                val habitId = intent.getLongExtra(EXTRA_HABIT_ID, -1L)
                if (habitId != -1L) {
                    val habit = habitRepository.getHabitById(habitId)
                    if (habit != null) {
                        val scheduledDate =
                            resolveScheduledDate(
                                intent.getStringExtra(EXTRA_SCHEDULED_DATE),
                                habit.reminderDate?.date
                                    ?: Clock.System.todayIn(TimeZone.currentSystemDefault()),
                            )
                        if (shouldShowHabitReminder(habit, scheduledDate, vacationModeRepository.periods.value)) {
                            showHabitNotification(
                                context,
                                habit.id,
                                habit.title,
                                habit.description,
                                scheduledDate,
                                habit.habitType,
                            )
                        }
                        rescheduleHabit(habit)
                    }
                }
            }
        }
    }

    private suspend fun handleChainReminder(
        context: Context,
        intent: Intent,
    ) {
        val chainId = intent.getLongExtra(EXTRA_HABIT_CHAIN_ID, -1L)
        if (chainId == -1L) return

        val habitChain = habitChainRepository.getHabitChainById(chainId) ?: return
        val fallbackDate =
            habitChain.periodicReminder?.date
                ?: Clock.System.todayIn(TimeZone.currentSystemDefault())
        val scheduledDate =
            resolveScheduledDate(
                intent.getStringExtra(EXTRA_SCHEDULED_DATE),
                fallbackDate,
            )
        val chainHabits = getChainHabits(habitChain.habitIds)
        if (shouldShowHabitChainReminder(chainHabits, scheduledDate, vacationModeRepository.periods.value)) {
            showHabitChainNotification(
                context,
                habitChain.id,
                habitChain.title,
                habitChain.description,
                scheduledDate,
            )
        }
        rescheduleHabitChain(habitChain)
    }

    @VisibleForTesting
    internal suspend fun getChainHabits(habitIds: List<Long>): List<Habit> =
        if (habitIds.isEmpty()) {
            emptyList()
        } else {
            habitRepository.getHabitsByIds(habitIds)
        }

    /**
     * Always runs, including while vacation mode is paused: a pause suppresses the *notification*
     * and never touches the alarm chain. Cancelling alarms instead would put the app on the hook
     * for restoring them — across process death, a reboot rebuilding alarms through
     * `RescheduleRemindersWorker`, and an exact-alarm permission that may have been revoked in the
     * meantime. Letting the chain keep advancing means reminders simply resume on their own.
     */
    private suspend fun rescheduleHabit(habit: Habit) {
        val nextReminderDate =
            HabitReminderDateUtil.advanceReminderIfNeeded(
                habit.reminderDate,
                habit.repeatDays,
            )
        if (nextReminderDate != null && nextReminderDate != habit.reminderDate) {
            val updatedHabit = habit.copy(reminderDate = nextReminderDate)
            habitRepository.updateHabit(updatedHabit)
            habitReminderScheduler.scheduleHabit(updatedHabit)
        }
    }

    private suspend fun rescheduleHabitChain(habitChain: HabitChain) {
        val nextReminderDate =
            HabitReminderDateUtil.advanceReminderIfNeeded(
                habitChain.periodicReminder,
                habitChain.repeatDays,
            )
        if (nextReminderDate != null && nextReminderDate != habitChain.periodicReminder) {
            val updatedChain = habitChain.copy(periodicReminder = nextReminderDate)
            habitChainRepository.updateHabitChain(updatedChain)
            habitReminderScheduler.scheduleHabitChain(updatedChain)
        }
    }

    private fun showHabitNotification(
        context: Context,
        habitId: Long,
        title: String,
        description: String,
        scheduledDate: LocalDate,
        habitType: HabitType = HabitType.BUILD,
    ) {
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        NotificationChannelManager.ensureHabitReminderChannel(context, notificationManager)

        if (!NotificationChannelManager.canPostNotifications(context)) {
            return
        }

        val id = RequestCodeGenerator.forHabit(habitId)

        val contentIntent =
            Intent(context, MainActivity::class.java).apply {
                action = ACTION_OPEN_HABIT
                putExtra(EXTRA_HABIT_ID, habitId)
                putExtra(EXTRA_OPEN_ROUTINES, true)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }

        val pendingIntent =
            PendingIntent.getActivity(
                context,
                id,
                contentIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

        val markAsCompleteIntent =
            Intent(context, MarkHabitAsCompletedReceiver::class.java).apply {
                action = ACTION_MARK_HABIT_COMPLETE
                putExtra(MarkHabitAsCompletedReceiver.EXTRA_HABIT_ID, habitId)
                putExtra(MarkHabitAsCompletedReceiver.EXTRA_SCHEDULED_DATE, scheduledDate.toString())
            }

        val markAsCompletePendingIntent =
            PendingIntent.getBroadcast(
                context,
                id,
                markAsCompleteIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

        val contentText = buildHabitNotificationContentText(context, title, description, habitType)

        val actionLabel = buildHabitNotificationActionLabel(context, habitType)

        val notification =
            NotificationCompat
                .Builder(context, NotificationChannelManager.HABIT_REMINDER_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_track_changes)
                .setContentTitle(title)
                .setContentText(contentText)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .addAction(
                    R.drawable.ic_check_circle,
                    actionLabel,
                    markAsCompletePendingIntent,
                ).build()

        notificationManager.notify(NotificationSyncManager.NOTIFICATION_TAG_HABIT, id, notification)
    }

    private fun showHabitChainNotification(
        context: Context,
        chainId: Long,
        title: String,
        description: String,
        scheduledDate: LocalDate,
    ) {
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        NotificationChannelManager.ensureHabitReminderChannel(context, notificationManager)

        if (!NotificationChannelManager.canPostNotifications(context)) {
            return
        }

        val id = RequestCodeGenerator.forHabitChain(chainId)

        val contentIntent =
            Intent(context, MainActivity::class.java).apply {
                action = ACTION_OPEN_CHAIN
                putExtra(EXTRA_HABIT_CHAIN_ID, chainId)
                putExtra(EXTRA_OPEN_ROUTINES, true)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }

        val pendingIntent =
            PendingIntent.getActivity(
                context,
                id,
                contentIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

        val startChainIntent =
            Intent(context, StartHabitChainReceiver::class.java).apply {
                action = ACTION_START_CHAIN
                putExtra(StartHabitChainReceiver.EXTRA_HABIT_CHAIN_ID, chainId)
                putExtra(StartHabitChainReceiver.EXTRA_SCHEDULED_DATE, scheduledDate.toString())
            }

        val startChainPendingIntent =
            PendingIntent.getBroadcast(
                context,
                id,
                startChainIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

        val notification =
            NotificationCompat
                .Builder(context, NotificationChannelManager.HABIT_REMINDER_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_track_changes)
                .setContentTitle(title)
                .setContentText(description.takeIf { it.isNotEmpty() })
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .addAction(
                    R.drawable.ic_routine,
                    context.getString(R.string.start_chain),
                    startChainPendingIntent,
                ).build()

        notificationManager.notify(NotificationSyncManager.NOTIFICATION_TAG_CHAIN, id, notification)
    }

    companion object {
        const val EXTRA_HABIT_ID = "HABIT_ID"
        const val EXTRA_HABIT_CHAIN_ID = "HABIT_CHAIN_ID"
        const val EXTRA_IS_CHAIN = "IS_CHAIN"
        const val EXTRA_OPEN_ROUTINES = "OPEN_ROUTINES"
        const val EXTRA_SCHEDULED_DATE = "SCHEDULED_DATE"

        const val ACTION_OPEN_HABIT = "com.mandrecode.tempo.ACTION_OPEN_HABIT"
        const val ACTION_OPEN_CHAIN = "com.mandrecode.tempo.ACTION_OPEN_CHAIN"
        const val ACTION_MARK_HABIT_COMPLETE = "com.mandrecode.tempo.ACTION_MARK_HABIT_COMPLETE"
        const val ACTION_START_CHAIN = "com.mandrecode.tempo.ACTION_START_CHAIN"

        @VisibleForTesting
        internal fun shouldShowHabitReminder(
            habit: Habit,
            scheduledDate: LocalDate,
            vacationPeriods: List<VacationPeriod> = emptyList(),
        ): Boolean =
            !CompletionHistoryUtil.isPausedOn(scheduledDate, vacationPeriods) &&
                !CompletionHistoryUtil.isDateInHistory(habit.completionHistory, scheduledDate.toString())

        @VisibleForTesting
        internal fun shouldShowHabitChainReminder(
            habits: List<Habit>,
            scheduledDate: LocalDate,
            vacationPeriods: List<VacationPeriod> = emptyList(),
        ): Boolean =
            !CompletionHistoryUtil.isPausedOn(scheduledDate, vacationPeriods) &&
                habits.none { habit ->
                    CompletionHistoryUtil.isDateInHistory(habit.completionHistory, scheduledDate.toString())
                }

        @VisibleForTesting
        internal fun resolveScheduledDate(
            scheduledDateExtra: String?,
            fallbackDate: LocalDate,
        ): LocalDate =
            scheduledDateExtra
                ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
                ?: fallbackDate

        /**
         * Returns the notification content text for a habit reminder.
         *
         * For QUIT habits, uses an encouraging "Still going strong?" prompt referencing the
         * habit title. For BUILD habits, falls back to the user-provided description (or null
         * when empty, which causes [NotificationCompat] to omit the content text line).
         */
        @VisibleForTesting
        internal fun buildHabitNotificationContentText(
            context: Context,
            title: String,
            description: String,
            habitType: HabitType,
        ): String? =
            if (habitType == HabitType.QUIT) {
                context.getString(R.string.quit_habit_reminder_text, title)
            } else {
                description.takeIf { it.isNotEmpty() }
            }

        /**
         * Returns the notification action button label for a habit reminder.
         *
         * QUIT habits cannot be "completed" in the same sense as BUILD habits; the action
         * instead affirms that the user is still abstaining ("Still on track").
         */
        @VisibleForTesting
        internal fun buildHabitNotificationActionLabel(
            context: Context,
            habitType: HabitType,
        ): String =
            if (habitType == HabitType.QUIT) {
                context.getString(R.string.still_on_track)
            } else {
                context.getString(R.string.mark_as_completed)
            }
    }
}
