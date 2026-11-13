package com.mandrecode.tempo.features.focus.data

import android.content.Context
import android.content.SharedPreferences
import com.google.common.truth.Truth.assertThat
import com.mandrecode.tempo.features.focus.domain.model.FocusSession
import com.mandrecode.tempo.features.focus.domain.model.SavedSession
import com.mandrecode.tempo.features.focus.domain.model.TaskFocusToday
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import org.junit.Test
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

class FocusSessionRepositoryImplTest {
    private val start = Instant.fromEpochMilliseconds(1_800_000_000_000)

    /** The clock's day, so the stored date stamp and the dates the tests pass agree. */
    private val today = LocalDate(2026, 7, 30)
    private val clock =
        object : Clock {
            override fun now(): Instant = today.atStartOfDayIn(TimeZone.currentSystemDefault()) + 12.hours
        }

    private lateinit var editor: SharedPreferences.Editor
    private lateinit var prefs: SharedPreferences

    /**
     * Builds the repository over a mock that behaves like a real store for the keys we set, so a
     * write followed by a fresh read exercises the round trip rather than the mock's defaults.
     */
    private fun createRepository(stored: MutableMap<String, Any> = mutableMapOf()): FocusSessionRepositoryImpl {
        editor =
            mockk(relaxed = true) {
                every { putLong(any(), any()) } answers {
                    stored[firstArg()] = secondArg<Long>()
                    this@mockk
                }
                every { putString(any(), any()) } answers {
                    stored[firstArg()] = secondArg<String>()
                    this@mockk
                }
                every { putInt(any(), any()) } answers {
                    stored[firstArg()] = secondArg<Int>()
                    this@mockk
                }
                every { putBoolean(any(), any()) } answers {
                    stored[firstArg()] = secondArg<Boolean>()
                    this@mockk
                }
                every { remove(any()) } answers {
                    stored.remove(firstArg())
                    this@mockk
                }
                every { apply() } just Runs
            }
        prefs =
            mockk {
                every { edit() } returns editor
                every { contains(any()) } answers { stored.containsKey(firstArg()) }
                every { getLong(any(), any()) } answers { stored[firstArg()] as? Long ?: secondArg() }
                every { getString(any(), any()) } answers { stored[firstArg()] as? String ?: secondArg() }
                every { getInt(any(), any()) } answers { stored[firstArg()] as? Int ?: secondArg() }
                every { getBoolean(any(), any()) } answers { stored[firstArg()] as? Boolean ?: secondArg() }
            }
        val context = mockk<Context> { every { getSharedPreferences(any(), any()) } returns prefs }
        return FocusSessionRepositoryImpl(context, clock)
    }

    /** A stored record already stamped with today, as a real day's writes would have left it. */
    private fun storedDay(record: String): MutableMap<String, Any> =
        mutableMapOf(
            "sessions_today_date" to today.toString(),
            "sessions_today_by_task" to record,
        )

    @Test
    fun `no session is stored initially`() {
        assertThat(createRepository().activeSession.value).isNull()
    }

    @Test
    fun `a running session round-trips through storage`() {
        val stored = mutableMapOf<String, Any>()
        val session =
            FocusSession.start(taskId = 7, title = "Report", now = start, length = 30.minutes)

        createRepository(stored).setActiveSession(session)

        assertThat(createRepository(stored).activeSession.value).isEqualTo(session)
    }

    @Test
    fun `a paused session round-trips with its banked time`() {
        val stored = mutableMapOf<String, Any>()
        val paused =
            FocusSession
                .start(taskId = 7, title = "Report", now = start)
                .pause(start + 8.minutes)

        createRepository(stored).setActiveSession(paused)
        val restored = createRepository(stored).activeSession.value

        assertThat(restored?.isPaused).isTrue()
        assertThat(restored?.completedBeforeNow).isEqualTo(8.minutes)
    }

    @Test
    fun `clearing the session removes every key rather than leaving a partial record`() {
        val stored = mutableMapOf<String, Any>()
        val repository = createRepository(stored)
        repository.setActiveSession(FocusSession.start(1, "Report", start))

        repository.setActiveSession(null)

        assertThat(repository.activeSession.value).isNull()
        assertThat(createRepository(stored).activeSession.value).isNull()
    }

    @Test
    fun `a record with no planned length is treated as absent`() {
        // Models a partially written or hand-edited store.
        val stored = mutableMapOf<String, Any>("session_task_id" to 5L)

        assertThat(createRepository(stored).activeSession.value).isNull()
    }

    @Test
    fun `the default length falls back to the standard session`() {
        assertThat(createRepository().defaultLengthMinutes.value)
            .isEqualTo(FocusSession.DEFAULT_LENGTH.inWholeMinutes.toInt())
    }

