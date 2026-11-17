package com.mandrecode.tempo.core.data.local

import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Deletion tombstones (#281). Every delete of a row with a stable identity, from any code path or
 * cascade, records `(uid, entityType, deletedAt)` in `tombstones`. A row coming back under the same
 * uid (Undo) clears it. Live tables keep only live rows, so no query filters on deletion.
 *
 * The triggers are created with the identity triggers, by [EntityIdentitySchema.createTriggers] on
 * every open.
 */
internal object TombstoneSchema {
    fun tombstoneTriggerName(table: String) = "identity_tombstone_$table"

    fun resurrectTriggerName(table: String) = "identity_resurrect_$table"

    /** The delete and re-insert triggers for one identity table. */
    fun triggers(table: String): List<String> =
        listOf(
            // Every delete, from any code path or cascade, leaves a tombstone (#281): live tables
            // keep only live rows, so no query can show a deleted record.
            """
            CREATE TRIGGER `${tombstoneTriggerName(table)}`
            AFTER DELETE ON `$table`
            WHEN OLD.uid != ''
            BEGIN
                INSERT OR REPLACE INTO tombstones (uid, entityType, deletedAt)
                VALUES (OLD.uid, '$table', ${EntityIdentitySchema.NOW_MILLIS});
            END
            """.trimIndent(),
            // A record restored under its original uid (Undo) is live again.
            """
            CREATE TRIGGER `${resurrectTriggerName(table)}`
            AFTER INSERT ON `$table`
            WHEN NEW.uid != ''
            BEGIN
                DELETE FROM tombstones WHERE uid = NEW.uid;
            END
            """.trimIndent(),
        )

    /**
     * How long a tombstone is kept (#281). It must outlast the longest plausible gap between a
     * device's syncs, or that device pushes a deleted row back; a device offline for longer must
     * fully resync. See docs/design/sync-boundary.md.
     */
    const val TOMBSTONE_RETENTION_DAYS = 180

    private const val TOMBSTONE_RETENTION_MILLIS = TOMBSTONE_RETENTION_DAYS * 86_400_000L

    /** Purges tombstones past [TOMBSTONE_RETENTION_DAYS] whenever the database opens. */
    val TombstonePurgeCallback =
        object : RoomDatabase.Callback() {
            override fun onOpen(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "DELETE FROM tombstones " +
                        "WHERE deletedAt < ${EntityIdentitySchema.NOW_MILLIS} - $TOMBSTONE_RETENTION_MILLIS",
                )
            }
        }

    /** v15 -> v16: the tombstone table. No backfill: earlier deletes left nothing to record. */
    fun migrate15To16(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `tombstones` (`uid` TEXT NOT NULL, `entityType` TEXT NOT NULL, " +
                "`deletedAt` INTEGER NOT NULL, PRIMARY KEY(`uid`))",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_tombstones_deletedAt` ON `tombstones` (`deletedAt`)")
    }
}
