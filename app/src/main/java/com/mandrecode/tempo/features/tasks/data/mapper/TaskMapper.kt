package com.mandrecode.tempo.features.tasks.data.mapper

import com.mandrecode.tempo.core.data.entity.TaskEntity
import com.mandrecode.tempo.features.tasks.domain.model.Task

fun TaskEntity.toDomain(): Task =
    Task(
        id = id,
        title = title,
        description = description,
        isCompleted = isCompleted,
        categoryId = categoryId,
        priority = priority,
        reminderDate = reminderDate,
        periodicity = periodicity,
        periodicityInterval = periodicityInterval,
        repeatDays = repeatDays,
        monthDayOption = monthDayOption,
        periodicityEndDate = periodicityEndDate,
        parentTaskId = parentTaskId,
        sortOrder = sortOrder,
        completedAt = completedAt,
        nextInstanceId = nextInstanceId,
        sectionId = sectionId,
    )

fun Task.toEntity(): TaskEntity =
    TaskEntity(
        id = id,
        title = title,
        description = description,
        isCompleted = isCompleted,
        categoryId = categoryId,
        priority = priority,
        reminderDate = reminderDate,
        periodicity = periodicity,
        periodicityInterval = periodicityInterval,
        repeatDays = repeatDays,
        monthDayOption = monthDayOption,
        periodicityEndDate = periodicityEndDate,
        parentTaskId = parentTaskId,
        sortOrder = sortOrder,
        completedAt = completedAt,
        nextInstanceId = nextInstanceId,
        sectionId = sectionId,
    )

fun List<TaskEntity>.toDomain(): List<Task> = map { it.toDomain() }

fun List<Task>.toEntity(): List<TaskEntity> = map { it.toEntity() }
