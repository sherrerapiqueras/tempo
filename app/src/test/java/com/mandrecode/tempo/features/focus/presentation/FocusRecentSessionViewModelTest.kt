package com.mandrecode.tempo.features.focus.presentation

import android.util.Log
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.mandrecode.tempo.features.focus.domain.model.FocusAgendaItem
import com.mandrecode.tempo.features.focus.domain.model.FocusSession
import com.mandrecode.tempo.features.focus.domain.model.SavedSession
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.time.Duration.Companion.minutes

/** Topic sessions (#517): setting one up, starting it, and running the recent ones again. */
@OptIn(ExperimentalCoroutinesApi::class)
class FocusRecentSessionViewModelTest : FocusViewModelHarness() {
    private val study get() = savedSession(4)

    /** What the sheet hands over on Start: the form as typed, with no id. */
    private fun draft(title: String = "Study") = SavedSession(0, title, focusMinutes = 25, breakMinutes = 5)

    /** `android.util.Log` is a stub on the JVM; the failure paths under test log through it. */
    private inline fun withLogMocked(block: () -> Unit) {
        mockkStatic(Log::class)
        every { Log.w(any<String>(), any<String>(), any()) } returns 0
        try {
            block()
        } finally {
            unmockkStatic(Log::class)
        }
    }

    @Test
    fun `recent sessions reach the screen, most recent first`() =
        runTest {
            stubDay()
            recentSessions.value = listOf(savedSession(5, "Work"), study)

            val viewModel = createViewModel()
            advanceUntilIdle()

            assertThat(
                viewModel.uiState.value.recentSessions
                    .map { it.title },
            ).containsExactly("Work", "Study")
                .inOrder()
        }

    @Test
    fun `a failing recents query leaves the list empty instead of crashing`() =
        runTest {
            stubDay()
            every { getSavedSessions() } returns flow { error("boom") }

            withLogMocked {
                val viewModel = createViewModel()
                advanceUntilIdle()

                assertThat(viewModel.uiState.value.recentSessions).isEmpty()
            }
        }

    @Test
    fun `adding a session opens the session screen on a new one`() =
        runTest {
            stubDay()
            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.uiEffect.test {
                viewModel.onEvent(FocusContract.UiEvent.AddSession)
                advanceUntilIdle()

                assertThat(awaitItem()).isEqualTo(FocusContract.UiEffect.OpenSessionScreen)
                cancelAndIgnoreRemainingEvents()
            }
            assertThat(viewModel.uiState.value.isSettingUpSession).isTrue()
            assertThat(viewModel.uiState.value.sessionSubjectKey).isEqualTo("session:0")
        }

    @Test
    fun `starting a new session remembers it, runs it, and hands the screen its real id`() =
        runTest {
            stubDay()
            val viewModel = createViewModel()
            advanceUntilIdle()
            viewModel.onEvent(FocusContract.UiEvent.AddSession)
            advanceUntilIdle()

            viewModel.uiEffect.test {
                viewModel.onEvent(FocusContract.UiEvent.StartNewSession(draft()))
                advanceUntilIdle()

                assertThat(awaitItem()).isEqualTo(FocusContract.UiEffect.OpenSessionScreen)
                cancelAndIgnoreRemainingEvents()
            }
            coVerify { rememberSession(draft()) }
            coVerify { focusSessionUseCases.startSaved(draft().copy(id = REMEMBERED_ID)) }
            // What lets the screen follow the timer it started rather than close on it.
            assertThat(viewModel.uiState.value.startedNewSessionId).isEqualTo(REMEMBERED_ID)
            // Starting ends the setup; the timer is the subject from here.
            assertThat(viewModel.uiState.value.setupSessionId).isNull()
        }

    @Test
    fun `a nameless session is neither remembered nor started`() =
        runTest {
            stubDay()
            val viewModel = createViewModel()
            advanceUntilIdle()
            viewModel.onEvent(FocusContract.UiEvent.AddSession)
            advanceUntilIdle()

            viewModel.onEvent(FocusContract.UiEvent.StartNewSession(draft(title = " ")))
            advanceUntilIdle()

            coVerify(exactly = 0) { focusSessionUseCases.startSaved(any()) }
            assertThat(viewModel.uiState.value.startedNewSessionId).isNull()
        }

