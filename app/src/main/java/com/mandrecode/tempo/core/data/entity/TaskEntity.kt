package com.mandrecode.tempo.core.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.mandrecode.tempo.core.domain.model.DayOfWeek
import com.mandrecode.tempo.core.domain.model.MonthDayOption
import com.mandrecode.tempo.core.domain.model.Periodicity
import com.mandrecode.tempo.core.domain.model.Priority
import com.mandrecode.tempo.core.domain.model.UNSET_TIMESTAMP
import com.mandrecode.tempo.core.domain.model.UNSET_UID
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime

@Entity(
    tableName = "tasks",
    indices = [Index("categoryId"), Index("parentTaskId"), Index("sectionId"), Index(value = ["uid"], unique = true)],
)
data class TaskEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val description: String,
    val isCompleted: Boolean = false,
    val categoryId: Long = DEFAULT_INBOX_CATEGORY_ENTITY.id,
    val priority: Priority? = null,
    val reminderDate: LocalDateTime? = null,
    val periodicity: Periodicity? = null,
    val periodicityInterval: Int = 1,
    val repeatDays: Set<DayOfWeek>? = null,
    val monthDayOption: MonthDayOption? = null,
    // The last date the series may have an occurrence on, inclusive (#509). Null means no end.
    val periodicityEndDate: LocalDate? = null,
    val parentTaskId: Long? = null,
    val sortOrder: Int = 0,
    val completedAt: LocalDateTime? = null,
    // When this task was archived as part of a periodic-completion flow, this id points to
    // the spawned next-occurrence task. Used to roll back the spawn (and restore recurrence
    // fields from the next instance) when the user unchecks the archived task.
    val nextInstanceId: Long? = null,
    // The task's section inside its category (#61), or null when unsectioned. Only meaningful
    // for top-level tasks; cleared whenever the task changes category.
    val sectionId: Long? = null,
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
