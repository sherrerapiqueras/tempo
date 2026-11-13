package com.mandrecode.tempo.features.focus.presentation

import android.util.Log
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.mandrecode.tempo.core.domain.model.VacationPeriod
import com.mandrecode.tempo.features.focus.domain.model.FocusAgendaItem
import com.mandrecode.tempo.features.focus.domain.model.FocusHeadlineBand
import com.mandrecode.tempo.features.focus.domain.model.FocusSession
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import org.junit.Test
import java.io.IOException
import kotlin.time.Duration.Companion.days

/** The day Focus shows: agenda, counts, streak, and opening items from it. */
@OptIn(ExperimentalCoroutinesApi::class)
class FocusViewModelTest : FocusViewModelHarness() {
    /**
     * The view model is created at startup, ahead of the tab (#438), so showing the tab — not
     * creating the view model — is what recounts: work may have changed elsewhere in between.
     */
    @Test
    fun `showing the tab recounts the day, creating the view model does not`() =
        runTest {
            stubDay()

            val viewModel = createViewModel()
            advanceUntilIdle()
            coVerify(exactly = 0) { recordDailyActivity(any()) }

            viewModel.onTabShown()
            advanceUntilIdle()

            coVerify(exactly = 1) { recordDailyActivity(clockToday()) }
        }

    @Test
    fun `showing the tab after midnight moves Focus onto the new day`() =
        runTest {
            val previousDay = clockToday()
            val currentDay = previousDay.plus(1, DateTimeUnit.DAY)
            every { getFocusAgenda(previousDay) } returns MutableStateFlow(agendaOf())
            every { getFocusAgenda(currentDay) } returns MutableStateFlow(agendaOf())
            every { getFocusHistory(any(), any()) } returns flowOf(emptyList())
            coEvery { getFocusStreak(any()) } returns 0
            val viewModel = createViewModel()
            advanceUntilIdle()

            clockNow += 1.days
            viewModel.onTabShown()
            advanceUntilIdle()

            assertThat(viewModel.uiState.value.today).isEqualTo(currentDay)
            coVerify { recordDailyActivity(currentDay) }
        }

    /**
     * The view model is created at startup even when Focus is hidden (#438), so a failed load must
     * not crash the app, and showing the tab tries again.
     */
    @Test
    fun `a failed load does not crash and showing the tab tries again`() =
        runTest {
            mockkStatic(Log::class)
            try {
                every { Log.w(any(), any<String>(), any()) } returns 0
                val today = clockToday()
                every { getFocusAgenda(today) } returnsMany
                    listOf(flow { throw IOException("database closed") }, MutableStateFlow(agendaOf()))
                every { getFocusHistory(any(), any()) } returns flowOf(emptyList())
                coEvery { getFocusStreak(any()) } returns 0
                val viewModel = createViewModel()
                advanceUntilIdle()
                assertThat(viewModel.uiState.value.isLoading).isTrue()

                viewModel.onTabShown()
                advanceUntilIdle()

                assertThat(viewModel.uiState.value.isLoading).isFalse()
            } finally {
                unmockkStatic(Log::class)
            }
        }

    /**
     * A task added while Focus is open — from its own editor, say — changes today's counts, and
     * the day's history is recounted once the save has landed rather than waiting for the tab to
     * be shown again (#434).
     */
    @Test
    fun `work added while Focus is open recounts the day`() =
        runTest {
            val agenda = MutableStateFlow(agendaOf())
            every { getFocusAgenda(any()) } returns agenda
            every { getFocusHistory(any(), any()) } returns flowOf(emptyList())
            coEvery { getFocusStreak(any()) } returns 0
            createViewModel()
            advanceUntilIdle()
            coVerify(exactly = 0) { recordDailyActivity(any()) }

            val entry = FocusAgendaItem.TaskEntry(task(1))
            agenda.value = agendaOf(todayItems = listOf(entry))
            advanceUntilIdle()

            coVerify(exactly = 1) { recordDailyActivity(clockToday()) }
        }

