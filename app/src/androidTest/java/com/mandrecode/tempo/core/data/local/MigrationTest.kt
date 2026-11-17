package com.mandrecode.tempo.core.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import com.mandrecode.tempo.core.domain.model.DEFAULT_INBOX_UID
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MigrationTest {
    companion object {
        private const val DB_NAME = "tempo_migration_test"
        private const val UUID_PATTERN = "[0-9a-f]{8}-[0-9a-f]{4}-[47][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}"
    }

    @get:Rule
    val helper =
        MigrationTestHelper(
            InstrumentationRegistry.getInstrumentation(),
            TempoDatabase::class.java,
        )

    @Test
    fun migrate1To2_addsCompletedAtColumn() {
        helper.createDatabase(DB_NAME, 1).use { db ->
            db.execSQL(
                """
                INSERT INTO tasks (title, description, isCompleted, categoryId, priority, sortOrder)
                VALUES ('Test task', 'desc', 0, 0, 1, 0)
                """.trimIndent(),
            )
        }

        helper
            .runMigrationsAndValidate(
                DB_NAME,
                2,
                true,
                TempoDatabase.MIGRATION_1_2,
            ).use { db ->
                db.query("SELECT completedAt FROM tasks WHERE title = 'Test task'").use { cursor ->
                    assertThat(cursor.moveToFirst()).isTrue()
                    assertThat(cursor.isNull(0)).isTrue()
                }
            }
    }

    @Test
    fun migrateAll_fromVersion1ToLatest() {
        helper.createDatabase(DB_NAME, 1).use { it.close() }
        // Succeeding without exception validates the full upgrade path
        helper.runMigrationsAndValidate(
            DB_NAME,
            9,
            true,
            *TempoDatabase.MIGRATIONS,
        )
    }

    @Test
    fun migrate5To6_addsRecurrenceColumns() {
        helper.createDatabase(DB_NAME, 5).use { db ->
            db.execSQL(
                """
                INSERT INTO tasks (title, description, isCompleted, categoryId, priority,
                    periodicity, parentTaskId, sortOrder, completedAt)
                VALUES ('Recurring task', 'desc', 0, -1, 1, 'DAILY', NULL, 0, NULL)
                """.trimIndent(),
            )
        }

        helper
            .runMigrationsAndValidate(
                DB_NAME,
                6,
                true,
                TempoDatabase.MIGRATION_5_6,
            ).use { db ->
                db
                    .query("SELECT periodicityInterval, repeatDays, monthDayOption FROM tasks WHERE title = 'Recurring task'")
                    .use { cursor ->
                        assertThat(cursor.moveToFirst()).isTrue()
                        assertThat(cursor.getInt(0)).isEqualTo(1)
                        assertThat(cursor.isNull(1)).isTrue()
                        assertThat(cursor.isNull(2)).isTrue()
                    }
            }
    }

    @Test
    fun migrate4To5_setsInboxIconOnDefaultCategory() {
        helper.createDatabase(DB_NAME, 4).use { db ->
            db.execSQL(
                """
                INSERT OR REPLACE INTO categories (id, name, color, icon, isDefault, sortOrder)
                VALUES (-1, 'Inbox', NULL, NULL, 1, -1)
                """.trimIndent(),
            )
        }

        helper
            .runMigrationsAndValidate(
                DB_NAME,
                5,
                true,
                TempoDatabase.MIGRATION_4_5,
            ).use { db ->
                db.query("SELECT icon FROM categories WHERE id = -1").use { cursor ->
                    assertThat(cursor.moveToFirst()).isTrue()
                    assertThat(cursor.getString(0)).isEqualTo("inbox")
                }
            }
    }

    @Test
    fun migrate4To5_preservesCustomIconOnDefaultCategory() {
        helper.createDatabase(DB_NAME, 4).use { db ->
            db.execSQL(
                """
                INSERT OR REPLACE INTO categories (id, name, color, icon, isDefault, sortOrder)
                VALUES (-1, 'Inbox', NULL, 'star', 1, -1)
                """.trimIndent(),
            )
        }

        helper
            .runMigrationsAndValidate(
                DB_NAME,
                5,
                true,
                TempoDatabase.MIGRATION_4_5,
            ).use { db ->
                db.query("SELECT icon FROM categories WHERE id = -1").use { cursor ->
                    assertThat(cursor.moveToFirst()).isTrue()
                    assertThat(cursor.getString(0)).isEqualTo("star")
                }
            }
    }

    @Test
    fun migrate6To7_replacesIsInvertedWithHabitType() {
        helper.createDatabase(DB_NAME, 6).use { db ->
            // Insert a normal habit (isInverted = 0)
            db.execSQL(
                """
                INSERT INTO habits (title, description, isCompleted, isInverted, createdDate, completionHistory)
                VALUES ('Exercise', 'Daily run', 0, 0, '2025-01-01T00:00', '')
                """.trimIndent(),
            )
            // Insert an inverted habit (isInverted = 1) — should become QUIT
            db.execSQL(
                """
                INSERT INTO habits (title, description, isCompleted, isInverted, createdDate, completionHistory)
                VALUES ('No smoking', 'Stay clean', 0, 1, '2025-01-01T00:00', '')
                """.trimIndent(),
            )
        }

        helper
            .runMigrationsAndValidate(
                DB_NAME,
                7,
                true,
                TempoDatabase.MIGRATION_6_7,
            ).use { db ->
                db
                    .query("SELECT title, habitType FROM habits ORDER BY id")
                    .use { cursor ->
                        assertThat(cursor.moveToFirst()).isTrue()
                        assertThat(cursor.getString(0)).isEqualTo("Exercise")
                        assertThat(cursor.getString(1)).isEqualTo("BUILD")

                        assertThat(cursor.moveToNext()).isTrue()
                        assertThat(cursor.getString(0)).isEqualTo("No smoking")
                        assertThat(cursor.getString(1)).isEqualTo("QUIT")
                    }
            }
    }

    @Test
    fun migrate6To7_purgesQuitHabitChainMembershipsAndPrunesEmptyChains() {
        helper.createDatabase(DB_NAME, 6).use { db ->
            // Three habits: one BUILD (id=1), one QUIT (id=2), one BUILD (id=3).
            db.execSQL(
                """
                INSERT INTO habits (id, title, description, isCompleted, isInverted, createdDate, completionHistory)
                VALUES (1, 'Read', '', 0, 0, '2025-01-01T00:00', '')
                """.trimIndent(),
            )
            db.execSQL(
                """
                INSERT INTO habits (id, title, description, isCompleted, isInverted, createdDate, completionHistory)
                VALUES (2, 'No smoking', '', 0, 1, '2025-01-01T00:00', '')
                """.trimIndent(),
            )
            db.execSQL(
                """
                INSERT INTO habits (id, title, description, isCompleted, isInverted, createdDate, completionHistory)
                VALUES (3, 'Stretch', '', 0, 0, '2025-01-01T00:00', '')
                """.trimIndent(),
            )

            // Two chains:
            //  - Chain 100: contains only the QUIT habit (id=2) → must be pruned.
            //  - Chain 200: mixes BUILD (id=1) + QUIT (id=2) + BUILD (id=3) → only the
            //    QUIT membership must be removed; the chain itself survives with
            //    BUILD-only members.
            db.execSQL(
                """
                INSERT INTO habit_chains (id, title, description, createdDate, completionHistory)
                VALUES (100, 'Quit only', '', '2025-01-01T00:00', '')
                """.trimIndent(),
            )
            db.execSQL(
                """
                INSERT INTO habit_chains (id, title, description, createdDate, completionHistory)
                VALUES (200, 'Mixed chain', '', '2025-01-01T00:00', '')
                """.trimIndent(),
            )
            db.execSQL(
                """
                INSERT INTO habit_chain_members (chainId, habitId, sortOrder)
                VALUES (100, 2, 0)
                """.trimIndent(),
            )
            db.execSQL(
                """
                INSERT INTO habit_chain_members (chainId, habitId, sortOrder)
                VALUES (200, 1, 0)
                """.trimIndent(),
            )
            db.execSQL(
                """
                INSERT INTO habit_chain_members (chainId, habitId, sortOrder)
                VALUES (200, 2, 1)
                """.trimIndent(),
            )
            db.execSQL(
                """
                INSERT INTO habit_chain_members (chainId, habitId, sortOrder)
                VALUES (200, 3, 2)
                """.trimIndent(),
            )
        }

        helper
            .runMigrationsAndValidate(
                DB_NAME,
                7,
                true,
                TempoDatabase.MIGRATION_6_7,
            ).use { db ->
                // No membership references the QUIT habit anymore.
                db.query("SELECT COUNT(*) FROM habit_chain_members WHERE habitId = 2").use { cursor ->
                    assertThat(cursor.moveToFirst()).isTrue()
                    assertThat(cursor.getInt(0)).isEqualTo(0)
                }
                // Mixed chain (200) still exists with its two BUILD memberships.
                db.query("SELECT habitId FROM habit_chain_members WHERE chainId = 200 ORDER BY habitId").use { cursor ->
                    assertThat(cursor.moveToFirst()).isTrue()
                    assertThat(cursor.getLong(0)).isEqualTo(1L)
                    assertThat(cursor.moveToNext()).isTrue()
                    assertThat(cursor.getLong(0)).isEqualTo(3L)
                    assertThat(cursor.moveToNext()).isFalse()
                }
                // Empty chain (100) was pruned.
                db.query("SELECT COUNT(*) FROM habit_chains WHERE id = 100").use { cursor ->
                    assertThat(cursor.moveToFirst()).isTrue()
                    assertThat(cursor.getInt(0)).isEqualTo(0)
                }
                // Mixed chain (200) survived.
                db.query("SELECT COUNT(*) FROM habit_chains WHERE id = 200").use { cursor ->
                    assertThat(cursor.moveToFirst()).isTrue()
                    assertThat(cursor.getInt(0)).isEqualTo(1)
                }
                // The QUIT habit row itself is preserved (only its chain links are gone).
                db.query("SELECT habitType FROM habits WHERE id = 2").use { cursor ->
                    assertThat(cursor.moveToFirst()).isTrue()
                    assertThat(cursor.getString(0)).isEqualTo("QUIT")
                }
            }
    }

    @Test
    fun migrate8To9_addsTaskIndicesAndPreservesRows() {
        helper.createDatabase(DB_NAME, 8).use { db ->
            db.execSQL(
                """
                INSERT INTO tasks (id, title, description, isCompleted, categoryId, priority,
                    parentTaskId, sortOrder, periodicityInterval)
                VALUES (1, 'Parent task', 'desc', 0, -1, 1, NULL, 0, 1)
                """.trimIndent(),
            )
            db.execSQL(
                """
                INSERT INTO tasks (id, title, description, isCompleted, categoryId, priority,
                    parentTaskId, sortOrder, periodicityInterval)
                VALUES (2, 'Subtask', 'desc', 0, -1, 1, 1, 0, 1)
                """.trimIndent(),
            )
        }

        helper
            .runMigrationsAndValidate(
                DB_NAME,
                9,
                true,
                TempoDatabase.MIGRATION_8_9,
            ).use { db ->
                val indexNames = mutableSetOf<String>()
                db.query("PRAGMA index_list('tasks')").use { cursor ->
                    val nameColumn = cursor.getColumnIndexOrThrow("name")
                    while (cursor.moveToNext()) {
                        indexNames.add(cursor.getString(nameColumn))
                    }
                }
                assertThat(indexNames).contains("index_tasks_categoryId")
                assertThat(indexNames).contains("index_tasks_parentTaskId")

                db.query("SELECT title FROM tasks ORDER BY id").use { cursor ->
                    assertThat(cursor.moveToFirst()).isTrue()
                    assertThat(cursor.getString(0)).isEqualTo("Parent task")
                    assertThat(cursor.moveToNext()).isTrue()
                    assertThat(cursor.getString(0)).isEqualTo("Subtask")
                }
            }
    }

    @Test
    fun migrate10To11_addsTaskAttachmentsAndPreservesTasks() {
        helper.createDatabase(DB_NAME, 10).use { db ->
            db.execSQL(
                """
                INSERT INTO tasks (id, title, description, isCompleted, categoryId, priority,
                    parentTaskId, sortOrder, periodicityInterval)
                VALUES (1, 'Pay rent', 'desc', 0, -1, 1, NULL, 0, 1)
                """.trimIndent(),
            )
        }

        helper
            .runMigrationsAndValidate(
                DB_NAME,
                11,
                true,
                TempoDatabase.MIGRATION_10_11,
            ).use { db ->
                db.execSQL(
                    """
                    INSERT INTO task_attachments (taskId, displayName, mimeType, sizeBytes, storedName)
                    VALUES (1, 'lease.pdf', 'application/pdf', 2048, 'abc')
                    """.trimIndent(),
                )
                db.query("SELECT displayName FROM task_attachments WHERE taskId = 1").use { cursor ->
                    assertThat(cursor.moveToFirst()).isTrue()
                    assertThat(cursor.getString(0)).isEqualTo("lease.pdf")
                }
                db.query("SELECT title FROM tasks WHERE id = 1").use { cursor ->
                    assertThat(cursor.moveToFirst()).isTrue()
                    assertThat(cursor.getString(0)).isEqualTo("Pay rent")
                }
            }
    }

    @Test
    fun migrate11To12_addsTaskSectionsAndLeavesEverythingUnsectioned() {
        helper.createDatabase(DB_NAME, 11).use { db ->
            db.execSQL(
                "INSERT INTO categories (id, name, color, icon, isDefault, sortOrder) VALUES (-1, 'Inbox', NULL, 'inbox', 1, -1)",
            )
            db.execSQL(
                """
                INSERT INTO tasks (id, title, description, isCompleted, categoryId, priority,
                    parentTaskId, sortOrder, periodicityInterval)
                VALUES (1, 'Buy litter', 'desc', 0, -1, NULL, NULL, 0, 1)
                """.trimIndent(),
            )
        }

        helper
            .runMigrationsAndValidate(
                DB_NAME,
                12,
                true,
                TempoDatabase.MIGRATION_11_12,
            ).use { db ->
                db.query("SELECT sectionId FROM tasks WHERE id = 1").use { cursor ->
                    assertThat(cursor.moveToFirst()).isTrue()
                    assertThat(cursor.isNull(0)).isTrue()
                }
                db.query("SELECT sectionsEnabled FROM categories WHERE id = -1").use { cursor ->
                    assertThat(cursor.moveToFirst()).isTrue()
                    assertThat(cursor.getInt(0)).isEqualTo(0)
                }
                db.execSQL("INSERT INTO task_sections (categoryId, name, sortOrder) VALUES (-1, 'Cat', 0)")
                db.query("SELECT name FROM task_sections WHERE categoryId = -1").use { cursor ->
                    assertThat(cursor.moveToFirst()).isTrue()
                    assertThat(cursor.getString(0)).isEqualTo("Cat")
                }
            }
    }

    @Test
    fun migrate12To13_addsPeriodicityEndDateAndLeavesSeriesWithoutAnEnd() {
        helper.createDatabase(DB_NAME, 12).use { db ->
            db.execSQL(
                "INSERT INTO categories (id, name, color, icon, isDefault, sortOrder, sectionsEnabled) " +
                    "VALUES (-1, 'Inbox', NULL, 'inbox', 1, -1, 0)",
            )
            db.execSQL(
                """
                INSERT INTO tasks (id, title, description, isCompleted, categoryId, priority,
                    periodicity, parentTaskId, sortOrder, periodicityInterval)
                VALUES (1, 'Water plants', 'desc', 0, -1, NULL, 'DAILY', NULL, 0, 2)
                """.trimIndent(),
            )
        }

        helper
            .runMigrationsAndValidate(
                DB_NAME,
                13,
                true,
                TempoDatabase.MIGRATION_12_13,
            ).use { db ->
                db.query("SELECT periodicityEndDate, periodicity, periodicityInterval FROM tasks WHERE id = 1").use { cursor ->
                    assertThat(cursor.moveToFirst()).isTrue()
                    assertThat(cursor.isNull(0)).isTrue()
                    assertThat(cursor.getString(1)).isEqualTo("DAILY")
                    assertThat(cursor.getInt(2)).isEqualTo(2)
                }
            }
    }

    @Test
    fun migrate13To14_addsEmptyFocusSessionsAndLeavesTasksAlone() {
        helper.createDatabase(DB_NAME, 13).use { db ->
            db.execSQL(
                "INSERT INTO categories (id, name, color, icon, isDefault, sortOrder, sectionsEnabled) " +
                    "VALUES (-1, 'Inbox', NULL, 'inbox', 1, -1, 0)",
            )
            db.execSQL(
                """
                INSERT INTO tasks (id, title, description, isCompleted, categoryId, priority,
                    periodicity, parentTaskId, sortOrder, periodicityInterval)
                VALUES (1, 'Read chapter 3', 'desc', 0, -1, NULL, NULL, NULL, 0, 1)
                """.trimIndent(),
            )
        }

        helper
            .runMigrationsAndValidate(
                DB_NAME,
                14,
                true,
                TempoDatabase.MIGRATION_13_14,
            ).use { db ->
                db.query("SELECT COUNT(*) FROM focus_sessions").use { cursor ->
                    assertThat(cursor.moveToFirst()).isTrue()
                    assertThat(cursor.getInt(0)).isEqualTo(0)
                }
                // A recent session can be stored beside the tasks that were there before the table was.
                db.execSQL(
                    "INSERT INTO focus_sessions (title, focusMinutes, breakMinutes, rounds, lastUsedAt) " +
                        "VALUES ('Study', 25, 5, 4, 1700000000000)",
                )
                db.query("SELECT lastUsedAt FROM focus_sessions").use { cursor ->
                    assertThat(cursor.moveToFirst()).isTrue()
                    assertThat(cursor.getLong(0)).isEqualTo(1_700_000_000_000)
                }
                db.query("SELECT title FROM tasks WHERE id = 1").use { cursor ->
                    assertThat(cursor.moveToFirst()).isTrue()
                    assertThat(cursor.getString(0)).isEqualTo("Read chapter 3")
                }
            }
    }

    @Test
    fun migrateAll_fromVersion1To15() {
        helper.createDatabase(DB_NAME, 1).use { it.close() }
        helper.runMigrationsAndValidate(DB_NAME, 15, true, *TempoDatabase.MIGRATIONS).use { db ->
            EntityIdentitySchema.TABLES.forEach { table ->
                db.query("SELECT COUNT(*) FROM sqlite_master WHERE type = 'trigger' AND tbl_name = '$table'").use {
                    it.moveToFirst()
                    assertThat(it.getInt(0)).isEqualTo(EntityIdentitySchema.triggerNames(table).size)
                }
            }
        }
    }

    @Test
    fun migrate14To15_givesEveryRowAStableIdentityAndKeepsData() {
        helper.createDatabase(DB_NAME, 14).use { db ->
            db.execSQL(
                "INSERT INTO categories (id, name, icon, isDefault, sortOrder, sectionsEnabled) " +
                    "VALUES (-1, 'My Inbox', 'inbox', 1, -1, 0), (5, 'Work', NULL, 0, 0, 1)",
            )
            db.execSQL("INSERT INTO task_sections (id, categoryId, name, sortOrder) VALUES (3, 5, 'Soon', 0)")
            db.execSQL(
                "INSERT INTO tasks (id, title, description, isCompleted, categoryId, periodicityInterval, " +
                    "sortOrder, sectionId, parentTaskId) VALUES " +
                    "(1, 'Parent', 'd', 0, 5, 1, 0, 3, NULL), (2, 'Child', '', 1, 5, 1, 1, NULL, 1)",
            )
            db.execSQL(
                "INSERT INTO task_attachments (id, taskId, displayName, mimeType, sizeBytes, storedName) " +
                    "VALUES (1, 1, 'a.pdf', 'application/pdf', 10, 's1')",
            )
            db.execSQL(
                "INSERT INTO habits (id, title, description, isCompleted, habitType, createdDate, completionHistory) " +
                    "VALUES (7, 'Read', '', 0, 'BUILD', '2026-01-02T03:04:05', '2026-01-02')",
            )
            db.execSQL(
                "INSERT INTO habit_chains (id, title, description, createdDate, completionHistory) " +
                    "VALUES (4, 'Morning', '', '2026-02-03T04:05:06', '')",
            )
            db.execSQL("INSERT INTO habit_chain_members (chainId, habitId, sortOrder) VALUES (4, 7, 0)")
            db.execSQL(
                "INSERT INTO focus_sessions (id, title, focusMinutes, breakMinutes, rounds, lastUsedAt) " +
                    "VALUES (1, 'Deep work', 50, 10, 2, 1700000000000), (2, 'Never used', 25, 5, 1, 0)",
            )
        }

        val before = System.currentTimeMillis()
        helper.runMigrationsAndValidate(DB_NAME, 15, true, TempoDatabase.MIGRATION_14_15).use { db ->
            val uids = mutableSetOf<String>()
            EntityIdentitySchema.TABLES.forEach { table ->
                db.query("SELECT uid, createdAt, updatedAt FROM `$table`").use { c ->
                    while (c.moveToNext()) {
                        val uid = c.getString(0)
                        assertThat(uid).matches(UUID_PATTERN)
                        assertThat(uids.add("$table/$uid")).isTrue()
                        assertThat(c.getLong(1)).isGreaterThan(0L)
                        assertThat(c.getLong(2)).isEqualTo(c.getLong(1))
                    }
                }
            }
            assertThat(uids).hasSize(10)

            db.query("SELECT uid, name FROM categories WHERE id = -1").use {
                it.moveToFirst()
                assertThat(it.getString(0)).isEqualTo(DEFAULT_INBOX_UID)
                assertThat(it.getString(1)).isEqualTo("My Inbox")
            }
            val zone = TimeZone.currentSystemDefault()
            db.query("SELECT createdAt FROM habits WHERE id = 7").use {
                it.moveToFirst()
                assertThat(it.getLong(0))
                    .isEqualTo(LocalDateTime.parse("2026-01-02T03:04:05").toInstant(zone).toEpochMilliseconds())
            }
            db.query("SELECT createdAt FROM habit_chains WHERE id = 4").use {
                it.moveToFirst()
                assertThat(it.getLong(0))
                    .isEqualTo(LocalDateTime.parse("2026-02-03T04:05:06").toInstant(zone).toEpochMilliseconds())
            }
            db.query("SELECT createdAt FROM focus_sessions ORDER BY id").use {
                it.moveToFirst()
                assertThat(it.getLong(0)).isEqualTo(1700000000000L)
                it.moveToNext()
                assertThat(it.getLong(0)).isAtLeast(before)
            }
            // Pre-existing data untouched.
            db.query("SELECT title, isCompleted, categoryId, sectionId, parentTaskId FROM tasks ORDER BY id").use {
                it.moveToFirst()
                assertThat(it.getString(0)).isEqualTo("Parent")
                assertThat(it.getLong(2)).isEqualTo(5L)
                assertThat(it.getLong(3)).isEqualTo(3L)
                it.moveToNext()
                assertThat(it.getString(0)).isEqualTo("Child")
                assertThat(it.getInt(1)).isEqualTo(1)
                assertThat(it.getLong(4)).isEqualTo(1L)
            }
            db.query("SELECT completionHistory FROM habits WHERE id = 7").use {
                it.moveToFirst()
                assertThat(it.getString(0)).isEqualTo("2026-01-02")
            }
            db.query("SELECT COUNT(*) FROM habit_chain_members").use {
                it.moveToFirst()
                assertThat(it.getInt(0)).isEqualTo(1)
            }
        }
    }
}
