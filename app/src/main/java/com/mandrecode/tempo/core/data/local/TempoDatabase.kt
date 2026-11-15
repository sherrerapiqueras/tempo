package com.mandrecode.tempo.core.data.local

import android.content.Context
import android.util.Log
import androidx.annotation.VisibleForTesting
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.mandrecode.tempo.BuildConfig
import com.mandrecode.tempo.R
import com.mandrecode.tempo.core.data.entity.CategoryEntity
import com.mandrecode.tempo.core.data.entity.Converters
import com.mandrecode.tempo.core.data.entity.DEFAULT_INBOX_CATEGORY_ENTITY
import com.mandrecode.tempo.core.data.entity.DailyFocusActivityEntity
import com.mandrecode.tempo.core.data.entity.HabitChainEntity
import com.mandrecode.tempo.core.data.entity.HabitChainMemberEntity
import com.mandrecode.tempo.core.data.entity.HabitEntity
import com.mandrecode.tempo.core.data.entity.SavedSessionEntity
import com.mandrecode.tempo.core.data.entity.TaskAttachmentEntity
import com.mandrecode.tempo.core.data.entity.TaskEntity
import com.mandrecode.tempo.core.data.entity.TaskSectionEntity
import com.mandrecode.tempo.core.data.local.dao.CategoryDao
import com.mandrecode.tempo.core.data.local.dao.DailyFocusActivityDao
import com.mandrecode.tempo.core.data.local.dao.HabitChainDao
import com.mandrecode.tempo.core.data.local.dao.HabitChainMemberDao
import com.mandrecode.tempo.core.data.local.dao.HabitDao
import com.mandrecode.tempo.core.data.local.dao.SavedSessionDao
import com.mandrecode.tempo.core.data.local.dao.TaskAttachmentDao
import com.mandrecode.tempo.core.data.local.dao.TaskDao
import com.mandrecode.tempo.core.data.local.dao.TaskSectionDao
import com.mandrecode.tempo.core.data.local.security.DatabaseEncryptionMigrator
import com.mandrecode.tempo.core.data.local.security.DbKdfIterMarker
import com.mandrecode.tempo.core.data.local.security.DbPassphraseProvider
import com.mandrecode.tempo.core.data.local.security.SqlCipherKdfIter
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

