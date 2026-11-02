package com.mandrecode.tempo.infrastructure.reminders.workers

import android.content.Context
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import com.mandrecode.tempo.core.data.preferences.ActiveLiveActivityPreferences
import com.mandrecode.tempo.core.domain.model.DayOfWeek
import com.mandrecode.tempo.core.domain.model.Periodicity
import com.mandrecode.tempo.core.domain.model.ScheduleResult
import com.mandrecode.tempo.features.focus.domain.usecase.FocusSessionUseCases
import com.mandrecode.tempo.features.routines.domain.model.Habit
import com.mandrecode.tempo.features.routines.domain.model.HabitChain
import com.mandrecode.tempo.features.routines.domain.repository.HabitChainRepository
import com.mandrecode.tempo.features.routines.domain.repository.HabitRepository
import com.mandrecode.tempo.features.routines.domain.scheduler.HabitReminderScheduler
import com.mandrecode.tempo.features.tasks.domain.model.Task
import com.mandrecode.tempo.features.tasks.domain.repository.TaskRepository
import com.mandrecode.tempo.features.tasks.domain.scheduler.MissedReminderScheduler
import com.mandrecode.tempo.features.tasks.domain.scheduler.TaskReminderScheduler
import com.mandrecode.tempo.features.tasks.domain.usecase.RollOverduePeriodicTaskUseCase
import com.mandrecode.tempo.infrastructure.liveactivity.HabitChainLiveActivityManager
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlin.time.Clock

class RescheduleRemindersWorkerTest {
    private lateinit var taskRepository: TaskRepository
    private lateinit var habitRepository: HabitRepository
    private lateinit var habitChainRepository: HabitChainRepository
    private lateinit var taskReminderScheduler: TaskReminderScheduler
    private lateinit var habitReminderScheduler: HabitReminderScheduler
    private lateinit var rollOverduePeriodicTaskUseCase: RollOverduePeriodicTaskUseCase
    private lateinit var activeLiveActivityPreferences: ActiveLiveActivityPreferences
    private lateinit var habitChainLiveActivityManager: HabitChainLiveActivityManager
    private lateinit var missedReminderScheduler: MissedReminderScheduler
    private val focusSessionUseCases = mockk<FocusSessionUseCases>(relaxed = true)
    private lateinit var worker: RescheduleRemindersWorker
    private lateinit var clock: Clock

    private val appContext = mockk<Context>(relaxed = true)
    private val workerParams = mockk<WorkerParameters>(relaxed = true)

    @Before
    fun setup() {
        taskRepository = mockk(relaxed = true)
        habitRepository = mockk(relaxed = true)
        habitChainRepository = mockk(relaxed = true)
        taskReminderScheduler = mockk(relaxed = true)
        habitReminderScheduler = mockk(relaxed = true)
        rollOverduePeriodicTaskUseCase = mockk(relaxed = true)
        activeLiveActivityPreferences = mockk(relaxed = true)
        habitChainLiveActivityManager = mockk(relaxed = true)
        missedReminderScheduler = mockk(relaxed = true)
        clock = mockk(relaxed = true)

        every { activeLiveActivityPreferences.getActiveChains() } returns emptyMap()

        worker =
            RescheduleRemindersWorker(
                appContext,
                workerParams,
                taskRepository,
                habitRepository,
                habitChainRepository,
                taskReminderScheduler,
                habitReminderScheduler,
                rollOverduePeriodicTaskUseCase,
                activeLiveActivityPreferences,
                habitChainLiveActivityManager,
                missedReminderScheduler,
                focusSessionUseCases,
                clock,
            )
    }

