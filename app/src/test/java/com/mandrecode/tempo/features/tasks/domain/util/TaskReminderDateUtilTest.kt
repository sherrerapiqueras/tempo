package com.mandrecode.tempo.features.tasks.domain.util

import com.google.common.truth.Truth.assertThat
import com.mandrecode.tempo.core.domain.model.DayOfWeek
import com.mandrecode.tempo.core.domain.model.MonthDayOption
import com.mandrecode.tempo.core.domain.model.Periodicity
import com.mandrecode.tempo.features.tasks.domain.model.Task
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.plus
import org.junit.Test

class TaskReminderDateUtilTest {
    private val fixedNow = LocalDateTime(2026, 6, 17, 12, 0, 0)

    // ── advanceReminderIfNeeded ──────────────────────────────────────────

    @Test
    fun `null reminder returns task unchanged`() {
        val task = task(reminderDate = null, periodicity = Periodicity.DAILY)

        val result = advanceReminder(task)

        assertThat(result).isEqualTo(task)
    }

    @Test
    fun `null periodicity returns task unchanged`() {
        val past = LocalDateTime(2020, 1, 1, 10, 0)
        val task = task(reminderDate = past, periodicity = null)

        val result = advanceReminder(task)

        assertThat(result).isEqualTo(task)
    }

    @Test
    fun `future reminder returns task unchanged`() {
        val future = LocalDateTime(2099, 1, 1, 10, 0)
        val task = task(reminderDate = future, periodicity = Periodicity.DAILY)

        val result = advanceReminder(task)

        assertThat(result.reminderDate).isEqualTo(future)
    }

    @Test
    fun `past daily reminder advances to future`() {
        val past = LocalDateTime(2020, 1, 1, 10, 0)
        val task = task(reminderDate = past, periodicity = Periodicity.DAILY)
        val now = fixedNow
        val result = advanceReminder(task)

        assertThat(result.reminderDate).isNotNull()
        assertThat(result.reminderDate!! > now).isTrue()
    }

    @Test
    fun `past weekly reminder advances to future`() {
        val past = LocalDateTime(2020, 1, 6, 9, 0)
        val task = task(reminderDate = past, periodicity = Periodicity.WEEKLY)
        val now = fixedNow
        val result = advanceReminder(task)

        assertThat(result.reminderDate).isNotNull()
        assertThat(result.reminderDate!! > now).isTrue()
    }

    @Test
    fun `past monthly reminder advances to future`() {
        val past = LocalDateTime(2020, 1, 15, 8, 30)
        val task = task(reminderDate = past, periodicity = Periodicity.MONTHLY)
        val now = fixedNow
        val result = advanceReminder(task)

        assertThat(result.reminderDate).isNotNull()
        assertThat(result.reminderDate!! > now).isTrue()
    }

    @Test
    fun `past yearly reminder advances to future`() {
        val past = LocalDateTime(2020, 6, 15, 12, 0)
        val task = task(reminderDate = past, periodicity = Periodicity.YEARLY)
        val now = fixedNow
        val result = advanceReminder(task)

        assertThat(result.reminderDate).isNotNull()
        assertThat(result.reminderDate!! > now).isTrue()
    }

    @Test
    fun `advanced reminder preserves hour and minute`() {
        val past = LocalDateTime(2020, 1, 1, 14, 45)
        val task = task(reminderDate = past, periodicity = Periodicity.DAILY)

        val result = advanceReminder(task)
        val advancedReminder = result.reminderDate!!

        assertThat(advancedReminder.hour).isEqualTo(14)
        assertThat(advancedReminder.minute).isEqualTo(45)
    }

    @Test
    fun `advanced reminder preserves other task fields`() {
        val past = LocalDateTime(2020, 1, 1, 10, 0)
        val task = task(reminderDate = past, periodicity = Periodicity.DAILY)

        val result = advanceReminder(task)

        assertThat(result.id).isEqualTo(task.id)
        assertThat(result.title).isEqualTo(task.title)
        assertThat(result.description).isEqualTo(task.description)
        assertThat(result.periodicity).isEqualTo(task.periodicity)
    }

