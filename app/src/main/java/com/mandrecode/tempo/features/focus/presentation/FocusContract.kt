package com.mandrecode.tempo.features.focus.presentation

import com.mandrecode.tempo.core.domain.model.DailyFocusActivity
import com.mandrecode.tempo.features.focus.domain.model.FocusAgendaItem
import com.mandrecode.tempo.features.focus.domain.model.FocusHeadlineBand
import com.mandrecode.tempo.features.focus.domain.model.FocusSession
import com.mandrecode.tempo.features.focus.domain.model.SavedSession
import com.mandrecode.tempo.features.focus.domain.model.SavedSessionEntry
import com.mandrecode.tempo.features.focus.domain.model.SessionCycle
import com.mandrecode.tempo.features.focus.domain.model.TaskFocusToday
import com.mandrecode.tempo.features.tasks.domain.model.Task
import com.mandrecode.tempo.features.tasks.domain.model.UndatedTask
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime

/**
 * Contract for the Focus screen following the MVI pattern.
 */
object FocusContract {
    data class UiState(
        val isLoading: Boolean = true,
        val today: LocalDate? = null,
        val streakDays: Int = 0,
        val history: ImmutableList<DailyFocusActivity> = persistentListOf(),
        val scheduledCount: Int = 0,
        val completedCount: Int = 0,
        val focusMinutes: Int = 0,
        val upNext: ImmutableList<FocusAgendaItem.TaskEntry> = persistentListOf(),
        val overdue: ImmutableList<FocusAgendaItem> = persistentListOf(),
        val todayItems: ImmutableList<FocusAgendaItem> = persistentListOf(),
        val undatedTaskCount: Int = 0,
        /**
         * Tasks whose subtasks are unfolded. Folded is the resting state: the day is a list of what
         * is on today, and every step of every task opened by default buried that under itself.
         */
        val expandedTaskIds: ImmutableList<Long> = persistentListOf(),
        val session: FocusSession? = null,
        /** Set when a session has just finished, driving the completion sheet. */
        val finishedSession: FinishedSession? = null,
        /** The session that last ended, so "another session" restarts the same work. */
        val lastSession: FocusSession? = null,
        /**
         * The sessions the user has saved (#517), each with the task it points at. Not part of the
         * day: they are not tasks, so they never count in [scheduledCount] or [completedCount].
         */
        val savedSessions: ImmutableList<SavedSessionEntry> = persistentListOf(),
        /**
         * The saved session the session screen is setting up before it runs: its id,
         * [SavedSession.NEW_ID] for one not saved yet, or `null` for none.
         */
        val setupSessionId: Long? = null,
        /** The open tasks a session can be linked to, loaded while one is being set up. */
        val linkableTasks: ImmutableList<Task> = persistentListOf(),
        /**
         * The task the session screen is showing when no session is running — opened from Up next
         * to look at the work before committing to a timer.
         */
        val previewTaskId: Long? = null,
        /**
         * What the task editor is open on, if anything. Focus hosts the sheets itself rather than
         * sending you to the owning tab: opening an item should not move you off the day you were
         * looking at.
         */
        val taskEditor: TaskEditorTarget? = null,
        /**
         * The planning sheet, when it is open. Undated work has no place in a day, so the one
         * thing Focus can offer it is somewhere to be given one.
         */
        val planSheet: PlanSheetState? = null,
        /**
         * A start waiting on the user's word, because taking it would end the session already
         * running. Held rather than acted on: replacing a session is not something to discover
         * afterwards.
         */
        val pendingStart: PendingStart? = null,
        val defaultSessionLengthMinutes: Int = 25,
        val breakLengthMinutes: Int = 5,
        /**
         * Whether today falls inside a vacation period. Vacation is a broad "relax without breaking
         * streaks" toggle rather than a habits-only one, so the day says so here too — and the
         * streak beside it is counted on those terms.
         */
        val isVacationModeActive: Boolean = false,
    ) {
        val headlineBand: FocusHeadlineBand
            get() = FocusHeadlineBand.resolve(scheduledCount, completedCount)

        /** Progress for the hero's indicator, `0f` when there is nothing to make progress on. */
        val progress: Float
            get() = if (scheduledCount <= 0) 0f else (completedCount.toFloat() / scheduledCount).coerceIn(0f, 1f)

        val isDayEmpty: Boolean get() = overdue.isEmpty() && todayItems.isEmpty()

        /**
         * Whether Focus has nothing at all to show. A day with no tasks still has its saved
         * sessions, and whatever is counting down — and neither may be hidden behind a message
         * saying there is nothing here.
         */
        val showsEmptyState: Boolean get() = isDayEmpty && savedSessions.isEmpty() && session == null

        /**
         * The saved sessions offered for starting: all of them but the one already running, which
         * has the card above.
         */
        val startableSessions: List<SavedSessionEntry>
            get() = savedSessions.filterNot { it.session.id == session?.savedSessionId }

        /**
         * The day's sections without whatever the running session is on.
         *
         * That task already has the card above, counting down. Leaving it in the list as well read
         * as two things when it is one, and as work still waiting when it is under way.
         */
        val visibleOverdue: List<FocusAgendaItem> get() = overdue.withoutSessionTask()

        val visibleToday: List<FocusAgendaItem> get() = todayItems.withoutSessionTask()

        /**
         * Only for a timer started on the task itself. A saved session linked to a task runs under
         * its own name, so the card above is not that task's, and its row stays where it was.
         */
        private fun List<FocusAgendaItem>.withoutSessionTask(): List<FocusAgendaItem> {
            val taskId = session?.takeIf { it.savedSessionId == null }?.taskId ?: return this
            return filterNot { it is FocusAgendaItem.TaskEntry && it.task.id == taskId }
        }

        /**
         * What "another session" would run for: the saved session's own length when that is what
         * would start again, otherwise the length in Settings.
         */
        val nextSessionMinutes: Int
            get() =
                savedSessions
                    .firstOrNull { it.session.id == lastSession?.savedSessionId }
                    ?.session
                    ?.focusMinutes
                    ?: defaultSessionLengthMinutes

        /** Whether the running session's card has a task to mark done. See [canComplete]. */
        val canCompleteRunningTask: Boolean get() = canComplete(session, runningEntry)

        /** The same question for the session screen, which may be showing a previewed task. */
        val canCompleteScreenTask: Boolean
            get() = screenSession == null || canComplete(screenSession, sessionEntry)

        /**
         * A timer on a task always offers finishing it. A saved session offers it only while its
         * linked task is there and open: linked to none, or to one since ticked off or deleted,
         * there is nothing for the button to do.
         */
        private fun canComplete(
            session: FocusSession?,
            entry: FocusAgendaItem.TaskEntry?,
        ): Boolean =
            when {
                session?.taskId == null -> false
                session.savedSessionId == null -> true
                else -> entry?.task?.isCompleted == false
            }

        /**
         * The Up next shortlist without the task the timer is on, which has the running card. Only
         * a timer on the task itself stands in for its card; a saved session linked to it does not.
         */
        val queuedUpNext: List<FocusAgendaItem.TaskEntry>
            get() = upNext.filterNot { session?.savedSessionId == null && it.task.id == session?.taskId }

        /**
         * The task the session screen is about.
         *
         * A preview wins over the running session, not the other way round: tapping a queued card
         * is asking for *that* task, and answering with whatever happens to be counting down was
         * the screen refusing to open the thing that was tapped.
         */
        val sessionTaskId: Long?
            get() = previewTaskId ?: session?.taskId.takeIf { setupSessionId == null }

        /**
         * The session being set up on the session screen: a new one, or a saved one with its task.
         * `null` when none is, or when the one named has since been deleted — which is how the
         * screen comes to close on it.
         */
        val sessionSetup: SessionEditorTarget?
            get() =
                when (val id = setupSessionId) {
                    null -> null
                    SavedSession.NEW_ID -> SessionEditorTarget()
                    else ->
                        savedSessions
                            .firstOrNull { it.session.id == id }
                            ?.let { SessionEditorTarget(session = it.session, linkedTask = it.linkedTask) }
                }

        /**
         * What the session screen is about, as one value: the previewed task, else the saved
         * session being set up, else whatever the running session is on. The screen latches this,
         * and closes when it stops being the subject.
         */
        val sessionSubjectKey: String?
            get() =
                when {
                    previewTaskId != null -> FocusSession.subjectKeyOf(taskId = previewTaskId)
                    // The key a saved session's timer will have once it runs, so the screen opened
                    // to set it up is still about the same thing after Start.
                    sessionSetup != null -> FocusSession.subjectKeyOf(taskId = null, savedSessionId = setupSessionId)
                    setupSessionId != null -> null
                    else -> session?.subjectKey
                }

        /**
         * The session that screen should count down — none while previewing another task, so the
         * ring stands still on the work you are looking at rather than on the work under way.
         */
        val screenSession: FocusSession? get() = session?.takeIf { it.subjectKey == sessionSubjectKey }

        /**
         * The entry the session screen is showing: the task being previewed, or the one the running
         * session is on.
         */
        val sessionEntry: FocusAgendaItem.TaskEntry? get() = entryFor(sessionTaskId)

        val sessionSubtasks: List<Task> get() = sessionEntry?.subtasks.orEmpty()

        /**
         * The entry for the task the timer is actually on, for the card that counts it down.
         *
         * Not [sessionEntry], which follows the screen's subject: while the sheet sits open on some
         * other task the card underneath is still the running one, and it should go on describing
         * the work it is timing.
         */
        val runningEntry: FocusAgendaItem.TaskEntry? get() = entryFor(session?.taskId)

        /**
         * [taskId]'s entry wherever it can be found, for a task just linked to a session being set
         * up: it may not be on today, and what it carries still has to be shown once it is chosen.
         */
        fun taskEntry(taskId: Long?): FocusAgendaItem.TaskEntry? =
            entryFor(taskId)
                ?: linkableTasks.firstOrNull { it.id == taskId }?.let { FocusAgendaItem.TaskEntry(task = it) }

        /** The task the last session ran on, for the choices offered once it has ended. */
        val lastSessionEntry: FocusAgendaItem.TaskEntry? get() = entryFor(lastSession?.taskId)

        /**
         * The row is searched too, not only the sections. It normally mirrors them, but this is a
         * lookup and a duplicate costs nothing, whereas missing the task the session is on costs
         * the screen its subject.
         *
         * Subtasks as well: a subtask's own reminder can start a session on it from the
         * notification, and the agenda only ever shows it nested inside its parent.
         */
        private fun entryFor(taskId: Long?): FocusAgendaItem.TaskEntry? {
            if (taskId == null) return null
            val entries = (upNext + overdue + todayItems).filterIsInstance<FocusAgendaItem.TaskEntry>()
            return entries.firstOrNull { it.task.id == taskId }
                ?: entries.firstNotNullOfOrNull { parent ->
                    parent.subtasks
                        .firstOrNull { it.id == taskId }
                        ?.let { subtask ->
                            FocusAgendaItem.TaskEntry(
                                task = subtask,
                                categoryName = parent.categoryName,
                                focusToday = parent.subtaskFocusToday[subtask.id] ?: TaskFocusToday(),
                            )
                        }
                }
                // A saved session may be for work that is not on today at all, and its controls
                // still have to be able to name and finish that work.
                ?: savedSessions
                    .firstNotNullOfOrNull { saved -> saved.linkedTask?.takeIf { it.id == taskId } }
                    ?.let { FocusAgendaItem.TaskEntry(task = it) }
        }
    }

