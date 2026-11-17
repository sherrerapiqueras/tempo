package com.mandrecode.tempo.core.data.local

import androidx.annotation.VisibleForTesting
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.mandrecode.tempo.core.data.entity.DEFAULT_INBOX_CATEGORY_ENTITY
import com.mandrecode.tempo.core.domain.model.DEFAULT_INBOX_UID
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant

/**
 * The SQL side of stable record identity (#30): which tables carry `uid` / `createdAt` /
 * `updatedAt`, the triggers that keep the timestamps honest, and the v14 -> v15 backfill.
 *
 * Timestamps are stamped by triggers rather than by each repository so that no write path, present
 * or future, can forget: about seventy DAO methods mutate these tables.
 *
 * - **Insert:** a row arriving with `uid = ''` gets a random UUID, and one arriving with
 *   `createdAt = 0` gets the current time for `createdAt` (and for `updatedAt`, unless that was
 *   supplied). Explicit values — a restore, a merge, a future sync — are kept as given.
 * - **Update:** any update that leaves `updatedAt` as it was (or sends 0) advances it to now, and
 *   always strictly past its old value. An update that supplies its own `updatedAt` keeps it.
 * - **Immutability:** an update that sends `uid` / `createdAt` unset gets the stored values back;
 *   one that sets a different value aborts. So code above the DAOs never needs to carry them:
 *   domain models rebuilt into entities send `''` / `0`, and a copy saved as a new row gets a
 *   fresh identity from the insert trigger.
 *
 * Room's schema JSON does not record triggers, so they are recreated on every open as well as in
 * the migration: that covers a fresh install, an upgrade, and a debug
 * destructive reset, which skips `onCreate`.
 */
internal object EntityIdentitySchema {
    /** Tables whose rows carry a stable identity. All use an `id INTEGER` primary key. */
    val TABLES =
        listOf(
            "categories",
            "tasks",
            "task_sections",
            "habits",
            "habit_chains",
            "task_attachments",
            "focus_sessions",
        )

    /**
     * Current time as epoch milliseconds. `unixepoch('subsec')` would read better but needs
     * SQLite 3.42; the framework SQLite used by test helpers can be far older. SQLite evaluates
     * 'now' once per statement, so every use within one statement agrees.
     */
    private const val NOW_MILLIS = "CAST(ROUND((julianday('now') - 2440587.5) * 86400000) AS INTEGER)"

    /** A random RFC 9562 version-4 UUID, lowercase canonical form; re-evaluated per row. */
    @VisibleForTesting
    const val RANDOM_UUID_V4 =
        "lower(hex(randomblob(4))) || '-' || lower(hex(randomblob(2))) || '-4' || " +
            "substr(lower(hex(randomblob(2))), 2) || '-' || substr('89ab', 1 + (random() & 3), 1) || " +
            "substr(lower(hex(randomblob(2))), 2) || '-' || lower(hex(randomblob(6)))"

    fun insertTriggerName(table: String) = "identity_insert_$table"

    fun updateTriggerName(table: String) = "identity_update_$table"

    fun protectTriggerName(table: String) = "identity_protect_$table"

    fun immutableTriggerName(table: String) = "identity_immutable_$table"

    fun triggerNames(table: String) =
        setOf(
            insertTriggerName(table),
            updateTriggerName(table),
            protectTriggerName(table),
            immutableTriggerName(table),
        )

