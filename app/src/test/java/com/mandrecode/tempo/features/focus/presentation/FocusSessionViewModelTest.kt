package com.mandrecode.tempo.features.focus.presentation

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.mandrecode.tempo.features.focus.domain.model.FocusAgendaItem
import com.mandrecode.tempo.features.focus.domain.model.FocusSession
import io.mockk.coEvery
import io.mockk.coVerify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.time.Duration.Companion.minutes

/** The timer: starting, pausing, breaks, and what a finished session offers. */
@OptIn(ExperimentalCoroutinesApi::class)
class FocusSessionViewModelTest : FocusViewModelHarness() {
    @Test
    fun `starting a session uses the up next task`() =
        runTest {
            val entry = FocusAgendaItem.TaskEntry(task(9, "Report"))
            stubDay(agendaOf(upNext = entry, todayItems = listOf(entry)))

            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.onEvent(FocusContract.UiEvent.StartSession())
            advanceUntilIdle()

            coVerify { focusSessionUseCases.start(taskId = 9, title = "Report") }
        }

    @Test
    fun `previewing up next opens the session screen without starting a session`() =
        runTest {
            val entry = FocusAgendaItem.TaskEntry(task(9, "Report"))
            stubDay(agendaOf(upNext = entry, todayItems = listOf(entry)))

            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.uiEffect.test {
                viewModel.onEvent(FocusContract.UiEvent.PreviewUpNext(9))
                advanceUntilIdle()

                assertThat(awaitItem()).isEqualTo(FocusContract.UiEffect.OpenSessionScreen)
                cancelAndIgnoreRemainingEvents()
            }

            coVerify(exactly = 0) { focusSessionUseCases.start(any(), any(), any()) }
            // The screen resolves its subject from the preview when nothing is running.
            assertThat(viewModel.uiState.value.sessionEntry).isEqualTo(entry)
        }

    @Test
    fun `leaving the session screen clears the preview`() =
        runTest {
            val entry = FocusAgendaItem.TaskEntry(task(9, "Report"))
            stubDay(agendaOf(upNext = entry, todayItems = listOf(entry)))

            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.onEvent(FocusContract.UiEvent.PreviewUpNext(9))
            viewModel.onEvent(FocusContract.UiEvent.ClearSessionPreview)
            advanceUntilIdle()

            assertThat(viewModel.uiState.value.sessionEntry).isNull()
        }

