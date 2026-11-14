package com.mandrecode.tempo.features.backup.data.mapper

import com.mandrecode.tempo.core.domain.model.DailyFocusActivity
import com.mandrecode.tempo.core.domain.model.DayOfWeek
import com.mandrecode.tempo.core.domain.model.MonthDayOption
import com.mandrecode.tempo.core.domain.model.Periodicity
import com.mandrecode.tempo.core.domain.model.Priority
import com.mandrecode.tempo.features.backup.data.model.BackupFileDto
import com.mandrecode.tempo.features.backup.data.model.CategoryBackupDto
import com.mandrecode.tempo.features.backup.data.model.ChainMemberBackupDto
import com.mandrecode.tempo.features.backup.data.model.DailyFocusActivityBackupDto
import com.mandrecode.tempo.features.backup.data.model.FocusSessionBackupDto
import com.mandrecode.tempo.features.backup.data.model.HabitBackupDto
import com.mandrecode.tempo.features.backup.data.model.HabitChainBackupDto
import com.mandrecode.tempo.features.backup.data.model.TaskBackupDto
import com.mandrecode.tempo.features.backup.data.model.TaskSectionBackupDto
import com.mandrecode.tempo.features.backup.domain.model.BackupData
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

/**
 * Maps between the stable backup DTOs and the domain [BackupData] snapshot.
 * Decoding throws [IllegalArgumentException] on unknown enum names or malformed
 * dates; the repository reports those as a corrupt file.
 */

fun BackupData.toDto(
    schemaVersion: Int,
    appVersion: String,
    exportedAt: LocalDateTime,
): BackupFileDto =
    BackupFileDto(
        schemaVersion = schemaVersion,
        appVersion = appVersion,
        exportedAt = exportedAt.toString(),
        categories = categories.map { it.toDto() },
        taskSections = sections.map { TaskSectionBackupDto(it.id, it.categoryId, it.name, it.sortOrder) },
        tasks = tasks.map { it.toDto() },
        habits = habits.map { it.toDto() },
        habitChains = habitChains.map { it.toDto() },
        habitChainMembers = chainMemberships.map { ChainMemberBackupDto(it.chainId, it.habitId, it.sortOrder) },
        settings = settings?.toDto(),
        dailyFocusActivity =
            dailyFocusActivity.map { activity ->
                DailyFocusActivityBackupDto(
                    date = activity.date.toString(),
                    scheduledCount = activity.scheduledCount,
                    completedCount = activity.completedCount,
                    focusMinutes = activity.focusMinutes,
                )
            },
        focusSessions =
            focusSessions.map { session ->
                FocusSessionBackupDto(
                    id = session.id,
                    title = session.title,
                    focusMinutes = session.focusMinutes,
                    breakMinutes = session.breakMinutes,
                    rounds = session.rounds,
                    taskId = session.taskId,
                )
            },
    )

fun BackupFileDto.toDomain(): BackupData =
    BackupData(
        categories = categories.map { it.toDomain() },
        sections = taskSections.map { TaskSection(it.id, it.categoryId, it.name, it.sortOrder) },
        tasks = tasks.map { it.toDomain() },
        habits = habits.map { it.toDomain() },
        habitChains = habitChains.map { it.toDomain() },
        chainMemberships = habitChainMembers.map { ChainMembership(it.chainId, it.habitId, it.sortOrder) },
        settings = settings?.toDomain(),
        dailyFocusActivity =
            dailyFocusActivity.mapNotNull { dto ->
                // A row with an unparseable date is dropped rather than failing the whole import:
                // history is derived data, and losing one day beats losing the restore.
                val date = runCatching { LocalDate.parse(dto.date) }.getOrNull() ?: return@mapNotNull null
                DailyFocusActivity(
                    date = date,
                    scheduledCount = dto.scheduledCount,
                    completedCount = dto.completedCount,
                    focusMinutes = dto.focusMinutes,
                )
            },
        focusSessions =
            focusSessions.map { dto ->
                // Brought inside what a timer can run on the way in: the file may have been edited.
                SavedSession(
                    id = dto.id,
                    title = dto.title,
                    focusMinutes = dto.focusMinutes,
                    breakMinutes = dto.breakMinutes,
                    rounds = dto.rounds,
                    taskId = dto.taskId,
                ).normalized()
            },
    )

