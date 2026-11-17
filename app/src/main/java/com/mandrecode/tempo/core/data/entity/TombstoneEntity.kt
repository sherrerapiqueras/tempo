package com.mandrecode.tempo.core.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * The record that a row with a stable identity was deleted (#281): its [uid], which table it lived
 * in, and when. Written and cleared only by database triggers (see EntityIdentitySchema), so every
 * delete path records one and live tables keep only live rows.
 */
@Entity(
    tableName = "tombstones",
    indices = [Index("deletedAt")],
)
data class TombstoneEntity(
    @PrimaryKey
    val uid: String,
    /** The table the record was deleted from, e.g. `tasks`. */
    val entityType: String,
    /** Epoch millis UTC of the deletion. */
    val deletedAt: Long,
)
