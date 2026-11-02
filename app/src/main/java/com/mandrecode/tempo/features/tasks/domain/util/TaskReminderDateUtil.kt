package com.mandrecode.tempo.features.tasks.domain.util

import com.mandrecode.tempo.core.domain.model.DayOfWeek
import com.mandrecode.tempo.core.domain.model.MonthDayOption
import com.mandrecode.tempo.core.domain.model.Periodicity
import com.mandrecode.tempo.features.tasks.domain.model.Task
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.DateTimePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

@Suppress("TooManyFunctions")
object TaskReminderDateUtil {
    private const val DAYS_PER_WEEK = 7
    private const val MONTHS_PER_YEAR = 12
    private const val HOURS_PER_DAY = 24

    /**
     * If the task has a reminder date in the past and a periodicity is configured,
     * advances the reminder date to the next future occurrence so the first alarm
     * is actually scheduled instead of silently skipped. A reminder is left where it is when the
     * next future occurrence would fall after the series' end date.
     */
    fun advanceReminderIfNeeded(
        task: Task,
        now: LocalDateTime = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()),
    ): Task {
        val advanced = advancePastNow(task, now)
        return if (advanced.isWithinEndDate()) advanced else task
    }

    /**
     * The date of the occurrence that follows [task], skipping any already past [now], or null
     * when that would fall after the series' end date, meaning [task] is its last occurrence.
     *
     * The end date has to be checked after skipping ahead: an occurrence that is within the end
     * date but already past would otherwise be created, and then roll over again, until the
     * series ran out.
     */
    fun nextOccurrenceWithinEnd(
        task: Task,
        now: LocalDateTime = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()),
    ): LocalDateTime? {
        val periodicity = task.periodicity ?: return null
        val next =
            calculateNextOccurrence(
                from = task.reminderDate ?: now,
                periodicity = periodicity,
                interval = task.periodicityInterval,
                repeatDays = task.repeatDays,
                monthDayOption = task.monthDayOption,
            )
        return advancePastNow(task.copy(reminderDate = next), now)
            .takeIf { it.isWithinEndDate() }
            ?.reminderDate
    }

    private fun Task.isWithinEndDate(): Boolean {
        val endDate = periodicityEndDate
        val date = reminderDate?.date
        return endDate == null || date == null || date <= endDate
    }

    private fun advancePastNow(
        task: Task,
        now: LocalDateTime,
    ): Task {
        val reminderDate = task.reminderDate
        val periodicity = task.periodicity
        if (reminderDate == null || periodicity == null || reminderDate >= now) return task

        val zone = TimeZone.currentSystemDefault()
        val nextDate =
            nextFutureOccurrence(
                from = reminderDate,
                periodicity = periodicity,
                interval = task.periodicityInterval.coerceAtLeast(1),
                repeatDays = task.repeatDays,
                monthDayOption = task.monthDayOption,
                zone = zone,
                now = now,
            )
        return task.copy(reminderDate = nextDate)
    }

    /**
     * Computes the next reminder date after [from], taking into account the full
     * recurrence configuration (periodicity type, interval, repeat-days, month-day option).
     *
     * Knows nothing of the end date, or of [now][Clock]: to decide what a series does next, use
     * [nextOccurrenceWithinEnd].
     */
    fun calculateNextOccurrence(
        from: LocalDateTime,
        periodicity: Periodicity,
        interval: Int = 1,
        repeatDays: Set<DayOfWeek>? = null,
        monthDayOption: MonthDayOption? = null,
        zone: TimeZone = TimeZone.currentSystemDefault(),
    ): LocalDateTime {
        val safeInterval = interval.coerceAtLeast(1)
        return when (periodicity) {
            Periodicity.WEEKLY ->
                if (!repeatDays.isNullOrEmpty()) {
                    nextWeeklyWithDays(from, safeInterval, repeatDays, zone)
                } else {
                    advanceByPeriod(from, DateTimePeriod(days = DAYS_PER_WEEK * safeInterval), zone)
                }

            Periodicity.MONTHLY ->
                nextMonthlyOccurrence(
                    from,
                    safeInterval,
                    monthDayOption ?: MonthDayOption.SAME_DAY,
                    zone,
                )

            Periodicity.DAILY ->
                advanceByPeriod(from, DateTimePeriod(days = safeInterval), zone)

            Periodicity.YEARLY ->
                advanceByPeriod(from, DateTimePeriod(years = safeInterval), zone)

            Periodicity.HOURLY ->
                advanceByPeriod(from, DateTimePeriod(hours = safeInterval), zone)
        }
    }

    /**
     * Returns true when the task's reminder is in the past and has nowhere to go: it has no
     * periodicity, or its next occurrence would fall after the end date. The caller can then tell
     * the user that the reminder won't fire.
     */
    fun isPastReminderThatWontFire(
        task: Task,
        now: LocalDateTime = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()),
    ): Boolean {
        val reminderDate = task.reminderDate ?: return false
        return reminderDate < now && (task.periodicity == null || !advancePastNow(task, now).isWithinEndDate())
    }

    // ── private helpers ─────────────────────────────────────────────────

    private fun nextFutureOccurrence(
        from: LocalDateTime,
        periodicity: Periodicity,
        interval: Int,
        repeatDays: Set<DayOfWeek>?,
        monthDayOption: MonthDayOption?,
        zone: TimeZone,
        now: LocalDateTime,
    ): LocalDateTime =
        when {
            periodicity == Periodicity.WEEKLY && !repeatDays.isNullOrEmpty() ->
                nextWeeklyWithDaysFuture(from, interval, repeatDays, zone, now)

            periodicity == Periodicity.MONTHLY ->
                nextMonthlyOccurrenceFuture(
                    from,
                    interval,
                    monthDayOption ?: MonthDayOption.SAME_DAY,
                    zone,
                    now,
                )

            else ->
                nextSimpleFuture(from, periodicity, interval, zone, now)
        }

    /**
     * Advances a simple (non-weekly-days, non-monthly) reminder to the first
     * future occurrence using fast-forward + step iteration.
     */
    private fun nextSimpleFuture(
        from: LocalDateTime,
        periodicity: Periodicity,
        interval: Int,
        zone: TimeZone,
        now: LocalDateTime,
    ): LocalDateTime {
        val basePeriod = basePeriodFor(periodicity, interval)
        var candidate = fastForwardCandidate(from, periodicity, interval, zone, now)
        while (candidate <= now) {
            candidate = advanceByPeriod(candidate, basePeriod, zone)
        }
        return candidate
    }

    private fun basePeriodFor(
        periodicity: Periodicity,
        interval: Int,
    ): DateTimePeriod =
        when (periodicity) {
            Periodicity.HOURLY -> DateTimePeriod(hours = interval)
            Periodicity.DAILY -> DateTimePeriod(days = interval)
            Periodicity.WEEKLY -> DateTimePeriod(days = DAYS_PER_WEEK * interval)
            Periodicity.YEARLY -> DateTimePeriod(years = interval)
            Periodicity.MONTHLY -> DateTimePeriod(months = interval)
        }

    /**
     * Returns a candidate jumped close to [now] in whole-period steps, to avoid
     * O(n) iteration when [from] is far in the past.
     */
    private fun fastForwardCandidate(
        from: LocalDateTime,
        periodicity: Periodicity,
        interval: Int,
        zone: TimeZone,
        now: LocalDateTime,
    ): LocalDateTime {
        val jumpPeriod = computeJumpPeriod(from, periodicity, interval, now) ?: return from
        return advanceByPeriod(from, jumpPeriod, zone)
    }

    private fun computeJumpPeriod(
        from: LocalDateTime,
        periodicity: Periodicity,
        interval: Int,
        now: LocalDateTime,
    ): DateTimePeriod? {
        val daysGap = from.date.daysUntil(now.date)
        val periodDays =
            when (periodicity) {
                Periodicity.DAILY -> interval
                Periodicity.WEEKLY -> DAYS_PER_WEEK * interval
                else -> 0
            }
        val period: DateTimePeriod? =
            if (periodDays > 0) {
                val jumpPeriods =
                    if (daysGap > periodDays) (daysGap / periodDays) - 1 else 0
                if (jumpPeriods > 0) DateTimePeriod(days = jumpPeriods * periodDays) else null
            } else if (periodicity == Periodicity.HOURLY &&
                daysGap > 1 &&
                interval in 1..HOURS_PER_DAY
            ) {
                val periodsPerDay = HOURS_PER_DAY / interval
                val jumpPeriods = (daysGap - 1).toLong() * periodsPerDay
                if (jumpPeriods > 0) {
                    DateTimePeriod(hours = (jumpPeriods * interval).toInt())
                } else {
                    null
                }
            } else {
                null
            }
        return period
    }

    /**
     * Advances a date by exactly one [period] using time-zone-aware arithmetic.
     */
    private fun advanceByPeriod(
        from: LocalDateTime,
        period: DateTimePeriod,
        zone: TimeZone,
    ): LocalDateTime = from.toInstant(zone).plus(period, zone).toLocalDateTime(zone)

    // ── Weekly with specific days ───────────────────────────────────────

    /**
     * When the task repeats on specific days of the week (e.g. Mon/Wed/Fri),
     * finds the next occurrence that is strictly after [from].
     *
     * If [interval] > 1 (e.g. "every 2 weeks on Mon/Wed"), the algorithm
     * cycles through the selected days within the current "repetition week"
     * before jumping to the next eligible week.
     */
    private fun nextWeeklyWithDays(
        from: LocalDateTime,
        interval: Int,
        days: Set<DayOfWeek>,
        zone: TimeZone,
    ): LocalDateTime {
        val sortedDays = days.sortedBy { it.value }
        val currentDay = DayOfWeek.fromKotlinDayOfWeek(from.dayOfWeek)
        // Find the next day in the same week (after current day)
        val nextInWeek = sortedDays.firstOrNull { it.value > currentDay.value }
        return if (nextInWeek != null) {
            // Same week, just jump to next selected day
            val daysAhead = nextInWeek.value - currentDay.value
            advanceByPeriod(from, DateTimePeriod(days = daysAhead), zone)
        } else {
            // No more selected days this week — jump to the first selected day
            // in the next eligible week (respecting the interval)
            val firstDay = sortedDays.first()
            // Days remaining until end of current ISO week (Sunday)
            val daysToEndOfWeek = DAYS_PER_WEEK - currentDay.value
            // Days from Monday to the target day
            val daysFromMonday = firstDay.value - 1
            // Total days = remaining in this week + (interval-1) full weeks + offset to target day
            val totalDays =
                daysToEndOfWeek + ((interval - 1) * DAYS_PER_WEEK) + daysFromMonday + 1
            advanceByPeriod(from, DateTimePeriod(days = totalDays), zone)
        }
    }

    /**
     * Like [nextWeeklyWithDays] but keeps advancing until the result is after [now].
     */
    private fun nextWeeklyWithDaysFuture(
        from: LocalDateTime,
        interval: Int,
        days: Set<DayOfWeek>,
        zone: TimeZone,
        now: LocalDateTime,
    ): LocalDateTime {
        // Jump ahead to approximately the right week to avoid O(n) day-by-day iteration.
        // Align to multiples of (interval * 7) so we never land in a skipped week.
        val daysUntilNow = from.date.daysUntil(now.date)
        val weekSpan = DAYS_PER_WEEK * interval.coerceAtLeast(1)
        val jumpDays =
            if (daysUntilNow > weekSpan) {
                (daysUntilNow / weekSpan) * weekSpan - weekSpan
            } else {
                0
            }
        var candidate =
            if (jumpDays > 0) {
                advanceByPeriod(from, DateTimePeriod(days = jumpDays), zone)
            } else {
                from
            }

        while (true) {
            candidate = nextWeeklyWithDays(candidate, interval, days, zone)
            if (candidate > now) return candidate
        }
    }

    // ── Monthly with day options ────────────────────────────────────────

    /**
     * Computes the next monthly occurrence considering the [monthDayOption].
     */
    private fun nextMonthlyOccurrence(
        from: LocalDateTime,
        interval: Int,
        monthDayOption: MonthDayOption,
        zone: TimeZone,
    ): LocalDateTime {
        val nextMonth = from.date.plus(DatePeriod(months = interval))
        return when (monthDayOption) {
            MonthDayOption.SAME_DAY ->
                advanceByPeriod(from, DateTimePeriod(months = interval), zone)

            MonthDayOption.FIRST_DAY -> {
                val firstOfMonth = LocalDate(nextMonth.year, nextMonth.month, 1)
                LocalDateTime(firstOfMonth, from.time)
                    .toInstant(zone)
                    .toLocalDateTime(zone)
            }

            MonthDayOption.LAST_DAY -> {
                val lastOfMonth = lastDayOfMonth(nextMonth.year, nextMonth.monthNumber)
                LocalDateTime(lastOfMonth, from.time)
                    .toInstant(zone)
                    .toLocalDateTime(zone)
            }
        }
    }

    /**
     * Like [nextMonthlyOccurrence] but keeps advancing until the result is after [now].
     */
    private fun nextMonthlyOccurrenceFuture(
        from: LocalDateTime,
        interval: Int,
        monthDayOption: MonthDayOption,
        zone: TimeZone,
        now: LocalDateTime,
    ): LocalDateTime {
        var candidate = from
        while (true) {
            candidate = nextMonthlyOccurrence(candidate, interval, monthDayOption, zone)
            if (candidate > now) return candidate
        }
    }

    /**
     * Returns the last calendar day of the given [year]/[month].
     */
    private fun lastDayOfMonth(
        year: Int,
        month: Int,
    ): LocalDate {
        val firstOfNext =
            if (month == MONTHS_PER_YEAR) {
                LocalDate(year + 1, 1, 1)
            } else {
                LocalDate(year, month + 1, 1)
            }
        return firstOfNext.minus(DatePeriod(days = 1))
    }
}