    /**
     * The plan sheet: the undated tasks it opened with, and what has happened to them since.
     *
     * [rows] is held against the ids the sheet opened with rather than against "whatever is still
     * undated". Planning a task is exactly what takes it out of that second answer, so a sheet
     * driven by it would erase each row the moment it did its job — and the planned/unplanned split
     * only exists because the sheet remembers what it started with.
     */
    data class PlanSheetState(
        val rows: ImmutableList<UndatedTask> = persistentListOf(),
        /**
         * What each task's reminder was when the sheet opened — all null in practice, since these
         * are the undated ones, but held explicitly because undo means "put the sheet back", not
         * "clear the date".
         */
        val originalReminders: ImmutableMap<Long, LocalDateTime?> = persistentMapOf(),
        /**
         * What this sheet has asked for, recorded the instant a chip is pressed.
         *
         * [rows] follow the repository, which answers a beat later. Closing inside that beat — a
         * tap and a swipe in the same breath — would otherwise have the sheet conclude it had
         * changed nothing, and quietly withhold the undo for a write already on its way.
         */
        val writes: ImmutableMap<Long, LocalDateTime?> = persistentMapOf(),
        val isLoading: Boolean = true,
    ) {
        /**
         * One pass over the rows, answering every question the sheet asks of them.
         *
         * Computed once per state rather than per read: the sections, the changed set and the
         * header rule were three separately written predicates over the same list, each re-filtering
         * on every recomposition and each free to drift from the others about what "settled" means.
         */
        private val grouping: Grouping by lazy { group() }

        /**
         * Settled, one way or the other: given a day, or ticked off.
         *
         * A task you complete while planning is not waiting for a day any more, and going on
         * counting it as one would have the sheet ask for something already done. The card shows it
         * struck through, so it reads as finished rather than scheduled.
         */
        val planned: List<UndatedTask> get() = grouping.planned

        val unplanned: List<UndatedTask> get() = grouping.unplanned

        /**
         * Everything this sheet session changed, however it was changed — a chip, or the full
         * editor opened over the sheet. One notion rather than two, so undo puts back exactly what
         * the sheet is answerable for.
         *
         * Two kinds of evidence, because neither alone is enough. A row that has moved covers what
         * the editor did behind the sheet's back; a recorded write covers what the sheet has asked
         * for but not yet seen come back. Either counts, and a task planned and then unplanned
         * counts as neither: [writes] holds the latest value asked for, not a history, so it lands
         * back on the original and drops out.
         */
        val changedTaskIds: List<Long> get() = grouping.changedTaskIds

        /** Whether closing has anything to offer back. */
        val hasChanges: Boolean get() = changedTaskIds.isNotEmpty()

        /**
         * A sheet where nothing is planned yet is one plain list: headers that only ever say
         * "Unplanned" over everything are labelling the obvious.
         */
        val showsSectionHeaders: Boolean get() = planned.isNotEmpty()

        private fun group(): Grouping {
            val planned = mutableListOf<UndatedTask>()
            val unplanned = mutableListOf<UndatedTask>()
            val changed = mutableListOf<Long>()
            rows.forEach { row ->
                val id = row.task.id
                val current = row.task.reminderDate
                val original = originalReminders[id]
                if (current != null || row.task.isCompleted) planned += row else unplanned += row
                val asked = writes[id]
                if (current != original || (writes.containsKey(id) && asked != original)) {
                    changed += id
                }
            }
            return Grouping(planned = planned, unplanned = unplanned, changedTaskIds = changed)
        }

        private data class Grouping(
            val planned: List<UndatedTask>,
            val unplanned: List<UndatedTask>,
            val changedTaskIds: List<Long>,
        )
    }