    @Test
    fun `doWork reschedules past reminders to future and updates database`() =
        runTest {
            val systemZone = TimeZone.currentSystemDefault()
            val nowTime = LocalDateTime(2020, 1, 3, 12, 0)
            coEvery { clock.now() } returns nowTime.toInstant(systemZone)

            // Setup past task with daily periodicity
            val pastTaskTime = LocalDateTime(2020, 1, 1, 12, 0)
            val task =
                Task(
                    id = 1L,
                    title = "Task 1",
                    description = "",
                    isCompleted = false,
                    reminderDate = pastTaskTime,
                    periodicity = com.mandrecode.tempo.core.domain.model.Periodicity.DAILY,
                )

            // Setup past habit with daily repeat
            val pastHabitTime = LocalDateTime(2020, 1, 1, 12, 0)
            val habit =
                Habit(
                    id = 1L,
                    title = "Habit 1",
                    description = "",
                    reminderDate = pastHabitTime,
                    isCompleted = false,
                    createdDate = pastHabitTime,
                    repeatDays = DayOfWeek.ALL_DAYS,
                )

            // Setup past habit chain
            val pastChainTime = LocalDateTime(2020, 1, 1, 12, 0)
            val chain =
                HabitChain(
                    id = 1L,
                    title = "Chain 1",
                    periodicReminder = pastChainTime,
                    repeatDays = DayOfWeek.ALL_DAYS,
                )

            coEvery { taskRepository.getTasksWithReminders() } returns listOf(task)
            coEvery { habitRepository.getHabitsWithReminders() } returns listOf(habit)
            coEvery { habitChainRepository.getHabitChainsWithReminders() } returns listOf(chain)
            coEvery { rollOverduePeriodicTaskUseCase(task, nowTime) } returns
                RollOverduePeriodicTaskUseCase.Result.ReusedNextInstance(
                    overdueTask = task,
                    nextInstance = task.copy(id = 10L, reminderDate = LocalDateTime(2020, 1, 4, 12, 0)),
                    scheduleResult = ScheduleResult.Skipped,
                )

            val result = worker.doWork()

            assertTrue(result is ListenableWorker.Result.Success)

            // Verify overdue periodic task rollover preserves the original task.
            coVerify {
                rollOverduePeriodicTaskUseCase(task, nowTime)
            }
            coVerify(exactly = 0) { taskRepository.updateTaskReminderDate(any(), any()) }
            coVerify(exactly = 0) { taskReminderScheduler.schedule(task) }

            // Verify habit updated and scheduled
            coVerify {
                habitRepository.updateHabit(match { it.id == 1L && it.reminderDate!! > pastHabitTime })
                habitReminderScheduler.scheduleHabit(match { it.id == 1L && it.reminderDate!! > pastHabitTime })
            }

            // Verify chain updated and scheduled
            coVerify {
                habitChainRepository.updateHabitChain(match { it.id == 1L && it.periodicReminder!! > pastChainTime })
                habitReminderScheduler.scheduleHabitChain(match { it.id == 1L && it.periodicReminder!! > pastChainTime })
            }
        }

    @Test
    fun `doWork reschedules future reminders without updating database`() =
        runTest {
            val systemZone = TimeZone.currentSystemDefault()
            val nowTime = LocalDateTime(2020, 1, 3, 12, 0)
            coEvery { clock.now() } returns nowTime.toInstant(systemZone)

            val futureTime = LocalDateTime(2099, 1, 1, 12, 0)
            val task =
                Task(
                    id = 1L,
                    title = "Task 1",
                    description = "",
                    isCompleted = false,
                    reminderDate = futureTime,
                )

            val habit =
                Habit(
                    id = 1L,
                    title = "Habit 1",
                    description = "",
                    reminderDate = futureTime,
                    isCompleted = false,
                    createdDate = futureTime,
                    repeatDays = DayOfWeek.ALL_DAYS,
                )

            val chain =
                HabitChain(
                    id = 1L,
                    title = "Chain 1",
                    periodicReminder = futureTime,
                    repeatDays = DayOfWeek.ALL_DAYS,
                )

            coEvery { taskRepository.getTasksWithReminders() } returns listOf(task)
            coEvery { habitRepository.getHabitsWithReminders() } returns listOf(habit)
            coEvery { habitChainRepository.getHabitChainsWithReminders() } returns listOf(chain)

            val result = worker.doWork()

            assertTrue(result is ListenableWorker.Result.Success)

            // Verifications: updates should NOT happen, but they should be scheduled.
            coVerify(exactly = 0) { taskRepository.updateTaskReminderDate(any(), any()) }
            coVerify(exactly = 0) { taskRepository.updateTask(any()) }
            coVerify { taskReminderScheduler.schedule(task) }

            coVerify(exactly = 0) { habitRepository.updateHabit(any()) }
            coVerify { habitReminderScheduler.scheduleHabit(habit) }

            coVerify(exactly = 0) { habitChainRepository.updateHabitChain(any()) }
            coVerify { habitReminderScheduler.scheduleHabitChain(chain) }
        }

