package com.mandrecode.tempo.features.tasks.domain.usecase

import com.google.common.truth.Truth.assertThat
import com.mandrecode.tempo.core.domain.model.Periodicity
import com.mandrecode.tempo.core.domain.model.ScheduleResult
import com.mandrecode.tempo.features.tasks.domain.model.Task
import com.mandrecode.tempo.features.tasks.domain.repository.TaskRepository
import com.mandrecode.tempo.features.tasks.domain.scheduler.TaskReminderScheduler
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import io.mockk.verifyOrder
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.junit.Before
import org.junit.Test
import kotlin.time.Clock

class UpdateTaskUseCaseTest {
    private lateinit var useCase: UpdateTaskUseCase
    private lateinit var taskRepository: TaskRepository
    private lateinit var taskReminderScheduler: TaskReminderScheduler

    @Before
    fun setup() {
        taskRepository = mockk(relaxed = true)
        taskReminderScheduler = mockk(relaxed = true)
        useCase = UpdateTaskUseCase(taskRepository, taskReminderScheduler)
    }

    @Test
    fun `updating completed task cancels reminder and dismisses notification`() =
        runTest {
            val task = task(isCompleted = true, reminderDate = LocalDateTime(2025, 6, 15, 10, 0))

            val result = useCase(task)

            verify { taskReminderScheduler.cancel(match { it.id == task.id }) }
            verify(exactly = 0) { taskReminderScheduler.schedule(any()) }
            assertThat((result as UpdateTaskUseCase.Result.Success).scheduleResult)
                .isEqualTo(ScheduleResult.Skipped)
        }

    @Test
    fun `updating task with no reminder cancels and skips scheduling`() =
        runTest {
            val task = task(isCompleted = false, reminderDate = null)

            val result = useCase(task)

            verify { taskReminderScheduler.cancel(match { it.id == task.id }) }
            verify(exactly = 0) { taskReminderScheduler.schedule(any()) }
            assertThat((result as UpdateTaskUseCase.Result.Success).scheduleResult)
                .isEqualTo(ScheduleResult.Skipped)
        }

    @Test
    fun `updating incomplete task with reminder schedules alarm`() =
        runTest {
            val reminder = LocalDateTime(2099, 1, 1, 10, 0)
            val task = task(isCompleted = false, reminderDate = reminder)
            every { taskReminderScheduler.schedule(any()) } returns ScheduleResult.Success(reminder)

            val result = useCase(task)

            verify(exactly = 0) { taskReminderScheduler.cancel(any()) }
            verify { taskReminderScheduler.schedule(match { it.id == task.id && it.reminderDate == reminder }) }
            assertThat((result as UpdateTaskUseCase.Result.Success).scheduleResult)
                .isEqualTo(ScheduleResult.Success(reminder))
        }

    @Test
    fun `updating incomplete task with reminder dismisses active notification before scheduling`() =
        runTest {
            val reminder = LocalDateTime(2099, 1, 1, 10, 0)
            val task = task(isCompleted = false, reminderDate = reminder)
            every { taskReminderScheduler.schedule(any()) } returns ScheduleResult.Success(reminder)

            useCase(task)

            verifyOrder {
                taskReminderScheduler.dismissNotification(task.id)
                taskReminderScheduler.schedule(match { it.id == task.id })
            }
            verify(exactly = 0) { taskReminderScheduler.cancel(any()) }
        }

    @Test
    fun `updating non-periodic past reminder cancels stale alarm when schedule is skipped`() =
        runTest {
            val pastReminder = LocalDateTime(2020, 1, 1, 10, 0)
            val task = task(isCompleted = false, reminderDate = pastReminder, periodicity = null)
            every { taskReminderScheduler.schedule(any()) } returns ScheduleResult.Skipped

            val result = useCase(task)

            verifyOrder {
                taskReminderScheduler.dismissNotification(task.id)
                taskReminderScheduler.schedule(match { it.id == task.id })
                taskReminderScheduler.cancel(match { it.id == task.id })
            }
            assertThat((result as UpdateTaskUseCase.Result.Success).scheduleResult)
                .isEqualTo(ScheduleResult.Skipped)
        }

    @Test
    fun `updating task persists to repository`() =
        runTest {
            val task = task(isCompleted = false, reminderDate = null)

            useCase(task)

            val savedTaskSlot = slot<Task>()
            coVerify { taskRepository.updateTask(capture(savedTaskSlot)) }
            assertThat(savedTaskSlot.captured).isEqualTo(task)
        }

