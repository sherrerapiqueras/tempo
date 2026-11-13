package com.mandrecode.tempo.core.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A saved focus session (#517): a name, its lengths and rounds, and optionally the task it works
 * on. Not a task and never listed with them.
 *
 * [taskId] carries no foreign key, like every other reference in this database. A link whose task
 * has gone simply resolves to nothing when it is read, and task ids are never reused, so a stale
 * one cannot come to point at something else.
 */
@Entity(
    tableName = "focus_sessions",
    indices = [Index("taskId")],
)
data class SavedSessionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val focusMinutes: Int,
    val breakMinutes: Int,
    val rounds: Int = 1,
    val taskId: Long? = null,
)