    @Test
    fun `past daily reminder with interval 3 advances to future`() {
        val past = LocalDateTime(2020, 1, 1, 10, 0)
        val task = task(reminderDate = past, periodicity = Periodicity.DAILY, periodicityInterval = 3)
        val now = fixedNow
        val result = advanceReminder(task)

        assertThat(result.reminderDate).isNotNull()
        assertThat(result.reminderDate!! > now).isTrue()
    }

    @Test
    fun `past weekly reminder with repeatDays advances to matching day`() {
        // 2020-01-06 is a Monday
        val past = LocalDateTime(2020, 1, 6, 9, 0)
        val days = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY)
        val task =
            task(
                reminderDate = past,
                periodicity = Periodicity.WEEKLY,
                repeatDays = days,
            )
        val now = fixedNow
        val result = advanceReminder(task)

        assertThat(result.reminderDate).isNotNull()
        assertThat(result.reminderDate!! > now).isTrue()
        // Should land on Mon, Wed, or Fri
        val resultDay = DayOfWeek.fromKotlinDayOfWeek(result.reminderDate!!.dayOfWeek)
        assertThat(resultDay).isIn(days)
    }

    @Test
    fun `past monthly reminder with FIRST_DAY option lands on 1st`() {
        val past = LocalDateTime(2020, 1, 15, 8, 0)
        val task =
            task(
                reminderDate = past,
                periodicity = Periodicity.MONTHLY,
                monthDayOption = MonthDayOption.FIRST_DAY,
            )
        val now = fixedNow
        val result = advanceReminder(task)

        assertThat(result.reminderDate).isNotNull()
        assertThat(result.reminderDate!! > now).isTrue()
        assertThat(result.reminderDate!!.dayOfMonth).isEqualTo(1)
    }

    @Test
    fun `past monthly reminder with LAST_DAY option lands on last day of month`() {
        val past = LocalDateTime(2020, 1, 15, 8, 0)
        val task =
            task(
                reminderDate = past,
                periodicity = Periodicity.MONTHLY,
                monthDayOption = MonthDayOption.LAST_DAY,
            )
        val now = fixedNow
        val result = advanceReminder(task)

        assertThat(result.reminderDate).isNotNull()
        assertThat(result.reminderDate!! > now).isTrue()
        // Verify it's the last day of its month
        val date = result.reminderDate!!.date
        val nextDay = date.plus(kotlinx.datetime.DatePeriod(days = 1))
        assertThat(nextDay.dayOfMonth).isEqualTo(1)
    }

    // ── calculateNextOccurrence ─────────────────────────────────────────

    @Test
    fun `calculateNextOccurrence daily interval 1`() {
        val from = LocalDateTime(2024, 6, 15, 10, 0)

        val next =
            TaskReminderDateUtil.calculateNextOccurrence(
                from = from,
                periodicity = Periodicity.DAILY,
            )

        assertThat(next.date).isEqualTo(kotlinx.datetime.LocalDate(2024, 6, 16))
        assertThat(next.hour).isEqualTo(10)
        assertThat(next.minute).isEqualTo(0)
    }

    @Test
    fun `calculateNextOccurrence daily interval 3`() {
        val from = LocalDateTime(2024, 6, 15, 10, 0)

        val next =
            TaskReminderDateUtil.calculateNextOccurrence(
                from = from,
                periodicity = Periodicity.DAILY,
                interval = 3,
            )

        assertThat(next.date).isEqualTo(kotlinx.datetime.LocalDate(2024, 6, 18))
        assertThat(next.hour).isEqualTo(10)
    }

    @Test
    fun `calculateNextOccurrence hourly interval 1`() {
        val from = LocalDateTime(2024, 6, 15, 10, 0)

        val next =
            TaskReminderDateUtil.calculateNextOccurrence(
                from = from,
                periodicity = Periodicity.HOURLY,
            )

        assertThat(next).isEqualTo(LocalDateTime(2024, 6, 15, 11, 0))
    }

    @Test
    fun `calculateNextOccurrence hourly interval 3`() {
        val from = LocalDateTime(2024, 6, 15, 10, 30)

        val next =
            TaskReminderDateUtil.calculateNextOccurrence(
                from = from,
                periodicity = Periodicity.HOURLY,
                interval = 3,
            )

        assertThat(next).isEqualTo(LocalDateTime(2024, 6, 15, 13, 30))
    }

    @Test
    fun `calculateNextOccurrence hourly interval 23 wraps to next day`() {
        val from = LocalDateTime(2024, 6, 15, 10, 0)

        val next =
            TaskReminderDateUtil.calculateNextOccurrence(
                from = from,
                periodicity = Periodicity.HOURLY,
                interval = 23,
            )

        assertThat(next).isEqualTo(LocalDateTime(2024, 6, 16, 9, 0))
    }

    @Test
    fun `past hourly reminder advances to future`() {
        val past = LocalDateTime(2020, 1, 1, 10, 0)
        val task =
            task(
                reminderDate = past,
                periodicity = Periodicity.HOURLY,
                periodicityInterval = 6,
            )
        val now = fixedNow
        val result = advanceReminder(task)

        assertThat(result.reminderDate).isNotNull()
        assertThat(result.reminderDate!! > now).isTrue()
    }

    @Test
    fun `calculateNextOccurrence weekly interval 1 without days`() {
        val from = LocalDateTime(2024, 6, 15, 10, 0)

        val next =
            TaskReminderDateUtil.calculateNextOccurrence(
                from = from,
                periodicity = Periodicity.WEEKLY,
            )

        assertThat(next.date).isEqualTo(kotlinx.datetime.LocalDate(2024, 6, 22))
    }

    @Test
    fun `calculateNextOccurrence weekly interval 2 without days`() {
        val from = LocalDateTime(2024, 6, 15, 10, 0)

        val next =
            TaskReminderDateUtil.calculateNextOccurrence(
                from = from,
                periodicity = Periodicity.WEEKLY,
                interval = 2,
            )

        assertThat(next.date).isEqualTo(kotlinx.datetime.LocalDate(2024, 6, 29))
    }

    @Test
    fun `calculateNextOccurrence weekly with repeatDays picks next day in same week`() {
        // 2024-06-10 is Monday
        val from = LocalDateTime(2024, 6, 10, 10, 0)
        val days = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY)

        val next =
            TaskReminderDateUtil.calculateNextOccurrence(
                from = from,
                periodicity = Periodicity.WEEKLY,
                repeatDays = days,
            )

        // Should be Wednesday 2024-06-12
        assertThat(next.date).isEqualTo(kotlinx.datetime.LocalDate(2024, 6, 12))
    }

    @Test
    fun `calculateNextOccurrence weekly with repeatDays wraps to next week`() {
        // 2024-06-14 is Friday
        val from = LocalDateTime(2024, 6, 14, 10, 0)
        val days = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY)

        val next =
            TaskReminderDateUtil.calculateNextOccurrence(
                from = from,
                periodicity = Periodicity.WEEKLY,
                repeatDays = days,
            )

        // No more days in this week, should wrap to Monday 2024-06-17
        assertThat(next.date).isEqualTo(kotlinx.datetime.LocalDate(2024, 6, 17))
    }

    @Test
    fun `calculateNextOccurrence weekly interval 2 with repeatDays advances within same week`() {
        // 2024-06-10 is Monday; Mon/Fri selected, interval=2
        // Should still advance to Friday in the same repetition week
        val from = LocalDateTime(2024, 6, 10, 10, 0)
        val days = setOf(DayOfWeek.MONDAY, DayOfWeek.FRIDAY)

        val next =
            TaskReminderDateUtil.calculateNextOccurrence(
                from = from,
                periodicity = Periodicity.WEEKLY,
                interval = 2,
                repeatDays = days,
            )

        // Friday of the same week = 2024-06-14
        assertThat(next.date).isEqualTo(kotlinx.datetime.LocalDate(2024, 6, 14))
    }

    @Test
    fun `calculateNextOccurrence weekly interval 2 with repeatDays skips a week`() {
        // 2024-06-14 is Friday — last selected day in week
        val from = LocalDateTime(2024, 6, 14, 10, 0)
        val days = setOf(DayOfWeek.MONDAY, DayOfWeek.FRIDAY)

        val next =
            TaskReminderDateUtil.calculateNextOccurrence(
                from = from,
                periodicity = Periodicity.WEEKLY,
                interval = 2,
                repeatDays = days,
            )

        // With interval=2, next week is skipped; first selected day of the week after is Monday
        // 2024-06-14 (Fri) -> skip 1 week -> first day (Mon) of the following week = 2024-06-24
        assertThat(next.date).isEqualTo(kotlinx.datetime.LocalDate(2024, 6, 24))
    }

    @Test
    fun `calculateNextOccurrence monthly SAME_DAY preserves day of month`() {
        val from = LocalDateTime(2024, 6, 15, 10, 0)

        val next =
            TaskReminderDateUtil.calculateNextOccurrence(
                from = from,
                periodicity = Periodicity.MONTHLY,
            )

        assertThat(next.date).isEqualTo(kotlinx.datetime.LocalDate(2024, 7, 15))
    }

    @Test
    fun `calculateNextOccurrence monthly interval 3`() {
        val from = LocalDateTime(2024, 6, 15, 10, 0)

        val next =
            TaskReminderDateUtil.calculateNextOccurrence(
                from = from,
                periodicity = Periodicity.MONTHLY,
                interval = 3,
            )

        assertThat(next.date).isEqualTo(kotlinx.datetime.LocalDate(2024, 9, 15))
    }

    @Test
    fun `calculateNextOccurrence monthly FIRST_DAY`() {
        val from = LocalDateTime(2024, 6, 15, 10, 0)

        val next =
            TaskReminderDateUtil.calculateNextOccurrence(
                from = from,
                periodicity = Periodicity.MONTHLY,
                monthDayOption = MonthDayOption.FIRST_DAY,
            )

        assertThat(next.date).isEqualTo(kotlinx.datetime.LocalDate(2024, 7, 1))
    }

    @Test
    fun `calculateNextOccurrence monthly LAST_DAY`() {
        val from = LocalDateTime(2024, 6, 15, 10, 0)

        val next =
            TaskReminderDateUtil.calculateNextOccurrence(
                from = from,
                periodicity = Periodicity.MONTHLY,
                monthDayOption = MonthDayOption.LAST_DAY,
            )

        assertThat(next.date).isEqualTo(kotlinx.datetime.LocalDate(2024, 7, 31))
    }

    @Test
    fun `calculateNextOccurrence monthly LAST_DAY handles February`() {
        val from = LocalDateTime(2024, 1, 31, 10, 0)

        val next =
            TaskReminderDateUtil.calculateNextOccurrence(
                from = from,
                periodicity = Periodicity.MONTHLY,
                monthDayOption = MonthDayOption.LAST_DAY,
            )

        // 2024 is a leap year, Feb has 29 days
        assertThat(next.date).isEqualTo(kotlinx.datetime.LocalDate(2024, 2, 29))
    }

    @Test
    fun `calculateNextOccurrence monthly LAST_DAY handles February non-leap`() {
        val from = LocalDateTime(2025, 1, 31, 10, 0)

        val next =
            TaskReminderDateUtil.calculateNextOccurrence(
                from = from,
                periodicity = Periodicity.MONTHLY,
                monthDayOption = MonthDayOption.LAST_DAY,
            )

        // 2025 is NOT a leap year
        assertThat(next.date).isEqualTo(kotlinx.datetime.LocalDate(2025, 2, 28))
    }

    @Test
    fun `calculateNextOccurrence monthly FIRST_DAY interval 2`() {
        val from = LocalDateTime(2024, 6, 1, 10, 0)

        val next =
            TaskReminderDateUtil.calculateNextOccurrence(
                from = from,
                periodicity = Periodicity.MONTHLY,
                interval = 2,
                monthDayOption = MonthDayOption.FIRST_DAY,
            )

        assertThat(next.date).isEqualTo(kotlinx.datetime.LocalDate(2024, 8, 1))
    }

    @Test
    fun `calculateNextOccurrence yearly interval 1`() {
        val from = LocalDateTime(2024, 6, 15, 10, 0)

        val next =
            TaskReminderDateUtil.calculateNextOccurrence(
                from = from,
                periodicity = Periodicity.YEARLY,
            )

        assertThat(next.date).isEqualTo(kotlinx.datetime.LocalDate(2025, 6, 15))
    }

    @Test
    fun `calculateNextOccurrence yearly interval 2`() {
        val from = LocalDateTime(2024, 6, 15, 10, 0)

        val next =
            TaskReminderDateUtil.calculateNextOccurrence(
                from = from,
                periodicity = Periodicity.YEARLY,
                interval = 2,
            )

        assertThat(next.date).isEqualTo(kotlinx.datetime.LocalDate(2026, 6, 15))
    }

    @Test
    fun `calculateNextOccurrence preserves time`() {
        val from = LocalDateTime(2024, 6, 15, 14, 45)

        val next =
            TaskReminderDateUtil.calculateNextOccurrence(
                from = from,
                periodicity = Periodicity.DAILY,
                interval = 5,
            )

        assertThat(next.hour).isEqualTo(14)
        assertThat(next.minute).isEqualTo(45)
    }

    // ── isPastReminderThatWontFire ─────────────────────────────────

    @Test
    fun `isPast returns true for past reminder without periodicity`() {
        val past = LocalDateTime(2020, 1, 1, 10, 0)
        val task = task(reminderDate = past, periodicity = null)

        assertThat(TaskReminderDateUtil.isPastReminderThatWontFire(task)).isTrue()
    }

    @Test
    fun `isPast returns false for past reminder with periodicity`() {
        val past = LocalDateTime(2020, 1, 1, 10, 0)
        val task = task(reminderDate = past, periodicity = Periodicity.DAILY)

        assertThat(TaskReminderDateUtil.isPastReminderThatWontFire(task)).isFalse()
    }

    @Test
    fun `isPast returns false for future reminder without periodicity`() {
        val future = LocalDateTime(2099, 1, 1, 10, 0)
        val task = task(reminderDate = future, periodicity = null)

        assertThat(TaskReminderDateUtil.isPastReminderThatWontFire(task)).isFalse()
    }

    @Test
    fun `isPast returns false for null reminder`() {
        val task = task(reminderDate = null, periodicity = null)

        assertThat(TaskReminderDateUtil.isPastReminderThatWontFire(task)).isFalse()
    }

    // ── helpers ──────────────────────────────────────────────────────────

    // ── periodicity end date ─────────────────────────────────────────────

    @Test
    fun `next occurrence on the end date is within it`() {
        val task = endingTask(LocalDate(2026, 6, 18), reminderDate = LocalDateTime(2026, 6, 17, 18, 0))

        assertThat(TaskReminderDateUtil.nextOccurrenceWithinEnd(task, fixedNow))
            .isEqualTo(LocalDateTime(2026, 6, 18, 18, 0))
    }

    @Test
    fun `next occurrence after the end date is null`() {
        val task = endingTask(LocalDate(2026, 6, 17), reminderDate = LocalDateTime(2026, 6, 17, 18, 0))

        assertThat(TaskReminderDateUtil.nextOccurrenceWithinEnd(task, fixedNow)).isNull()
    }

    @Test
    fun `no end date always has a next occurrence`() {
        val task = task(reminderDate = LocalDateTime(2026, 6, 17, 18, 0), periodicity = Periodicity.DAILY)

        assertThat(TaskReminderDateUtil.nextOccurrenceWithinEnd(task, fixedNow))
            .isEqualTo(LocalDateTime(2026, 6, 18, 18, 0))
    }

    @Test
    fun `no periodicity has no next occurrence`() {
        val task = task(reminderDate = LocalDateTime(2026, 6, 17, 18, 0), periodicity = null)

        assertThat(TaskReminderDateUtil.nextOccurrenceWithinEnd(task, fixedNow)).isNull()
    }

    @Test
    fun `interval that steps over the end date ends the series`() {
        val task =
            endingTask(LocalDate(2026, 6, 23), reminderDate = LocalDateTime(2026, 6, 17, 18, 0))
                .copy(periodicity = Periodicity.DAILY, periodicityInterval = 7)

        assertThat(TaskReminderDateUtil.nextOccurrenceWithinEnd(task, fixedNow)).isNull()
    }

    @Test
    fun `hourly occurrences continue through the end date and stop after it`() {
        val endDate = LocalDate(2026, 6, 17)
        val lastHour =
            endingTask(endDate, reminderDate = LocalDateTime(2026, 6, 17, 22, 0))
                .copy(periodicity = Periodicity.HOURLY)
        val pastLastHour = lastHour.copy(reminderDate = LocalDateTime(2026, 6, 17, 23, 0))

        assertThat(TaskReminderDateUtil.nextOccurrenceWithinEnd(lastHour, fixedNow))
            .isEqualTo(LocalDateTime(2026, 6, 17, 23, 0))
        assertThat(TaskReminderDateUtil.nextOccurrenceWithinEnd(pastLastHour, fixedNow)).isNull()
    }

    @Test
    fun `weekly next occurrence respects the end date`() {
        // 17 June 2026 is a Wednesday, and the next selected day is the following Monday, the 22nd.
        val weekly =
            endingTask(LocalDate(2026, 6, 21), reminderDate = LocalDateTime(2026, 6, 17, 18, 0))
                .copy(periodicity = Periodicity.WEEKLY, repeatDays = setOf(DayOfWeek.MONDAY))

        assertThat(TaskReminderDateUtil.nextOccurrenceWithinEnd(weekly, fixedNow)).isNull()
        assertThat(
            TaskReminderDateUtil.nextOccurrenceWithinEnd(
                weekly.copy(periodicityEndDate = LocalDate(2026, 6, 22)),
                fixedNow,
            ),
        ).isEqualTo(LocalDateTime(2026, 6, 22, 18, 0))
    }

    @Test
    fun `monthly and yearly next occurrences respect the end date`() {
        val monthly =
            endingTask(LocalDate(2026, 7, 16), reminderDate = LocalDateTime(2026, 6, 17, 18, 0))
                .copy(periodicity = Periodicity.MONTHLY)
        val yearly =
            endingTask(LocalDate(2027, 6, 16), reminderDate = LocalDateTime(2026, 6, 17, 18, 0))
                .copy(periodicity = Periodicity.YEARLY)

        assertThat(TaskReminderDateUtil.nextOccurrenceWithinEnd(monthly, fixedNow)).isNull()
        assertThat(TaskReminderDateUtil.nextOccurrenceWithinEnd(yearly, fixedNow)).isNull()
    }

    @Test
    fun `skipping ahead past the end date ends the series even when the first step is within it`() {
        // The very next occurrence, the 11th, is within the end date but is itself long gone.
        val task = endingTask(LocalDate(2026, 6, 15), reminderDate = LocalDateTime(2026, 6, 10, 9, 0))

        assertThat(TaskReminderDateUtil.nextOccurrenceWithinEnd(task, fixedNow)).isNull()
    }

    @Test
    fun `skipping ahead stays on the next future occurrence within the end date`() {
        val task = endingTask(LocalDate(2026, 6, 30), reminderDate = LocalDateTime(2026, 6, 10, 9, 0))

        assertThat(TaskReminderDateUtil.nextOccurrenceWithinEnd(task, fixedNow))
            .isEqualTo(LocalDateTime(2026, 6, 18, 9, 0))
    }

    @Test
    fun `advance leaves the reminder alone when it would pass the end date`() {
        val task = endingTask(LocalDate(2026, 6, 17), reminderDate = LocalDateTime(2026, 6, 17, 9, 0))

        assertThat(advanceReminder(task)).isEqualTo(task)
    }

    @Test
    fun `advance moves the reminder when the next occurrence is within the end date`() {
        val task = endingTask(LocalDate(2026, 6, 18), reminderDate = LocalDateTime(2026, 6, 17, 9, 0))

        assertThat(advanceReminder(task).reminderDate).isEqualTo(LocalDateTime(2026, 6, 18, 9, 0))
    }

    @Test
    fun `withoutRecurrence clears every recurrence field`() {
        val stripped =
            endingTask(LocalDate(2026, 6, 18), reminderDate = LocalDateTime(2026, 6, 17, 9, 0))
                .copy(
                    periodicityInterval = 3,
                    repeatDays = setOf(DayOfWeek.MONDAY),
                    monthDayOption = MonthDayOption.LAST_DAY,
                ).withoutRecurrence()

        assertThat(stripped.periodicity).isNull()
        assertThat(stripped.periodicityInterval).isEqualTo(1)
        assertThat(stripped.repeatDays).isNull()
        assertThat(stripped.monthDayOption).isNull()
        assertThat(stripped.periodicityEndDate).isNull()
        assertThat(stripped.reminderDate).isEqualTo(LocalDateTime(2026, 6, 17, 9, 0))
    }

    @Test
    fun `past reminder that the end date stops from advancing won't fire`() {
        val task = endingTask(LocalDate(2026, 6, 17), reminderDate = LocalDateTime(2026, 6, 17, 9, 0))

        assertThat(TaskReminderDateUtil.isPastReminderThatWontFire(task, fixedNow)).isTrue()
    }

    @Test
    fun `past reminder that can still advance within the end date will fire`() {
        val task = endingTask(LocalDate(2026, 6, 18), reminderDate = LocalDateTime(2026, 6, 17, 9, 0))

        assertThat(TaskReminderDateUtil.isPastReminderThatWontFire(task, fixedNow)).isFalse()
    }

    @Test
    fun `future reminder past its own end date will fire`() {
        val task = endingTask(LocalDate(2026, 6, 17), reminderDate = LocalDateTime(2026, 6, 20, 9, 0))

        assertThat(TaskReminderDateUtil.isPastReminderThatWontFire(task, fixedNow)).isFalse()
    }

    @Test
    fun `asNextOccurrence starts a fresh incomplete task and keeps the recurrence`() {
        val next =
            endingTask(LocalDate(2026, 6, 30), reminderDate = LocalDateTime(2026, 6, 17, 9, 0))
                .copy(isCompleted = true, completedAt = fixedNow, nextInstanceId = 9L)
                .asNextOccurrence(LocalDateTime(2026, 6, 18, 9, 0))

        assertThat(next.id).isEqualTo(0L)
        assertThat(next.isCompleted).isFalse()
        assertThat(next.completedAt).isNull()
        assertThat(next.nextInstanceId).isNull()
        assertThat(next.reminderDate).isEqualTo(LocalDateTime(2026, 6, 18, 9, 0))
        assertThat(next.periodicity).isEqualTo(Periodicity.DAILY)
        assertThat(next.periodicityEndDate).isEqualTo(LocalDate(2026, 6, 30))
    }

    private fun endingTask(
        endDate: LocalDate,
        reminderDate: LocalDateTime,
    ) = task(reminderDate = reminderDate, periodicity = Periodicity.DAILY).copy(periodicityEndDate = endDate)

    private fun advanceReminder(task: Task): Task = TaskReminderDateUtil.advanceReminderIfNeeded(task, now = fixedNow)

    private fun task(
        reminderDate: LocalDateTime? = null,
        periodicity: Periodicity? = null,
        periodicityInterval: Int = 1,
        repeatDays: Set<DayOfWeek>? = null,
        monthDayOption: MonthDayOption? = null,
    ) = Task(
        id = 1L,
        title = "Test Task",
        description = "Test Description",
        reminderDate = reminderDate,
        periodicity = periodicity,
        periodicityInterval = periodicityInterval,
        repeatDays = repeatDays,
        monthDayOption = monthDayOption,
    )
}