    /**
     * Drops and recreates every identity trigger, so a database always runs the triggers this build
     * defines, even if an earlier build created different ones.
     */
    fun createTriggers(db: SupportSQLiteDatabase) {
        TABLES.forEach { table ->
            db.execSQL("DROP TRIGGER IF EXISTS `${insertTriggerName(table)}`")
            db.execSQL("DROP TRIGGER IF EXISTS `${updateTriggerName(table)}`")
            db.execSQL("DROP TRIGGER IF EXISTS `${protectTriggerName(table)}`")
            db.execSQL("DROP TRIGGER IF EXISTS `${immutableTriggerName(table)}`")
            db.execSQL(
                """
                CREATE TRIGGER `${insertTriggerName(table)}`
                AFTER INSERT ON `$table`
                WHEN NEW.uid = '' OR NEW.createdAt = 0
                BEGIN
                    UPDATE `$table` SET
                        uid = CASE WHEN NEW.uid = '' THEN $RANDOM_UUID_V4 ELSE uid END,
                        createdAt = CASE WHEN NEW.createdAt = 0 THEN $NOW_MILLIS ELSE createdAt END,
                        updatedAt = CASE
                            WHEN NEW.updatedAt != 0 THEN updatedAt
                            WHEN NEW.createdAt != 0 THEN NEW.createdAt
                            ELSE $NOW_MILLIS
                        END
                    WHERE id = NEW.id;
                END
                """.trimIndent(),
            )
            // Advances updatedAt on any update that did not supply its own: one that left it as it
            // was, or sent 0 (a mapper that does not carry it). `OLD.uid != ''` skips the insert
            // trigger's own fill-in and the protect trigger's restore, which are not edits.
            // The stamp is strictly greater than the old value, even within one millisecond:
            // Android's SQLite runs with recursive triggers on, and a stamp equal to the old value
            // would satisfy WHEN again and recurse until SQLite gives up.
            db.execSQL(
                """
                CREATE TRIGGER `${updateTriggerName(table)}`
                AFTER UPDATE ON `$table`
                WHEN OLD.uid != '' AND (NEW.updatedAt IS OLD.updatedAt OR NEW.updatedAt = 0)
                BEGIN
                    UPDATE `$table` SET updatedAt = MAX($NOW_MILLIS, OLD.updatedAt + 1) WHERE id = NEW.id;
                END
                """.trimIndent(),
            )
            // uid and createdAt never change once stored. An update that sends them unset — a row
            // rebuilt from a domain model that does not carry them, so '' and 0 — gets the stored
            // values back. The restore's own UPDATE sees OLD.uid = '' or OLD.createdAt = 0, so it
            // does not fire again.
            db.execSQL(
                """
                CREATE TRIGGER `${protectTriggerName(table)}`
                AFTER UPDATE ON `$table`
                WHEN (OLD.uid != '' AND NEW.uid = '') OR (OLD.createdAt != 0 AND NEW.createdAt = 0)
                BEGIN
                    UPDATE `$table` SET
                        uid = CASE WHEN NEW.uid = '' THEN OLD.uid ELSE uid END,
                        createdAt = CASE WHEN NEW.createdAt = 0 THEN OLD.createdAt ELSE createdAt END
                    WHERE id = NEW.id;
                END
                """.trimIndent(),
            )
            // Setting either to a *different* stored value is a bug (two records claiming one
            // identity), so it fails loudly instead of being quietly undone.
            db.execSQL(
                """
                CREATE TRIGGER `${immutableTriggerName(table)}`
                BEFORE UPDATE ON `$table`
                WHEN (OLD.uid != '' AND NEW.uid != '' AND NEW.uid IS NOT OLD.uid)
                    OR (OLD.createdAt != 0 AND NEW.createdAt != 0 AND NEW.createdAt IS NOT OLD.createdAt)
                BEGIN
                    SELECT RAISE(ABORT, 'uid and createdAt are immutable');
                END
                """.trimIndent(),
            )
        }
    }

    /** Recreates the triggers on every open; see the class docs for why not only in `onCreate`. */
    val TriggersCallback =
        object : RoomDatabase.Callback() {
            override fun onOpen(db: SupportSQLiteDatabase) {
                createTriggers(db)
            }
        }

    /**
     * v14 -> v15. Adds the columns, gives every existing row a uid (the seeded Inbox, id -1, the
     * well-known one), backfills creation times, then adds the unique indexes and triggers.
     *
     * Habits and chains already record a local creation date, converted with the device's current
     * zone (SQLite has no zone data; approximate across DST, fine for history). Saved sessions use
     * their last-used time. Everything else gets the migration time. `updatedAt = createdAt`.
     */
    fun migrate14To15(
        db: SupportSQLiteDatabase,
        nowMillis: Long,
        zone: TimeZone,
    ) {
        TABLES.forEach { table ->
            db.execSQL("ALTER TABLE `$table` ADD COLUMN `uid` TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE `$table` ADD COLUMN `createdAt` INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE `$table` ADD COLUMN `updatedAt` INTEGER NOT NULL DEFAULT 0")
        }

        // The seeded Inbox row, not "whichever category is default": the default can move, a uid cannot.
        db.execSQL(
            "UPDATE categories SET uid = ? WHERE id = ?",
            arrayOf<Any>(DEFAULT_INBOX_UID, DEFAULT_INBOX_CATEGORY_ENTITY.id),
        )
        TABLES.forEach { table ->
            db.execSQL("UPDATE `$table` SET uid = $RANDOM_UUID_V4 WHERE uid = ''")
        }

        backfillFromLocalDate(db, "habits", zone)
        backfillFromLocalDate(db, "habit_chains", zone)
        db.execSQL("UPDATE focus_sessions SET createdAt = lastUsedAt WHERE lastUsedAt > 0")
        TABLES.forEach { table ->
            db.execSQL("UPDATE `$table` SET createdAt = ? WHERE createdAt = 0", arrayOf(nowMillis))
            db.execSQL("UPDATE `$table` SET updatedAt = createdAt")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_${table}_uid` ON `$table` (`uid`)")
        }

        createTriggers(db)
    }

    private fun backfillFromLocalDate(
        db: SupportSQLiteDatabase,
        table: String,
        zone: TimeZone,
    ) {
        val updates = mutableListOf<Pair<Long, Long>>()
        db.query("SELECT id, createdDate FROM `$table` WHERE createdDate IS NOT NULL").use { cursor ->
            while (cursor.moveToNext()) {
                val millis =
                    runCatching {
                        LocalDateTime.parse(cursor.getString(1)).toInstant(zone).toEpochMilliseconds()
                    }.getOrNull() ?: continue
                updates += cursor.getLong(0) to millis
            }
        }
        updates.forEach { (id, millis) ->
            db.execSQL("UPDATE `$table` SET createdAt = ? WHERE id = ?", arrayOf(millis, id))
        }
    }
}
