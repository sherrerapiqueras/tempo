package com.mandrecode.tempo.core.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A topic session the user has started before (#517): a name with the lengths and rounds it last
 * ran with, and when that was. Not a task and never listed with them.
 *
 * Kept by use rather than saved: the table holds only the few most recently used, see
 * [com.mandrecode.tempo.core.data.local.dao.SavedSessionDao.remember].
 */
@Entity(tableName = "focus_sessions")
data class SavedSessionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val focusMinutes: Int,
    val breakMinutes: Int,
    val rounds: Int = 1,
    /** Epoch milliseconds of the last start; what orders the recents and decides which drop off. */
    val lastUsedAt: Long = 0,
)
