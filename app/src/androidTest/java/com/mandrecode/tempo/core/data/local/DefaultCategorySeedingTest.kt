package com.mandrecode.tempo.core.data.local

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.mandrecode.tempo.R
import com.mandrecode.tempo.core.data.entity.CategoryEntity
import com.mandrecode.tempo.core.data.entity.TaskEntity
import com.mandrecode.tempo.core.data.local.security.DbKdfIterMarker
import com.mandrecode.tempo.core.domain.model.DEFAULT_INBOX_UID
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

/**
 * The default category is ensured every time the database opens, so a database that lost it, or was
 * reset by a schema downgrade, is never left without one. Every database here is built through
 * [TempoDatabase.configure], the same configuration the app uses, apart from the SQLCipher factory.
 */
@RunWith(AndroidJUnit4::class)
class DefaultCategorySeedingTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val dbName = "default_category_seeding_test_${UUID.randomUUID()}"
    private val open = mutableListOf<TempoDatabase>()

    @Before
    fun setUp() {
        context.deleteDatabase(dbName)
    }

    @After
    fun tearDown() {
        open.forEach { runCatching { it.close() } }
        context.deleteDatabase(dbName)
        // Not scoped per database; creating files here writes it, and an absent one is always safe.
        DbKdfIterMarker.clear(context)
    }

    @Test
    fun freshDatabase_seedsTheInboxAsTheDefault() =
        runTest {
            val database = build()

            val categories = database.categoryDao().getAllCategoriesSync()

            assertThat(categories).hasSize(1)
            val inbox = categories.single()
            assertThat(inbox.id).isGreaterThan(0L)
            assertThat(inbox.uid).isEqualTo(DEFAULT_INBOX_UID)
            assertThat(inbox.name).isEqualTo(context.getString(R.string.category_inbox))
            assertThat(inbox.icon).isEqualTo("inbox")
            assertThat(inbox.isDefault).isTrue()
            assertThat(inbox.sortOrder).isEqualTo(-1)
        }

    @Test
    fun reopeningADatabaseWithoutCategories_restoresTheInbox() =
        runTest {
            val first = build()
            first.categoryDao().deleteAllCategories()
            assertThat(first.categoryDao().getAllCategoriesSync()).isEmpty()
            first.close()

            val reopened = build()

            val inbox = inboxOrNull(reopened)
            assertThat(inbox).isNotNull()
            assertThat(inbox!!.isDefault).isTrue()
            assertThat(reopened.categoryDao().getAllCategoriesSync()).hasSize(1)
        }

    @Test
    fun aRenamedInbox_isUntouchedOnReopen() =
        runTest {
            val first = build()
            val edited =
                inboxOf(first).copy(
                    name = "My things",
                    color = "#336699",
                    icon = "star",
                    sortOrder = 7,
                    sectionsEnabled = true,
                )
            first.categoryDao().updateCategory(edited)
            // As stored: the update itself advances updatedAt (#30); reopening must change nothing.
            val stored = inboxOf(first)
            first.close()

            val reopened = build()

            assertThat(stored.name).isEqualTo(edited.name)
            assertThat(reopened.categoryDao().getAllCategoriesSync()).containsExactly(stored)
        }

    @Test
    fun anInboxThatIsNotTheDefault_isNotRewrittenWhenNoCategoryIsDefault() =
        runTest {
            val first = build()
            val notDefault = inboxOf(first).copy(isDefault = false)
            first.categoryDao().updateCategory(notDefault)
            val stored = inboxOf(first)
            first.close()

            val reopened = build()

            assertThat(stored.isDefault).isFalse()
            assertThat(reopened.categoryDao().getAllCategoriesSync()).containsExactly(stored)
        }

    @Test
    fun aDeletedInbox_isNotBroughtBackWhileAnotherCategoryIsTheDefault() =
        runTest {
            val first = build()
            val workId = first.categoryDao().insertCategory(CategoryEntity(name = "Work", sortOrder = 1))
            first.categoryDao().setDefaultCategory(workId)
            first.categoryDao().deleteCategoryById(inboxOf(first).id)
            first.close()

            val reopened = build()

            val categories = reopened.categoryDao().getAllCategoriesSync()
            assertThat(categories.map { it.id }).containsExactly(workId)
            assertThat(categories.count { it.isDefault }).isEqualTo(1)
        }

    @Test
    fun aDowngrade_inADebugBuild_resetsTheDatabaseAndStillHasTheInbox() =
        runTest {
            val first = build()
            first.categoryDao().insertCategory(CategoryEntity(name = "Work", sortOrder = 1))
            first.taskDao().insertTask(TaskEntity(title = "Lost in the reset", description = "", categoryId = 1))
            first.close()
            markAsWrittenByANewerBuild()

            val reopened = build(allowDestructiveDowngrade = true)

            assertThat(rowCount(reopened, "tasks")).isEqualTo(0)
            val categories = reopened.categoryDao().getAllCategoriesSync()
            assertThat(categories.map { it.uid }).containsExactly(DEFAULT_INBOX_UID)
            assertThat(categories.single().isDefault).isTrue()
        }

    @Test
    fun aDowngrade_inAReleaseBuild_failsToOpenAndLeavesTheFileUntouched() =
        runTest {
            val first = build()
            first.categoryDao().insertCategory(CategoryEntity(name = "Work", sortOrder = 1))
            first.taskDao().insertTask(TaskEntity(title = "Must survive", description = "", categoryId = 1))
            first.close()
            markAsWrittenByANewerBuild()

            val refused = build(allowDestructiveDowngrade = false)
            assertThrows(IllegalStateException::class.java) { refused.openHelper.writableDatabase }
            runCatching { refused.close() }

            SQLiteDatabase
                .openDatabase(context.getDatabasePath(dbName).path, null, SQLiteDatabase.OPEN_READONLY)
                .use { raw ->
                    assertThat(raw.version).isEqualTo(NEWER_VERSION)
                    assertThat(scalar(raw, "SELECT COUNT(*) FROM tasks")).isEqualTo(1)
                    assertThat(scalar(raw, "SELECT COUNT(*) FROM categories WHERE name = 'Work'")).isEqualTo(1)
                }
        }

    private fun build(allowDestructiveDowngrade: Boolean = false): TempoDatabase =
        TempoDatabase
            .configure(
                Room.databaseBuilder(context, TempoDatabase::class.java, dbName),
                context,
                allowDestructiveDowngrade,
            ).build()
            .also { open += it }

    /** What a newer build leaves behind: the same file, at a schema version this build has never seen. */
    private fun markAsWrittenByANewerBuild() {
        SQLiteDatabase
            .openDatabase(context.getDatabasePath(dbName).path, null, SQLiteDatabase.OPEN_READWRITE)
            .use { it.version = NEWER_VERSION }
    }

    private fun rowCount(
        database: TempoDatabase,
        table: String,
    ): Int =
        database.openHelper.readableDatabase.query("SELECT COUNT(*) FROM $table").use {
            it.moveToFirst()
            it.getInt(0)
        }

    private fun scalar(
        raw: SQLiteDatabase,
        sql: String,
    ): Int =
        raw.rawQuery(sql, null).use {
            it.moveToFirst()
            it.getInt(0)
        }

    /** The seeded Inbox, found by its well-known uid: no reserved local id identifies it (#326). */
    private suspend fun inboxOrNull(database: TempoDatabase) =
        database.categoryDao().getAllCategoriesSync().singleOrNull { it.uid == DEFAULT_INBOX_UID }

    private suspend fun inboxOf(database: TempoDatabase) = inboxOrNull(database)!!

    private companion object {
        const val NEWER_VERSION = 99
    }
}
