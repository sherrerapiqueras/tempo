package com.mandrecode.tempo.features.focus.domain.usecase

import com.google.common.truth.Truth.assertThat
import com.mandrecode.tempo.features.focus.domain.model.FocusSession
import com.mandrecode.tempo.features.focus.domain.repository.FocusSessionRepository
import com.mandrecode.tempo.features.tasks.domain.model.Task
import com.mandrecode.tempo.features.tasks.domain.repository.TaskRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.time.Instant

class StartFocusSessionOnTaskUseCaseTest {
    private val activeSession = MutableStateFlow<FocusSession?>(null)
    private val taskRepository = mockk<TaskRepository>(relaxed = true)
    private val sessionRepository =
        mockk<FocusSessionRepository>(relaxed = true) {
            every { this@mockk.activeSession } returns this@StartFocusSessionOnTaskUseCaseTest.activeSession
        }
    private val focusSessionUseCases = mockk<FocusSessionUseCases>(relaxed = true)

    private val useCase = StartFocusSessionOnTaskUseCase(taskRepository, sessionRepository, focusSessionUseCases)

    @Test
    fun `starts a session on the task under its current title`() =
        runTest {
            coEvery { taskRepository.getTaskById(TASK_ID) } returns task(title = "Renamed report")

            assertThat(useCase(TASK_ID)).isTrue()

            coVerify { focusSessionUseCases.start(taskId = TASK_ID, title = "Renamed report") }
            verify { sessionRepository.setPreviewTaskId(null) }
        }

    @Test
    fun `replaces a session running on another task`() =
        runTest {
            coEvery { taskRepository.getTaskById(TASK_ID) } returns task()
            activeSession.value = session(taskId = 99L)

            assertThat(useCase(TASK_ID)).isTrue()

            coVerify { focusSessionUseCases.start(taskId = TASK_ID, title = "Report") }
        }

    @Test
    fun `a break on the same task gives way to a real session`() =
        runTest {
            coEvery { taskRepository.getTaskById(TASK_ID) } returns task()
            activeSession.value = session(taskId = TASK_ID, isBreak = true)

            assertThat(useCase(TASK_ID)).isTrue()

            coVerify { focusSessionUseCases.start(taskId = TASK_ID, title = "Report") }
        }

    @Test
    fun `carries on with a session already running on the same task`() =
        runTest {
            coEvery { taskRepository.getTaskById(TASK_ID) } returns task()
            activeSession.value = session(taskId = TASK_ID)

            assertThat(useCase(TASK_ID)).isTrue()

            coVerify(exactly = 0) { focusSessionUseCases.start(any(), any(), any(), any()) }
        }

    @Test
    fun `resumes a paused session on the same task`() =
        runTest {
            coEvery { taskRepository.getTaskById(TASK_ID) } returns task()
            activeSession.value = session(taskId = TASK_ID).pause(Instant.fromEpochMilliseconds(1_800_000_060_000))

            assertThat(useCase(TASK_ID)).isTrue()

            verify { focusSessionUseCases.resume() }
            coVerify(exactly = 0) { focusSessionUseCases.start(any(), any(), any(), any()) }
        }

    @Test
    fun `settles an expired session before deciding, then starts afresh`() =
        runTest {
            coEvery { taskRepository.getTaskById(TASK_ID) } returns task()
            activeSession.value = session(taskId = TASK_ID)
            // The end alarm is late: reconciling finds the session over and clears it.
            coEvery { focusSessionUseCases.reconcile() } answers {
                activeSession.value = null
                null
            }

            assertThat(useCase(TASK_ID)).isTrue()

            coVerify { focusSessionUseCases.start(taskId = TASK_ID, title = "Report") }
        }

    @Test
    fun `does nothing for a task that no longer exists`() =
        runTest {
            coEvery { taskRepository.getTaskById(TASK_ID) } returns null

            assertThat(useCase(TASK_ID)).isFalse()

            coVerify(exactly = 0) { focusSessionUseCases.start(any(), any(), any(), any()) }
        }

    @Test
    fun `does nothing for a task completed since its reminder fired`() =
        runTest {
            coEvery { taskRepository.getTaskById(TASK_ID) } returns task(isCompleted = true)

            assertThat(useCase(TASK_ID)).isFalse()

            coVerify(exactly = 0) { focusSessionUseCases.start(any(), any(), any(), any()) }
        }

    private fun task(
        title: String = "Report",
        isCompleted: Boolean = false,
    ) = Task(
        id = TASK_ID,
        title = title,
        description = "",
        isCompleted = isCompleted,
    )

    private fun session(
        taskId: Long,
        isBreak: Boolean = false,
    ) = FocusSession.start(
        taskId = taskId,
        title = "Running",
        now = Instant.fromEpochMilliseconds(1_800_000_000_000),
        isBreak = isBreak,
    )

    private companion object {
        const val TASK_ID = 7L
    }
}