private fun Category.toDto(): CategoryBackupDto =
    CategoryBackupDto(
        id = id,
        name = name,
        color = color,
        icon = icon,
        isDefault = isDefault,
        sortOrder = sortOrder,
        sectionsEnabled = sectionsEnabled,
    )

private fun CategoryBackupDto.toDomain(): Category =
    Category(
        id = id,
        name = name,
        color = color,
        icon = icon,
        isDefault = isDefault,
        sortOrder = sortOrder,
        sectionsEnabled = sectionsEnabled,
    )

private fun Task.toDto(): TaskBackupDto =
    TaskBackupDto(
        id = id,
        title = title,
        description = description,
        isCompleted = isCompleted,
        categoryId = categoryId,
        priority = priority?.name,
        reminderDate = reminderDate?.toString(),
        periodicity = periodicity?.name,
        periodicityInterval = periodicityInterval,
        repeatDays = repeatDays?.map { it.value },
        monthDayOption = monthDayOption?.name,
        periodicityEndDate = periodicityEndDate?.toString(),
        parentTaskId = parentTaskId,
        sortOrder = sortOrder,
        completedAt = completedAt?.toString(),
        nextInstanceId = nextInstanceId,
        sectionId = sectionId,
    )

private fun TaskBackupDto.toDomain(): Task =
    Task(
        id = id,
        title = title,
        description = description,
        isCompleted = isCompleted,
        categoryId = categoryId,
        priority = priority?.let { enumValueOf<Priority>(it) },
        reminderDate = reminderDate?.let { LocalDateTime.parse(it) },
        periodicity = periodicity?.let { enumValueOf<Periodicity>(it) },
        periodicityInterval = periodicityInterval,
        repeatDays = repeatDays?.toDayOfWeekSet(),
        monthDayOption = monthDayOption?.let { enumValueOf<MonthDayOption>(it) },
        periodicityEndDate = periodicityEndDate?.let { LocalDate.parse(it) },
        parentTaskId = parentTaskId,
        sortOrder = sortOrder,
        completedAt = completedAt?.let { LocalDateTime.parse(it) },
        nextInstanceId = nextInstanceId,
        sectionId = sectionId,
    )

private fun Habit.toDto(): HabitBackupDto =
    HabitBackupDto(
        id = id,
        title = title,
        description = description,
        icon = icon,
        colorKey = colorKey,
        reminderDate = reminderDate?.toString(),
        isCompleted = isCompleted,
        habitType = habitType.name,
        createdDate = createdDate.toString(),
        completionHistory = completionHistory,
        repeatDays = repeatDays?.map { it.value },
    )

private fun HabitBackupDto.toDomain(): Habit =
    Habit(
        id = id,
        title = title,
        description = description,
        icon = icon,
        colorKey = colorKey,
        reminderDate = reminderDate?.let { LocalDateTime.parse(it) },
        isCompleted = isCompleted,
        habitType = enumValueOf<HabitType>(habitType),
        createdDate = LocalDateTime.parse(createdDate),
        completionHistory = completionHistory,
        repeatDays = repeatDays?.toDayOfWeekSet(),
    )

private fun HabitChain.toDto(): HabitChainBackupDto =
    HabitChainBackupDto(
        id = id,
        title = title,
        description = description,
        colorKey = colorKey,
        icon = icon,
        periodicReminder = periodicReminder?.toString(),
        createdDate = createdDate.toString(),
        completionHistory = completionHistory,
        repeatDays = repeatDays?.map { it.value },
    )

private fun HabitChainBackupDto.toDomain(): HabitChain =
    HabitChain(
        id = id,
        title = title,
        description = description,
        colorKey = colorKey,
        icon = icon,
        periodicReminder = periodicReminder?.let { LocalDateTime.parse(it) },
        createdDate = LocalDateTime.parse(createdDate),
        completionHistory = completionHistory,
        repeatDays = repeatDays?.toDayOfWeekSet(),
    )

private fun List<Int>.toDayOfWeekSet(): Set<DayOfWeek> =
    mapTo(mutableSetOf()) { value ->
        requireNotNull(DayOfWeek.fromValue(value)) { "Unknown day of week: $value" }
    }
