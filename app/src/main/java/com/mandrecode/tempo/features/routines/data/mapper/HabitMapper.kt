package com.mandrecode.tempo.features.routines.data.mapper

import com.mandrecode.tempo.core.data.entity.HabitEntity
import com.mandrecode.tempo.features.routines.domain.model.Habit
import com.mandrecode.tempo.features.routines.domain.model.HabitType

fun HabitEntity.toDomain(): Habit =
    Habit(
        id = id,
        title = title,
        description = description,
        icon = icon,
        colorKey = colorKey,
        reminderDate = reminderDate,
        isCompleted = isCompleted,
        habitType = parseHabitType(habitType),
        createdDate = createdDate,
        completionHistory = completionHistory,
        repeatDays = repeatDays,
        uid = uid,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

/**
 * Defensive parser for the persisted [HabitType] enum name. Silently falls back to
 * [HabitType.BUILD] when the stored value does not match a known enum entry — this
 * is unreachable in practice (writes go through [toEntity] which uses [Enum.name])
 * but keeps reads safe from future enum renames or hand-edited databases. The
 * fallback contract is exercised in `HabitMapperTest`.
 */
private fun parseHabitType(raw: String): HabitType = HabitType.entries.firstOrNull { it.name == raw } ?: HabitType.BUILD

fun Habit.toEntity(): HabitEntity =
    HabitEntity(
        id = id,
        title = title,
        description = description,
        icon = icon,
        colorKey = colorKey,
        reminderDate = reminderDate,
        isCompleted = isCompleted,
        habitType = habitType.name,
        createdDate = createdDate,
        completionHistory = completionHistory,
        repeatDays = repeatDays,
        uid = uid,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

fun List<HabitEntity>.toDomain(): List<Habit> = map { it.toDomain() }
