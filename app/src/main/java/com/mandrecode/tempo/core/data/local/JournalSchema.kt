package com.mandrecode.tempo.core.data.local

import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * The pending-changes journal (#546). Every insert, update and delete of a row with a stable
 * identity, from any code path or cascade, replaces that record's single journal row with its
 * latest operation and a fresh sequence number. The journal is bounded by the number of records,
 * not edits, and a future sync engine reads it in order and acknowledges what it delivered.
 *
 * Coalescing is an explicit delete-then-insert, not `INSERT OR REPLACE`: inside a trigger, the outer
 * statement's conflict policy overrides the body's, and Room's `@Update` runs as `UPDATE OR ABORT`.
 *
 * Rows still being assigned an identity (`uid = ''`) are skipped; the identity trigger's own
 * update that fills the uid in journals them a moment later. The triggers are created with the
 * identity triggers, by [EntityIdentitySchema.createTriggers] on every open.
 */
internal object JournalSchema {
    fun insertTriggerName(table: String) = "journal_insert_$table"

    fun updateTriggerName(table: String) = "journal_update_$table"

    fun deleteTriggerName(table: String) = "journal_delete_$table"

    fun triggerNames(table: String) =
        setOf(
            insertTriggerName(table),
            updateTriggerName(table),
            deleteTriggerName(table),
        )

    fun triggers(table: String): List<String> =
        listOf(
            journal(insertTriggerName(table), "AFTER INSERT", table, "NEW", "upsert"),
            journal(updateTriggerName(table), "AFTER UPDATE", table, "NEW", "upsert"),
            journal(deleteTriggerName(table), "AFTER DELETE", table, "OLD", "delete"),
        )

    private fun journal(
        name: String,
        event: String,
        table: String,
        row: String,
        operation: String,
    ) = """
        CREATE TRIGGER `$name`
        $event ON `$table`
        WHEN $row.uid != ''
        BEGIN
            DELETE FROM pending_changes WHERE uid = $row.uid AND entityType = '$table';
            INSERT INTO pending_changes (uid, entityType, operation, changedAt)
            VALUES ($row.uid, '$table', '$operation', ${EntityIdentitySchema.NOW_MILLIS});
        END
        """.trimIndent()

    /** v16 -> v17: the journal table, empty. Changes before this version left nothing to replay. */
    fun migrate16To17(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `pending_changes` (" +
                "`seq` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `uid` TEXT NOT NULL, " +
                "`entityType` TEXT NOT NULL, `operation` TEXT NOT NULL, `changedAt` INTEGER NOT NULL)",
        )
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_pending_changes_uid_entityType` " +
                "ON `pending_changes` (`uid`, `entityType`)",
        )
    }
}
