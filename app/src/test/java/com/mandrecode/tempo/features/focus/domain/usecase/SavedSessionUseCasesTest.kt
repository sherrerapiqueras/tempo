package com.mandrecode.tempo.features.focus.domain.usecase

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.mandrecode.tempo.features.focus.domain.model.SavedSession
import com.mandrecode.tempo.features.focus.domain.repository.SavedSessionRepository
import com.mandrecode.tempo.features.tasks.domain.model.Task
import com.mandrecode.tempo.features.tasks.domain.repository.TaskRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Test

class SavedSessionUseCasesTest {
    private val savedSessionRepository = mockk<SavedSessionRepository>(relaxed = true)
    private val taskRepository = mockk<TaskRepository>(relaxed = true)

    private val study = SavedSession(id = 1, title = "Study", focusMinutes = 25, breakMinutes = 5)

    private fun task(
        id: Long,
        title: String,
        isCompleted: Boolean = false,
        parentTaskId: Long? = null,
    ) = Task(id = id, title = title, description = "", isCompleted = isCompleted, parentTaskId = parentTaskId)

    @Test
    fun `sessions come with the task they point at`() =
        runTest {
            val chapter = task(7, "Read chapter 3")
            every { savedSessionRepository.getAll() } returns flowOf(listOf(study, study.copy(id = 2, taskId = 7)))
            every { taskRepository.getAllTasks() } returns flowOf(listOf(chapter))

            GetSavedSessionsUseCase(savedSessionRepository, taskRepository)().test {
                val entries = awaitItem()
                assertThat(entries.map { it.linkedTask }).containsExactly(null, chapter).inOrder()
                awaitComplete()
            }
        }

    @Test
    fun `a link whose task is gone resolves to nothing`() =
        runTest {
            every { savedSessionRepository.getAll() } returns flowOf(listOf(study.copy(taskId = 99)))
            every { taskRepository.getAllTasks() } returns flowOf(emptyList())

            GetSavedSessionsUseCase(savedSessionRepository, taskRepository)().test {
                assertThat(awaitItem().single().linkedTask).isNull()
                awaitComplete()
            }
        }

    @Test
    fun `a new session is inserted and comes back with its id`() =
        runTest {
            coEvery { savedSessionRepository.insert(any()) } returns 12

            val saved = SaveSavedSessionUseCase(savedSessionRepository)(study.copy(id = 0, title = " Study "))

            assertThat(saved).isEqualTo(study.copy(id = 12))
            coVerify { savedSessionRepository.insert(study.copy(id = 0)) }
        }

    @Test
    fun `an existing session is updated, normalized`() =
        runTest {
            val saved = SaveSavedSessionUseCase(savedSessionRepository)(study.copy(rounds = 0))

            assertThat(saved).isEqualTo(study)
            coVerify { savedSessionRepository.update(study) }
            coVerify(exactly = 0) { savedSessionRepository.insert(any()) }
        }

    @Test
    fun `a session with no name is not saved`() =
        runTest {
            val saved = SaveSavedSessionUseCase(savedSessionRepository)(study.copy(title = "   "))

            assertThat(saved).isNull()
            coVerify(exactly = 0) { savedSessionRepository.insert(any()) }
            coVerify(exactly = 0) { savedSessionRepository.update(any()) }
        }

    @Test
    fun `deleting removes the session by id`() =
        runTest {
            DeleteSavedSessionUseCase(savedSessionRepository)(1)

            coVerify { savedSessionRepository.delete(1) }
        }

    @Test
    fun `only open top-level tasks can be linked, by name`() =
        runTest {
            every { taskRepository.getAllTasks() } returns
                flowOf(
                    listOf(
                        task(1, "write"),
                        task(2, "Done already", isCompleted = true),
                        task(3, "A step", parentTaskId = 1),
                        task(4, "Call"),
                    ),
                )

            GetLinkableTasksUseCase(taskRepository)().test {
                assertThat(awaitItem().map { it.id }).containsExactly(4L, 1L).inOrder()
                awaitComplete()
            }
        }

    @Test
    fun `tasks are not read while no session points at one`() =
        runTest {
            every { savedSessionRepository.getAll() } returns flowOf(listOf(study), emptyList())

            GetSavedSessionsUseCase(savedSessionRepository, taskRepository)().test {
                assertThat(awaitItem().single().session).isEqualTo(study)
                assertThat(awaitItem()).isEmpty()
                awaitComplete()
            }
            io.mockk.verify(exactly = 0) { taskRepository.getAllTasks() }
        }
}
