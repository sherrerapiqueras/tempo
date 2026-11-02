package com.mandrecode.tempo.features.tasks.data.mapper

import com.google.common.truth.Truth.assertThat
import com.mandrecode.tempo.core.data.entity.TaskEntity
import com.mandrecode.tempo.core.domain.model.DayOfWeek
import com.mandrecode.tempo.core.domain.model.MonthDayOption
import com.mandrecode.tempo.core.domain.model.Periodicity
import com.mandrecode.tempo.core.domain.model.Priority
import com.mandrecode.tempo.features.tasks.domain.model.Task
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import org.junit.Test

class TaskMapperTest {
    @Test
    fun `toDomain maps all fields correctly`() {
        val entity =
            TaskEntity(
                id = 1,
                title = "Test",
                description = "Desc",
                isCompleted = true,
                categoryId = 5,
                priority = Priority.HIGH,
                reminderDate = LocalDateTime(2024, 6, 15, 10, 0),
                periodicity = Periodicity.DAILY,
                parentTaskId = 2,
                sortOrder = 3,
                completedAt = LocalDateTime(2024, 6, 16, 14, 30),
            )

        val domain = entity.toDomain()

        assertThat(domain.id).isEqualTo(1)
        assertThat(domain.title).isEqualTo("Test")
        assertThat(domain.description).isEqualTo("Desc")
        assertThat(domain.isCompleted).isTrue()
        assertThat(domain.categoryId).isEqualTo(5)
        assertThat(domain.priority).isEqualTo(Priority.HIGH)
        assertThat(domain.reminderDate).isEqualTo(LocalDateTime(2024, 6, 15, 10, 0))
        assertThat(domain.periodicity).isEqualTo(Periodicity.DAILY)
        assertThat(domain.parentTaskId).isEqualTo(2)
        assertThat(domain.sortOrder).isEqualTo(3)
        assertThat(domain.completedAt).isEqualTo(LocalDateTime(2024, 6, 16, 14, 30))
    }

    @Test
    fun `toEntity maps all fields correctly`() {
        val domain =
            Task(
                id = 1,
                title = "Test",
                description = "Desc",
                isCompleted = false,
                categoryId = 3,
                priority = Priority.MEDIUM,
                reminderDate = LocalDateTime(2024, 7, 1, 9, 30),
                periodicity = Periodicity.WEEKLY,
                parentTaskId = null,
                sortOrder = 0,
                completedAt = LocalDateTime(2024, 7, 2, 11, 0),
            )

        val entity = domain.toEntity()

        assertThat(entity.id).isEqualTo(1)
        assertThat(entity.title).isEqualTo("Test")
        assertThat(entity.priority).isEqualTo(Priority.MEDIUM)
        assertThat(entity.periodicity).isEqualTo(Periodicity.WEEKLY)
        assertThat(entity.completedAt).isEqualTo(LocalDateTime(2024, 7, 2, 11, 0))
    }

    @Test
    fun `toDomain handles null priority and periodicity`() {
        val entity = TaskEntity(id = 1, title = "T", description = "D")

        val domain = entity.toDomain()

        assertThat(domain.priority).isNull()
        assertThat(domain.periodicity).isNull()
    }

    @Test
    fun `round trip preserves all data`() {
        val original =
            Task(
                id = 5,
                title = "Round Trip",
                description = "Test",
                isCompleted = true,
                priority = Priority.LOW,
                periodicity = Periodicity.MONTHLY,
                sectionId = 7L,
            )

        val result = original.toEntity().toDomain()

        assertThat(result).isEqualTo(original)
    }

    @Test
    fun `list toDomain maps all elements`() {
        val entities =
            listOf(
                TaskEntity(id = 1, title = "A", description = ""),
                TaskEntity(id = 2, title = "B", description = ""),
            )

        val domains = entities.toDomain()

        assertThat(domains).hasSize(2)
        assertThat(domains[0].title).isEqualTo("A")
        assertThat(domains[1].title).isEqualTo("B")
    }

    @Test
    fun `toDomain maps complex periodicity fields`() {
        val days = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY)
        val entity =
            TaskEntity(
                id = 1,
                title = "Complex",
                description = "",
                periodicity = Periodicity.WEEKLY,
                periodicityInterval = 2,
                repeatDays = days,
                monthDayOption = null,
            )

        val domain = entity.toDomain()

        assertThat(domain.periodicityInterval).isEqualTo(2)
        assertThat(domain.repeatDays).isEqualTo(days)
        assertThat(domain.monthDayOption).isNull()
    }

    @Test
    fun `toEntity maps complex periodicity fields`() {
        val domain =
            Task(
                id = 1,
                title = "Monthly",
                description = "",
                periodicity = Periodicity.MONTHLY,
                periodicityInterval = 3,
                repeatDays = null,
                monthDayOption = MonthDayOption.LAST_DAY,
            )

        val entity = domain.toEntity()

        assertThat(entity.periodicityInterval).isEqualTo(3)
        assertThat(entity.repeatDays).isNull()
        assertThat(entity.monthDayOption).isEqualTo(MonthDayOption.LAST_DAY)
    }

    @Test
    fun `round trip preserves complex periodicity fields`() {
        val days = setOf(DayOfWeek.TUESDAY, DayOfWeek.THURSDAY)
        val original =
            Task(
                id = 7,
                title = "Round Trip Complex",
                description = "Test",
                periodicity = Periodicity.WEEKLY,
                periodicityInterval = 4,
                repeatDays = days,
                monthDayOption = null,
            )

        val result = original.toEntity().toDomain()

        assertThat(result).isEqualTo(original)
    }

    @Test
    fun `round trip preserves periodicity end date`() {
        val original =
            Task(
                id = 8,
                title = "Until March",
                description = "",
                periodicity = Periodicity.DAILY,
                periodicityEndDate = LocalDate(2027, 3, 31),
            )

        val result = original.toEntity().toDomain()

        assertThat(result.periodicityEndDate).isEqualTo(LocalDate(2027, 3, 31))
    }
}
