package com.mandrecode.tempo.features.focus.presentation

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mandrecode.tempo.core.domain.model.VacationPeriod
import com.mandrecode.tempo.core.domain.repository.VacationModeRepository
import com.mandrecode.tempo.features.focus.domain.model.FocusSession
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
import com.mandrecode.tempo.features.tasks.domain.usecase.GetUndatedTasksUseCase
import com.mandrecode.tempo.features.tasks.domain.usecase.RestoreTaskRemindersUseCase
import com.mandrecode.tempo.features.tasks.domain.usecase.ToggleTaskCompletionUseCase
import com.mandrecode.tempo.features.tasks.domain.usecase.UpdateTaskUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import javax.inject.Inject
import kotlin.time.Clock

@HiltViewModel
class FocusViewModel
    @Inject
    constructor(
        private val getFocusAgenda: GetFocusAgendaUseCase,
        private val getFocusHistory: GetFocusHistoryUseCase,
        private val getFocusStreak: GetFocusStreakUseCase,
        private val recordDailyActivity: RecordDailyActivityUseCase,
        private val toggleTaskCompletion: ToggleTaskCompletionUseCase,
        internal val focusSessionRepository: FocusSessionRepository,
        internal val focusSessionUseCases: FocusSessionUseCases,
        internal val getUndatedTasks: GetUndatedTasksUseCase,
        internal val updateTask: UpdateTaskUseCase,
        internal val restoreTaskReminders: RestoreTaskRemindersUseCase,
        internal val getSavedSessions: GetSavedSessionsUseCase,
        internal val getLinkableTasks: GetLinkableTasksUseCase,
        internal val saveSavedSession: SaveSavedSessionUseCase,
        internal val deleteSavedSession: DeleteSavedSessionUseCase,
        private val vacationModeRepository: VacationModeRepository,
        internal val clock: Clock,
    ) : ViewModel() {
        internal val mutableUiState = MutableStateFlow(FocusContract.UiState())
        val uiState: StateFlow<FocusContract.UiState> = mutableUiState.asStateFlow()

        private val effectChannel = Channel<FocusContract.UiEffect>(Channel.BUFFERED)
        val uiEffect = effectChannel.receiveAsFlow()

        private val today: LocalDate get() = clock.todayIn(TimeZone.currentSystemDefault())

        /** Cancelled when the sheet closes, so a closed sheet is not still collecting for nobody. */
        internal var planSheetJob: Job? = null

        /** Cancelled when the session sheet closes, for the same reason. */
        internal var linkableTasksJob: Job? = null

        /** Replaced when an interaction finds that the open screen has crossed into a new day. */
        private var observeDayJob: Job? = null
        private var observedDay: LocalDate? = null

        /**
         * The agenda's counts the last time this day was seen, to tell a real change from the first
         * load. Reset with the day, so moving onto a new one never reads as a change.
         */
        private var lastAgendaCounts: Pair<Int, Int>? = null

        init {
            observeSession()
            observeSavedSessions()
            refreshDayIfStale()
        }

        /**
         * The Focus tab has come into view, or the app back to the front with it showing. The view
         * model is created at startup, ahead of the tab (#438), so this — not creation — is when
         * the tab catches up: onto a new day if one has started, and with a recount, since work
         * may have been added or rescheduled from another tab since the last completion.
         */
        fun onTabShown() {
            refreshDayIfStale()
            viewModelScope.launch { recordDailyActivity(today) }
        }

        fun onEvent(event: FocusContract.UiEvent) {
            when (event) {
                is FocusContract.UiEvent.ToggleTaskCompletion ->
                    viewModelScope.launch {
                        refreshDayIfStale()
                        toggleTaskCompletion(event.task)
                        // Finishing the work is finishing the session: no point counting down on
                        // something already done.
                        val session = mutableUiState.value.session
                        if (session?.taskId == event.task.id && !event.task.isCompleted) {
                            finishSession()
                        }
                    }

                is FocusContract.UiEvent.ToggleSubtasksExpanded ->
                    mutableUiState.update {
                        it.copy(expandedTaskIds = it.expandedTaskIds.toggling(event.taskId))
                    }

                else -> onPlanSheetEvent(event)
            }
        }

        /** Which sheet or dialog is open — state changes only, no work behind them. */
        internal fun onEditorEvent(event: FocusContract.UiEvent) {
            when (event) {
                is FocusContract.UiEvent.EditTask ->
                    mutableUiState.update {
                        it.copy(
                            taskEditor = FocusContract.TaskEditorTarget.Existing(event.task),
                        )
                    }

                is FocusContract.UiEvent.AddSubtask ->
                    mutableUiState.update {
                        it.copy(
                            taskEditor = FocusContract.TaskEditorTarget.NewSubtask(event.parentTaskId),
                        )
                    }

                FocusContract.UiEvent.DismissEditor ->
                    mutableUiState.update { it.copy(taskEditor = null) }

                FocusContract.UiEvent.DismissPendingStart ->
                    mutableUiState.update { it.copy(pendingStart = null) }

                FocusContract.UiEvent.ConfirmPendingStart -> {
                    val pending = mutableUiState.value.pendingStart ?: return
                    mutableUiState.update { it.copy(pendingStart = null) }
                    val savedSessionId = pending.savedSessionId
                    if (savedSessionId != null) {
                        startSavedSession(savedSessionId, force = true)
                    } else {
                        startSessionOn(pending.taskId, pending.lengthMinutes, force = true)
                    }
                }

                else -> onSessionEditorEvent(event)
            }
        }

        /**
         * Timer controls. Split from the completion-sheet handling below so neither `when` grows
         * past what is readable, and starting is inlined here because it has exactly one caller.
         */
        internal fun onSessionEvent(event: FocusContract.UiEvent) {
            when (event) {
                is FocusContract.UiEvent.StartSession ->
                    startSessionOn(event.taskId, event.lengthMinutes)

                FocusContract.UiEvent.BackToWork -> backToWork()

                FocusContract.UiEvent.PauseSession -> focusSessionUseCases.pause()
                FocusContract.UiEvent.ResumeSession -> focusSessionUseCases.resume()
                FocusContract.UiEvent.StopSession -> viewModelScope.launch { finishSession() }

                FocusContract.UiEvent.CompleteSessionTask ->
                    viewModelScope.launch { completeSessionTask() }

                FocusContract.UiEvent.OpenSessionScreen -> {
                    // Reaching for the running session is asking for it by name, so a preview left
                    // over from some other card must not be what opens instead.
                    focusSessionRepository.setPreviewTaskId(null)
                    sendEffect(FocusContract.UiEffect.OpenSessionScreen)
                }

                is FocusContract.UiEvent.PreviewUpNext -> {
                    // Look at the work first: the screen opens on the task with its timer at full
                    // length, and starting stays a separate, deliberate tap.
                    focusSessionRepository.setPreviewTaskId(event.taskId)
                    sendEffect(FocusContract.UiEffect.OpenSessionScreen)
                }

                FocusContract.UiEvent.ClearSessionPreview ->
                    focusSessionRepository.setPreviewTaskId(null)

                else -> onCompletionSheetEvent(event)
            }
        }

        /** What the sheet offers once time is up. */
        private fun onCompletionSheetEvent(event: FocusContract.UiEvent) {
            val finished = mutableUiState.value.finishedSession
            val last = mutableUiState.value.lastSession
            when (event) {
                FocusContract.UiEvent.StartAnotherSession ->
                    viewModelScope.launch {
                        mutableUiState.update { it.copy(finishedSession = null) }
                        if (finished != null && last != null) {
                            startAgain(last)
                            sendEffect(FocusContract.UiEffect.OpenSessionScreen)
                        }
                    }

                FocusContract.UiEvent.TakeBreak ->
                    viewModelScope.launch {
                        mutableUiState.update { it.copy(finishedSession = null) }
                        if (finished != null && last != null) {
                            // A real countdown, not a dismissal: the break notifies when it is over
                            // so the user is not the one who has to remember to come back.
                            focusSessionUseCases.start(
                                taskId = last.taskId,
                                title = last.title,
                                isBreak = true,
                                // A saved session rests for as long as it says it does, and stays
                                // that session through the rest, so coming back restarts it.
                                lengthMinutes =
                                    last.cycle
                                        ?.breakLength
                                        ?.inWholeMinutes
                                        ?.toInt(),
                                // Pinned to its last round: this rest follows a session that is
                                // over, however it ended, and must not pick its rounds back up.
                                cycle = last.cycle?.finished(),
                            )
                        }
                    }

                FocusContract.UiEvent.DismissFinishedSession ->
                    mutableUiState.update { it.copy(finishedSession = null) }
                else -> Unit
            }
        }

        /**
         * Ends the running session because the user said so.
         *
         * No sheet: stopping and marking done are already decisions, and asking "what next?" the
         * moment one is made is asking a question that has just been answered. The banked minutes
         * need no announcement either — they are already in the hero and the heatmap on the screen
         * this returns to. The sheet belongs to the one ending nobody chose, see [onSessionVanished].
         */
        internal suspend fun finishSession() {
            val ended = focusSessionUseCases.end() ?: return
            mutableUiState.update { it.copy(lastSession = ended) }
        }

        /**
         * Starts on the card that was tapped, or on whatever the screen is already showing when
         * nothing names one. Also the way out of a break: starting replaces whatever is running,
         * and a break banks no minutes, so cutting one short costs nothing.
         */
        internal fun startSessionOn(
            taskId: Long?,
            lengthMinutes: Int?,
            force: Boolean = false,
        ) {
            val state = mutableUiState.value
            val task =
                taskId
                    ?.let { id -> state.upNext.firstOrNull { it.task.id == id } }
                    ?.task
                    ?: (state.sessionEntry ?: state.upNext.firstOrNull())?.task
                    ?: return

            // Starting replaces whatever is running and banks only what it earned, so swapping
            // tasks mid-session is a real loss of the current one's remaining time. Ask first —
            // unless it is the same task, where "starting" is just carrying on.
            // Only a focus session on something else is at risk of being thrown away; the same
            // task is carrying on, and a break has nothing to lose. A saved session linked to this
            // task is something else too: it has rounds of its own that a plain start would drop.
            val taskKey = FocusSession.subjectKeyOf(taskId = task.id)
            val replaced = state.session?.takeIf { !it.isBreak && it.subjectKey != taskKey }
            if (!force && replaced != null) {
                mutableUiState.update {
                    it.copy(
                        pendingStart =
                            FocusContract.PendingStart(
                                taskId = task.id,
                                title = task.title,
                                lengthMinutes = lengthMinutes,
                                replacingTaskTitle = replaced.title,
                            ),
                    )
                }
                return
            }

            viewModelScope.launch {
                focusSessionUseCases.start(
                    taskId = task.id,
                    title = task.title,
                    lengthMinutes = lengthMinutes,
                )
                focusSessionRepository.setPreviewTaskId(null)
                // Starting is a commitment to the work, so the app follows the user into it rather
                // than leaving them to find what they just began.
                sendEffect(FocusContract.UiEffect.OpenSessionScreen)
            }
        }

        /**
         * Finishing the work, not just the timer: a session you cut short is not the same as a job
         * done, so completing has to be its own action.
         *
         * Falls back to the task the last session ran on, because the completion sheet offers this
         * after the session has already ended — by then there is no running session to read from.
         */
        private suspend fun completeSessionTask() {
            val state = mutableUiState.value
            val entry = state.sessionEntry ?: state.lastSessionEntry
            if (entry != null && !entry.task.isCompleted) {
                toggleTaskCompletion(entry.task)
                // Only the timer that was on this work: finishing a task you had merely opened to
                // look at must not end the session running on another one.
                if (state.session?.taskId == entry.task.id) finishSession()
            }
            // Whether or not there was anything left to tick, the screen has said its piece. Leaving
            // the preview standing on a finished task left the button there with nothing to do,
            // which read as it being broken.
            focusSessionRepository.setPreviewTaskId(null)
            mutableUiState.update { it.copy(finishedSession = null) }
        }

        private fun observeSession() {
            viewModelScope.launch {
                focusSessionRepository.activeSession.collect { session ->
                    val previous = mutableUiState.value.session
                    mutableUiState.update { it.copy(session = session) }
                    // The alarm receiver ends an expired session wherever the app happens to be,
                    // so a session disappearing is the only signal there is. Having run out is what
                    // tells the two endings apart: one the user stopped still had time on it, and
                    // only the one nobody chose is worth asking a question about.
                    if (session == null && previous != null && previous.hasExpired(clock.now())) {
                        mutableUiState.update { state ->
                            state.copy(
                                lastSession = previous,
                                finishedSession =
                                    FocusContract.FinishedSession(
                                        title = previous.title,
                                        minutes = previous.plannedLength.inWholeMinutes.toInt(),
                                        wasBreak = previous.isBreak,
                                        breakMinutes =
                                            previous.cycle
                                                ?.breakLength
                                                ?.inWholeMinutes
                                                ?.toInt()
                                                ?: focusSessionRepository.breakLengthMinutes.value,
                                        hasTask = previous.taskId != null,
                                    ),
                            )
                        }
                    }
                }
            }
            viewModelScope.launch {
                focusSessionRepository.defaultLengthMinutes.collect { minutes ->
                    mutableUiState.update { it.copy(defaultSessionLengthMinutes = minutes) }
                }
            }
            viewModelScope.launch {
                focusSessionRepository.previewTaskId.collect { taskId ->
                    mutableUiState.update { it.copy(previewTaskId = taskId) }
                }
            }
            viewModelScope.launch {
                focusSessionRepository.breakLengthMinutes.collect { minutes ->
                    mutableUiState.update { it.copy(breakLengthMinutes = minutes) }
                }
            }
        }

        private fun refreshDayIfStale(): LocalDate {
            val day = today
            if (observedDay == day) return day

            observedDay = day
            lastAgendaCounts = null
            val previousObserveDayJob = observeDayJob
            observeDayJob =
                viewModelScope.launch {
                    previousObserveDayJob?.cancelAndJoin()
                    // The vacation periods are collected here rather than on their own, because the
                    // streak is counted on their terms: pausing while this screen is open has to move
                    // the number as well as the badge, and two collectors would have let the badge
                    // appear beside a streak still counted the old way.
                    combine(
                        getFocusAgenda(day),
                        getFocusHistory(day),
                        vacationModeRepository.periods,
                    ) { agenda, history, periods ->
                        Triple(agenda, history, periods)
                    }.onEach { (agenda, history, periods) ->
                        if (observedDay != day) return@onEach
                        // Work added, finished or moved while Focus is open — from its own editor, a
                        // notification, another window — changes today's counts, and the day's
                        // history has to follow, or a snapshot would still show the day complete
                        // after a task was added to it. Recounting on the agenda's change rather than
                        // on the editor closing waits for the save itself, which can land after the
                        // sheet has gone. The first load is only a baseline.
                        val counts = agenda.scheduledCount to agenda.completedCount
                        val previousCounts = lastAgendaCounts
                        lastAgendaCounts = counts
                        if (previousCounts != null && previousCounts != counts) {
                            viewModelScope.launch { recordDailyActivity(day) }
                        }
                        val streak = getFocusStreak(day)
                        mutableUiState.update { state ->
                            state.copy(
                                isLoading = false,
                                today = day,
                                streakDays = streak,
                                isVacationModeActive = VacationPeriod.activeOn(periods, day) != null,
                                history = history.toPersistentList(),
                                scheduledCount = agenda.scheduledCount,
                                completedCount = agenda.completedCount,
                                focusMinutes = history.lastOrNull { it.date == day }?.focusMinutes ?: 0,
                                upNext = agenda.upNext.toPersistentList(),
                                overdue = agenda.overdue.toPersistentList(),
                                todayItems = agenda.today.toPersistentList(),
                                undatedTaskCount = agenda.undatedTaskCount,
                            )
                        }
                    }.catch { exception ->
                        if (exception is CancellationException) throw exception
                        // The view model is created at startup ahead of the tab (#438), even when
                        // Focus is hidden or onboarding is showing, so a failed query — the streak
                        // included — must not take the app down. Forgetting the day lets the next
                        // look at the tab, or the next interaction, try again.
                        Log.w(FOCUS_VIEW_MODEL_TAG, "Unable to load the Focus day", exception)
                        if (observedDay == day) observedDay = null
                    }.collect()
                }
            return day
        }

        internal fun sendEffect(effect: FocusContract.UiEffect) {
            viewModelScope.launch { effectChannel.send(effect) }
        }
    }

private const val FOCUS_VIEW_MODEL_TAG = "FocusViewModel"

/** Adds [id] when absent, removes it when present — the shape both expansion toggles need. */
private fun ImmutableList<Long>.toggling(id: Long): ImmutableList<Long> =
    if (id in this) (this - id).toPersistentList() else (this + id).toPersistentList()