    @Test
    fun `nothing starts when no session is being set up`() =
        runTest {
            stubDay()
            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.onEvent(FocusContract.UiEvent.StartNewSession(draft()))
            advanceUntilIdle()

            coVerify(exactly = 0) { rememberSession(any()) }
        }

    @Test
    fun `a second tap on start while the first is on its way starts nothing more`() =
        runTest {
            stubDay()
            val viewModel = createViewModel()
            advanceUntilIdle()
            viewModel.onEvent(FocusContract.UiEvent.AddSession)
            advanceUntilIdle()

            viewModel.onEvent(FocusContract.UiEvent.StartNewSession(draft()))
            viewModel.onEvent(FocusContract.UiEvent.StartNewSession(draft()))
            advanceUntilIdle()

            coVerify(exactly = 1) { rememberSession(any()) }
        }

    @Test
    fun `starting a new session over a running timer asks first, and remembers it only once confirmed`() =
        runTest {
            stubDay()
            sessionFlow.value = FocusSession.start(taskId = 1, title = "Report", now = nowInstant)
            val viewModel = createViewModel()
            advanceUntilIdle()
            viewModel.onEvent(FocusContract.UiEvent.AddSession)
            advanceUntilIdle()

            viewModel.onEvent(FocusContract.UiEvent.StartNewSession(draft()))
            advanceUntilIdle()

            val pending = viewModel.uiState.value.pendingStart
            assertThat(pending?.newSession).isEqualTo(draft())
            assertThat(pending?.replacingTaskTitle).isEqualTo("Report")
            coVerify(exactly = 0) { rememberSession(any()) }

            viewModel.onEvent(FocusContract.UiEvent.ConfirmPendingStart)
            advanceUntilIdle()

            assertThat(viewModel.uiState.value.pendingStart).isNull()
            coVerify { rememberSession(draft()) }
            coVerify { focusSessionUseCases.startSaved(draft().copy(id = REMEMBERED_ID)) }
        }

    @Test
    fun `backing out of replacing the running timer leaves no trace of the new session`() =
        runTest {
            stubDay()
            sessionFlow.value = FocusSession.start(taskId = 1, title = "Report", now = nowInstant)
            val viewModel = createViewModel()
            advanceUntilIdle()
            viewModel.onEvent(FocusContract.UiEvent.AddSession)
            advanceUntilIdle()

            viewModel.onEvent(FocusContract.UiEvent.StartNewSession(draft()))
            viewModel.onEvent(FocusContract.UiEvent.DismissPendingStart)
            advanceUntilIdle()

            coVerify(exactly = 0) { rememberSession(any()) }
        }

    @Test
    fun `starting a new session during a break does not ask`() =
        runTest {
            stubDay()
            sessionFlow.value = FocusSession.start(taskId = 1, title = "Report", now = nowInstant, isBreak = true)
            val viewModel = createViewModel()
            advanceUntilIdle()
            viewModel.onEvent(FocusContract.UiEvent.AddSession)
            advanceUntilIdle()

            viewModel.onEvent(FocusContract.UiEvent.StartNewSession(draft()))
            advanceUntilIdle()

            assertThat(viewModel.uiState.value.pendingStart).isNull()
            coVerify { focusSessionUseCases.startSaved(any()) }
        }

    @Test
    fun `running a recent topic again moves it back to the front`() =
        runTest {
            stubDay()
            recentSessions.value = listOf(study)
            val viewModel = createViewModel()
            advanceUntilIdle()
            runSessionOut(taskId = null, title = "Study", cycle = FocusSession.start(study, nowInstant).cycle)

            viewModel.onEvent(FocusContract.UiEvent.StartAnotherSession)
            advanceUntilIdle()

            coVerify { rememberSession(study) }
        }

    @Test
    fun `a topic session has nothing to mark done`() =
        runTest {
            stubDay()
            sessionFlow.value = FocusSession.start(study, nowInstant)
            val viewModel = createViewModel()
            advanceUntilIdle()

            assertThat(viewModel.uiState.value.canCompleteRunningTask).isFalse()
        }

