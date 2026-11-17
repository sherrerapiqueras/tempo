package com.mandrecode.tempo.features.focus.data.mapper

import com.mandrecode.tempo.core.data.entity.SavedSessionEntity
import com.mandrecode.tempo.features.focus.domain.model.SavedSession

fun SavedSessionEntity.toDomain(): SavedSession =
    SavedSession(
        id = id,
        title = title,
        focusMinutes = focusMinutes,
        breakMinutes = breakMinutes,
        rounds = rounds,
        lastUsedAt = lastUsedAt,
        uid = uid,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

fun SavedSession.toEntity(): SavedSessionEntity =
    SavedSessionEntity(
        id = id,
        title = title,
        focusMinutes = focusMinutes,
        breakMinutes = breakMinutes,
        rounds = rounds,
        lastUsedAt = lastUsedAt,
        uid = uid,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )
