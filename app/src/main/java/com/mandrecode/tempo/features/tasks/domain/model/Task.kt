package com.mandrecode.tempo.features.tasks.domain.model

import com.mandrecode.tempo.core.domain.model.DayOfWeek
import com.mandrecode.tempo.core.domain.model.MonthDayOption
import com.mandrecode.tempo.core.domain.model.Periodicity
import com.mandrecode.tempo.core.domain.model.Priority
import com.mandrecode.tempo.core.domain.model.UNSET_TIMESTAMP
import com.mandrecode.tempo.core.domain.model.UNSET_UID
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime

data class Task(
    val id: Long = 0,
    val title: String,
    val description: String,
    val isCompleted: Boolean = false,
    val categoryId: Long = 0,
    val priority: Priority? = null,
    val reminderDate: LocalDateTime? = null,
    val periodicity: Periodicity? = null,
    val periodicityInterval: Int = 1,
    val repeatDays: Set<DayOfWeek>? = null,
    val monthDayOption: MonthDayOption? = null,
    /**
     * The last date the series may have an occurrence on, inclusive (#509), or null for a series
     * with no end. Only meaningful while [periodicity] is set.
     */
    val periodicityEndDate: LocalDate? = null,
    val parentTaskId: Long? = null,
    val sortOrder: Int = 0,
    val completedAt: LocalDateTime? = null,
    /**
     * When this task was archived as part of a periodic-completion flow, points to the
     * spawned next-occurrence task. Allows the toggle use case to roll back the spawn
     * (delete next instance, restore recurrence) when the user unchecks the archived task.
     */
    val nextInstanceId: Long? = null,
    /**
     * Whether any file is attached (#380), for the card's paperclip. Read from the attachments
     * table when tasks are loaded, never stored on the task itself.
     */
    val hasAttachments: Boolean = false,
    /**
     * The section of [categoryId] this task sits in (#61), or null when unsectioned. Only
     * meaningful for top-level tasks; a category change clears it.
     */
    val sectionId: Long? = null,
    /** Stable global identity (#30), assigned by the database on insert; [UNSET_UID] until stored. */
    val uid: String = UNSET_UID,
    /** Epoch millis UTC, stamped by the database; [UNSET_TIMESTAMP] until stored. */
    val createdAt: Long = UNSET_TIMESTAMP,
    /** Epoch millis UTC of the last stored change, stamped by the database. */
    val updatedAt: Long = UNSET_TIMESTAMP,
) {
    /**
     * This record saved as a *new* one: no local id and no identity, so the database assigns both.
     * Any copy that becomes another row must go through here — reusing the source's uid fails (#30).
     */
    fun asNew(): Task = copy(id = 0, uid = UNSET_UID, createdAt = UNSET_TIMESTAMP, updatedAt = UNSET_TIMESTAMP)

    /** The next occurrence of this series, as a new task due on [reminderDate]. */
    fun asNextOccurrence(reminderDate: LocalDateTime): Task =
        asNew().copy(
            isCompleted = false,
            completedAt = null,
            reminderDate = reminderDate,
            nextInstanceId = null,
        )

    /** This task as a one-off: only the latest occurrence of a series holds these fields. */
    fun withoutRecurrence(): Task =
        copy(
            periodicity = null,
            periodicityInterval = 1,
            repeatDays = null,
            monthDayOption = null,
            periodicityEndDate = null,
        )
}

val DEFAULT_ADD_CATEGORY = Category(id = -2, name = "Add Category")
