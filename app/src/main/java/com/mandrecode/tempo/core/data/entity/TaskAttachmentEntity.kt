package com.mandrecode.tempo.core.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.mandrecode.tempo.core.domain.model.UNSET_TIMESTAMP
import com.mandrecode.tempo.core.domain.model.UNSET_UID

/**
 * No foreign key to `tasks`: a deleted task keeps its attachment rows so that undoing the
 * deletion restores them too. Rows left behind are pruned at startup (see
 * TaskAttachmentRepository.pruneOrphans).
 */
@Entity(
    tableName = "task_attachments",
    indices = [Index("taskId"), Index("storedName"), Index(value = ["uid"], unique = true)],
)
data class TaskAttachmentEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val taskId: Long,
    val displayName: String,
    val mimeType: String?,
    val sizeBytes: Long,
    val storedName: String,
    /**
     * Stable global identity (#30): never changes, unique across devices. [UNSET_UID] lets the
     * database assign it on insert.
     */
    @ColumnInfo(defaultValue = "''")
    val uid: String = UNSET_UID,
    /** Epoch millis UTC; [UNSET_TIMESTAMP] lets the database stamp it on insert (#30). */
    @ColumnInfo(defaultValue = "0")
    val createdAt: Long = UNSET_TIMESTAMP,
    /** Epoch millis UTC; advanced by a database trigger on every update (#30). */
    @ColumnInfo(defaultValue = "0")
    val updatedAt: Long = UNSET_TIMESTAMP,
)
