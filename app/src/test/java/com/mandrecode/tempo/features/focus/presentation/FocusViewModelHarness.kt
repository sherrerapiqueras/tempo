package com.mandrecode.tempo.features.focus.presentation

import com.mandrecode.tempo.core.domain.model.DailyFocusActivity
import com.mandrecode.tempo.core.domain.model.VacationPeriod
import com.mandrecode.tempo.core.domain.repository.VacationModeRepository
import com.mandrecode.tempo.features.focus.domain.model.FocusAgenda
import com.mandrecode.tempo.features.focus.domain.model.FocusAgendaItem
import com.mandrecode.tempo.features.focus.domain.model.FocusSession
import com.mandrecode.tempo.features.focus.domain.model.SavedSession
import com.mandrecode.tempo.features.focus.domain.model.SavedSessionEntry
import com.mandrecode.tempo.features.focus.domain.model.SessionCycle
import com.mandrecode.tempo.features.focus.domain.repository.FocusSessionRepository
import com.mandrecode.tempo.features.focus.domain.usecase.DeleteSavedSessionUseCase
import com.mandrecode.tempo.features.focus.domain.usecase.FocusSessionUseCases
import com.mandrecode.tempo.features.focus.domain.usecase.GetFocusAgendaUseCase
import com.mandrecode.tempo.features.focus.domain.usecase.GetFocusHistoryUseCase
import com.mandrecode.tempo.features.focus.domain.usecase.GetFocusStreakUseCase
import com.mandrecode.tempo.features.focus.domain.usecase.GetLinkableTasksUseCase
import com.mandrecode.tempo.features.focus.domain.usecase.GetSavedSessionsUseCase
import com.mandrecode.tempo.features.focus.domain.usecase.RecordDailyActivityUseCase
import com.mandrecode.tempo.features.focus.domain.usecase.SaveSavedSessionUseCase
import com.mandrecode.tempo.features.tasks.domain.model.Category
import com.mandrecode.tempo.features.tasks.domain.model.Task
import com.mandrecode.tempo.features.tasks.domain.model.UndatedTask
import com.mandrecode.tempo.features.tasks.domain.usecase.GetUndatedTasksUseCase
import com.mandrecode.tempo.features.tasks.domain.usecase.RestoreTaskRemindersUseCase
import com.mandrecode.tempo.features.tasks.domain.usecase.ToggleTaskCompletionUseCase
import com.mandrecode.tempo.features.tasks.domain.usecase.UpdateTaskUseCase
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import org.junit.After
import org.junit.Before
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/**
 * Mocks, dispatcher and fixtures shared by the Focus view-model tests.
 *
 * Extracted when one test class outgrew what a single file should hold: the day's behaviour and
 * the timer's are two subjects, together only because they share a setup.
 */
@OptIn(ExperimentalCoroutinesApi::class)
abstract class FocusViewModelHarness {
    private val testDispatcher = StandardTestDispatcher()
    protected val today = LocalDate(2026, 7, 29)
    protected val nowInstant = Instant.fromEpochMilliseconds(1_800_000_000_000)
    protected var clockNow = nowInstant

    protected val getFocusAgenda = mockk<GetFocusAgendaUseCase>()
    protected val getFocusHistory = mockk<GetFocusHistoryUseCase>()
    protected val getFocusStreak = mockk<GetFocusStreakUseCase>(relaxed = true)
    protected val recordDailyActivity = mockk<RecordDailyActivityUseCase>(relaxed = true)
    protected val toggleTaskCompletion = mockk<ToggleTaskCompletionUseCase>(relaxed = true)
    protected val focusSessionUseCases = mockk<FocusSessionUseCases>(relaxed = true)
    protected val sessionFlow = MutableStateFlow<FocusSession?>(null)
    protected val lengthFlow = MutableStateFlow(25)
    protected val previewFlow = MutableStateFlow<Long?>(null)
    protected val setupFlow = MutableStateFlow<Long?>(null)
    protected val focusSessionRepository =
        mockk<FocusSessionRepository> {
            every { activeSession } returns sessionFlow
            every { defaultLengthMinutes } returns lengthFlow
            every { previewTaskId } returns previewFlow
            every { breakLengthMinutes } returns MutableStateFlow(5)
            every { setupSessionId } returns setupFlow
            // The real repository's rule: the screen is about one thing, so naming a task forgets
            // the session being set up, and clearing the preview clears both.
            every { setPreviewTaskId(any()) } answers {
                setupFlow.value = null
                previewFlow.value = firstArg()
            }
            every { setSetupSessionId(any()) } answers {
                val id: Long? = firstArg()
                if (id != null) previewFlow.value = null
                setupFlow.value = id
            }
        }

