package com.mandrecode.tempo.features.focus.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.mandrecode.tempo.features.focus.domain.model.FocusSession
import com.mandrecode.tempo.features.focus.domain.model.FocusSession.Companion.BREAK_LENGTH_RANGE
import com.mandrecode.tempo.features.focus.domain.model.FocusSession.Companion.DEFAULT_BREAK_LENGTH
import com.mandrecode.tempo.features.focus.domain.model.FocusSession.Companion.DEFAULT_LENGTH
import com.mandrecode.tempo.features.focus.domain.model.FocusSession.Companion.SESSION_LENGTH_RANGE
import com.mandrecode.tempo.features.focus.domain.model.SessionCycle
import com.mandrecode.tempo.features.focus.domain.model.TaskFocusToday
import com.mandrecode.tempo.features.focus.domain.repository.FocusSessionRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant

@Singleton
class FocusSessionRepositoryImpl
    @Inject
    constructor(
        @ApplicationContext context: Context,
        private val clock: Clock,
    ) : FocusSessionRepository {
        private val prefs: SharedPreferences =
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        private val sessionFlow = MutableStateFlow(readSession())
        private val defaultLengthFlow =
            MutableStateFlow(readLength(KEY_DEFAULT_LENGTH, DEFAULT_LENGTH, SESSION_LENGTH_RANGE))
        private val breakLengthFlow =
            MutableStateFlow(readLength(KEY_BREAK_LENGTH, DEFAULT_BREAK_LENGTH, BREAK_LENGTH_RANGE))
        private val previewTaskIdFlow = MutableStateFlow<Long?>(null)
        private val setupSessionIdFlow = MutableStateFlow<Long?>(null)
        private val focusTodayFlow =
            MutableStateFlow(prefs.readFocusOn(clock.todayIn(TimeZone.currentSystemDefault())))

        override val activeSession: StateFlow<FocusSession?> = sessionFlow.asStateFlow()

        override val defaultLengthMinutes: StateFlow<Int> = defaultLengthFlow.asStateFlow()

        override val breakLengthMinutes: StateFlow<Int> = breakLengthFlow.asStateFlow()

        override val previewTaskId: StateFlow<Long?> = previewTaskIdFlow.asStateFlow()

        override val setupSessionId: StateFlow<Long?> = setupSessionIdFlow.asStateFlow()

        override val focusToday: StateFlow<Map<Long, TaskFocusToday>> = focusTodayFlow.asStateFlow()

        override fun focusOn(
            taskId: Long,
            date: LocalDate,
        ): TaskFocusToday = prefs.readFocusOn(taskId, date)

        override fun recordSessionFor(
            taskId: Long,
            today: LocalDate,
        ) = update(taskId, today) { it.copy(sessions = it.sessions + 1) }

        override fun addFocusMinutesFor(
            taskId: Long,
            minutes: Int,
            today: LocalDate,
        ) {
            if (minutes <= 0) return
            update(taskId, today) { it.copy(minutes = it.minutes + minutes) }
        }

        /**
         * One writer for both numbers, so they can never fall on opposite sides of midnight: the
         * whole record is stamped with the date it belongs to and starts over when that date moves,
         * rather than yesterday's runs colouring today's card.
         */
        private fun update(
            taskId: Long,
            today: LocalDate,
            change: (TaskFocusToday) -> TaskFocusToday,
        ) {
            val current =
                if (prefs.getString(KEY_SESSIONS_DATE, null) == today.toString()) {
                    focusTodayFlow.value
                } else {
                    emptyMap()
                }
            val updated =
                current + (taskId to change(current[taskId] ?: TaskFocusToday()))
            prefs.edit {
                putString(KEY_SESSIONS_DATE, today.toString())
                putString(KEY_SESSIONS_BY_TASK, updated.serialize())
            }
            focusTodayFlow.value = updated
        }

        override fun setActiveSession(session: FocusSession?) {
            prefs.edit {
                if (session == null) {
                    remove(KEY_TASK_ID)
                    remove(KEY_TASK_TITLE)
                    remove(KEY_PLANNED_MILLIS)
                    remove(KEY_COMPLETED_MILLIS)
                    remove(KEY_RUNNING_SINCE)
                    remove(KEY_IS_BREAK)
                    removeCycle()
                } else {
                    // -1 rather than removing the key, as for a paused session below: "no task" is
                    // a stored state, and the key's presence is what says a session exists at all.
                    putLong(KEY_TASK_ID, session.taskId ?: NO_TASK)
                    putString(KEY_TASK_TITLE, session.title)
                    putLong(KEY_PLANNED_MILLIS, session.plannedLength.inWholeMilliseconds)
                    putLong(KEY_COMPLETED_MILLIS, session.completedBeforeNow.inWholeMilliseconds)
                    // -1 rather than removing the key, so "paused" is a stored state instead of an
                    // absence that could be confused with a partially written record.
                    putLong(KEY_RUNNING_SINCE, session.runningSince?.toEpochMilliseconds() ?: NOT_RUNNING)
                    putBoolean(KEY_IS_BREAK, session.isBreak)
                    session.cycle?.let { putCycle(it) } ?: removeCycle()
                }
            }
            sessionFlow.value = session
        }

        override fun setPreviewTaskId(taskId: Long?) {
            setupSessionIdFlow.value = null
            previewTaskIdFlow.value = taskId
        }

        override fun setSetupSessionId(sessionId: Long?) {
            if (sessionId != null) previewTaskIdFlow.value = null
            setupSessionIdFlow.value = sessionId
        }

        override fun setDefaultLengthMinutes(minutes: Int) {
            val normalized = minutes.coerceIn(FocusSession.SESSION_LENGTH_RANGE)
            prefs.edit { putInt(KEY_DEFAULT_LENGTH, normalized) }
            defaultLengthFlow.value = normalized
        }

        override fun setBreakLengthMinutes(minutes: Int) {
            val normalized = minutes.coerceIn(FocusSession.BREAK_LENGTH_RANGE)
            prefs.edit { putInt(KEY_BREAK_LENGTH, normalized) }
            breakLengthFlow.value = normalized
        }

        private fun readSession(): FocusSession? {
            val plannedMillis =
                if (prefs.contains(KEY_TASK_ID)) prefs.getLong(KEY_PLANNED_MILLIS, 0L) else 0L
            if (plannedMillis <= 0L) return null

            val runningSince = prefs.getLong(KEY_RUNNING_SINCE, NOT_RUNNING)
            return FocusSession(
                taskId = prefs.getLong(KEY_TASK_ID, NO_TASK).takeIf { it != NO_TASK },
                title = prefs.getString(KEY_TASK_TITLE, null).orEmpty(),
                plannedLength = plannedMillis.milliseconds,
                isBreak = prefs.getBoolean(KEY_IS_BREAK, false),
                completedBeforeNow = prefs.getLong(KEY_COMPLETED_MILLIS, 0L).milliseconds,
                runningSince =
                    runningSince
                        .takeIf { it != NOT_RUNNING }
                        ?.let { Instant.fromEpochMilliseconds(it) },
                cycle = prefs.readCycle(),
            )
        }

        /** A stored length in minutes, inside what a timer can run, or [fallback] when none is. */
        private fun readLength(
            key: String,
            fallback: Duration,
            range: IntRange,
        ): Int = prefs.getInt(key, fallback.inWholeMinutes.toInt()).coerceIn(range)

        private companion object {
            const val PREFS_NAME = "focus_session_prefs"
            const val KEY_TASK_ID = "session_task_id"
            const val KEY_TASK_TITLE = "session_task_title"
            const val KEY_PLANNED_MILLIS = "session_planned_millis"
            const val KEY_COMPLETED_MILLIS = "session_completed_millis"
            const val KEY_RUNNING_SINCE = "session_running_since"
            const val KEY_IS_BREAK = "session_is_break"
            const val KEY_DEFAULT_LENGTH = "session_default_length_minutes"
            const val KEY_BREAK_LENGTH = "session_break_length_minutes"
            const val NOT_RUNNING = -1L
            const val NO_TASK = -1L
        }
    }

