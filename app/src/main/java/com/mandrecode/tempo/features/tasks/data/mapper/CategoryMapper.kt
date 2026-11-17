package com.mandrecode.tempo.features.tasks.data.mapper

import com.mandrecode.tempo.core.data.entity.CategoryEntity
import com.mandrecode.tempo.features.tasks.domain.model.Category

fun CategoryEntity.toDomain(): Category =
    Category(
        id = id,
        name = name,
        color = color,
        icon = icon,
        isDefault = isDefault,
        sortOrder = sortOrder,
        sectionsEnabled = sectionsEnabled,
        uid = uid,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

fun Category.toEntity(): CategoryEntity =
    CategoryEntity(
        id = id,
        name = name,
        color = color,
        icon = icon,
        isDefault = isDefault,
        sortOrder = sortOrder,
        sectionsEnabled = sectionsEnabled,
        uid = uid,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

fun List<CategoryEntity>.toDomain(): List<Category> = map { it.toDomain() }