    @Test
    fun `doWork rolls overdue periodic tasks without updating original task entity`() =
        runTest {
            val systemZone = TimeZone.currentSystemDefault()
            val nowTime = LocalDateTime(2020, 1, 3, 12, 0)
            coEvery { clock.now() } returns nowTime.toInstant(systemZone)

            val pastTaskTime = LocalDateTime(2020, 1, 1, 12, 0)
            val task =
                Task(
                    id = 1L,
                    title = "Task 1",
                    description = "",
                    isCompleted = false,
                    categoryId = 5L,
                    reminderDate = pastTaskTime,
                    periodicity = com.mandrecode.tempo.core.domain.model.Periodicity.DAILY,
                )

            coEvery { taskRepository.getTasksWithReminders() } returns listOf(task)
            coEvery { habitRepository.getHabitsWithReminders() } returns emptyList()
            coEvery { habitChainRepository.getHabitChainsWithReminders() } returns emptyList()
            coEvery { rollOverduePeriodicTaskUseCase(task, nowTime) } returns
                RollOverduePeriodicTaskUseCase.Result.CreatedNextInstance(
                    overdueTask = task,
                    nextInstance = task.copy(id = 10L, reminderDate = LocalDateTime(2020, 1, 4, 12, 0)),
                    scheduleResult = ScheduleResult.Skipped,
                )

            val result = worker.doWork()

            assertTrue(result is ListenableWorker.Result.Success)

            // Rollover should not mutate the overdue original task.
            coVerify(exactly = 0) { taskRepository.updateTask(any()) }
            coVerify(exactly = 0) { taskRepository.updateTaskReminderDate(any(), any()) }
            coVerify { rollOverduePeriodicTaskUseCase(task, nowTime) }
        }

    @Test
    fun `doWork advances overdue periodic subtasks without rollover`() =
        runTest {
            val systemZone = TimeZone.currentSystemDefault()
            val nowTime = LocalDateTime(2020, 1, 3, 12, 0)
            coEvery { clock.now() } returns nowTime.toInstant(systemZone)

            val pastTaskTime = LocalDateTime(2020, 1, 1, 12, 0)
            val subtask =
                Task(
                    id = 2L,
                    title = "Subtask",
                    description = "",
                    isCompleted = false,
                    parentTaskId = 1L,
                    reminderDate = pastTaskTime,
                    periodicity = Periodicity.DAILY,
                )

            coEvery { taskRepository.getTasksWithReminders() } returns listOf(subtask)
            coEvery { habitRepository.getHabitsWithReminders() } returns emptyList()
            coEvery { habitChainRepository.getHabitChainsWithReminders() } returns emptyList()

            val result = worker.doWork()

            assertTrue(result is ListenableWorker.Result.Success)
            coVerify(exactly = 0) { rollOverduePeriodicTaskUseCase(any(), any()) }
            coVerify { taskRepository.updateTaskReminderDate(eq(2L), match { it > pastTaskTime }) }
            coVerify { taskReminderScheduler.schedule(match { it.id == 2L && it.reminderDate!! > pastTaskTime }) }
        }

