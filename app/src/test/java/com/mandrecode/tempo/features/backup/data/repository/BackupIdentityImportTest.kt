package com.mandrecode.tempo.features.backup.data.repository

import com.google.common.truth.Truth.assertThat
import com.mandrecode.tempo.core.data.entity.CategoryEntity
import com.mandrecode.tempo.core.data.entity.HabitChainEntity
import com.mandrecode.tempo.core.data.entity.HabitEntity
import com.mandrecode.tempo.core.data.entity.TaskEntity
import com.mandrecode.tempo.core.domain.model.DEFAULT_INBOX_UID
import com.mandrecode.tempo.features.backup.data.mapper.toDto
import com.mandrecode.tempo.features.backup.domain.model.ImportMode
import com.mandrecode.tempo.features.backup.domain.model.ImportOutcome
import com.mandrecode.tempo.infrastructure.security.BackupEncryptionService
import io.mockk.coEvery
import io.mockk.coVerify
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import org.junit.Test

/** Backup schema v2 (#283): records keep their identity through export, restore and merge. */
class BackupIdentityImportTest : BackupRepositoryTestBase() {
    @Test
    fun `frozen v2 fixture restores every record under its identity`() =
        runTest {
            val envelope = BackupEncryptionService().encrypt(readFixture("/backup/v2-backup.json"), testPassphrase)
            val envelopeJson = testJsonFormat.encodeToString(envelope.toDto())
            val categories = mutableListOf<CategoryEntity>()
            val tasks = mutableListOf<TaskEntity>()
            val habits = mutableListOf<HabitEntity>()
            coEvery { categoryDao.insertCategory(capture(categories)) } returns 0
            coEvery { taskDao.insertTask(capture(tasks)) } returns 0
            coEvery { habitDao.insertHabit(capture(habits)) } returns 0

            val outcome = repository.importFromJson(envelopeJson, ImportMode.REPLACE, testPassphrase)

            assertThat(outcome).isInstanceOf(ImportOutcome.Success::class.java)
            val work = categories.single { it.name == "Work" }
            assertThat(work.uid).isEqualTo("0195a1b2-c3d4-7e5f-8a6b-7c8d9e0f1a2b")
            assertThat(work.createdAt).isEqualTo(1767312000000L)
            assertThat(work.updatedAt).isEqualTo(1767398400000L)
            // The Inbox keeps the well-known uid without any legacy id mapping.
            assertThat(categories.single { it.isDefault }.uid).isEqualTo(DEFAULT_INBOX_UID)
            assertThat(tasks.map { it.uid })
                .containsExactly("0195a1b2-c3d4-7e5f-8a6b-7c8d9e0f1a2d", "0195a1b2-c3d4-7e5f-8a6b-7c8d9e0f1a2e")
            assertThat(tasks.single { it.title == "Report" }.updatedAt).isEqualTo(1767484800000L)
            assertThat(habits.single().uid).isEqualTo("0195a1b2-c3d4-7e5f-8a6b-7c8d9e0f1a2f")
        }

    @Test
    fun `export carries each record's identity through a replace-import`() =
        runTest {
            stubLocalData()
            val identified =
                localCategories().mapIndexed { index, category ->
                    category.copy(
                        uid = "0195a1b2-c3d4-7e5f-8a6b-00000000000$index",
                        createdAt = 1_000L,
                        updatedAt = 2_000L,
                    )
                }
            coEvery { categoryDao.getAllCategoriesSync() } returns identified
            val json = repository.exportEncrypted(testPassphrase)
            val inserted = mutableListOf<CategoryEntity>()
            coEvery { categoryDao.insertCategory(capture(inserted)) } returns 0

            repository.importFromJson(json, ImportMode.REPLACE, testPassphrase)

            assertThat(inserted).containsExactlyElementsIn(identified)
        }