    /** A start that would replace a running session, waiting to be confirmed. */
    data class PendingStart(
        val taskId: Long?,
        val title: String,
        val lengthMinutes: Int?,
        val replacingTaskTitle: String,
        /** Set when it is a saved session waiting to start, rather than a timer on a task. */
        val savedSessionId: Long? = null,
        /** The rest and rounds a task run was asked for; one round is a plain session. */
        val breakMinutes: Int? = null,
        val rounds: Int = 1,
    )

    /**
     * The session being set up: [session] to change one that exists, `null` for a new one.
     *
     * Only the target is state. The draft being typed lives in the sheet, the way the task and
     * habit editors keep theirs, and comes back whole in [UiEvent.SaveSession].
     */
    data class SessionEditorTarget(
        val session: SavedSession? = null,
        val linkedTask: Task? = null,
    ) {
        val isNew: Boolean get() = session == null
    }

    /** What the session sheet hands back when it is saved. */
    data class SessionDraft(
        /** `0` for a session that does not exist yet. */
        val id: Long = 0,
        val title: String,
        val focusMinutes: Int,
        val breakMinutes: Int,
        val rounds: Int,
        val taskId: Long?,
    ) {
        fun toSavedSession(): SavedSession =
            SavedSession(
                id = id,
                title = title,
                focusMinutes = focusMinutes,
                breakMinutes = breakMinutes,
                rounds = rounds,
                taskId = taskId,
            )
    }