    @Test
    fun `doWork falls back to normal scheduling when rollover becomes not applicable`() =
        runTest {
            val systemZone = TimeZone.currentSystemDefault()
            val nowTime = LocalDateTime(2020, 1, 3, 12, 0)
            coEvery { clock.now() } returns nowTime.toInstant(systemZone)

            val pastTaskTime = LocalDateTime(2020, 1, 1, 12, 0)
            val task =
                Task(
                    id = 1L,
                    title = "Task",
                    description = "",
                    isCompleted = false,
                    reminderDate = pastTaskTime,
                    periodicity = Periodicity.DAILY,
                )
            val scheduledSlot = slot<Task>()
            coEvery { taskRepository.getTasksWithReminders() } returns listOf(task)
            coEvery { taskRepository.getTaskById(1L) } returns task
            coEvery { habitRepository.getHabitsWithReminders() } returns emptyList()
            coEvery { habitChainRepository.getHabitChainsWithReminders() } returns emptyList()
            coEvery { rollOverduePeriodicTaskUseCase(task, nowTime) } returns
                RollOverduePeriodicTaskUseCase.Result.NotApplicable
            coEvery { taskReminderScheduler.schedule(capture(scheduledSlot)) } returns ScheduleResult.Skipped

            val result = worker.doWork()

            assertTrue(result is ListenableWorker.Result.Success)
            coVerify { rollOverduePeriodicTaskUseCase(task, nowTime) }
            coVerify { taskRepository.updateTaskReminderDate(eq(1L), match { it > pastTaskTime }) }
            assertTrue(scheduledSlot.captured.reminderDate!! > pastTaskTime)
        }

    @Test
    fun `doWork skips stale task when rollover becomes not applicable and refreshed task is completed`() =
        runTest {
            val systemZone = TimeZone.currentSystemDefault()
            val nowTime = LocalDateTime(2020, 1, 3, 12, 0)
            coEvery { clock.now() } returns nowTime.toInstant(systemZone)

            val task =
                Task(
                    id = 1L,
                    title = "Task",
                    description = "",
                    isCompleted = false,
                    reminderDate = LocalDateTime(2020, 1, 1, 12, 0),
                    periodicity = Periodicity.DAILY,
                )
            coEvery { taskRepository.getTasksWithReminders() } returns listOf(task)
            coEvery { taskRepository.getTaskById(1L) } returns task.copy(isCompleted = true)
            coEvery { habitRepository.getHabitsWithReminders() } returns emptyList()
            coEvery { habitChainRepository.getHabitChainsWithReminders() } returns emptyList()
            coEvery { rollOverduePeriodicTaskUseCase(task, nowTime) } returns
                RollOverduePeriodicTaskUseCase.Result.NotApplicable

            val result = worker.doWork()

            assertTrue(result is ListenableWorker.Result.Success)
            coVerify(exactly = 0) { taskRepository.updateTaskReminderDate(any(), any()) }
            coVerify(exactly = 0) { taskReminderScheduler.schedule(any()) }
        }

    @Test
    fun `doWork cancels stale alarm when rollover becomes not applicable and reminder was cleared`() =
        runTest {
            val systemZone = TimeZone.currentSystemDefault()
            val nowTime = LocalDateTime(2020, 1, 3, 12, 0)
            coEvery { clock.now() } returns nowTime.toInstant(systemZone)

            val task =
                Task(
                    id = 1L,
                    title = "Task",
                    description = "",
                    isCompleted = false,
                    reminderDate = LocalDateTime(2020, 1, 1, 12, 0),
                    periodicity = Periodicity.DAILY,
                )
            val refreshedTask = task.copy(reminderDate = null)
            coEvery { taskRepository.getTasksWithReminders() } returns listOf(task)
            coEvery { taskRepository.getTaskById(1L) } returns refreshedTask
            coEvery { habitRepository.getHabitsWithReminders() } returns emptyList()
            coEvery { habitChainRepository.getHabitChainsWithReminders() } returns emptyList()
            coEvery { rollOverduePeriodicTaskUseCase(task, nowTime) } returns
                RollOverduePeriodicTaskUseCase.Result.NotApplicable

            val result = worker.doWork()

            assertTrue(result is ListenableWorker.Result.Success)
            coVerify { taskReminderScheduler.cancel(refreshedTask) }
            coVerify(exactly = 0) { taskRepository.updateTaskReminderDate(any(), any()) }
            coVerify(exactly = 0) { taskReminderScheduler.schedule(any()) }
        }