    @Test
    fun `setting a supported length stores it verbatim`() {
        val repository = createRepository()

        repository.setDefaultLengthMinutes(45)

        assertThat(repository.defaultLengthMinutes.value).isEqualTo(45)
        verify { editor.putInt("session_default_length_minutes", 45) }
    }

    @Test
    fun `a length outside the range is clamped to it`() {
        val repository = createRepository()

        repository.setDefaultLengthMinutes(FocusSession.SESSION_LENGTH_RANGE.last + 30)
        assertThat(repository.defaultLengthMinutes.value)
            .isEqualTo(FocusSession.SESSION_LENGTH_RANGE.last)

        repository.setDefaultLengthMinutes(0)
        assertThat(repository.defaultLengthMinutes.value)
            .isEqualTo(FocusSession.SESSION_LENGTH_RANGE.first)
    }

    @Test
    fun `the break length is its own setting`() {
        val repository = createRepository()

        repository.setBreakLengthMinutes(15)

        assertThat(repository.breakLengthMinutes.value).isEqualTo(15)
        // Changing one never moves the other.
        assertThat(repository.defaultLengthMinutes.value)
            .isEqualTo(FocusSession.DEFAULT_LENGTH.inWholeMinutes.toInt())
    }

    @Test
    fun `a break round-trips as a break`() {
        val stored = mutableMapOf<String, Any>()
        val brk =
            FocusSession.start(taskId = 7, title = "Report", now = start, isBreak = true)

        createRepository(stored).setActiveSession(brk)

        assertThat(createRepository(stored).activeSession.value?.isBreak).isTrue()
    }

    @Test
    fun `the stored default length is read back`() {
        val stored = mutableMapOf<String, Any>("session_default_length_minutes" to 15)

        assertThat(createRepository(stored).defaultLengthMinutes.value).isEqualTo(15)
    }

    @Test
    fun `session counts accumulate per task and survive a fresh read`() {
        val stored = mutableMapOf<String, Any>()
        val repository = createRepository(stored)

        repository.recordSessionFor(taskId = 7, today = today)
        repository.recordSessionFor(taskId = 7, today = today)
        repository.recordSessionFor(taskId = 9, today = today)

        assertThat(repository.focusToday.value[7L]?.sessions).isEqualTo(2)
        assertThat(repository.focusToday.value[9L]?.sessions).isEqualTo(1)
        // The format is hand-rolled, so a fresh instance reading it back is the real test.
        assertThat(createRepository(stored).focusToday.value[7L]?.sessions).isEqualTo(2)
    }

    @Test
    fun `minutes accumulate per task alongside the counts and survive a fresh read`() {
        val stored = mutableMapOf<String, Any>()
        val repository = createRepository(stored)

        repository.addFocusMinutesFor(taskId = 7, minutes = 12, today = today)
        repository.recordSessionFor(taskId = 7, today = today)
        repository.addFocusMinutesFor(taskId = 7, minutes = 25, today = today)

        assertThat(createRepository(stored).focusToday.value[7L])
            .isEqualTo(TaskFocusToday(sessions = 1, minutes = 37))
    }

    @Test
    fun `minutes with no finished session still leave a trace`() {
        val repository = createRepository(mutableMapOf())

        // What a session stopped early leaves behind: real time worked, and no run to its name.
        repository.addFocusMinutesFor(taskId = 7, minutes = 12, today = today)

        assertThat(repository.focusToday.value[7L])
            .isEqualTo(TaskFocusToday(sessions = 0, minutes = 12))
    }

    @Test
    fun `nothing is recorded for a session that banked no whole minute`() {
        val repository = createRepository(mutableMapOf())

        repository.addFocusMinutesFor(taskId = 7, minutes = 0, today = today)

        assertThat(repository.focusToday.value).isEmpty()
    }

    @Test
    fun `a new day starts the counts over`() {
        val stored = mutableMapOf<String, Any>()
        val repository = createRepository(stored)
        repository.recordSessionFor(taskId = 7, today = today)
        repository.addFocusMinutesFor(taskId = 7, minutes = 25, today = today)

        repository.addFocusMinutesFor(taskId = 7, minutes = 5, today = today.plus(1, DateTimeUnit.DAY))

        // Yesterday's runs do not colour today's card — and the two numbers roll over together,
        // rather than one of them carrying the day across midnight.
        assertThat(repository.focusToday.value[7L])
            .isEqualTo(TaskFocusToday(sessions = 0, minutes = 5))
    }

    @Test
    fun `a malformed stored entry is skipped rather than crashing the read`() {
        val stored = storedDay("7:2:30,rubbish,9:x,:,11:3:5")

        val today = createRepository(stored).focusToday.value

        assertThat(today[7L]).isEqualTo(TaskFocusToday(sessions = 2, minutes = 30))
        assertThat(today[11L]).isEqualTo(TaskFocusToday(sessions = 3, minutes = 5))
        assertThat(today).hasSize(2)
    }

