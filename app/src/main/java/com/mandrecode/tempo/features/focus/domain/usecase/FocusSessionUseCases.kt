package com.mandrecode.tempo.features.focus.domain.usecase

import com.mandrecode.tempo.core.domain.repository.DailyFocusActivityRepository
import com.mandrecode.tempo.features.focus.domain.model.FocusSession
import com.mandrecode.tempo.features.focus.domain.model.SavedSession
import com.mandrecode.tempo.features.focus.domain.model.SessionCycle
import com.mandrecode.tempo.features.focus.domain.repository.FocusSessionRepository
import com.mandrecode.tempo.features.focus.domain.scheduler.FocusSessionScheduler
import jakarta.inject.Inject
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Starting, pausing, resuming and ending the one focus session.
 *
 * Grouped in a single class because every operation shares the same three side effects — persist
 * the session, reschedule the alarm, redraw the notification — and splitting them across four
 * classes would mean repeating that trio four times.
 */
class FocusSessionUseCases
    @Inject
    constructor(
        private val sessionRepository: FocusSessionRepository,
        private val activityRepository: DailyFocusActivityRepository,
        private val scheduler: FocusSessionScheduler,
        private val clock: Clock,
    ) {
        /**
         * Begins a session on [taskId]. Any session already running is ended first and its minutes
         * banked, so starting on a second task replaces rather than stacks.
         *
         * [lengthMinutes] overrides the configured length for this start alone, for the times one
         * session wants to be shorter or longer without changing what every session is. [cycle]
         * keeps a break taken after a saved session's last round tied to that session.
         */
        suspend fun start(
            taskId: Long?,
            title: String,
            isBreak: Boolean = false,
            lengthMinutes: Int? = null,
            cycle: SessionCycle? = null,
        ) {
            end()
            val configured =
                if (isBreak) {
                    sessionRepository.breakLengthMinutes.value
                } else {
                    sessionRepository.defaultLengthMinutes.value
                }
            val length = FocusSession.lengthOf(lengthMinutes ?: configured)
            apply(
                FocusSession.start(
                    taskId = taskId,
                    title = title,
                    now = clock.now(),
                    length = length,
                    isBreak = isBreak,
                    cycle = cycle,
                ),
            )
        }

        /**
         * Begins [saved] from its first round, replacing whatever is running the way [start] does.
         */
        suspend fun startSaved(saved: SavedSession) {
            end()
            apply(FocusSession.start(saved.normalized(), clock.now()))
        }

        /**
         * The running phase's time is up: moves a saved session on to its rest or its next round,
         * and ends anything else.
         *
         * Acts only on a session that has really run out, so a late or repeated alarm finds the
         * phase it already started and leaves it alone. One that arrives early — the clock was set
         * back, or the alarm beat the stored end by a hair — is armed again rather than dropped:
         * this alarm is the only thing that ends a session while the app is closed.
         *
         * The next phase replaces the expired one in a single write, so the session never reads as
         * gone in between, which is what Focus takes as "finished" and answers with its sheet. It
         * is stored before the expired round is banked: a process killed between the two loses that
         * round's minutes rather than finding the round still there and banking it twice, the same
         * side [end] errs on.
         */
        suspend fun timeUp(): TimeUp? {
            val now = clock.now()
            val running = sessionRepository.activeSession.value
            val session = running?.takeIf { it.hasExpired(now) }
            if (session == null) {
                running?.takeIf { !it.isPaused }?.let { scheduler.scheduleSessionEnd(it) }
            } else {
                val next = session.nextPhase(now)
                if (next == null) {
                    end()
                } else {
                    // Cleared first, so the new phase arrives as news: left in place, the
                    // notification would only update, and it alerts once.
                    scheduler.clearOngoingNotification()
                    apply(next)
                    settle(session, now)
                }
            }
            return session?.let { TimeUp(finished = it, next = sessionRepository.activeSession.value) }
        }

        /** Cuts a rest between rounds short and starts the next round now. */
        fun skipBreak(): Boolean {
            val next =
                sessionRepository.activeSession.value
                    ?.takeIf { it.isBreak }
                    ?.nextPhase(clock.now())
            next?.let { apply(it) }
            return next != null
        }

        fun pause() {
            val session = sessionRepository.activeSession.value ?: return
            if (session.isPaused) return
            // The alarm and its countdown are both tied to a wall-clock end time, so a paused
            // session must not keep either.
            scheduler.cancelSessionEnd()
            apply(session.pause(clock.now()), reschedule = false)
        }

        fun resume() {
            val session = sessionRepository.activeSession.value ?: return
            if (!session.isPaused) return
            apply(session.resume(clock.now()))
        }

        /**
         * Ends the session, banking whole elapsed minutes into today's activity record. Safe to
         * call when nothing is running.
         *
         * @return the session that was ended, or `null` if there was none.
         */
        suspend fun end(): FocusSession? {
            val session = sessionRepository.activeSession.value ?: return null
            val now = clock.now()

            scheduler.cancelSessionEnd()
            scheduler.clearOngoingNotification()
            sessionRepository.setActiveSession(null)

            settle(session, now)
            return session
        }

        /** Banks what [session] earned up to [now]: the day's minutes, and the task's if it has one. */
        private suspend fun settle(
            session: FocusSession,
            now: Instant,
        ) {
            // A break is time away from the work, so it is never banked as focus time, and it is
            // not a run at the task either.
            if (session.isBreak) return
            val today = clock.todayIn(TimeZone.currentSystemDefault())
            val minutes = session.bankableMinutes(now)
            if (minutes > 0) {
                activityRepository.addFocusMinutes(today, minutes)
                // Against the task as well as against the day, so the card can say what this
                // particular work has already taken rather than only what the day has.
                session.taskId?.let { sessionRepository.addFocusMinutesFor(it, minutes, today) }
            }
            // A run at the task is one that went the distance. Stopping early is the user saying
            // this was not it, so the card goes back to offering a fresh start rather than
            // reporting a session done — and having run out is the same test the finished-session
            // sheet uses, so the two never disagree about what counted. The minutes still stand:
            // they are what a cut-short session leaves behind.
            if (session.hasExpired(now)) {
                session.taskId?.let { sessionRepository.recordSessionFor(it, today) }
            }
        }

        /**
         * Reconciles a session restored from storage on app start or after a reboot: one whose time
         * already ran out is completed rather than resumed, so no stale countdown is ever shown.
         *
         * Ended outright, even with rounds to go: starting a rest for a round that ran out while
         * the phone was off would be a timer nobody asked for.
         *
         * @return the session that had already finished, or `null` if there was nothing to settle.
         */
        suspend fun reconcile(): FocusSession? {
            val session = sessionRepository.activeSession.value ?: return null
            return if (session.hasExpired(clock.now())) {
                end()
            } else {
                if (!session.isPaused) apply(session)
                null
            }
        }

        private fun apply(
            session: FocusSession,
            reschedule: Boolean = true,
        ) {
            sessionRepository.setActiveSession(session)
            if (reschedule) scheduler.scheduleSessionEnd(session)
            scheduler.showOngoingNotification(session)
        }
    }

/** What [FocusSessionUseCases.timeUp] did: the phase that ran out, and what it started, if anything. */
data class TimeUp(
    val finished: FocusSession,
    val next: FocusSession?,
)