    /** What the plan sheet opens onto. Empty unless a test says otherwise. */
    protected val undatedTasks = MutableStateFlow<List<UndatedTask>>(emptyList())
    protected val getUndatedTasks =
        mockk<GetUndatedTasksUseCase> {
            every { this@mockk.invoke() } returns undatedTasks
            // Pinned rows follow the same source, filtered the way the real one filters: the tests
            // that matter here are about what the sheet does with a row, not about the query.
            every { pinnedTo(any()) } answers {
                val ids: Set<Long> = firstArg()
                undatedTasks.map { rows -> rows.filter { it.task.id in ids } }
            }
        }
    protected val updateTask = mockk<UpdateTaskUseCase>(relaxed = true)
    protected val restoreTaskReminders = mockk<RestoreTaskRemindersUseCase>(relaxed = true)

    /** The saved sessions on Focus. None unless a test says otherwise. */
    protected val savedSessions = MutableStateFlow<List<SavedSessionEntry>>(emptyList())
    protected val getSavedSessions =
        mockk<GetSavedSessionsUseCase> { every { this@mockk.invoke() } returns savedSessions }
    protected val linkableTasks = MutableStateFlow<List<Task>>(emptyList())
    protected val getLinkableTasks =
        mockk<GetLinkableTasksUseCase> { every { this@mockk.invoke() } returns linkableTasks }
    protected val saveSavedSession = mockk<SaveSavedSessionUseCase>(relaxed = true)
    protected val deleteSavedSession = mockk<DeleteSavedSessionUseCase>(relaxed = true)

    protected fun savedSession(
        id: Long,
        title: String = "Study",
        rounds: Int = 1,
        taskId: Long? = null,
    ) = SavedSession(id = id, title = title, focusMinutes = 25, breakMinutes = 5, rounds = rounds, taskId = taskId)

    protected val vacationPeriods = MutableStateFlow<List<VacationPeriod>>(emptyList())
    protected val vacationModeRepository =
        mockk<VacationModeRepository> { every { periods } returns vacationPeriods }

    protected val clock =
        object : Clock {
            override fun now(): Instant = clockNow
        }

    protected fun clockToday(): LocalDate = clock.todayIn(TimeZone.currentSystemDefault())

    protected fun task(
        id: Long,
        title: String = "Task $id",
        isCompleted: Boolean = false,
    ) = Task(
        id = id,
        title = title,
        description = "",
        isCompleted = isCompleted,
        reminderDate = LocalDateTime(today, LocalTime(9, 0)),
    )

    /** A row as the plan sheet first sees it: open, top-level, and carrying no date. */
    protected fun undatedRow(
        id: Long,
        title: String = "Loose end $id",
        category: Category? = null,
        reminderDate: LocalDateTime? = null,
    ) = UndatedTask(
        task =
            Task(
                id = id,
                title = title,
                description = "",
                reminderDate = reminderDate,
            ),
        category = category,
    )

    protected fun agendaOf(
        upNext: FocusAgendaItem.TaskEntry? = null,
        todayItems: List<FocusAgendaItem> = emptyList(),
        undated: Int = 0,
    ) = FocusAgenda(upNext = listOfNotNull(upNext), today = todayItems, undatedTaskCount = undated)

    protected fun stubDay(agenda: FocusAgenda = agendaOf()) {
        every { getFocusAgenda(any()) } returns flowOf(agenda)
        every { getFocusHistory(any(), any()) } returns
            flowOf(listOf(DailyFocusActivity(date = today, scheduledCount = 2, completedCount = 1)))
        coEvery { getFocusStreak(any()) } returns 14
    }

    protected fun createViewModel() =
        FocusViewModel(
            getFocusAgenda = getFocusAgenda,
            getFocusHistory = getFocusHistory,
            getFocusStreak = getFocusStreak,
            recordDailyActivity = recordDailyActivity,
            toggleTaskCompletion = toggleTaskCompletion,
            focusSessionRepository = focusSessionRepository,
            focusSessionUseCases = focusSessionUseCases,
            getUndatedTasks = getUndatedTasks,
            updateTask = updateTask,
            restoreTaskReminders = restoreTaskReminders,
            getSavedSessions = getSavedSessions,
            getLinkableTasks = getLinkableTasks,
            saveSavedSession = saveSavedSession,
            deleteSavedSession = deleteSavedSession,
            vacationModeRepository = vacationModeRepository,
            clock = clock,
        )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /**
     * The alarm receiver's ending, as the ViewModel sees it: a session whose time is spent, then
     * gone from the repository. Nothing else tells the app a session expired.
     */
    protected suspend fun TestScope.runSessionOut(
        taskId: Long? = 1,
        title: String = "Report",
        isBreak: Boolean = false,
        cycle: SessionCycle? = null,
    ) {
        sessionFlow.value =
            FocusSession.start(
                taskId = taskId,
                title = title,
                now = nowInstant - 30.minutes,
                isBreak = isBreak,
                cycle = cycle,
            )
        advanceUntilIdle()
        sessionFlow.value = null
        advanceUntilIdle()
    }
}
