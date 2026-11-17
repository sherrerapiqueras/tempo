package com.mandrecode.tempo.core.data.entity

import androidx.room.Entity
import androidx.room.Index

/**
 * The record that a row with a stable identity was deleted (#281): its [uid], which table it lived
 * in, and when. Written and cleared only by database triggers (see EntityIdentitySchema), so every
 * delete path records one and live tables keep only live rows.
 */
@Entity(
    tableName = "tombstones",
    // uid is unique per table, not across tables, so the kind is part of the key.
    primaryKeys = ["uid", "entityType"],
    indices = [Index("deletedAt")],
)
data class TombstoneEntity(
    val uid: String,
    /** The table the record was deleted from, e.g. `tasks`. */
    val entityType: String,
    /** Epoch millis UTC of the deletion. */
    val deletedAt: Long,
)