    @Test
    fun `yesterday's record is not read back as today's`() {
        val stored =
            mutableMapOf<String, Any>(
                "sessions_today_date" to today.minus(1, DateTimeUnit.DAY).toString(),
                "sessions_today_by_task" to "7:2:50",
            )

        // The record outlives the day it belongs to, so an app opened the next morning has to
        // discard it on the way in — not wait for a write to notice the date has moved.
        assertThat(createRepository(stored).focusToday.value).isEmpty()
    }

    @Test
    fun `a single task's day is read straight from storage`() {
        val stored = storedDay("7:2:30,9:1:25")

        assertThat(createRepository(stored).focusOn(taskId = 7, date = today))
            .isEqualTo(TaskFocusToday(sessions = 2, minutes = 30))
    }

    @Test
    fun `a single task's day survives a malformed record ahead of it`() {
        // The single-task read stops at its match, so it has to keep skipping rubbish on the way.
        val stored = storedDay("rubbish,9:x,7:2:30")

        assertThat(createRepository(stored).focusOn(taskId = 7, date = today))
            .isEqualTo(TaskFocusToday(sessions = 2, minutes = 30))
    }

    @Test
    fun `a task with nothing stored has had nothing out of the day`() {
        val stored = storedDay("7:2:30")

        assertThat(createRepository(stored).focusOn(taskId = 404, date = today))
            .isEqualTo(TaskFocusToday())
    }

    @Test
    fun `a day the record does not belong to comes back empty`() {
        val stored = storedDay("7:2:30")

        assertThat(createRepository(stored).focusOn(taskId = 7, date = today.plus(1, DateTimeUnit.DAY)))
            .isEqualTo(TaskFocusToday())
    }

    @Test
    fun `the single-task read is judged against the date asked for, not the one in memory`() {
        // The repository was built yesterday and still holds that map; a caller asking about today
        // must not be told yesterday's answer.
        val stored =
            mutableMapOf<String, Any>(
                "sessions_today_date" to today.minus(1, DateTimeUnit.DAY).toString(),
                "sessions_today_by_task" to "7:2:50",
            )
        val repository = createRepository(stored)

        assertThat(repository.focusOn(taskId = 7, date = today)).isEqualTo(TaskFocusToday())
        assertThat(repository.focusOn(taskId = 7, date = today.minus(1, DateTimeUnit.DAY)))
            .isEqualTo(TaskFocusToday(sessions = 2, minutes = 50))
    }

    @Test
    fun `records written before minutes were tracked read back as sessions with no time`() {
        val stored = storedDay("7:2,9:1")

        assertThat(createRepository(stored).focusToday.value[7L])
            .isEqualTo(TaskFocusToday(sessions = 2, minutes = 0))
    }

    @Test
    fun `a saved session's round round-trips with its cycle`() {
        val stored = mutableMapOf<String, Any>()
        val session =
            FocusSession.start(
                SavedSession(id = 4, title = "Study", focusMinutes = 50, breakMinutes = 10, rounds = 3, taskId = 9),
                start,
            )

        createRepository(stored).setActiveSession(session)

        assertThat(createRepository(stored).activeSession.value).isEqualTo(session)
    }

    @Test
    fun `a session linked to no task round-trips with no task`() {
        val stored = mutableMapOf<String, Any>()
        val session =
            FocusSession.start(
                SavedSession(id = 4, title = "Study", focusMinutes = 25, breakMinutes = 5),
                start,
            )

        createRepository(stored).setActiveSession(session)
        val restored = createRepository(stored).activeSession.value

        assertThat(restored?.taskId).isNull()
        assertThat(restored).isEqualTo(session)
    }

    @Test
    fun `a session stored before saved sessions existed reads back with its task and no cycle`() {
        val stored =
            mutableMapOf<String, Any>(
                "session_task_id" to 7L,
                "session_task_title" to "Report",
                "session_planned_millis" to 30.minutes.inWholeMilliseconds,
                "session_completed_millis" to 0L,
                "session_running_since" to start.toEpochMilliseconds(),
                "session_is_break" to false,
            )

        val restored = createRepository(stored).activeSession.value

        assertThat(restored)
            .isEqualTo(FocusSession.start(taskId = 7, title = "Report", now = start, length = 30.minutes))
    }

    @Test
    fun `replacing a saved session's round with a plain timer leaves no cycle behind`() {
        val stored = mutableMapOf<String, Any>()
        val repository = createRepository(stored)
        repository.setActiveSession(
            FocusSession.start(SavedSession(id = 4, title = "Study", focusMinutes = 25, breakMinutes = 5), start),
        )

        repository.setActiveSession(FocusSession.start(taskId = 1, title = "Report", now = start))

        assertThat(createRepository(stored).activeSession.value?.cycle).isNull()
    }
}