    @Test
    fun `a topic session finishes with nothing to mark done, and its own rest on offer`() =
        runTest {
            stubDay()
            val viewModel = createViewModel()
            advanceUntilIdle()

            runSessionOut(taskId = null, title = "Study", cycle = FocusSession.start(study, nowInstant).cycle)

            val finished = viewModel.uiState.value.finishedSession
            assertThat(finished?.hasTask).isFalse()
            assertThat(finished?.title).isEqualTo("Study")
            assertThat(finished?.breakMinutes).isEqualTo(5)
        }

    @Test
    fun `moving from a round to its rest is not a finished session`() =
        runTest {
            stubDay()
            val viewModel = createViewModel()
            advanceUntilIdle()
            val round = FocusSession.start(savedSession(4, rounds = 2), nowInstant - 30.minutes)
            sessionFlow.value = round
            advanceUntilIdle()

            sessionFlow.value = round.nextPhase(nowInstant)
            advanceUntilIdle()

            assertThat(viewModel.uiState.value.finishedSession).isNull()
            assertThat(
                viewModel.uiState.value.session
                    ?.isBreak,
            ).isTrue()
        }

    @Test
    fun `another session after a recent topic restarts it from its first round`() =
        runTest {
            stubDay()
            recentSessions.value = listOf(study)
            val viewModel = createViewModel()
            advanceUntilIdle()
            runSessionOut(taskId = null, title = "Study", cycle = FocusSession.start(study, nowInstant).cycle)

            viewModel.onEvent(FocusContract.UiEvent.StartAnotherSession)
            advanceUntilIdle()

            coVerify { focusSessionUseCases.startSaved(study) }
        }

    @Test
    fun `another session after a topic that has since dropped off the recents runs under the same name`() =
        runTest {
            stubDay()
            val viewModel = createViewModel()
            advanceUntilIdle()
            runSessionOut(taskId = null, title = "Study", cycle = FocusSession.start(study, nowInstant).cycle)

            viewModel.onEvent(FocusContract.UiEvent.StartAnotherSession)
            advanceUntilIdle()

            coVerify { focusSessionUseCases.start(taskId = null, title = "Study") }
        }

    @Test
    fun `a break after a topic session runs for its own rest and stays that session`() =
        runTest {
            stubDay()
            val viewModel = createViewModel()
            advanceUntilIdle()
            val cycle = FocusSession.start(study.copy(breakMinutes = 12), nowInstant).cycle
            runSessionOut(taskId = null, title = "Study", cycle = cycle)

            viewModel.onEvent(FocusContract.UiEvent.TakeBreak)
            advanceUntilIdle()

            coVerify {
                focusSessionUseCases.start(
                    taskId = null,
                    title = "Study",
                    isBreak = true,
                    lengthMinutes = 12,
                    cycle = cycle,
                )
            }
        }

    @Test
    fun `back to work between rounds skips the rest rather than starting over`() =
        runTest {
            stubDay()
            recentSessions.value = listOf(savedSession(4, rounds = 2))
            sessionFlow.value = FocusSession.start(savedSession(4, rounds = 2), nowInstant).nextPhase(nowInstant)
            every { focusSessionUseCases.skipBreak() } returns true
            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.onEvent(FocusContract.UiEvent.BackToWork)
            advanceUntilIdle()

            coVerify(exactly = 0) { focusSessionUseCases.startSaved(any()) }
        }

    @Test
    fun `back to work from a rest after the last round restarts the topic`() =
        runTest {
            stubDay()
            recentSessions.value = listOf(study)
            sessionFlow.value = FocusSession.start(study, nowInstant).copy(isBreak = true)
            every { focusSessionUseCases.skipBreak() } returns false
            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.onEvent(FocusContract.UiEvent.BackToWork)
            advanceUntilIdle()

            assertThat(viewModel.uiState.value.pendingStart).isNull()
            coVerify { focusSessionUseCases.startSaved(study) }
        }