    /**
     * Why the task editor is open: to change a task, or add a subtask beneath one. New top-level
     * tasks are made in Tasks; what Focus's add button makes is a session (#517).
     */
    sealed interface TaskEditorTarget {
        data class Existing(
            val task: Task,
        ) : TaskEditorTarget

        data class NewSubtask(
            val parentTaskId: Long,
        ) : TaskEditorTarget
    }

    /** What the completion sheet reports: plain facts, no score and no streak. */
    data class FinishedSession(
        val title: String,
        val minutes: Int,
        /** A finished break offers going back to the work, not another break. */
        val wasBreak: Boolean = false,
        /** What a break would run for: the saved session's own rest, or the length in Settings. */
        val breakMinutes: Int = 5,
        /** Whether there is a task to mark done. A session linked to none has nothing to tick. */
        val hasTask: Boolean = true,
    )

    sealed interface UiEvent {
        data class ToggleTaskCompletion(
            val task: Task,
        ) : UiEvent

        data class ToggleSubtasksExpanded(
            val taskId: Long,
        ) : UiEvent

        data class EditTask(
            val task: Task,
        ) : UiEvent

        /** The floating bar's add button: opens the session screen on a new session. */
        data object AddSession : UiEvent

        /** A saved session's card was tapped: opens the session screen on it, not yet running. */
        data class EditSession(
            val sessionId: Long,
        ) : UiEvent

