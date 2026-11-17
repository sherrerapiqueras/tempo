package com.mandrecode.tempo.core.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.mandrecode.tempo.core.domain.model.DayOfWeek
import com.mandrecode.tempo.core.domain.model.UNSET_TIMESTAMP
import com.mandrecode.tempo.core.domain.model.UNSET_UID
import kotlinx.datetime.LocalDateTime

@Entity(
    tableName = "habits",
    indices = [Index(value = ["uid"], unique = true)],
)
data class HabitEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val description: String,
    val icon: String? = null,
    val colorKey: String? = null,
    val reminderDate: LocalDateTime? = null,
    val isCompleted: Boolean = false,
    val habitType: String = "BUILD",
    val createdDate: LocalDateTime,
    val completionHistory: String = "",
    val repeatDays: Set<DayOfWeek>? = null,
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