    @Test
    fun `a fresh install's Inbox survives a v1 export and replace-import with its identity`() =
        runTest {
            stubLocalData()
            // Since #326 a freshly seeded Inbox has an ordinary id; only its uid marks it.
            coEvery { categoryDao.getAllCategoriesSync() } returns
                listOf(
                    CategoryEntity(id = 7, name = "Inbox", icon = "inbox", isDefault = true, uid = DEFAULT_INBOX_UID),
                    CategoryEntity(id = 1, name = "Work"),
                )
            coEvery { taskDao.getAllTasksSync() } returns
                listOf(TaskEntity(id = 3, title = "T", description = "", categoryId = 7))
            coEvery { sectionDao.getAllSectionsSync() } returns emptyList()
            val json = repository.exportEncrypted(testPassphrase)
            val categories = mutableListOf<CategoryEntity>()
            val tasks = mutableListOf<TaskEntity>()
            coEvery { categoryDao.insertCategory(capture(categories)) } returns 0
            coEvery { taskDao.insertTask(capture(tasks)) } returns 0

            repository.importFromJson(json, ImportMode.REPLACE, testPassphrase)

            val inbox = categories.single { it.isDefault }
            assertThat(inbox.uid).isEqualTo(DEFAULT_INBOX_UID)
            assertThat(tasks.single().categoryId).isEqualTo(inbox.id)
        }

    @Test
    fun `chains are exported and restored with their identity`() =
        runTest {
            stubLocalData()
            val chain = localChains().single().copy(uid = CHAIN_UID, createdAt = 1_000L, updatedAt = 2_000L)
            coEvery { habitChainDao.getAllHabitChainsSync() } returns listOf(chain)
            val json = repository.exportEncrypted(testPassphrase)
            val inserted = mutableListOf<HabitChainEntity>()
            coEvery { habitChainDao.insertHabitChain(capture(inserted)) } returns 0

            repository.importFromJson(json, ImportMode.REPLACE, testPassphrase)

            assertThat(inserted.single().uid).isEqualTo(CHAIN_UID)
            assertThat(inserted.single().updatedAt).isEqualTo(2_000L)
        }

    @Test
    fun `merging a chain renamed on the device matches it by identity`() =
        runTest {
            stubLocalData()
            coEvery { habitChainDao.getAllHabitChainsSync() } returns
                listOf(localChains().single().copy(uid = CHAIN_UID, title = "Renamed here"))
            val json =
                """
                {"schemaVersion":2,"habitChains":[
                  {"id":1,"title":"Morning","createdDate":"2026-01-01T08:00","uid":"$CHAIN_UID"}
                ]}
                """.trimIndent()

            val outcome =
                repository.importFromJson(encryptedJsonOf(json), ImportMode.MERGE, testPassphrase)
                    as ImportOutcome.Success

            assertThat(outcome.summary.habitChains.skipped).isEqualTo(1)
            assertThat(outcome.summary.totalConflicts).isEqualTo(0)
            coVerify(exactly = 0) { habitChainDao.insertHabitChain(any()) }
        }

    @Test
    fun `a v2 category with the legacy Inbox id and an unusable uid does not take the Inbox uid`() =
        runTest {
            val inserted = mutableListOf<CategoryEntity>()
            coEvery { categoryDao.insertCategory(capture(inserted)) } returns 0
            val json =
                """
                {"schemaVersion":2,"categories":[
                  {"id":4,"name":"Inbox","isDefault":true,"uid":"$DEFAULT_INBOX_UID"},
                  {"id":-1,"name":"Edited","uid":"not-a-uuid"}
                ]}
                """.trimIndent()

            val outcome = repository.importFromJson(encryptedJsonOf(json), ImportMode.REPLACE, testPassphrase)

            assertThat(outcome).isInstanceOf(ImportOutcome.Success::class.java)
            assertThat(inserted.single { it.name == "Edited" }.uid).isEmpty()
            assertThat(inserted.single { it.name == "Inbox" }.uid).isEqualTo(DEFAULT_INBOX_UID)
        }
}

private const val CHAIN_UID = "0195a1b2-c3d4-7e5f-8a6b-0000000000cc"