    @Test
    fun `starting a session takes the user into it`() =
        runTest {
            val entry = FocusAgendaItem.TaskEntry(task(9, "Report"))
            stubDay(agendaOf(upNext = entry, todayItems = listOf(entry)))

            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.uiEffect.test {
                viewModel.onEvent(FocusContract.UiEvent.StartSession())
                advanceUntilIdle()

                assertThat(awaitItem()).isEqualTo(FocusContract.UiEffect.OpenSessionScreen)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `another session also takes the user into it`() =
        runTest {
            stubDay()
            val viewModel = createViewModel()
            advanceUntilIdle()
            runSessionOut(taskId = 11)

            viewModel.uiEffect.test {
                viewModel.onEvent(FocusContract.UiEvent.StartAnotherSession)
                advanceUntilIdle()

                assertThat(awaitItem()).isEqualTo(FocusContract.UiEffect.OpenSessionScreen)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `the running session is mirrored into state`() =
        runTest {
            stubDay()
            val viewModel = createViewModel()
            advanceUntilIdle()

            sessionFlow.value = FocusSession.start(1, "Report", nowInstant)
            advanceUntilIdle()

            assertThat(
                viewModel.uiState.value.session
                    ?.taskId,
            ).isEqualTo(1)
        }

    @Test
    fun `opening the session asks for its screen rather than flipping a flag`() =
        runTest {
            stubDay()
            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.uiEffect.test {
                viewModel.onEvent(FocusContract.UiEvent.OpenSessionScreen)
                advanceUntilIdle()

                assertThat(awaitItem()).isEqualTo(FocusContract.UiEffect.OpenSessionScreen)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `a session that runs out raises the completion sheet`() =
        runTest {
            stubDay()
            val viewModel = createViewModel()
            advanceUntilIdle()

            runSessionOut()

            val finished = viewModel.uiState.value.finishedSession
            assertThat(finished?.title).isEqualTo("Report")
            // The planned length, not the elapsed time: an expired session ran its full course.
            assertThat(finished?.minutes).isEqualTo(25)
            assertThat(finished?.wasBreak).isFalse()
        }

    @Test
    fun `stopping raises no sheet, since the user already decided`() =
        runTest {
            stubDay()
            val session = FocusSession.start(1, "Report", nowInstant)
            sessionFlow.value = session
            coEvery { focusSessionUseCases.end() } returns session

            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.onEvent(FocusContract.UiEvent.StopSession)
            advanceUntilIdle()

            assertThat(viewModel.uiState.value.finishedSession).isNull()
        }

    @Test
    fun `a session cleared before its time is up raises no sheet`() =
        runTest {
            stubDay()
            val viewModel = createViewModel()
            advanceUntilIdle()

            sessionFlow.value = FocusSession.start(1, "Report", nowInstant)
            advanceUntilIdle()
            sessionFlow.value = null
            advanceUntilIdle()

            assertThat(viewModel.uiState.value.finishedSession).isNull()
        }

    @Test
    fun `a break that runs out is reported as a break`() =
        runTest {
            stubDay()
            val viewModel = createViewModel()
            advanceUntilIdle()

            runSessionOut(isBreak = true)

            assertThat(
                viewModel.uiState.value.finishedSession
                    ?.wasBreak,
            ).isTrue()
        }

    @Test
    fun `completing the session's own task ends the session`() =
        runTest {
            val open = task(4, "Report")
            stubDay(agendaOf(todayItems = listOf(FocusAgendaItem.TaskEntry(open))))
            val session = FocusSession.start(4, "Report", nowInstant)
            sessionFlow.value = session
            coEvery { focusSessionUseCases.end() } returns session

            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.onEvent(FocusContract.UiEvent.ToggleTaskCompletion(open))
            advanceUntilIdle()

            coVerify { focusSessionUseCases.end() }
        }

    @Test
    fun `completing an unrelated task leaves the session running`() =
        runTest {
            val other = task(8)
            stubDay(agendaOf(todayItems = listOf(FocusAgendaItem.TaskEntry(other))))
            sessionFlow.value = FocusSession.start(4, "Report", nowInstant)

            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.onEvent(FocusContract.UiEvent.ToggleTaskCompletion(other))
            advanceUntilIdle()

            coVerify(exactly = 0) { focusSessionUseCases.end() }
        }

    @Test
    fun `dismissing clears the completion sheet`() =
        runTest {
            stubDay()
            val session = FocusSession.start(1, "Report", nowInstant)
            sessionFlow.value = session
            coEvery { focusSessionUseCases.end() } returns session

            val viewModel = createViewModel()
            advanceUntilIdle()
            viewModel.onEvent(FocusContract.UiEvent.StopSession)
            advanceUntilIdle()

            viewModel.onEvent(FocusContract.UiEvent.DismissFinishedSession)

            assertThat(viewModel.uiState.value.finishedSession).isNull()
        }

    @Test
    fun `pause and resume delegate to the session use cases`() =
        runTest {
            stubDay()
            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.onEvent(FocusContract.UiEvent.PauseSession)
            viewModel.onEvent(FocusContract.UiEvent.ResumeSession)

            coVerify { focusSessionUseCases.pause() }
            coVerify { focusSessionUseCases.resume() }
        }

    @Test
    fun `taking a break starts a real break countdown`() =
        runTest {
            stubDay()
            val viewModel = createViewModel()
            advanceUntilIdle()
            runSessionOut()

            viewModel.onEvent(FocusContract.UiEvent.TakeBreak)
            advanceUntilIdle()

            assertThat(viewModel.uiState.value.finishedSession).isNull()
            coVerify { focusSessionUseCases.start(taskId = any(), title = any(), isBreak = true) }
        }

    @Test
    fun `the completion sheet can finish the task once the session has already ended`() =
        runTest {
            val open = task(9, "Report")
            stubDay(agendaOf(todayItems = listOf(FocusAgendaItem.TaskEntry(open))))
            val viewModel = createViewModel()
            advanceUntilIdle()

            // The sheet offers this after expiry, when there is no running session left to read
            // the task from — it has to fall back to the one the last session ran on.
            runSessionOut(taskId = 9)
            assertThat(viewModel.uiState.value.finishedSession).isNotNull()

            viewModel.onEvent(FocusContract.UiEvent.CompleteSessionTask)
            advanceUntilIdle()

            coVerify { toggleTaskCompletion(open) }
            assertThat(viewModel.uiState.value.finishedSession).isNull()
        }

    @Test
    fun `finishing from the sheet is a no-op when the task is already done`() =
        runTest {
            val done = task(9, "Report", isCompleted = true)
            stubDay(agendaOf(todayItems = listOf(FocusAgendaItem.TaskEntry(done))))
            val viewModel = createViewModel()
            advanceUntilIdle()
            runSessionOut(taskId = 9)

            viewModel.onEvent(FocusContract.UiEvent.CompleteSessionTask)
            advanceUntilIdle()

            coVerify(exactly = 0) { toggleTaskCompletion(any()) }
            // The sheet still closes: the choice was made either way.
            assertThat(viewModel.uiState.value.finishedSession).isNull()
        }

    @Test
    fun `marking the session's task done completes it and ends the session`() =
        runTest {
            val open = task(4, "Report")
            stubDay(agendaOf(upNext = FocusAgendaItem.TaskEntry(open)))
            val session = FocusSession.start(4, "Report", nowInstant)
            sessionFlow.value = session
            coEvery { focusSessionUseCases.end() } returns session

            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.onEvent(FocusContract.UiEvent.CompleteSessionTask)
            advanceUntilIdle()

            coVerify { toggleTaskCompletion(open) }
            coVerify { focusSessionUseCases.end() }
        }

    @Test
    fun `marking done a subtask session started from its reminder completes the subtask`() =
        runTest {
            // A subtask's own reminder can start a session on it, while the agenda only ever
            // shows the subtask nested inside its parent.
            val parent = task(4, "Report")
            val subtask = task(5, "Charts")
            stubDay(agendaOf(todayItems = listOf(FocusAgendaItem.TaskEntry(parent, subtasks = listOf(subtask)))))
            val session = FocusSession.start(5, "Charts", nowInstant)
            sessionFlow.value = session
            coEvery { focusSessionUseCases.end() } returns session

            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.onEvent(FocusContract.UiEvent.CompleteSessionTask)
            advanceUntilIdle()

            coVerify { toggleTaskCompletion(subtask) }
            coVerify(exactly = 0) { toggleTaskCompletion(parent) }
            coVerify { focusSessionUseCases.end() }
        }

    @Test
    fun `marking a previewed task done leaves the session running on its own task`() =
        runTest {
            val running = task(4, "Cocinar")
            val looked = task(9, "Tarea")
            stubDay(
                agendaOf(
                    upNext = FocusAgendaItem.TaskEntry(running),
                    todayItems = listOf(FocusAgendaItem.TaskEntry(looked)),
                ),
            )
            sessionFlow.value = FocusSession.start(4, "Cocinar", nowInstant)
            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.onEvent(FocusContract.UiEvent.PreviewUpNext(9))
            advanceUntilIdle()
            viewModel.onEvent(FocusContract.UiEvent.CompleteSessionTask)
            advanceUntilIdle()

            coVerify { toggleTaskCompletion(looked) }
            // The timer was never on this task, so finishing it has no timer to end.
            coVerify(exactly = 0) { focusSessionUseCases.end() }
        }

    @Test
    fun `marking done clears the preview, so the screen never lingers on finished work`() =
        runTest {
            val open = task(9, "Tarea")
            stubDay(agendaOf(todayItems = listOf(FocusAgendaItem.TaskEntry(open))))
            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.onEvent(FocusContract.UiEvent.PreviewUpNext(9))
            advanceUntilIdle()
            viewModel.onEvent(FocusContract.UiEvent.CompleteSessionTask)
            advanceUntilIdle()

            assertThat(viewModel.uiState.value.previewTaskId).isNull()
        }

    @Test
    fun `reaching for the running session drops a preview left on another card`() =
        runTest {
            val running = task(4, "Cocinar")
            stubDay(agendaOf(upNext = FocusAgendaItem.TaskEntry(running)))
            sessionFlow.value = FocusSession.start(4, "Cocinar", nowInstant)
            val viewModel = createViewModel()
            advanceUntilIdle()
            previewFlow.value = 9
            advanceUntilIdle()

            viewModel.onEvent(FocusContract.UiEvent.OpenSessionScreen)
            advanceUntilIdle()

            assertThat(viewModel.uiState.value.previewTaskId).isNull()
            assertThat(viewModel.uiState.value.sessionTaskId).isEqualTo(4)
        }

    @Test
    fun `another session restarts on the task the last one ran`() =
        runTest {
            stubDay()
            val viewModel = createViewModel()
            advanceUntilIdle()
            runSessionOut(taskId = 11)

            viewModel.onEvent(FocusContract.UiEvent.StartAnotherSession)
            advanceUntilIdle()

            coVerify { focusSessionUseCases.start(taskId = 11, title = "Report") }
        }

    @Test
    fun `the configured session length reaches state`() =
        runTest {
            stubDay()
            lengthFlow.value = 45

            val viewModel = createViewModel()
            advanceUntilIdle()

            assertThat(viewModel.uiState.value.defaultSessionLengthMinutes).isEqualTo(45)
        }

    @Test
    fun `starting while a break runs replaces it without asking`() =
        runTest {
            val entry = FocusAgendaItem.TaskEntry(task(9, "Report"))
            stubDay(agendaOf(upNext = entry, todayItems = listOf(entry)))
            sessionFlow.value = FocusSession.start(4, "Something else", nowInstant, isBreak = true)

            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.onEvent(FocusContract.UiEvent.StartSession(taskId = 9))
            advanceUntilIdle()

            // A break has no minutes to lose, so there is nothing to warn about.
            assertThat(viewModel.uiState.value.pendingStart).isNull()
            coVerify { focusSessionUseCases.start(taskId = 9, title = "Report", lengthMinutes = null) }
        }

    @Test
    fun `dismissing the replacement leaves the running session alone`() =
        runTest {
            val entry = FocusAgendaItem.TaskEntry(task(9, "Report"))
            stubDay(agendaOf(upNext = entry, todayItems = listOf(entry)))
            sessionFlow.value = FocusSession.start(4, "Something else", nowInstant)

            val viewModel = createViewModel()
            advanceUntilIdle()
            viewModel.onEvent(FocusContract.UiEvent.StartSession(taskId = 9))
            advanceUntilIdle()

            viewModel.onEvent(FocusContract.UiEvent.DismissPendingStart)
            advanceUntilIdle()

            assertThat(viewModel.uiState.value.pendingStart).isNull()
            coVerify(exactly = 0) { focusSessionUseCases.start(any(), any(), any(), any()) }
        }

    @Test
    fun `confirming the replacement starts the session that was waiting on it`() =
        runTest {
            val entry = FocusAgendaItem.TaskEntry(task(9, "Report"))
            stubDay(agendaOf(upNext = entry, todayItems = listOf(entry)))
            sessionFlow.value = FocusSession.start(4, "Something else", nowInstant)

            val viewModel = createViewModel()
            advanceUntilIdle()
            viewModel.onEvent(FocusContract.UiEvent.StartSession(taskId = 9, lengthMinutes = 45))
            advanceUntilIdle()
            assertThat(
                viewModel.uiState.value.pendingStart
                    ?.taskId,
            ).isEqualTo(9)

            viewModel.onEvent(FocusContract.UiEvent.ConfirmPendingStart)
            advanceUntilIdle()

            // The length the user picked survives the question — the dialog holds the start, it
            // does not rewrite it.
            assertThat(viewModel.uiState.value.pendingStart).isNull()
            coVerify { focusSessionUseCases.start(taskId = 9, title = "Report", lengthMinutes = 45) }
        }

    @Test
    fun `confirming with nothing waiting starts nothing`() =
        runTest {
            stubDay()
            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.onEvent(FocusContract.UiEvent.ConfirmPendingStart)
            advanceUntilIdle()

            coVerify(exactly = 0) { focusSessionUseCases.start(any(), any(), any(), any()) }
        }

    @Test
    fun `back to work cuts the break short and restarts on the same task`() =
        runTest {
            val entry = FocusAgendaItem.TaskEntry(task(9, "Report"))
            stubDay(agendaOf(upNext = entry, todayItems = listOf(entry)))
            sessionFlow.value = FocusSession.start(9, "Report", nowInstant, isBreak = true)

            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.onEvent(FocusContract.UiEvent.BackToWork)
            advanceUntilIdle()

            coVerify { focusSessionUseCases.start(taskId = 9, title = "Report", lengthMinutes = null) }
        }

    @Test
    fun `back to work does nothing when the session is not a break`() =
        runTest {
            val entry = FocusAgendaItem.TaskEntry(task(9, "Report"))
            stubDay(agendaOf(upNext = entry, todayItems = listOf(entry)))
            sessionFlow.value = FocusSession.start(9, "Report", nowInstant)

            val viewModel = createViewModel()
            advanceUntilIdle()

            // There is no break to come back from, and restarting would throw away the minutes the
            // running session has earned.
            viewModel.onEvent(FocusContract.UiEvent.BackToWork)
            advanceUntilIdle()

            coVerify(exactly = 0) { focusSessionUseCases.start(any(), any(), any(), any()) }
        }
}
