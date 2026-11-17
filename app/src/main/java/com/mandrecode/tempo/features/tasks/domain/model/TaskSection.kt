package com.mandrecode.tempo.features.tasks.domain.model

import com.mandrecode.tempo.core.domain.model.UNSET_TIMESTAMP
import com.mandrecode.tempo.core.domain.model.UNSET_UID

/**
 * A named divider inside one category (#61) — structure for a long list, not a list of its own.
 * Not completable, and carries no icon, colour or sharing identity.
 */
data class TaskSection(
    val id: Long = 0,
    val categoryId: Long,
    val name: String,
    val sortOrder: Int = 0,
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
    fun asNew(): TaskSection = copy(id = 0, uid = UNSET_UID, createdAt = UNSET_TIMESTAMP, updatedAt = UNSET_TIMESTAMP)
}

/**
 * What deleting a section took away, so undo can put it back: the section itself and the
 * top-level tasks that were in it (completed ones included).
 */
data class SectionDeletionSnapshot(
    val section: TaskSection,
    val taskIds: List<Long>,
)
