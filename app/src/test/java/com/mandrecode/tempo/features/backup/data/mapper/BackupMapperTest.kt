package com.mandrecode.tempo.features.backup.data.mapper

import com.google.common.truth.Truth.assertThat
import com.mandrecode.tempo.core.domain.model.DayOfWeek
import com.mandrecode.tempo.core.domain.model.MonthDayOption
import com.mandrecode.tempo.core.domain.model.Periodicity
import com.mandrecode.tempo.core.domain.model.Priority
import com.mandrecode.tempo.core.domain.model.ThemeMode
import com.mandrecode.tempo.core.domain.model.VacationPeriod
import com.mandrecode.tempo.features.backup.data.model.BackupFileDto
import com.mandrecode.tempo.features.backup.data.model.FocusSessionBackupDto
import com.mandrecode.tempo.features.backup.data.model.SettingsBackupDto
import com.mandrecode.tempo.features.backup.data.model.TaskBackupDto
import com.mandrecode.tempo.features.backup.data.model.VacationPeriodBackupDto
import com.mandrecode.tempo.features.backup.domain.model.BackupData
import com.mandrecode.tempo.features.backup.domain.model.BackupSettings
import com.mandrecode.tempo.features.backup.domain.model.ChainMembership
import com.mandrecode.tempo.features.focus.domain.model.SavedSession
import com.mandrecode.tempo.features.routines.domain.model.Habit
import com.mandrecode.tempo.features.routines.domain.model.HabitChain
import com.mandrecode.tempo.features.routines.domain.model.HabitType
import com.mandrecode.tempo.features.tasks.domain.model.Category
import com.mandrecode.tempo.features.tasks.domain.model.Task
import com.mandrecode.tempo.features.tasks.domain.model.TaskSection
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import org.junit.Assert.assertThrows
import org.junit.Test

class BackupMapperTest {
    private val workCategory =
        Category(id = 1, name = "Work", color = "red", icon = "star", sortOrder = 2, sectionsEnabled = true)

    @Test
    fun `domain to dto to domain round trip preserves every field`() {
        val data = fullyPopulatedBackupData()

        val roundTripped =
            data
                .toDto(schemaVersion = 1, appVersion = "1.0.0", exportedAt = LocalDateTime(2026, 7, 21, 10, 0))
                .toDomain()

        assertThat(roundTripped).isEqualTo(data)
    }

    private fun fullyPopulatedBackupData(): BackupData =
        BackupData(
            categories = listOf(workCategory),
            tasks =
                listOf(
                    Task(
                        id = 10,
                        title = "Report",
                        description = "Quarterly",
                        isCompleted = true,
                        categoryId = 1,
                        priority = Priority.HIGH,
                        reminderDate = LocalDateTime(2026, 8, 1, 9, 0),
                        periodicity = Periodicity.MONTHLY,
                        periodicityInterval = 2,
                        repeatDays = setOf(DayOfWeek.MONDAY, DayOfWeek.SUNDAY),
                        monthDayOption = MonthDayOption.LAST_DAY,
                        periodicityEndDate = LocalDate(2027, 3, 31),
                        parentTaskId = null,
                        sortOrder = 3,
                        completedAt = LocalDateTime(2026, 7, 1, 12, 30),
                        nextInstanceId = 11,
                        sectionId = 4,
                    ),
                ),
            sections = listOf(TaskSection(id = 4, categoryId = 1, name = "Q3", sortOrder = 1)),
            habits =
                listOf(
                    Habit(
                        id = 20,
                        title = "Run",
                        description = "5k",
                        icon = "shoe",
                        colorKey = "green",
                        reminderDate = LocalDateTime(2026, 7, 22, 7, 0),
                        isCompleted = false,
                        habitType = HabitType.QUIT,
                        createdDate = LocalDateTime(2026, 1, 1, 8, 0),
                        completionHistory = "2026-01-02,2026-01-03",
                        repeatDays = setOf(DayOfWeek.TUESDAY),
                    ),
                ),
            habitChains =
                listOf(
                    HabitChain(
                        id = 30,
                        title = "Morning",
                        description = "Wake up routine",
                        colorKey = "blue",
                        icon = "sun",
                        periodicReminder = LocalDateTime(2026, 7, 22, 6, 30),
                        createdDate = LocalDateTime(2026, 1, 1, 8, 0),
                        completionHistory = "2026-01-05",
                        repeatDays = setOf(DayOfWeek.MONDAY),
                    ),
                ),
            chainMemberships = listOf(ChainMembership(chainId = 30, habitId = 20, sortOrder = 0)),
            settings = sampleSettings(),
        )

    private fun sampleSettings(): BackupSettings =
        BackupSettings(
            themeMode = ThemeMode.DARK,
            useTempoColors = true,
            autoRemoveCompletedTasks = true,
            completedTaskRetentionDays = 90,
            vacationPeriods =
                listOf(
                    VacationPeriod(LocalDate(2026, 3, 1), LocalDate(2026, 3, 8)),
                    VacationPeriod(LocalDate(2026, 7, 14)),
                ),
        )

