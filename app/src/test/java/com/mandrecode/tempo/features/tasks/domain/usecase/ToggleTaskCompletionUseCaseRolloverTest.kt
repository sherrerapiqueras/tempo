package com.mandrecode.tempo.features.tasks.domain.usecase

import com.google.common.truth.Truth.assertThat
import com.mandrecode.tempo.core.domain.model.Periodicity
import com.mandrecode.tempo.core.domain.model.ScheduleResult
import com.mandrecode.tempo.core.domain.usecase.DailyActivityRecorder
import com.mandrecode.tempo.features.tasks.domain.model.Task
import com.mandrecode.tempo.features.tasks.domain.repository.TaskAttachmentRepository
import com.mandrecode.tempo.features.tasks.domain.repository.TaskRepository
import com.mandrecode.tempo.features.tasks.domain.scheduler.TaskReminderScheduler
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import org.junit.Before
import org.junit.Test

/**
 * Dedicated coverage for next-instance rollover linking and stale-link replacement.
 *
 * Non-rollover toggle behavior remains in [ToggleTaskCompletionUseCaseTest].
 */
class ToggleTaskCompletionUseCaseRolloverTest {
    private val dailyActivityRecorder = mockk<DailyActivityRecorder>(relaxed = true)
    private val taskAttachmentRepository = mockk<TaskAttachmentRepository>(relaxed = true)
    private lateinit var useCase: ToggleTaskCompletionUseCase
    private lateinit var taskRepository: TaskRepository
    private lateinit var taskReminderScheduler: TaskReminderScheduler
    private lateinit var updateTaskUseCase: UpdateTaskUseCase

    @Before
    fun setup() {
        taskRepository = mockk(relaxed = true)
        taskReminderScheduler = mockk(relaxed = true)
        updateTaskUseCase = mockk(relaxed = true)

        coEvery { taskRepository.runInTransaction<Any?>(any()) } coAnswers {
            val block = firstArg<suspend () -> Any?>()
            block()
        }
        coEvery { taskRepository.getTaskById(any()) } returns null

        useCase =
            ToggleTaskCompletionUseCase(
                taskRepository,
                taskReminderScheduler,
                updateTaskUseCase,
                dailyActivityRecorder,
                taskAttachmentRepository,
            )
    }

    @Test
    fun `completing periodic task with existing nextInstanceId reuses spawned next instance`() =
        runTest {
            val task = periodicTask(nextInstanceId = 42L)
            val existingNextInstance =
                periodicTask(
                    id = 42L,
                    reminderDate = LocalDateTime(2024, 6, 16, 10, 0),
                    nextInstanceId = null,
                )
            val scheduledSlot = slot<Task>()
            coEvery { taskRepository.getTaskById(42L) } returns existingNextInstance
            coEvery { taskRepository.getSubtasksSync(1L) } returns emptyList()
            coEvery { taskReminderScheduler.schedule(capture(scheduledSlot)) } answers {
                ScheduleResult.Success(scheduledSlot.captured.reminderDate!!)
            }

            useCase(task)

            coVerify(exactly = 0) { taskRepository.insertTask(any()) }
            coVerify { taskRepository.updateTaskNextInstanceId(1L, 42L) }
            assertThat(scheduledSlot.captured).isEqualTo(existingNextInstance)
        }

    @Test
    fun `completing periodic task re-reads current nextInstanceId before inserting next instance`() =
        runTest {
            val staleInput = periodicTask(nextInstanceId = null)
            val currentTask = staleInput.copy(nextInstanceId = 42L)
            val existingNextInstance =
                periodicTask(
                    id = 42L,
                    reminderDate = LocalDateTime(2024, 6, 16, 10, 0),
                    nextInstanceId = null,
                )
            coEvery { taskRepository.getTaskById(1L) } returns currentTask
            coEvery { taskRepository.getTaskById(42L) } returns existingNextInstance
            coEvery { taskRepository.getSubtasksSync(1L) } returns emptyList()

            useCase(staleInput)

            coVerify(exactly = 0) { taskRepository.insertTask(any()) }
            coVerify { taskRepository.updateTaskNextInstanceId(1L, 42L) }
            coVerify { taskReminderScheduler.schedule(existingNextInstance) }
        }

    @Test
    fun `completing periodic task with stale nextInstanceId creates replacement next instance`() =
        runTest {
            val task = periodicTask(nextInstanceId = 99L)
            coEvery { taskRepository.getTaskById(99L) } returns null
            coEvery { taskRepository.insertTask(any()) } returns 42L
            coEvery { taskRepository.getSubtasksSync(1L) } returns emptyList()

            useCase(task)

            coVerify { taskRepository.updateTaskNextInstanceId(1L, null) }
            coVerify { taskRepository.updateTaskNextInstanceId(1L, 42L) }
            coVerify { taskRepository.insertTask(any()) }
        }

    @Test
    fun `completing periodic task with completed nextInstanceId creates replacement next instance`() =
        runTest {
            val task = periodicTask(nextInstanceId = 99L)
            val completedNextInstance =
                periodicTask(
                    id = 99L,
                    reminderDate = LocalDateTime(2024, 6, 16, 10, 0),
                    nextInstanceId = null,
                ).copy(
                    isCompleted = true,
                    periodicity = null,
                )
            coEvery { taskRepository.getTaskById(99L) } returns completedNextInstance
            coEvery { taskRepository.insertTask(any()) } returns 42L
            coEvery { taskRepository.getSubtasksSync(1L) } returns emptyList()

            useCase(task)

            coVerify { taskRepository.updateTaskNextInstanceId(1L, null) }
            coVerify { taskRepository.updateTaskNextInstanceId(1L, 42L) }
            coVerify { taskRepository.insertTask(any()) }
            coVerify(exactly = 0) { taskReminderScheduler.schedule(completedNextInstance) }
        }

