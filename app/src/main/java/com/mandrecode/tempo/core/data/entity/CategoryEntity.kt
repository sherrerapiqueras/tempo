package com.mandrecode.tempo.core.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.mandrecode.tempo.core.domain.model.DEFAULT_INBOX_UID
import com.mandrecode.tempo.core.domain.model.UNSET_TIMESTAMP
import com.mandrecode.tempo.core.domain.model.UNSET_UID

@Entity(
    tableName = "categories",
    indices = [Index(value = ["uid"], unique = true)],
)
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val color: String? = null,
    val icon: String? = null,
    val isDefault: Boolean = false,
    val sortOrder: Int = 0,
    /**
     * Whether this category's list is split into sections (#61). Off by default: opt-in. The SQL
     * default matters as much as the Kotlin one: raw inserts that predate the column, such as the
     * fresh-install Inbox seed, must still succeed, and it keeps a new install matching 11 -> 12.
     */
    @ColumnInfo(defaultValue = "0")
    val sectionsEnabled: Boolean = false,
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

val DEFAULT_INBOX_CATEGORY_ENTITY =
    CategoryEntity(
        id = -1,
        name = "Inbox",
        icon = "inbox",
        isDefault = true,
        sortOrder = -1,
        uid = DEFAULT_INBOX_UID,
    )
