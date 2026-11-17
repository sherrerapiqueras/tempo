package com.mandrecode.tempo.features.tasks.data.mapper

import com.mandrecode.tempo.core.data.entity.TaskSectionEntity
import com.mandrecode.tempo.features.tasks.domain.model.TaskSection

fun TaskSectionEntity.toDomain(): TaskSection =
    TaskSection(
        id = id,
        categoryId = categoryId,
        name = name,
        sortOrder = sortOrder,
        uid = uid,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

fun TaskSection.toEntity(): TaskSectionEntity =
    TaskSectionEntity(
        id = id,
        categoryId = categoryId,
        name = name,
        sortOrder = sortOrder,
        uid = uid,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )
