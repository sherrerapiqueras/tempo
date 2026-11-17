package com.mandrecode.tempo.features.tasks.domain.model

import com.mandrecode.tempo.core.domain.model.UNSET_TIMESTAMP
import com.mandrecode.tempo.core.domain.model.UNSET_UID

/**
 * Stand-in for "no category chosen yet", while categories load or when none exist. It is never a
 * persisted id — Room generates category ids from 1 and the seeded Inbox is -1 — so a task that
 * carries it belongs to nothing and shows up nowhere. Nothing may be created with it.
 */
const val NO_CATEGORY_ID = 0L

data class Category(
    val id: Long = 0,
    val name: String,
    val color: String? = null,
    val icon: String? = null,
    val isDefault: Boolean = false,
    val sortOrder: Int = 0,
    /**
     * Whether the list is split into sections (#61). Turning it off hides them without deleting
     * anything, so turning it back on restores every section and membership.
     */
    val sectionsEnabled: Boolean = false,
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
    fun asNew(): Category = copy(id = 0, uid = UNSET_UID, createdAt = UNSET_TIMESTAMP, updatedAt = UNSET_TIMESTAMP)
}
