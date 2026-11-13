package com.mandrecode.tempo.features.focus.domain.model

import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/**
 * A running or paused focus session.
 *
 * There is at most one of these at a time — starting a session on another item replaces this one
 * rather than queueing. Modelled as a single nullable value everywhere for that reason, never a
 * collection.
 *
 * It runs on a task, on a saved session, or on a saved session linked to a task. [taskId] is the
 * task the work belongs to, if any; [cycle] is set when a [SavedSession] started it (#517).
 *
 * Time is kept as two pieces: [completedBeforeNow], the time banked by earlier run segments, and
 * [runningSince], when the current segment began. Nothing has to tick — remaining time is always
 * derived from the clock, so the session stays correct across process death, and the notification's
 * chronometer counts down against the same [endsAt] the alarm fires at.
 */
data class FocusSession(
    /** The task this time is spent on, or `null` for a saved session linked to none. */
    val taskId: Long?,
    /** The task's title, or the saved session's own name. */
    val title: String,
    val plannedLength: Duration,
    /** A break counts down and notifies like a session, but banks no focus minutes. */
    val isBreak: Boolean = false,
    /** Time already banked by previous run segments; grows on each pause. */
    val completedBeforeNow: Duration = Duration.ZERO,
    /** Start of the current run segment, or `null` while paused. */
    val runningSince: Instant? = null,
    /** Where a saved session's run stands, or `null` for a timer started straight on a task. */
    val cycle: SessionCycle? = null,
) {
    val isPaused: Boolean get() = runningSince == null

    val savedSessionId: Long? get() = cycle?.savedSessionId

    /**
     * What this timer is about, as one value: the saved session when one started it, else the task.
     * The session sheet latches this to know when its subject has gone away.
     */
    val subjectKey: String get() = subjectKeyOf(taskId = taskId, savedSessionId = savedSessionId)

    /**
     * What follows when this phase's time runs out, started at [now] — or `null` when the run is
     * over.
     *
     * A focus round with rounds still to go is followed by its rest, and that rest by the next
     * round. Nothing follows the last round: a rest nobody is coming back from is not a rest.
     */
    fun nextPhase(now: Instant): FocusSession? {
        val current = cycle?.takeIf { it.round < it.totalRounds } ?: return null
        return if (isBreak) {
            copy(
                plannedLength = current.focusLength,
                isBreak = false,
                completedBeforeNow = Duration.ZERO,
                runningSince = now,
                cycle = current.copy(round = current.round + 1),
            )
        } else {
            copy(
                plannedLength = current.breakLength,
                isBreak = true,
                completedBeforeNow = Duration.ZERO,
                runningSince = now,
            )
        }
    }

    /** When the session will finish if left running. Meaningless while paused. */
    val endsAt: Instant?
        get() = runningSince?.plus(plannedLength - completedBeforeNow)

    fun elapsed(now: Instant): Duration {
        val current = runningSince?.let { now - it } ?: Duration.ZERO
        return (completedBeforeNow + current).coerceIn(Duration.ZERO, plannedLength)
    }

    fun remaining(now: Instant): Duration = (plannedLength - elapsed(now)).coerceAtLeast(Duration.ZERO)

    fun hasExpired(now: Instant): Boolean = !isPaused && remaining(now) <= Duration.ZERO

    /** Whole minutes worth banking; a session stopped inside its first minute banks nothing. */
    fun bankableMinutes(now: Instant): Int = elapsed(now).inWholeMinutes.toInt()

    fun pause(now: Instant): FocusSession =
        if (isPaused) {
            this
        } else {
            copy(completedBeforeNow = elapsed(now), runningSince = null)
        }

    fun resume(now: Instant): FocusSession = if (isPaused) copy(runningSince = now) else this

    companion object {
        val DEFAULT_LENGTH: Duration = 25.minutes
        val DEFAULT_BREAK_LENGTH: Duration = 5.minutes

        /** The quick adjust before a session starts moves in fives, as people say them out loud. */
        const val LENGTH_STEP_MINUTES: Int = 5

        /**
         * Uncapped in spirit: the clock face the lengths are set on stops at 23:59 and there is no
         * reason for this app to be stricter than that. How long you want to sit with something is
         * not ours to have an opinion about.
         */
        val SESSION_LENGTH_RANGE: IntRange = 1..MAX_LENGTH_MINUTES
        val BREAK_LENGTH_RANGE: IntRange = 1..MAX_LENGTH_MINUTES

        private const val MAX_LENGTH_MINUTES = 23 * 60 + 59

        /** The most rounds one session repeats: a working day of pomodoros, and then some. */
        val ROUNDS_RANGE: IntRange = 1..MAX_ROUNDS

        private const val MAX_ROUNDS = 12
        private const val TASK_SUBJECT_PREFIX = "task:"
        private const val SESSION_SUBJECT_PREFIX = "session:"

        fun start(
            taskId: Long?,
            title: String,
            now: Instant,
            length: Duration = DEFAULT_LENGTH,
            isBreak: Boolean = false,
            cycle: SessionCycle? = null,
        ): FocusSession =
            FocusSession(
                taskId = taskId,
                title = title,
                plannedLength = length,
                isBreak = isBreak,
                runningSince = now,
                cycle = cycle,
            )

        /** The first focus round of [saved]. */
        fun start(
            saved: SavedSession,
            now: Instant,
        ): FocusSession =
            start(
                taskId = saved.taskId,
                title = saved.title,
                now = now,
                length = lengthOf(saved.focusMinutes),
                cycle =
                    SessionCycle(
                        savedSessionId = saved.id,
                        round = 1,
                        totalRounds = saved.rounds.coerceIn(ROUNDS_RANGE),
                        focusLength = lengthOf(saved.focusMinutes),
                        breakLength = lengthOf(saved.breakMinutes),
                    ),
            )

        /** See [FocusSession.subjectKey]; also names a task that is only being previewed. */
        fun subjectKeyOf(
            taskId: Long?,
            savedSessionId: Long? = null,
        ): String =
            when {
                savedSessionId != null -> "$SESSION_SUBJECT_PREFIX$savedSessionId"
                else -> "$TASK_SUBJECT_PREFIX$taskId"
            }

        fun lengthOf(minutes: Int): Duration = minutes.coerceAtLeast(1).minutes
    }
}

/**
 * Where a saved session's run stands.
 *
 * The lengths are copied here when the run starts rather than read back from the saved session at
 * each round: editing or deleting the session must not change a run already under way, and the
 * receiver that advances the rounds has no business opening the database to ask.
 */
data class SessionCycle(
    val savedSessionId: Long,
    /** The focus round under way, or the one the current rest follows. Starts at 1. */
    val round: Int,
    val totalRounds: Int,
    val focusLength: Duration,
    val breakLength: Duration,
) {
    val hasRounds: Boolean get() = totalRounds > 1
}
