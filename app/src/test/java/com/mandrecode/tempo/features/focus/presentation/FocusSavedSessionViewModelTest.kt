package com.mandrecode.tempo.features.focus.presentation

import android.util.Log
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.mandrecode.tempo.features.focus.domain.model.FocusAgendaItem
import com.mandrecode.tempo.features.focus.domain.model.FocusSession
import com.mandrecode.tempo.features.focus.domain.model.SavedSessionEntry
import io.mockk.coEvery
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

/** Saved sessions (#517): making, changing, deleting and running them from Focus. */
@OptIn(ExperimentalCoroutinesApi::class)
class FocusSavedSessionViewModelTest : FocusViewModelHarness() {
    private val study get() = savedSession(4)

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

    private fun draftOf(
        id: Long = 0,
        title: String = "Study",
    ) = FocusContract.SessionDraft(id, title, focusMinutes = 25, breakMinutes = 5, rounds = 1, taskId = null)

    @Test
    fun `saved sessions reach the screen with their linked tasks`() =
        runTest {
            stubDay()
            val linked = SavedSessionEntry(savedSession(4, taskId = 9), linkedTask = task(9))
            savedSessions.value = listOf(linked)

            val viewModel = createViewModel()
            advanceUntilIdle()

            assertThat(viewModel.uiState.value.savedSessions).containsExactly(linked)
        }

    @Test
    fun `a failing sessions query leaves the row empty instead of crashing`() =
        runTest {
            stubDay()
            every { getSavedSessions() } returns flow { error("boom") }

            withLogMocked {
                val viewModel = createViewModel()
                advanceUntilIdle()

                assertThat(viewModel.uiState.value.savedSessions).isEmpty()
            }
        }

    @Test
    fun `opening the sheet loads the tasks that can be linked, and closing drops them`() =
        runTest {
            stubDay()
            linkableTasks.value = listOf(task(1), task(2))
            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.onEvent(FocusContract.UiEvent.AddSession)
            advanceUntilIdle()
            assertThat(
                viewModel.uiState.value.linkableTasks
                    .map { it.id },
            ).containsExactly(1L, 2L)

            viewModel.onEvent(FocusContract.UiEvent.ClearSessionPreview)
            advanceUntilIdle()
            assertThat(viewModel.uiState.value.linkableTasks).isEmpty()
        }

    @Test
    fun `a failing task list still leaves the sheet open`() =
        runTest {
            stubDay()
            every { getLinkableTasks() } returns flow { error("boom") }
            val viewModel = createViewModel()
            advanceUntilIdle()

            withLogMocked {
                viewModel.onEvent(FocusContract.UiEvent.AddSession)
                advanceUntilIdle()

                assertThat(viewModel.uiState.value.sessionSetup).isNotNull()
            }
        }

    @Test
    fun `tapping a saved session opens the sheet on it, with its task`() =
        runTest {
            stubDay()
            val linked = SavedSessionEntry(savedSession(4, taskId = 9), linkedTask = task(9))
            savedSessions.value = listOf(linked)
            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.uiEffect.test {
                viewModel.onEvent(FocusContract.UiEvent.EditSession(99))
                viewModel.onEvent(FocusContract.UiEvent.EditSession(4))
                advanceUntilIdle()

                // The session screen itself, not a form of its own: one effect, for the one that exists.
                assertThat(awaitItem()).isEqualTo(FocusContract.UiEffect.OpenSessionScreen)
                expectNoEvents()
            }
            assertThat(viewModel.uiState.value.sessionSetup)
                .isEqualTo(FocusContract.SessionEditorTarget(session = linked.session, linkedTask = linked.linkedTask))
            assertThat(viewModel.uiState.value.sessionSubjectKey).isEqualTo("session:4")
        }

