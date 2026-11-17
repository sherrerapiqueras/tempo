package com.mandrecode.tempo.features.tasks.data.mapper

import com.mandrecode.tempo.core.data.entity.TaskAttachmentEntity
import com.mandrecode.tempo.features.tasks.domain.model.TaskAttachment

fun TaskAttachmentEntity.toDomain(): TaskAttachment =
    TaskAttachment(
        id = id,
        taskId = taskId,
        displayName = displayName,
        mimeType = mimeType,
        sizeBytes = sizeBytes,
        storedName = storedName,
        uid = uid,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

fun TaskAttachment.toEntity(): TaskAttachmentEntity =
    TaskAttachmentEntity(
        id = id,
        taskId = taskId,
        displayName = displayName,
        mimeType = mimeType,
        sizeBytes = sizeBytes,
        storedName = storedName,
        uid = uid,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

@JvmName("taskAttachmentEntitiesToDomain")
fun List<TaskAttachmentEntity>.toDomain(): List<TaskAttachment> = map { it.toDomain() }