    @Test
    fun `back to work from a break with neither a task nor a recent topic starts a plain session`() =
        runTest {
            stubDay()
            sessionFlow.value = FocusSession.start(taskId = null, title = "Study", now = nowInstant, isBreak = true)
            every { focusSessionUseCases.skipBreak() } returns false
            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.onEvent(FocusContract.UiEvent.BackToWork)
            advanceUntilIdle()

            coVerify { focusSessionUseCases.start(taskId = null, title = "Study") }
        }

    @Test
    fun `back to work does nothing while a focus round is running`() =
        runTest {
            stubDay()
            sessionFlow.value = FocusSession.start(study, nowInstant)
            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.onEvent(FocusContract.UiEvent.BackToWork)
            advanceUntilIdle()

            coVerify(exactly = 0) { focusSessionUseCases.skipBreak() }
        }

    @Test
    fun `a task can be started with a rest and rounds for this run alone`() =
        runTest {
            val entry = FocusAgendaItem.TaskEntry(task(9, "Report"))
            stubDay(agendaOf(upNext = entry, todayItems = listOf(entry)))
            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.onEvent(
                FocusContract.UiEvent.StartSession(lengthMinutes = 50, taskId = 9, breakMinutes = 10, rounds = 3),
            )
            advanceUntilIdle()

            coVerify {
                focusSessionUseCases.start(
                    taskId = 9,
                    title = "Report",
                    lengthMinutes = 50,
                    cycle = FocusSession.adHocCycle(focusMinutes = 50, breakMinutes = 10, rounds = 3),
                )
            }
            coVerify(exactly = 0) { rememberSession(any()) }
        }

    @Test
    fun `a task start that had to ask first keeps its rest and rounds when confirmed`() =
        runTest {
            val entry = FocusAgendaItem.TaskEntry(task(9, "Report"))
            stubDay(agendaOf(upNext = entry, todayItems = listOf(entry)))
            sessionFlow.value = FocusSession.start(taskId = 1, title = "Emails", now = nowInstant)
            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.onEvent(FocusContract.UiEvent.StartSession(lengthMinutes = 25, taskId = 9, rounds = 2))
            advanceUntilIdle()
            assertThat(
                viewModel.uiState.value.pendingStart
                    ?.rounds,
            ).isEqualTo(2)

            viewModel.onEvent(FocusContract.UiEvent.ConfirmPendingStart)
            advanceUntilIdle()

            coVerify {
                focusSessionUseCases.start(
                    taskId = 9,
                    title = "Report",
                    lengthMinutes = 25,
                    cycle = FocusSession.adHocCycle(focusMinutes = 25, breakMinutes = 5, rounds = 2),
                )
            }
        }

    @Test
    fun `looking at a task instead forgets the session that was being set up`() =
        runTest {
            val entry = FocusAgendaItem.TaskEntry(task(9, "Report"))
            stubDay(agendaOf(upNext = entry, todayItems = listOf(entry)))
            val viewModel = createViewModel()
            advanceUntilIdle()
            viewModel.onEvent(FocusContract.UiEvent.AddSession)
            advanceUntilIdle()

            viewModel.onEvent(FocusContract.UiEvent.PreviewUpNext(9))
            advanceUntilIdle()

            assertThat(viewModel.uiState.value.isSettingUpSession).isFalse()
            assertThat(viewModel.uiState.value.sessionSubjectKey).isEqualTo("task:9")
        }

    @Test
    fun `a break after a session that ended part-way does not pick its rounds back up`() =
        runTest {
            stubDay()
            val viewModel = createViewModel()
            advanceUntilIdle()
            val midWay = FocusSession.start(savedSession(4, rounds = 4), nowInstant).cycle
            runSessionOut(taskId = null, title = "Study", cycle = midWay)

            viewModel.onEvent(FocusContract.UiEvent.TakeBreak)
            advanceUntilIdle()

            coVerify {
                focusSessionUseCases.start(
                    taskId = null,
                    title = "Study",
                    isBreak = true,
                    lengthMinutes = 5,
                    cycle = midWay?.copy(round = 4),
                )
            }
        }
}