    @Test
    fun `saving stores the draft and closes the sheet without starting anything`() =
        runTest {
            stubDay()
            coEvery { saveSavedSession(any()) } returns study
            val viewModel = createViewModel()
            advanceUntilIdle()
            viewModel.onEvent(FocusContract.UiEvent.AddSession)

            viewModel.onEvent(FocusContract.UiEvent.SaveSession(draftOf()))
            advanceUntilIdle()

            coVerify { saveSavedSession(draftOf().toSavedSession()) }
            assertThat(viewModel.uiState.value.sessionSetup).isNull()
            coVerify(exactly = 0) { focusSessionUseCases.startSaved(any()) }
        }

    @Test
    fun `saving and starting runs the session just saved and opens it`() =
        runTest {
            stubDay()
            coEvery { saveSavedSession(any()) } returns study
            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.onEvent(FocusContract.UiEvent.AddSession)

            viewModel.uiEffect.test {
                viewModel.onEvent(FocusContract.UiEvent.SaveSession(draftOf(), startNow = true))
                advanceUntilIdle()

                assertThat(awaitItem()).isEqualTo(FocusContract.UiEffect.OpenSessionScreen)
                cancelAndIgnoreRemainingEvents()
            }
            coVerify { focusSessionUseCases.startSaved(study) }
        }

    @Test
    fun `saving and starting an edited session runs it as edited`() =
        runTest {
            stubDay()
            savedSessions.value = listOf(SavedSessionEntry(study))
            val edited = study.copy(focusMinutes = 50)
            coEvery { saveSavedSession(any()) } returns edited
            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.onEvent(FocusContract.UiEvent.EditSession(4))

            viewModel.onEvent(FocusContract.UiEvent.SaveSession(draftOf(id = 4), startNow = true))
            advanceUntilIdle()

            coVerify { focusSessionUseCases.startSaved(edited) }
        }

    @Test
    fun `a draft that could not be saved starts nothing`() =
        runTest {
            stubDay()
            coEvery { saveSavedSession(any()) } returns null
            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.onEvent(FocusContract.UiEvent.AddSession)

            viewModel.onEvent(FocusContract.UiEvent.SaveSession(draftOf(title = " "), startNow = true))
            advanceUntilIdle()

            coVerify(exactly = 0) { focusSessionUseCases.startSaved(any()) }
        }

    @Test
    fun `starting a saved session with nothing running starts it at once`() =
        runTest {
            stubDay()
            savedSessions.value = listOf(SavedSessionEntry(study))
            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.onEvent(FocusContract.UiEvent.StartSavedSession(4))
            viewModel.onEvent(FocusContract.UiEvent.StartSavedSession(99))
            advanceUntilIdle()

            coVerify(exactly = 1) { focusSessionUseCases.startSaved(any()) }
            coVerify { focusSessionUseCases.startSaved(study) }
        }

    @Test
    fun `starting a saved session over a running task timer asks first, and confirming starts it`() =
        runTest {
            stubDay()
            savedSessions.value = listOf(SavedSessionEntry(study))
            sessionFlow.value = FocusSession.start(taskId = 1, title = "Report", now = nowInstant)
            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.onEvent(FocusContract.UiEvent.StartSavedSession(4))
            advanceUntilIdle()

            val pending = viewModel.uiState.value.pendingStart
            assertThat(pending?.savedSessionId).isEqualTo(4)
            assertThat(pending?.replacingTaskTitle).isEqualTo("Report")
            coVerify(exactly = 0) { focusSessionUseCases.startSaved(any()) }

            viewModel.onEvent(FocusContract.UiEvent.ConfirmPendingStart)
            advanceUntilIdle()

            assertThat(viewModel.uiState.value.pendingStart).isNull()
            coVerify { focusSessionUseCases.startSaved(study) }
        }

    @Test
    fun `starting a saved session during a break does not ask`() =
        runTest {
            stubDay()
            savedSessions.value = listOf(SavedSessionEntry(study))
            sessionFlow.value = FocusSession.start(taskId = 1, title = "Report", now = nowInstant, isBreak = true)
            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.onEvent(FocusContract.UiEvent.StartSavedSession(4))
            advanceUntilIdle()

            assertThat(viewModel.uiState.value.pendingStart).isNull()
            coVerify { focusSessionUseCases.startSaved(study) }
        }