// A saved session's cycle is read and written by the file-level helpers below, so its keys sit
// where they and the class can both see them.
private const val KEY_CYCLE_SESSION_ID = "session_cycle_saved_id"
private const val KEY_CYCLE_ROUND = "session_cycle_round"
private const val KEY_CYCLE_TOTAL_ROUNDS = "session_cycle_total_rounds"
private const val KEY_CYCLE_FOCUS_MILLIS = "session_cycle_focus_millis"
private const val KEY_CYCLE_BREAK_MILLIS = "session_cycle_break_millis"
private const val NO_SAVED_SESSION = -1L
private const val NO_ROUNDS = 0

/**
 * A session stored before saved sessions existed has no cycle keys, and reads as none. The rounds
 * are what say a cycle is there: one set on a task for a single run has no saved session to name.
 */
private fun SharedPreferences.readCycle(): SessionCycle? {
    val totalRounds = getInt(KEY_CYCLE_TOTAL_ROUNDS, NO_ROUNDS)
    if (totalRounds == NO_ROUNDS) return null
    return SessionCycle(
        savedSessionId = getLong(KEY_CYCLE_SESSION_ID, NO_SAVED_SESSION).takeIf { it != NO_SAVED_SESSION },
        round = getInt(KEY_CYCLE_ROUND, 1),
        totalRounds = totalRounds,
        focusLength = getLong(KEY_CYCLE_FOCUS_MILLIS, 0L).milliseconds,
        breakLength = getLong(KEY_CYCLE_BREAK_MILLIS, 0L).milliseconds,
    )
}