    @Test
    fun `state carries the agenda, streak and counts`() =
        runTest {
            val entry = FocusAgendaItem.TaskEntry(task(1))
            stubDay(agendaOf(upNext = entry, todayItems = listOf(entry), undated = 3))

            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.uiState.test {
                val state = awaitItem()
                assertThat(state.isLoading).isFalse()
                assertThat(state.streakDays).isEqualTo(14)
                assertThat(state.upNext).containsExactly(entry)
                assertThat(state.undatedTaskCount).isEqualTo(3)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `the headline band follows the counts`() =
        runTest {
            val items = (1L..4L).map { FocusAgendaItem.TaskEntry(task(it, isCompleted = it <= 3)) }
            stubDay(agendaOf(todayItems = items))

            val viewModel = createViewModel()
            advanceUntilIdle()

            assertThat(viewModel.uiState.value.headlineBand).isEqualTo(FocusHeadlineBand.NEARLY_THERE)
        }

    @Test
    fun `editing a task opens its editor in Focus, without leaving the tab`() =
        runTest {
            stubDay()
            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.onEvent(FocusContract.UiEvent.EditTask(task(2)))
            advanceUntilIdle()

            assertThat(viewModel.uiState.value.taskEditor)
                .isEqualTo(FocusContract.TaskEditorTarget.Existing(task(2)))

            viewModel.onEvent(FocusContract.UiEvent.DismissEditor)
            advanceUntilIdle()

            assertThat(viewModel.uiState.value.taskEditor).isNull()
        }

    @Test
    fun `adding from the bar sets up a new session on the session screen, not a task form`() =
        runTest {
            stubDay()
            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.onEvent(FocusContract.UiEvent.AddSession)
            advanceUntilIdle()

            assertThat(viewModel.uiState.value.sessionSetup).isEqualTo(FocusContract.SessionEditorTarget())
            assertThat(viewModel.uiState.value.taskEditor).isNull()

            viewModel.onEvent(FocusContract.UiEvent.DismissSessionEditor)
            advanceUntilIdle()
            assertThat(viewModel.uiState.value.sessionSetup).isNull()
        }

    @Test
    fun `adding a subtask opens the editor on the parent rather than on a task`() =
        runTest {
            stubDay()
            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.onEvent(FocusContract.UiEvent.AddSubtask(7))
            advanceUntilIdle()

            assertThat(viewModel.uiState.value.taskEditor)
                .isEqualTo(FocusContract.TaskEditorTarget.NewSubtask(7))
        }

    @Test
    fun `the undated footer opens the plan sheet without leaving Focus`() =
        runTest {
            stubDay()
            undatedTasks.value = listOf(undatedRow(1), undatedRow(2))
            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.onEvent(FocusContract.UiEvent.UndatedTasksClicked)
            advanceUntilIdle()

            val sheet = viewModel.uiState.value.planSheet
            assertThat(sheet).isNotNull()
            assertThat(sheet?.rows?.map { it.task.id }).containsExactly(1L, 2L)
            assertThat(sheet?.isLoading).isFalse()
        }

    @Test
    fun `marking done is a no-op when the task is already complete`() =
        runTest {
            val done = task(4, "Report", isCompleted = true)
            stubDay(agendaOf(upNext = FocusAgendaItem.TaskEntry(done)))
            sessionFlow.value = FocusSession.start(4, "Report", nowInstant)

            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.onEvent(FocusContract.UiEvent.CompleteSessionTask)
            advanceUntilIdle()

            coVerify(exactly = 0) { toggleTaskCompletion(any()) }
        }

    @Test
    fun `a day inside a vacation period says so`() =
        runTest {
            stubDay()
            vacationPeriods.value = listOf(VacationPeriod(start = today.minus(2, DateTimeUnit.DAY)))

            val viewModel = createViewModel()
            advanceUntilIdle()

            // Drives the palm on the title, the same badge Routines carries while paused.
            assertThat(viewModel.uiState.value.isVacationModeActive).isTrue()
        }

    @Test
    fun `a day outside every vacation period does not`() =
        runTest {
            stubDay()
            vacationPeriods.value =
                listOf(
                    VacationPeriod(
                        start = today.minus(9, DateTimeUnit.DAY),
                        endInclusive = today.minus(4, DateTimeUnit.DAY),
                    ),
                )

            val viewModel = createViewModel()
            advanceUntilIdle()

            assertThat(viewModel.uiState.value.isVacationModeActive).isFalse()
        }

    @Test
    fun `pausing while Focus is open recounts the streak, not just the badge`() =
        runTest {
            stubDay()
            coEvery { getFocusStreak(any()) } returns 3
            val viewModel = createViewModel()
            advanceUntilIdle()
            assertThat(viewModel.uiState.value.streakDays).isEqualTo(3)

            // The streak is counted on the vacation periods' terms, so a pause taken without
            // leaving the screen has to move the number, not only raise the badge beside it.
            coEvery { getFocusStreak(any()) } returns 9
            vacationPeriods.value = listOf(VacationPeriod(start = today))
            advanceUntilIdle()

            assertThat(viewModel.uiState.value.streakDays).isEqualTo(9)
            assertThat(viewModel.uiState.value.isVacationModeActive).isTrue()
        }

    @Test
    fun `a task's subtasks fold and unfold`() =
        runTest {
            stubDay()
            val viewModel = createViewModel()
            advanceUntilIdle()

            // Folded is the resting state, so the first toggle has to open rather than close.
            viewModel.onEvent(FocusContract.UiEvent.ToggleSubtasksExpanded(9))
            assertThat(viewModel.uiState.value.expandedTaskIds).containsExactly(9L)

            viewModel.onEvent(FocusContract.UiEvent.ToggleSubtasksExpanded(9))
            assertThat(viewModel.uiState.value.expandedTaskIds).isEmpty()
        }

    @Test
    fun `completing a task after midnight replaces the observed day`() =
        runTest {
            val previousDay = clockToday()
            val currentDay = previousDay.plus(1, DateTimeUnit.DAY)
            val previousAgenda = MutableStateFlow(agendaOf())
            val entry = FocusAgendaItem.TaskEntry(task(3, isCompleted = true))
            val currentAgenda = MutableStateFlow(agendaOf(todayItems = listOf(entry)))
            every { getFocusAgenda(previousDay) } returns previousAgenda
            every { getFocusAgenda(currentDay) } returns currentAgenda
            every { getFocusHistory(any(), any()) } returns flowOf(emptyList())
            coEvery { getFocusStreak(any()) } returns 14
            val viewModel = createViewModel()
            advanceUntilIdle()

            clockNow += 1.days
            viewModel.onEvent(FocusContract.UiEvent.ToggleTaskCompletion(task(3)))
            advanceUntilIdle()

            coVerify { toggleTaskCompletion(task(3)) }
            assertThat(viewModel.uiState.value.today).isEqualTo(currentDay)
            assertThat(viewModel.uiState.value.todayItems).containsExactly(entry)

            previousAgenda.value = agendaOf(todayItems = listOf(FocusAgendaItem.TaskEntry(task(99))))
            advanceUntilIdle()
            assertThat(viewModel.uiState.value.todayItems).containsExactly(entry)

            viewModel.onEvent(FocusContract.UiEvent.ToggleTaskCompletion(task(4)))
            advanceUntilIdle()
            io.mockk.verify(exactly = 1) { getFocusAgenda(currentDay) }
        }

    @Test
    fun `an empty task day has zero progress and no session candidate`() =
        runTest {
            stubDay(agendaOf())
            val viewModel = createViewModel()
            advanceUntilIdle()

            assertThat(viewModel.uiState.value.isDayEmpty).isTrue()
            assertThat(viewModel.uiState.value.scheduledCount).isEqualTo(0)
            assertThat(viewModel.uiState.value.completedCount).isEqualTo(0)
            assertThat(viewModel.uiState.value.progress).isEqualTo(0f)
            viewModel.onEvent(FocusContract.UiEvent.StartSession())
            advanceUntilIdle()
            coVerify(exactly = 0) { focusSessionUseCases.start(any(), any(), any()) }
        }
}