    @Test
    fun `starting a task's own timer over a saved session linked to it asks first`() =
        runTest {
            val entry = FocusAgendaItem.TaskEntry(task(9, "Report"))
            stubDay(agendaOf(upNext = entry, todayItems = listOf(entry)))
            sessionFlow.value = FocusSession.start(savedSession(4, taskId = 9), nowInstant)
            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.onEvent(FocusContract.UiEvent.StartSession(taskId = 9))
            advanceUntilIdle()

            assertThat(
                viewModel.uiState.value.pendingStart
                    ?.taskId,
            ).isEqualTo(9)
            assertThat(
                viewModel.uiState.value.pendingStart
                    ?.savedSessionId,
            ).isNull()
        }

    @Test
    fun `deleting a saved session removes it and closes the sheet`() =
        runTest {
            stubDay()
            savedSessions.value = listOf(SavedSessionEntry(study))
            val viewModel = createViewModel()
            advanceUntilIdle()
            viewModel.onEvent(FocusContract.UiEvent.EditSession(4))

            viewModel.onEvent(FocusContract.UiEvent.DeleteSession(4))
            advanceUntilIdle()

            coVerify { deleteSavedSession(4) }
            coVerify(exactly = 0) { focusSessionUseCases.end() }
            assertThat(viewModel.uiState.value.sessionSetup).isNull()
        }

    @Test
    fun `deleting the session that is running ends its timer first`() =
        runTest {
            stubDay()
            savedSessions.value = listOf(SavedSessionEntry(study))
            val running = FocusSession.start(study, nowInstant)
            sessionFlow.value = running
            coEvery { focusSessionUseCases.end() } returns running
            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.onEvent(FocusContract.UiEvent.DeleteSession(4))
            advanceUntilIdle()

            coVerify { focusSessionUseCases.end() }
            coVerify { deleteSavedSession(4) }
        }

    @Test
    fun `a session linked to no task finishes with nothing to mark done, and its own rest on offer`() =
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
    fun `another session after a saved one restarts it from its first round`() =
        runTest {
            stubDay()
            savedSessions.value = listOf(SavedSessionEntry(study))
            val viewModel = createViewModel()
            advanceUntilIdle()
            runSessionOut(taskId = null, title = "Study", cycle = FocusSession.start(study, nowInstant).cycle)

            viewModel.onEvent(FocusContract.UiEvent.StartAnotherSession)
            advanceUntilIdle()

            coVerify { focusSessionUseCases.startSaved(study) }
        }

    @Test
    fun `another session after a saved one that was since deleted runs under the same name`() =
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
    fun `a break after a saved session runs for its own rest and stays that session`() =
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
            savedSessions.value = listOf(SavedSessionEntry(savedSession(4, rounds = 2)))
            sessionFlow.value = FocusSession.start(savedSession(4, rounds = 2), nowInstant).nextPhase(nowInstant)
            every { focusSessionUseCases.skipBreak() } returns true
            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.onEvent(FocusContract.UiEvent.BackToWork)
            advanceUntilIdle()