private fun SharedPreferences.Editor.putCycle(cycle: SessionCycle) {
    putLong(KEY_CYCLE_SESSION_ID, cycle.savedSessionId ?: NO_SAVED_SESSION)
    putInt(KEY_CYCLE_ROUND, cycle.round)
    putInt(KEY_CYCLE_TOTAL_ROUNDS, cycle.totalRounds)
    putLong(KEY_CYCLE_FOCUS_MILLIS, cycle.focusLength.inWholeMilliseconds)
    putLong(KEY_CYCLE_BREAK_MILLIS, cycle.breakLength.inWholeMilliseconds)
}

private fun SharedPreferences.Editor.removeCycle() {
    remove(KEY_CYCLE_SESSION_ID)
    remove(KEY_CYCLE_ROUND)
    remove(KEY_CYCLE_TOTAL_ROUNDS)
    remove(KEY_CYCLE_FOCUS_MILLIS)
    remove(KEY_CYCLE_BREAK_MILLIS)
}

// The day's record is read by the file-level helper below as well as by the class, so its keys sit
// where both can see them.
private const val KEY_SESSIONS_DATE = "sessions_today_date"
private const val KEY_SESSIONS_BY_TASK = "sessions_today_by_task"

/** The stored form of a day: `id:sessions:minutes`, one record per task. */
private fun Map<Long, TaskFocusToday>.serialize(): String =
    entries.joinToString(",") { (taskId, day) -> "$taskId:${day.sessions}:${day.minutes}" }

/**
 * The stored day's records, one per task, but only if the stored day is [date]'s.
 *
 * A flat `id:sessions:minutes` list — small, and never read by anything but this file. The date is
 * checked on the way in rather than only when writing: the record outlives the day it belongs to,
 * and an app opened the next morning would otherwise read yesterday's runs back and let every card
 * claim work the day has not had.
 *
 * The two-field `id:sessions` records written before minutes were tracked still parse, as a day with
 * runs behind it and no time recorded; the next write brings them up to date.
 *
 * A sequence so a caller after one task can stop at it. The missed-reminder sweep asks this once per
 * overdue task, and building the whole day's map each time to throw all but one entry away made a
 * per-task question cost the length of the day.
 */
private fun SharedPreferences.focusRecordsOn(date: LocalDate): Sequence<Pair<Long, TaskFocusToday>> {
    if (getString(KEY_SESSIONS_DATE, null) != date.toString()) return emptySequence()
    return getString(KEY_SESSIONS_BY_TASK, null)
        .orEmpty()
        .splitToSequence(',')
        .mapNotNull { record ->
            val parts = record.split(':').takeIf { it.size in 2..3 } ?: return@mapNotNull null
            val taskId = parts[0].toLongOrNull() ?: return@mapNotNull null
            val sessions = parts[1].toIntOrNull() ?: return@mapNotNull null
            val minutes = parts.getOrNull(2)?.toIntOrNull() ?: 0
            taskId to TaskFocusToday(sessions = sessions, minutes = minutes)
        }
}

private fun SharedPreferences.readFocusOn(date: LocalDate): Map<Long, TaskFocusToday> = focusRecordsOn(date).toMap()

private fun SharedPreferences.readFocusOn(
    taskId: Long,
    date: LocalDate,
): TaskFocusToday =
    focusRecordsOn(date)
        .firstOrNull { (id, _) -> id == taskId }
        ?.second ?: TaskFocusToday()