@Database(
    entities = [
        TaskEntity::class,
        CategoryEntity::class,
        HabitEntity::class,
        HabitChainEntity::class,
        HabitChainMemberEntity::class,
        DailyFocusActivityEntity::class,
        TaskAttachmentEntity::class,
        TaskSectionEntity::class,
        SavedSessionEntity::class,
    ],
    version = 14,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class TempoDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao

    abstract fun categoryDao(): CategoryDao

    abstract fun habitDao(): HabitDao

    abstract fun habitChainDao(): HabitChainDao

    abstract fun habitChainMemberDao(): HabitChainMemberDao

    abstract fun dailyFocusActivityDao(): DailyFocusActivityDao

    abstract fun taskAttachmentDao(): TaskAttachmentDao

    abstract fun taskSectionDao(): TaskSectionDao

    abstract fun savedSessionDao(): SavedSessionDao

    companion object {
        const val DATABASE_NAME = "tempo_database"

        @Volatile
        @Suppress("ktlint:standard:property-naming")
        private var INSTANCE: TempoDatabase? = null
        private val instanceMutex = Mutex()

        val MIGRATION_1_2 =
            object : Migration(1, 2) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL("ALTER TABLE tasks ADD COLUMN completedAt TEXT")
                }
            }

        val MIGRATION_2_3 =
            object : Migration(2, 3) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL("ALTER TABLE categories ADD COLUMN color TEXT DEFAULT NULL")
                    db.execSQL("ALTER TABLE categories ADD COLUMN icon TEXT DEFAULT NULL")
                    db.execSQL(
                        "ALTER TABLE categories ADD COLUMN isDefault INTEGER NOT NULL DEFAULT 0",
                    )
                    db.execSQL(
                        "ALTER TABLE categories ADD COLUMN sortOrder INTEGER NOT NULL DEFAULT 0",
                    )
                }
            }

        val MIGRATION_3_4 =
            object : Migration(3, 4) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        """
                        INSERT OR IGNORE INTO categories (id, name, color, icon, isDefault, sortOrder)
                        VALUES (-1, 'Inbox', NULL, 'inbox', 1, -1)
                        """.trimIndent(),
                    )
                }
            }

        val MIGRATION_4_5 =
            object : Migration(4, 5) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        "UPDATE categories SET icon = 'inbox' WHERE id = -1 AND icon IS NULL",
                    )
                }
            }

        val MIGRATION_5_6 =
            object : Migration(5, 6) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        "ALTER TABLE tasks ADD COLUMN periodicityInterval INTEGER NOT NULL DEFAULT 1",
                    )
                    db.execSQL(
                        "ALTER TABLE tasks ADD COLUMN repeatDays TEXT DEFAULT NULL",
                    )
                    db.execSQL(
                        "ALTER TABLE tasks ADD COLUMN monthDayOption TEXT DEFAULT NULL",
                    )
                }
            }

        /**
         * Replaces the unused `isInverted` (INTEGER) column with `habitType` (TEXT).
         * Requires a table rebuild because SQLite does not support ALTER COLUMN.
         * Maps `isInverted = 1` → "QUIT", everything else → "BUILD".
         */
        val MIGRATION_6_7 =
            object : Migration(6, 7) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    // habit_chain_members has a FK on habits(id); drop+recreate must run with
                    // FKs disabled to avoid violations during the table swap.
                    db.execSQL("PRAGMA foreign_keys=OFF")
                    db.beginTransaction()
                    try {
                        // Defensively clear any leftover habits_new table from a previously
                        // interrupted migration run. Without this, CREATE TABLE could silently
                        // no-op (with IF NOT EXISTS) or fail loudly mid-transaction; either
                        // way the subsequent INSERT could corrupt or duplicate rows.
                        db.execSQL("DROP TABLE IF EXISTS habits_new")
                        db.execSQL(
                            """
                            CREATE TABLE habits_new (
                                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                                title TEXT NOT NULL,
                                description TEXT NOT NULL,
                                icon TEXT,
                                colorKey TEXT,
                                reminderDate TEXT,
                                isCompleted INTEGER NOT NULL,
                                habitType TEXT NOT NULL,
                                createdDate TEXT NOT NULL,
                                completionHistory TEXT NOT NULL,
                                repeatDays TEXT
                            )
                            """.trimIndent(),
                        )
                        db.execSQL(
                            """
                            INSERT INTO habits_new (id, title, description, icon, colorKey,
                                reminderDate, isCompleted, habitType, createdDate,
                                completionHistory, repeatDays)
                            SELECT id, title, description, icon, colorKey,
                                reminderDate, isCompleted,
                                CASE WHEN isInverted = 1 THEN 'QUIT' ELSE 'BUILD' END,
                                createdDate, completionHistory, repeatDays
                            FROM habits
                            """.trimIndent(),
                        )
                        // Quit habits cannot belong to chains. Read the post-migration habit
                        // type from `habits_new` (the source of truth at this point) rather
                        // than the about-to-be-dropped legacy `habits.isInverted` column —
                        // this stays correct regardless of statement ordering and documents
                        // the rule in domain terms.
                        db.execSQL(
                            """
                            DELETE FROM habit_chain_members
                            WHERE habitId IN (SELECT id FROM habits_new WHERE habitType = 'QUIT')
                            """.trimIndent(),
                        )
                        db.execSQL(
                            """
                            DELETE FROM habit_chains
                            WHERE id NOT IN (SELECT DISTINCT chainId FROM habit_chain_members)
                            """.trimIndent(),
                        )
                        db.execSQL("DROP TABLE habits")
                        db.execSQL("ALTER TABLE habits_new RENAME TO habits")
                        db.setTransactionSuccessful()
                    } finally {
                        db.endTransaction()
                        db.execSQL("PRAGMA foreign_keys=ON")
                    }
                }
            }

        /**
         * Adds the `nextInstanceId` column to `tasks`. When a periodic task is completed,
         * the archived row's `nextInstanceId` is set to the spawned next-occurrence id so
         * the toggle use case can roll the spawn back if the user unchecks the archived task.
         */
        val MIGRATION_7_8 =
            object : Migration(7, 8) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL("ALTER TABLE tasks ADD COLUMN nextInstanceId INTEGER DEFAULT NULL")
                }
            }

        /**
         * Adds indices on `tasks.categoryId` and `tasks.parentTaskId`. `TaskDao` filters on
         * both columns (subtask lookups, sort-order queries, category-scoped deletes), so the
         * indices replace full table scans. Index names match Room's generated names so the
         * migrated schema stays identity-equal to the exported `9.json`. Indices-only change:
         * no foreign keys are added (FK cascade is deferred to a separate considered change).
         */
        val MIGRATION_8_9 =
            object : Migration(8, 9) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        "CREATE INDEX IF NOT EXISTS `index_tasks_categoryId` ON `tasks` (`categoryId`)",
                    )
                    db.execSQL(
                        "CREATE INDEX IF NOT EXISTS `index_tasks_parentTaskId` ON `tasks` (`parentTaskId`)",
                    )
                }
            }

        /**
         * Adds `daily_focus_activity`, the durable per-day record behind the Focus summary.
         * Purely additive: no existing table is touched, so a downgrade leaves the table orphaned
         * but harmless. History starts empty — counts for past days cannot be reconstructed,
         * because the completed tasks they would come from may already have been purged.
         */
        val MIGRATION_9_10 =
            object : Migration(9, 10) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS `daily_focus_activity` (
                            `date` TEXT NOT NULL,
                            `scheduledCount` INTEGER NOT NULL,
                            `completedCount` INTEGER NOT NULL,
                            `focusMinutes` INTEGER NOT NULL,
                            PRIMARY KEY(`date`)
                        )
                        """.trimIndent(),
                    )
                }
            }

        /**
         * Adds `task_attachments` (#380). Purely additive, like 9→10: no existing table changes,
         * and the files the rows point to live outside the database.
         */
        val MIGRATION_10_11 =
            object : Migration(10, 11) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS `task_attachments` (
                            `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            `taskId` INTEGER NOT NULL,
                            `displayName` TEXT NOT NULL,
                            `mimeType` TEXT,
                            `sizeBytes` INTEGER NOT NULL,
                            `storedName` TEXT NOT NULL
                        )
                        """.trimIndent(),
                    )
                    db.execSQL(
                        "CREATE INDEX IF NOT EXISTS `index_task_attachments_taskId` ON `task_attachments` (`taskId`)",
                    )
                    db.execSQL(
                        "CREATE INDEX IF NOT EXISTS `index_task_attachments_storedName` " +
                            "ON `task_attachments` (`storedName`)",
                    )
                }
            }

        /**
         * Adds task sections (#61): the `task_sections` table, a nullable `tasks.sectionId` and the
         * per-category `sectionsEnabled` switch. Purely additive — every existing task reads as
         * unsectioned and every category has sections off, so no backfill is needed.
         */
        val MIGRATION_11_12 =
            object : Migration(11, 12) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS `task_sections` (
                            `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            `categoryId` INTEGER NOT NULL,
                            `name` TEXT NOT NULL,
                            `sortOrder` INTEGER NOT NULL
                        )
                        """.trimIndent(),
                    )
                    db.execSQL(
                        "CREATE INDEX IF NOT EXISTS `index_task_sections_categoryId` ON `task_sections` (`categoryId`)",
                    )
                    db.execSQL("ALTER TABLE tasks ADD COLUMN sectionId INTEGER DEFAULT NULL")
                    db.execSQL("ALTER TABLE categories ADD COLUMN sectionsEnabled INTEGER NOT NULL DEFAULT 0")
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_tasks_sectionId` ON `tasks` (`sectionId`)")
                }
            }

        /**
         * Adds the optional end date of a repeating task (#509). Purely additive — every existing
         * task reads as a series with no end, so no backfill is needed.
         */
        val MIGRATION_12_13 =
            object : Migration(12, 13) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL("ALTER TABLE tasks ADD COLUMN periodicityEndDate TEXT DEFAULT NULL")
                }
            }

        /**
         * Adds the topic sessions remembered from Focus (#517). A new table and nothing else: no
         * existing row changes, and a timer already running is stored outside the database.
         */
        val MIGRATION_13_14 =
            object : Migration(13, 14) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS `focus_sessions` (" +
                            "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                            "`title` TEXT NOT NULL, " +
                            "`focusMinutes` INTEGER NOT NULL, " +
                            "`breakMinutes` INTEGER NOT NULL, " +
                            "`rounds` INTEGER NOT NULL, " +
                            "`lastUsedAt` INTEGER NOT NULL)",
                    )
                }
            }

        val MIGRATIONS =
            arrayOf(
                MIGRATION_1_2,
                MIGRATION_2_3,
                MIGRATION_3_4,
                MIGRATION_4_5,
                MIGRATION_5_6,
                MIGRATION_6_7,
                MIGRATION_7_8,
                MIGRATION_8_9,
                MIGRATION_9_10,
                MIGRATION_10_11,
                MIGRATION_11_12,
                MIGRATION_12_13,
                MIGRATION_13_14,
            )

        suspend fun getDatabase(
            context: Context,
            passphraseProvider: DbPassphraseProvider,
            migrator: DatabaseEncryptionMigrator,
        ): TempoDatabase =
            INSTANCE ?: instanceMutex.withLock {
                INSTANCE ?: run {
                    val appContext = context.applicationContext
                    val passphrase = passphraseProvider.getOrCreatePassphrase()
                    // Guarantees that by the time Room opens this file below, it is already
                    // keyed at SqlCipherKdfIter.CURRENT — either because it didn't exist yet
                    // (fresh install, created below at CURRENT via the SupportOpenHelperFactory
                    // hook) or because it's been re-keyed from SqlCipherKdfIter.LEGACY. Room can
                    // therefore always open with CURRENT, unconditionally, below.
                    migrator.migrateIfNeeded(DATABASE_NAME, passphrase)
                    val instance =
                        configure(
                            Room
                                .databaseBuilder(
                                    appContext,
                                    TempoDatabase::class.java,
                                    DATABASE_NAME,
                                ).openHelperFactory(
                                    SupportOpenHelperFactory(
                                        passphrase,
                                        SqlCipherKdfIter.hookFor(SqlCipherKdfIter.CURRENT),
                                        false,
                                    ),
                                ),
                            appContext,
                            allowDestructiveDowngrade = BuildConfig.DEBUG,
                        ).build()
                    INSTANCE = instance
                    instance
                }
            }

        /**
         * Everything about how the database opens except where it lives and how it is keyed:
         * migrations, the callbacks, and what happens when the file's schema version does not match
         * the build. The app and the instrumented tests both build through here, so the tests run
         * the configuration that ships.
         *
         * A downgrade (the file is newer than this build) or an upgrade with no migration path
         * makes Room throw and leaves the file untouched. The one exception is
         * [allowDestructiveDowngrade], which the app turns on for debug builds only: switching
         * between branches with different schema versions on one device is a normal workflow
         * there, and the data is disposable. It drops *every* table, not just the ones this
         * schema knows, so tables only a newer schema has do not survive with stale rows. A
         * release build never wipes — a silent wipe cannot be undone, a failed open can (install
         * the matching version) — and no build wipes for a missing upgrade path, which is a
         * forgotten migration that should crash in development.
         */
        @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
        internal fun configure(
            builder: RoomDatabase.Builder<TempoDatabase>,
            context: Context,
            allowDestructiveDowngrade: Boolean,
        ): RoomDatabase.Builder<TempoDatabase> =
            builder
                .addMigrations(*MIGRATIONS)
                .addCallback(inboxCallback(context))
                .addCallback(OrphanedAttachmentRowsCallback)
                .addCallback(DestructiveMigrationWarningCallback)
                .apply {
                    if (allowDestructiveDowngrade) {
                        fallbackToDestructiveMigrationOnDowngrade(dropAllTables = true)
                    }
                }

        /**
         * Drops attachment rows whose task no longer exists, every time the database opens.
         *
         * Task deletion keeps attachment rows so that Undo can bring them back (#380); once the
         * process that offered the Undo is gone, nothing can. Doing it here rather than in a
         * background sweep matters: Room runs onOpen before any query reaches the database, so no
         * deletion made in this process — and no Undo window — can exist yet.
         */
        @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
        internal val OrphanedAttachmentRowsCallback =
            object : RoomDatabase.Callback() {
                override fun onOpen(db: SupportSQLiteDatabase) {
                    db.execSQL("DELETE FROM task_attachments WHERE taskId NOT IN (SELECT id FROM tasks)")
                }
            }

        /**
         * Says so in the log when Room resets the database. Only a debug build configured by
         * [configure] with `allowDestructiveDowngrade` can get here, and a reset that nobody
         * notices is how the missing Inbox went unexplained.
         *
         * This cannot restore anything itself: Room calls it after dropping the tables and before
         * recreating them, so there is no `categories` table to write to yet. The default category
         * comes back through [inboxCallback]'s `onOpen`, which always runs afterwards.
         */
        private val DestructiveMigrationWarningCallback =
            object : RoomDatabase.Callback() {
                override fun onDestructiveMigration(db: SupportSQLiteDatabase) {
                    Log.w(
                        LOG_TAG,
                        "The database schema did not match this build: every table was dropped and recreated empty.",
                    )
                }
            }

        /**
         * Keeps a default category in the database, and records a new file's [SqlCipherKdfIter]
         * marker.
         *
         * [onOpen][RoomDatabase.Callback.onOpen] runs after every way a database can come up: a
         * fresh file (after `onCreate`), a migration, and a destructive reset, which does *not* call
         * `onCreate`. Seeding there, instead of only in `onCreate`, is what keeps a reset database
         * from having no categories at all. The insert is conditional on there being no default
         * category: with one present it writes nothing, so a user's rename is never overwritten, and
         * a user who made another category the default and deleted the Inbox does not get it back as
         * a second default. The localized name is only used when a row is actually created.
         *
         * `onCreate` only fires when Room itself creates the underlying file (i.e. a genuine
         * fresh install with no prior database) — the migrator's export/re-key paths always
         * hand Room an already-populated file, which Room treats as existing (`onOpen`, not
         * `onCreate`). So this is also the one place a freshly-created database's
         * [SqlCipherKdfIter] marker needs to be recorded: [DatabaseEncryptionMigrator] only ever
         * sees this file on its *next* launch, once it already exists.
         */
        @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
        internal fun inboxCallback(context: Context) =
            object : RoomDatabase.Callback() {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    DbKdfIterMarker.write(context, SqlCipherKdfIter.CURRENT)
                }

                override fun onOpen(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        """
                        INSERT OR IGNORE INTO categories (id, name, icon, isDefault, sortOrder)
                        SELECT ?, ?, ?, 1, ?
                        WHERE NOT EXISTS (SELECT 1 FROM categories WHERE isDefault = 1)
                        """.trimIndent(),
                        arrayOf<Any?>(
                            DEFAULT_INBOX_CATEGORY_ENTITY.id,
                            context.getString(R.string.category_inbox),
                            DEFAULT_INBOX_CATEGORY_ENTITY.icon,
                            DEFAULT_INBOX_CATEGORY_ENTITY.sortOrder,
                        ),
                    )
                }
            }

        private const val LOG_TAG = "TempoDatabase"
    }
}