    @Test
    fun `completing the last occurrence creates no next one and schedules nothing`() =
        runTest {
            val task =
                periodicTask(reminderDate = LocalDateTime(2099, 3, 31, 10, 0))
                    .copy(periodicityEndDate = LocalDate(2099, 3, 31))
            val archivedSlot = slot<Task>()
            coEvery { taskRepository.getSubtasksSync(1L) } returns emptyList()

            val result = useCase(task)

            assertThat(result).isInstanceOf(ToggleTaskCompletionUseCase.Result.PeriodicCompleted::class.java)
            coVerify(exactly = 0) { taskRepository.insertTask(any()) }
            coVerify(exactly = 0) { taskReminderScheduler.schedule(any()) }
            coVerify { taskRepository.updateTask(capture(archivedSlot)) }
            assertThat(archivedSlot.captured.isCompleted).isTrue()
            assertThat(archivedSlot.captured.periodicity).isNull()
            assertThat(archivedSlot.captured.periodicityEndDate).isNull()
            assertThat(archivedSlot.captured.nextInstanceId).isNull()
        }

    @Test
    fun `completing an earlier occurrence carries the end date to the next one`() =
        runTest {
            val endDate = LocalDate(2099, 3, 31)
            val task =
                periodicTask(reminderDate = LocalDateTime(2099, 3, 30, 10, 0))
                    .copy(periodicityEndDate = endDate)
            val insertedSlot = slot<Task>()
            coEvery { taskRepository.getSubtasksSync(1L) } returns emptyList()
            coEvery { taskRepository.insertTask(capture(insertedSlot)) } returns 77L

            useCase(task)

            assertThat(insertedSlot.captured.reminderDate).isEqualTo(LocalDateTime(2099, 3, 31, 10, 0))
            assertThat(insertedSlot.captured.periodicityEndDate).isEqualTo(endDate)
            coVerify { taskRepository.updateTaskNextInstanceId(1L, 77L) }
        }

    @Test
    fun `the end date is read from the task as it is inside the transaction`() =
        runTest {
            val task = periodicTask(reminderDate = LocalDateTime(2099, 3, 31, 10, 0))
            coEvery { taskRepository.getTaskById(1L) } returns
                task.copy(periodicityEndDate = LocalDate(2099, 3, 31))
            coEvery { taskRepository.getSubtasksSync(1L) } returns emptyList()

            useCase(task)

            coVerify(exactly = 0) { taskRepository.insertTask(any()) }
        }

    @Test
    fun `unchecking a completed last occurrence leaves a one-off task`() =
        runTest {
            val completedLast =
                periodicTask(reminderDate = LocalDateTime(2099, 3, 31, 10, 0))
                    .withoutRecurrence()
                    .copy(isCompleted = true, completedAt = LocalDateTime(2099, 3, 31, 11, 0))
            val updatedSlot = slot<Task>()
            coEvery { updateTaskUseCase(capture(updatedSlot)) } returns
                UpdateTaskUseCase.Result.Success(ScheduleResult.Skipped)
            coEvery { taskRepository.getSubtasksSync(1L) } returns emptyList()

            useCase(completedLast)

            coVerify(exactly = 0) { taskRepository.insertTask(any()) }
            assertThat(updatedSlot.captured.isCompleted).isFalse()
            assertThat(updatedSlot.captured.periodicity).isNull()
        }

    @Test
    fun `next occurrence follows the date the caller passed rather than the stored one`() =
        runTest {
            val fired = periodicTask(reminderDate = LocalDateTime(2099, 3, 1, 9, 0))
            val insertedSlot = slot<Task>()
            coEvery { taskRepository.getTaskById(1L) } returns
                fired.copy(reminderDate = LocalDateTime(2099, 3, 3, 9, 0))
            coEvery { taskRepository.getSubtasksSync(1L) } returns emptyList()
            coEvery { taskRepository.insertTask(capture(insertedSlot)) } returns 77L

            useCase(fired)

            assertThat(insertedSlot.captured.reminderDate).isEqualTo(LocalDateTime(2099, 3, 2, 9, 0))
        }

    @Test
    fun `the end date is judged from the date the caller passed`() =
        runTest {
            val fired =
                periodicTask(reminderDate = LocalDateTime(2099, 3, 1, 9, 0))
                    .copy(periodicityEndDate = LocalDate(2099, 3, 2))
            val insertedSlot = slot<Task>()
            coEvery { taskRepository.getTaskById(1L) } returns
                fired.copy(reminderDate = LocalDateTime(2099, 3, 2, 9, 0))
            coEvery { taskRepository.getSubtasksSync(1L) } returns emptyList()
            coEvery { taskRepository.insertTask(capture(insertedSlot)) } returns 77L

            useCase(fired)

            assertThat(insertedSlot.captured.reminderDate).isEqualTo(LocalDateTime(2099, 3, 2, 9, 0))
        }

    private fun periodicTask(
        id: Long = 1L,
        reminderDate: LocalDateTime = LocalDateTime(2024, 6, 15, 10, 0),
        nextInstanceId: Long? = null,
    ): Task =
        Task(
            id = id,
            title = "Periodic",
            description = "",
            isCompleted = false,
            periodicity = Periodicity.DAILY,
            reminderDate = reminderDate,
            nextInstanceId = nextInstanceId,
        )
}
