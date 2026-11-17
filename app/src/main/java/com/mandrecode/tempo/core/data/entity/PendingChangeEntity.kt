package com.mandrecode.tempo.core.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A record that changed and has not been acknowledged as delivered (#546). One row per record,
 * coalesced: a later change replaces it with the latest [operation] and a new [seq]. Written only by
 * database triggers (see JournalSchema), so every write path journals.
 */
@Entity(
    tableName = "pending_changes",
    indices = [Index(value = ["uid"], unique = true)],
)
data class PendingChangeEntity(
    /** Never reused (AUTOINCREMENT): orders changes and lets an acknowledgement detect a newer one. */
    @PrimaryKey(autoGenerate = true)
    val seq: Long = 0,
    val uid: String,
    /** The table the record lives (or lived) in, e.g. `tasks`. */
    val entityType: String,
    /** [OPERATION_UPSERT] or [OPERATION_DELETE]. */
    val operation: String,
    /** Epoch millis UTC of the change. */
    val changedAt: Long,
) {
    companion object {
        const val OPERATION_UPSERT = "upsert"
        const val OPERATION_DELETE = "delete"
    }
}