        data object DismissSessionEditor : UiEvent

        /** Saves the sheet's draft and closes it; with [startNow], starts its timer as well. */
        data class SaveSession(
            val draft: SessionDraft,
            val startNow: Boolean = false,
        ) : UiEvent

        /** Removes a saved session, ending its timer first if it is the one running. */
        data class DeleteSession(
            val sessionId: Long,
        ) : UiEvent

        /** Starts a saved session from its first round. */
        data class StartSavedSession(
            val sessionId: Long,
        ) : UiEvent

        data class AddSubtask(
            val parentTaskId: Long,
        ) : UiEvent

        data object ConfirmPendingStart : UiEvent

        data object DismissPendingStart : UiEvent

        data object DismissEditor : UiEvent

        /** Opens the plan sheet. Focus used to answer this by leaving for Tasks. */
        data object UndatedTasksClicked : UiEvent

        /** One quick-plan choice: [date], at whatever time `PlanReminderTimeUtil` makes of it. */
        data class PlanTask(
            val taskId: Long,
            val date: LocalDate,
        ) : UiEvent

        /**
         * Takes a task's date back off, returning it to the unplanned section.
         *
         * The same chip that set the day, pressed again. Planning several tasks at speed means
         * mis-tapping some of them, and a choice that cannot be taken back is one people make
         * slowly.
         */
        data class UnplanTask(
            val taskId: Long,
        ) : UiEvent

        /**
         * Closes the sheet, however it was closed — the button, the handle, back, the scrim or
         * Escape. All of them leave the planning in place and offer to take the batch back, because
         * every one of them is the same act: leaving, having planned something.
         */
        data object ClosePlanSheet : UiEvent

