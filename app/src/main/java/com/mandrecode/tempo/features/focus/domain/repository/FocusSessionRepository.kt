package com.mandrecode.tempo.features.focus.domain.repository

import com.mandrecode.tempo.features.focus.domain.model.FocusSession
import com.mandrecode.tempo.features.focus.domain.model.SavedSession
import com.mandrecode.tempo.features.focus.domain.model.TaskFocusToday
import kotlinx.coroutines.flow.StateFlow
import kotlinx.datetime.LocalDate

/**
 * The single active focus session, or `null` when none is running.
 *
 * Backed by preferences rather than Room: it is one small record with no history to query, and it
 * has to be readable from a broadcast receiver on a cold start without opening the encrypted
 * database.
 */
interface FocusSessionRepository {
    val activeSession: StateFlow<FocusSession?>

    /** Default length for sessions started from now on; changing it never affects a running one. */
    val defaultLengthMinutes: StateFlow<Int>

    /** How long a break runs, on the same terms. */
    val breakLengthMinutes: StateFlow<Int>

    /**
     * The task the session screen is showing when nothing is running — opened from Up next to look
     * at the work before committing to a timer.
     *
     * Here rather than in a view model because the session screen is its own navigation entry with
     * its own view model, so this is the same boundary [activeSession] already crosses. In memory
     * only: unlike a session, an unstarted preview is not worth surviving the process.
     */
    val previewTaskId: StateFlow<Long?>

    /**
     * The saved session the session screen is setting up before it runs: its id,
     * [SavedSession.NEW_ID] for one not saved yet, or `null` for none (#517).
     *
     * Beside [previewTaskId] for the same reason, and never set together with it: the screen is
     * about one thing. Setting either clears the other, and clearing the preview clears both.
     */
    val setupSessionId: StateFlow<Long?>

    /**
     * What each task has had out of today, by task id.
     *
     * Kept here beside the session rather than in the day's activity record: that record is history
     * and only ever needs totals, while this is a fact about right now — what the work you are
     * looking at has already taken. It resets with the date.
     */
    val focusToday: StateFlow<Map<Long, TaskFocusToday>>

    /**
     * What [taskId] has had out of [date], or an empty record when the stored day is not [date].
     *
     * A read rather than a look at [focusToday], because that flow is seeded once with the date it
     * was built on: a process alive across midnight goes on holding yesterday's map until something
     * writes to it. Nothing on screen minds, but a caller deciding whether to stay quiet does — it
     * would silence a reminder on the strength of work done the day before.
     */
    fun focusOn(
        taskId: Long,
        date: LocalDate,
    ): TaskFocusToday

    fun setActiveSession(session: FocusSession?)

    /** Counts one finished focus session against [taskId]. Breaks are not sessions. */
    fun recordSessionFor(
        taskId: Long,
        today: LocalDate,
    )

    /**
     * Adds [minutes] worked to [taskId]'s day, whether or not the session that earned them ran its
     * full length — a session cut short is still work done, and the only record of it.
     */
    fun addFocusMinutesFor(
        taskId: Long,
        minutes: Int,
        today: LocalDate,
    )

    fun setDefaultLengthMinutes(minutes: Int)

    fun setBreakLengthMinutes(minutes: Int)

    fun setPreviewTaskId(taskId: Long?)

    fun setSetupSessionId(sessionId: Long?)
}
