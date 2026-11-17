package com.mandrecode.tempo.core.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.mandrecode.tempo.core.domain.model.UNSET_TIMESTAMP
import com.mandrecode.tempo.core.domain.model.UNSET_UID

/**
 * A topic session the user has started before (#517): a name with the lengths and rounds it last
 * ran with, and when that was. Not a task and never listed with them.
 *
 * Kept by use rather than saved: the table holds only the few most recently used, see
 * [com.mandrecode.tempo.core.data.local.dao.SavedSessionDao.remember].
 */
@Entity(
    tableName = "focus_sessions",
    indices = [Index(value = ["uid"], unique = true)],
)
data class SavedSessionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val focusMinutes: Int,
    val breakMinutes: Int,
    val rounds: Int = 1,
    /** Epoch milliseconds of the last start; what orders the recents and decides which drop off. */
    val lastUsedAt: Long = 0,
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