            coVerify(exactly = 0) { focusSessionUseCases.startSaved(any()) }
        }

    @Test
    fun `back to work from a rest after the last round restarts the saved session`() =
        runTest {
            stubDay()
            savedSessions.value = listOf(SavedSessionEntry(study))
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
    fun `back to work from a break with neither a task nor its saved session starts a plain session`() =
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
    fun `marking done on a linked session completes its task even when it is not on today`() =
        runTest {
            stubDay()
            val elsewhere = task(8, "Read chapter 3")
            savedSessions.value = listOf(SavedSessionEntry(savedSession(4, taskId = 8), linkedTask = elsewhere))
            val running = FocusSession.start(savedSession(4, taskId = 8), nowInstant)
            sessionFlow.value = running
            coEvery { focusSessionUseCases.end() } returns running
            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.onEvent(FocusContract.UiEvent.CompleteSessionTask)
            advanceUntilIdle()

            coVerify { toggleTaskCompletion(elsewhere) }
            coVerify { focusSessionUseCases.end() }
        }

    @Test
    fun `a second tap on save while the first is being written saves nothing more`() =
        runTest {
            stubDay()
            coEvery { saveSavedSession(any()) } returns study
            val viewModel = createViewModel()
            advanceUntilIdle()
            viewModel.onEvent(FocusContract.UiEvent.AddSession)
            advanceUntilIdle()

            viewModel.onEvent(FocusContract.UiEvent.SaveSession(draftOf()))
            viewModel.onEvent(FocusContract.UiEvent.SaveSession(draftOf()))
            advanceUntilIdle()

            coVerify(exactly = 1) { saveSavedSession(any()) }
        }

    @Test
    fun `saving a new session and starting it moves the screen onto its real id, then runs it`() =
        runTest {
            stubDay()
            coEvery { saveSavedSession(any()) } returns study
            val viewModel = createViewModel()
            advanceUntilIdle()
            viewModel.onEvent(FocusContract.UiEvent.AddSession)
            advanceUntilIdle()
            assertThat(viewModel.uiState.value.sessionSubjectKey).isEqualTo("session:0")
            val seen = mutableListOf<Long?>()
            every { focusSessionRepository.setSetupSessionId(any()) } answers {
                seen += firstArg<Long?>()
                setupFlow.value = firstArg()
            }

            viewModel.onEvent(FocusContract.UiEvent.SaveSession(draftOf(), startNow = true))
            advanceUntilIdle()

            assertThat(seen).containsExactly(4L)
            coVerify { focusSessionUseCases.startSaved(study) }
            // Starting clears what was being set up; the timer is the subject from here.
            assertThat(viewModel.uiState.value.setupSessionId).isNull()
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
            coVerify(exactly = 0) { saveSavedSession(any()) }
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
            assertThat(
                viewModel.uiState.value.sessionSetup
                    ?.isNew,
            ).isTrue()
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

            assertThat(viewModel.uiState.value.sessionSetup).isNull()
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

    @Test
    fun `taking the link off a screen opened on a task leaves a new session being set up`() =
        runTest {
            val entry = FocusAgendaItem.TaskEntry(task(9, "Report"))
            stubDay(agendaOf(upNext = entry, todayItems = listOf(entry)))
            val viewModel = createViewModel()
            advanceUntilIdle()
            viewModel.onEvent(FocusContract.UiEvent.PreviewUpNext(9))
            advanceUntilIdle()

            viewModel.onEvent(FocusContract.UiEvent.UnlinkPreviewTask)
            advanceUntilIdle()

            assertThat(viewModel.uiState.value.previewTaskId).isNull()
            assertThat(
                viewModel.uiState.value.sessionSetup
                    ?.isNew,
            ).isTrue()
            assertThat(viewModel.uiState.value.sessionSubjectKey).isEqualTo("session:0")
        }

    @Test
    fun `a tap that still says new, after the session has its id, updates it instead of adding another`() =
        runTest {
            stubDay()
            coEvery { saveSavedSession(any()) } returns study
            val viewModel = createViewModel()
            advanceUntilIdle()
            // The screen's draft has not caught up: the session is already being set up as 4.
            setupFlow.value = 4

            viewModel.onEvent(FocusContract.UiEvent.SaveSession(draftOf(id = 0)))
            advanceUntilIdle()

            coVerify { saveSavedSession(draftOf(id = 4).toSavedSession()) }
        }

    @Test
    fun `nothing is saved when no session is being set up`() =
        runTest {
            stubDay()
            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.onEvent(FocusContract.UiEvent.SaveSession(draftOf()))
            advanceUntilIdle()

            coVerify(exactly = 0) { saveSavedSession(any()) }
        }
}
