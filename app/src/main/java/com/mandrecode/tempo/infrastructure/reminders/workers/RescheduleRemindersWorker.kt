package com.mandrecode.tempo.infrastructure.reminders.workers

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.mandrecode.tempo.core.data.preferences.ActiveLiveActivityPreferences
import com.mandrecode.tempo.features.focus.domain.usecase.FocusSessionUseCases
import com.mandrecode.tempo.features.routines.domain.repository.HabitChainRepository
import com.mandrecode.tempo.features.routines.domain.repository.HabitRepository
import com.mandrecode.tempo.features.routines.domain.scheduler.HabitReminderScheduler
import com.mandrecode.tempo.features.routines.domain.util.HabitReminderDateUtil
import com.mandrecode.tempo.features.tasks.domain.model.Task
import com.mandrecode.tempo.features.tasks.domain.repository.TaskRepository
import com.mandrecode.tempo.features.tasks.domain.scheduler.MissedReminderScheduler
import com.mandrecode.tempo.features.tasks.domain.scheduler.TaskReminderScheduler
import com.mandrecode.tempo.features.tasks.domain.usecase.RollOverduePeriodicTaskUseCase
import com.mandrecode.tempo.features.tasks.domain.util.TaskReminderDateUtil
import com.mandrecode.tempo.infrastructure.liveactivity.HabitChainLiveActivityManager
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

@HiltWorker
class RescheduleRemindersWorker
    @AssistedInject
    constructor(
        @Assisted appContext: Context,
        @Assisted workerParams: WorkerParameters,
        private val taskRepository: TaskRepository,
        private val habitRepository: HabitRepository,
        private val habitChainRepository: HabitChainRepository,
        private val taskReminderScheduler: TaskReminderScheduler,
        private val habitReminderScheduler: HabitReminderScheduler,
        private val rollOverduePeriodicTaskUseCase: RollOverduePeriodicTaskUseCase,
        private val activeLiveActivityPreferences: ActiveLiveActivityPreferences,
        private val habitChainLiveActivityManager: HabitChainLiveActivityManager,
        private val missedReminderScheduler: MissedReminderScheduler,
        private val focusSessionUseCases: FocusSessionUseCases,
        private val clock: Clock,
    ) : CoroutineWorker(appContext, workerParams) {
        override suspend fun doWork(): Result =
            try {
                val now = clock.now().toLocalDateTime(TimeZone.currentSystemDefault())
                rescheduleTasks(now)
                rescheduleHabits(now)
                rescheduleHabitChains(now)
                resyncActiveLiveActivities(now.date)
                // This worker runs at startup, on boot, on time/timezone change, on package
                // replacement, and every 24h — the same events that drop the catch-up alarm.
                missedReminderScheduler.sync()
                // Restores the alarm and countdown of a session that survived the reboot, or
                // settles one whose time ran out while the device was off.
                focusSessionUseCases.reconcile()
                Result.success()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (runAttemptCount < 3) {
                    Result.retry()
                } else {
                    Result.failure()
                }
            }

        private suspend fun rescheduleTasks(now: LocalDateTime) {
            val tasks = taskRepository.getTasksWithReminders()
            tasks.filter { !it.isCompleted }.forEach { task ->
                val taskToSchedule =
                    if (task.shouldRollOver(now)) {
                        when (rollOverduePeriodicTaskUseCase(task, now)) {
                            is RollOverduePeriodicTaskUseCase.Result.CreatedNextInstance,
                            is RollOverduePeriodicTaskUseCase.Result.ReusedNextInstance,
                            RollOverduePeriodicTaskUseCase.Result.SeriesEnded,
                            RollOverduePeriodicTaskUseCase.Result.FailedToCreate,
                            RollOverduePeriodicTaskUseCase.Result.MissingOriginal,
                            -> return@forEach
                            RollOverduePeriodicTaskUseCase.Result.NotApplicable ->
                                taskRepository.getTaskById(task.id)?.takeUnless { it.isCompleted }?.also {
                                    if (it.reminderDate == null) {
                                        taskReminderScheduler.cancel(it)
                                        return@forEach
                                    }
                                } ?: return@forEach
                        }
                    } else {
                        task
                    }

                val updatedTask = TaskReminderDateUtil.advanceReminderIfNeeded(taskToSchedule, now)
                if (updatedTask.reminderDate != taskToSchedule.reminderDate) {
                    taskRepository.updateTaskReminderDate(task.id, updatedTask.reminderDate)
                    taskReminderScheduler.schedule(updatedTask)
                } else {
                    taskReminderScheduler.schedule(taskToSchedule)
                }
            }
        }

        private fun Task.shouldRollOver(now: LocalDateTime): Boolean =
            parentTaskId == null &&
                periodicity != null &&
                reminderDate != null &&
                reminderDate < now

        private suspend fun rescheduleHabits(now: LocalDateTime) {
            val habits = habitRepository.getHabitsWithReminders()
            habits.forEach { habit ->
                val nextReminderDate =
                    HabitReminderDateUtil.advanceReminderIfNeeded(
                        habit.reminderDate,
                        habit.repeatDays,
                        now,
                    )
                if (nextReminderDate != null && nextReminderDate != habit.reminderDate) {
                    val updatedHabit = habit.copy(reminderDate = nextReminderDate)
                    habitRepository.updateHabit(updatedHabit)
                    habitReminderScheduler.scheduleHabit(updatedHabit)
                } else {
                    habitReminderScheduler.scheduleHabit(habit)
                }
            }
        }

        private suspend fun rescheduleHabitChains(now: LocalDateTime) {
            val chains = habitChainRepository.getHabitChainsWithReminders()
            chains.forEach { chain ->
                val nextReminderDate =
                    HabitReminderDateUtil.advanceReminderIfNeeded(
                        chain.periodicReminder,
                        chain.repeatDays,
                        now,
                    )
                if (nextReminderDate != null && nextReminderDate != chain.periodicReminder) {
                    val updatedChain = chain.copy(periodicReminder = nextReminderDate)
                    habitChainRepository.updateHabitChain(updatedChain)
                    habitReminderScheduler.scheduleHabitChain(updatedChain)
                } else {
                    habitReminderScheduler.scheduleHabitChain(chain)
                }
            }
        }

        /**
         * Rebuilds live activities that were active when the app stopped. Only today's records are
         * still meaningful — a record left over from a previous day would otherwise be rebuilt
         * against today's unrelated progress, so it is cleared instead.
         */
        private suspend fun resyncActiveLiveActivities(today: LocalDate) {
            activeLiveActivityPreferences.getActiveChains().forEach { (chainId, date) ->
                if (date == today) {
                    habitRepository.refreshHabitChainLiveActivity(chainId, date)
                } else {
                    habitChainLiveActivityManager.dismissLiveActivity(chainId)
                }
            }
        }
    }
