package com.mandrecode.tempo.features.routines.data.mapper

import com.mandrecode.tempo.core.data.entity.HabitChainEntity
import com.mandrecode.tempo.core.data.entity.HabitChainMemberEntity
import com.mandrecode.tempo.core.data.entity.HabitChainWithMembers
import com.mandrecode.tempo.features.routines.domain.model.HabitChain

fun HabitChainWithMembers.toDomain(): HabitChain =
    HabitChain(
        id = chain.id,
        title = chain.title,
        description = chain.description,
        colorKey = chain.colorKey,
        icon = chain.icon,
        habitIds = members.sortedBy { it.sortOrder }.map { it.habitId },
        periodicReminder = chain.periodicReminder,
        createdDate = chain.createdDate,
        completionHistory = chain.completionHistory,
        repeatDays = chain.repeatDays,
        uid = chain.uid,
        createdAt = chain.createdAt,
        updatedAt = chain.updatedAt,
    )

fun HabitChain.toEntity(): HabitChainEntity =
    HabitChainEntity(
        id = id,
        title = title,
        description = description,
        colorKey = colorKey,
        icon = icon,
        periodicReminder = periodicReminder,
        createdDate = createdDate,
        completionHistory = completionHistory,
        repeatDays = repeatDays,
        uid = uid,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

fun HabitChain.toMemberEntities(): List<HabitChainMemberEntity> =
    habitIds.mapIndexed { index, habitId ->
        HabitChainMemberEntity(
            chainId = id,
            habitId = habitId,
            sortOrder = index,
        )
    }

fun List<HabitChainWithMembers>.toDomain(): List<HabitChain> = map { it.toDomain() }