    @Test
    fun `minimal data with nulls and defaults round trips unchanged`() {
        val data =
            BackupData(
                categories = listOf(Category(id = -1, name = "Inbox", isDefault = true, sortOrder = -1)),
                tasks = listOf(Task(id = 1, title = "Bare", description = "", categoryId = -1)),
                habits =
                    listOf(
                        Habit(
                            id = 2,
                            title = "Bare habit",
                            description = "",
                            createdDate = LocalDateTime(2026, 1, 1, 0, 0),
                        ),
                    ),
                habitChains = emptyList(),
                chainMemberships = emptyList(),
            )

        val roundTripped =
            data
                .toDto(schemaVersion = 1, appVersion = "", exportedAt = LocalDateTime(2026, 1, 1, 0, 0))
                .toDomain()

        assertThat(roundTripped).isEqualTo(data)
    }

    @Test
    fun `settings without vacation periods decode to an empty list`() {
        val dto =
            SettingsBackupDto(
                themeMode = "SYSTEM",
            )

        assertThat(dto.toDomain().vacationPeriods).isEmpty()
    }

    @Test
    fun `hand-edited vacation periods are normalized on decode`() {
        val dto =
            SettingsBackupDto(
                themeMode = "SYSTEM",
                vacationPeriods =
                    listOf(
                        // Overlapping with the next one, unsorted, plus an inverted and an
                        // unparsable entry that must both be dropped.
                        VacationPeriodBackupDto(start = "2026-03-05", end = "2026-03-20"),
                        VacationPeriodBackupDto(start = "2026-03-01", end = "2026-03-10"),
                        VacationPeriodBackupDto(start = "2026-05-10", end = "2026-05-01"),
                        VacationPeriodBackupDto(start = "not-a-date"),
                        VacationPeriodBackupDto(start = "2026-06-01", end = "nonsense"),
                    ),
            )

        assertThat(dto.toDomain().vacationPeriods)
            .containsExactly(VacationPeriod(LocalDate(2026, 3, 1), LocalDate(2026, 3, 20)))
    }

    @Test
    fun `task written before end dates existed decodes with no end date`() {
        val dto =
            com.mandrecode.tempo.features.backup.data.model
                .BackupFileDto(
                    schemaVersion = 1,
                    categories = listOf(),
                    tasks = listOf(TaskBackupDto(id = 1, title = "T", categoryId = 1, periodicity = "DAILY")),
                )

        assertThat(
            dto
                .toDomain()
                .tasks
                .single()
                .periodicityEndDate,
        ).isNull()
    }

    @Test
    fun `malformed end date fails decoding`() {
        val dto =
            com.mandrecode.tempo.features.backup.data.model
                .BackupFileDto(
                    schemaVersion = 1,
                    categories = listOf(),
                    tasks = listOf(TaskBackupDto(id = 1, title = "T", categoryId = 1, periodicityEndDate = "soon")),
                )

        assertThrows(IllegalArgumentException::class.java) { dto.toDomain() }
    }

    @Test
    fun `unknown day of week number fails decoding`() {
        val dto =
            com.mandrecode.tempo.features.backup.data.model
                .BackupFileDto(
                    schemaVersion = 1,
                    categories = listOf(),
                    tasks =
                        listOf(
                            TaskBackupDto(id = 1, title = "T", categoryId = 1, repeatDays = listOf(9)),
                        ),
                )

        assertThrows(IllegalArgumentException::class.java) { dto.toDomain() }
    }

    @Test
    fun `saved focus sessions round trip, linked and unlinked`() {
        val data =
            BackupData(
                categories = emptyList(),
                tasks = emptyList(),
                habits = emptyList(),
                habitChains = emptyList(),
                chainMemberships = emptyList(),
                focusSessions =
                    listOf(
                        SavedSession(1, "Study", focusMinutes = 50, breakMinutes = 10, rounds = 4, taskId = 7),
                        SavedSession(id = 2, title = "Read", focusMinutes = 25, breakMinutes = 5),
                    ),
            )

        val roundTripped =
            data
                .toDto(schemaVersion = 1, appVersion = "", exportedAt = LocalDateTime(2026, 11, 13, 0, 0))
                .toDomain()

        assertThat(roundTripped.focusSessions).isEqualTo(data.focusSessions)
    }

    @Test
    fun `a hand-edited focus session is brought into range on decode`() {
        val dto =
            BackupFileDto(
                schemaVersion = 1,
                focusSessions =
                    listOf(FocusSessionBackupDto(id = 1, title = " Study ", focusMinutes = 0, rounds = 500)),
            )

        val session = dto.toDomain().focusSessions.single()

        assertThat(session.title).isEqualTo("Study")
        assertThat(session.focusMinutes).isEqualTo(1)
        assertThat(session.breakMinutes).isEqualTo(5)
        assertThat(session.rounds).isEqualTo(12)
    }

    @Test
    fun `a file written before saved sessions existed decodes with none`() {
        assertThat(BackupFileDto(schemaVersion = 1).toDomain().focusSessions).isEmpty()
    }
}