    @Test
    fun `doWork leaves the reminder alone when rollover ends the series`() =
        runTest {
            val systemZone = TimeZone.currentSystemDefault()
            val nowTime = LocalDateTime(2020, 1, 3, 12, 0)
            coEvery { clock.now() } returns nowTime.toInstant(systemZone)

            val task =
                Task(
                    id = 1L,
                    title = "Task 1",
                    description = "",
                    reminderDate = LocalDateTime(2020, 1, 1, 12, 0),
                    periodicity = Periodicity.DAILY,
                    periodicityEndDate = LocalDate(2020, 1, 1),
                )

            coEvery { taskRepository.getTasksWithReminders() } returns listOf(task)
            coEvery { habitRepository.getHabitsWithReminders() } returns emptyList()
            coEvery { habitChainRepository.getHabitChainsWithReminders() } returns emptyList()
            coEvery { rollOverduePeriodicTaskUseCase(task, nowTime) } returns
                RollOverduePeriodicTaskUseCase.Result.SeriesEnded

            val result = worker.doWork()

            assertTrue(result is ListenableWorker.Result.Success)
            coVerify(exactly = 0) { taskRepository.updateTaskReminderDate(any(), any()) }
            coVerify(exactly = 0) { taskReminderScheduler.schedule(any()) }
        }

    @Test
    fun `doWork resyncs live activity for each active chain recorded for today`() =
        runTest {
            val systemZone = TimeZone.currentSystemDefault()
            val nowTime = LocalDateTime(2020, 1, 3, 12, 0)
            val today = nowTime.date
            coEvery { clock.now() } returns nowTime.toInstant(systemZone)

            coEvery { taskRepository.getTasksWithReminders() } returns emptyList()
            coEvery { habitRepository.getHabitsWithReminders() } returns emptyList()
            coEvery { habitChainRepository.getHabitChainsWithReminders() } returns emptyList()
            every { activeLiveActivityPreferences.getActiveChains() } returns
                mapOf(1L to today, 2L to today)

            val result = worker.doWork()

            assertTrue(result is ListenableWorker.Result.Success)
            coVerify { habitRepository.refreshHabitChainLiveActivity(1L, today) }
            coVerify { habitRepository.refreshHabitChainLiveActivity(2L, today) }
        }

    @Test
    fun `doWork dismisses instead of rebuilding an active chain recorded for a previous day`() =
        runTest {
            // Regression test for #254: a leftover record from an earlier day must not be
            // rebuilt against today's unrelated progress on every app open.
            val systemZone = TimeZone.currentSystemDefault()
            val nowTime = LocalDateTime(2020, 1, 3, 12, 0)
            coEvery { clock.now() } returns nowTime.toInstant(systemZone)

            coEvery { taskRepository.getTasksWithReminders() } returns emptyList()
            coEvery { habitRepository.getHabitsWithReminders() } returns emptyList()
            coEvery { habitChainRepository.getHabitChainsWithReminders() } returns emptyList()
            every { activeLiveActivityPreferences.getActiveChains() } returns
                mapOf(1L to LocalDate(2020, 1, 2))

            val result = worker.doWork()

            assertTrue(result is ListenableWorker.Result.Success)
            coVerify(exactly = 0) {
                habitRepository.refreshHabitChainLiveActivity(any<Long>(), any(), any())
            }
            coVerify { habitChainLiveActivityManager.dismissLiveActivity(1L) }
        }

    @Test
    fun `doWork does not resync live activities when none are persisted as active`() =
        runTest {
            val systemZone = TimeZone.currentSystemDefault()
            val nowTime = LocalDateTime(2020, 1, 3, 12, 0)
            coEvery { clock.now() } returns nowTime.toInstant(systemZone)

            coEvery { taskRepository.getTasksWithReminders() } returns emptyList()
            coEvery { habitRepository.getHabitsWithReminders() } returns emptyList()
            coEvery { habitChainRepository.getHabitChainsWithReminders() } returns emptyList()

            val result = worker.doWork()

            assertTrue(result is ListenableWorker.Result.Success)
            coVerify(exactly = 0) { habitRepository.refreshHabitChainLiveActivity(any<Long>()) }
            coVerify(exactly = 0) { habitChainLiveActivityManager.dismissLiveActivity(any()) }
        }
}