    @Test
    fun `empty title returns validation error`() =
        runTest {
            val task = task(title = "")

            val result = useCase(task)

            assertThat(result).isInstanceOf(UpdateTaskUseCase.Result.ValidationError::class.java)
            assertThat((result as UpdateTaskUseCase.Result.ValidationError).type)
                .isEqualTo(CreateTaskUseCase.ValidationErrorType.TITLE_EMPTY)
        }

    @Test
    fun `validation error does not cancel or schedule reminders`() =
        runTest {
            val task = task(title = "")

            useCase(task)

            verify(exactly = 0) { taskReminderScheduler.cancel(any()) }
            verify(exactly = 0) { taskReminderScheduler.schedule(any()) }
            coVerify(exactly = 0) { taskRepository.updateTask(any()) }
        }

    @Test
    fun `updating periodic task with past reminder advances to future date`() =
        runTest {
            val pastReminder = LocalDateTime(2020, 1, 1, 10, 0)
            val task = task(reminderDate = pastReminder, periodicity = Periodicity.DAILY)

            val taskSlot = slot<Task>()
            every { taskReminderScheduler.schedule(any()) } returns ScheduleResult.Skipped

            val result = useCase(task)

            coVerify { taskRepository.updateTask(capture(taskSlot)) }
            val savedReminder = taskSlot.captured.reminderDate
            val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())

            assertThat(savedReminder).isNotNull()
            assertThat(savedReminder!! > now).isTrue()
            assertThat(savedReminder.hour).isEqualTo(pastReminder.hour)
            assertThat(savedReminder.minute).isEqualTo(pastReminder.minute)
            assertThat((result as UpdateTaskUseCase.Result.Success).reminderAdvanced).isTrue()
        }

    @Test
    fun `updating non-periodic task with past reminder sets pastReminderWontFire flag`() =
        runTest {
            val pastReminder = LocalDateTime(2020, 1, 1, 10, 0)
            val task = task(reminderDate = pastReminder, periodicity = null)

            val result = useCase(task)

            assertThat((result as UpdateTaskUseCase.Result.Success).pastReminderWontFire).isTrue()
            assertThat(result.reminderAdvanced).isFalse()
        }

    @Test
    fun `updating task with future reminder does not advance`() =
        runTest {
            val futureReminder = LocalDateTime(2099, 1, 1, 10, 0)
            val task = task(reminderDate = futureReminder, periodicity = Periodicity.DAILY)
            every { taskReminderScheduler.schedule(any()) } returns ScheduleResult.Success(futureReminder)

            val result = useCase(task)

            assertThat((result as UpdateTaskUseCase.Result.Success).reminderAdvanced).isFalse()
            assertThat(result.pastReminderWontFire).isFalse()
        }

    @Test
    fun `updating task trims title and description before saving`() =
        runTest {
            val task = task(title = "  Padded Title  ", description = "  Padded Desc  ")

            val taskSlot = slot<Task>()
            coVerify(exactly = 0) { taskRepository.updateTask(any()) }

            useCase(task)

            coVerify { taskRepository.updateTask(capture(taskSlot)) }
            assertThat(taskSlot.captured.title).isEqualTo("Padded Title")
            assertThat(taskSlot.captured.description).isEqualTo("Padded Desc")
        }

    @Test
    fun `updating completed task with past reminder does not set advancement flags`() =
        runTest {
            val pastReminder = LocalDateTime(2020, 1, 1, 10, 0)
            val task = task(isCompleted = true, reminderDate = pastReminder, periodicity = Periodicity.DAILY)

            val result = useCase(task)

            assertThat((result as UpdateTaskUseCase.Result.Success).reminderAdvanced).isFalse()
            assertThat(result.pastReminderWontFire).isFalse()
            assertThat(result.scheduleResult).isEqualTo(ScheduleResult.Skipped)
        }

    private fun task(
        title: String = "Test Task",
        description: String = "Test Description",
        isCompleted: Boolean = false,
        reminderDate: LocalDateTime? = null,
        periodicity: Periodicity? = null,
    ) = Task(
        id = 1L,
        title = title,
        description = description,
        isCompleted = isCompleted,
        reminderDate = reminderDate,
        periodicity = periodicity,
    )
}