        /**
         * Puts [batch] back, exactly as the sheet that produced it found things.
         *
         * The batch travels with the offer rather than being looked up when it is taken. A snackbar
         * sits on screen for seconds, and in those seconds another sheet can be opened and closed —
         * so an undo that read "the last batch" could quietly restore somebody else's.
         */
        data class UndoPlanBatch(
            val batch: ImmutableMap<Long, LocalDateTime?>,
        ) : UiEvent

        /**
         * [lengthMinutes] applies to this start alone and never changes the setting. [taskId]
         * names the card you started from; without one it is whatever the screen is showing.
         */
        data class StartSession(
            val lengthMinutes: Int? = null,
            val taskId: Long? = null,
            /** With more than one of [rounds], the run rests for this long between them. */
            val breakMinutes: Int? = null,
            val rounds: Int = 1,
        ) : UiEvent

        data object PauseSession : UiEvent

        data object ResumeSession : UiEvent

        data object StopSession : UiEvent

        /** Finishes the work itself, not just the timer — ticks the task and ends the session. */
        data object CompleteSessionTask : UiEvent

        /** Opens the session screen for an Up next card without starting its timer. */
        data class PreviewUpNext(
            val taskId: Long,
        ) : UiEvent

        data object ClearSessionPreview : UiEvent

        /**
         * Takes the task off the session screen that was opened on it, leaving a new session with
         * no task: what the link's cross does when the task is where the session came from.
         */
        data object UnlinkPreviewTask : UiEvent

        data object OpenSessionScreen : UiEvent

        data object StartAnotherSession : UiEvent

        data object TakeBreak : UiEvent

        /** Ends the break early and starts a fresh session on the same task. */
        data object BackToWork : UiEvent

        data object DismissFinishedSession : UiEvent
    }

    sealed interface UiEffect {
        /** The session gets its own slide-in screen, so opening it is navigation. */
        data object OpenSessionScreen : UiEffect

        /**
         * The sheet has closed on [count] newly planned tasks, and the way back is still open.
         *
         * Offered here rather than inside the sheet because it is about what the sheet did, and by
         * the time there is anything to say the sheet is gone.
         */
        data class PlanBatchConfirmed(
            /** What to put back, and what each task's reminder was before the sheet touched it. */
            val batch: ImmutableMap<Long, LocalDateTime?>,
        ) : UiEffect {
            val count: Int get() = batch.size
        }
    }
}

/** Pause while running, resume while paused: the one toggle every session surface offers. */
internal fun FocusSession.pauseResumeEvent(): FocusContract.UiEvent =
    if (isPaused) FocusContract.UiEvent.ResumeSession else FocusContract.UiEvent.PauseSession

/** The run [this] asks for, held while the user is asked about replacing what is running. */
internal fun FocusContract.UiEvent.StartSession.toPendingStart(
    task: Task,
    replacing: String,
): FocusContract.PendingStart =
    FocusContract.PendingStart(
        taskId = task.id,
        title = task.title,
        lengthMinutes = lengthMinutes,
        replacingTaskTitle = replacing,
        breakMinutes = breakMinutes,
        rounds = rounds,
    )

/** The same start, asked for again once the user has agreed to it. */
internal fun FocusContract.PendingStart.toStartSession(): FocusContract.UiEvent.StartSession =
    FocusContract.UiEvent.StartSession(
        lengthMinutes = lengthMinutes,
        taskId = taskId,
        breakMinutes = breakMinutes,
        rounds = rounds,
    )

/** The rounds this run was given, or `null` for the single session a task normally gets. */
internal fun FocusContract.UiEvent.StartSession.cycle(
    defaultFocusMinutes: Int,
    defaultBreakMinutes: Int,
): SessionCycle? =
    FocusSession.adHocCycle(
        focusMinutes = lengthMinutes ?: defaultFocusMinutes,
        breakMinutes = breakMinutes ?: defaultBreakMinutes,
        rounds = rounds,
    )
