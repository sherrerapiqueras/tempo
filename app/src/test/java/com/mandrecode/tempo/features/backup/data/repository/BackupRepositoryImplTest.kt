package com.mandrecode.tempo.features.backup.data.repository

import android.content.Context
import androidx.room.withTransaction
import com.google.common.truth.Truth.assertThat
import com.mandrecode.tempo.core.data.entity.CategoryEntity
import com.mandrecode.tempo.core.data.entity.HabitChainEntity
import com.mandrecode.tempo.core.data.entity.HabitChainMemberEntity
import com.mandrecode.tempo.core.data.entity.HabitEntity
import com.mandrecode.tempo.core.data.entity.SavedSessionEntity
import com.mandrecode.tempo.core.data.entity.TaskEntity
import com.mandrecode.tempo.core.data.entity.TaskSectionEntity
import com.mandrecode.tempo.core.data.local.TempoDatabase
import com.mandrecode.tempo.core.data.local.dao.CategoryDao
import com.mandrecode.tempo.core.data.local.dao.HabitChainDao
import com.mandrecode.tempo.core.data.local.dao.HabitChainMemberDao
import com.mandrecode.tempo.core.data.local.dao.HabitDao
import com.mandrecode.tempo.core.data.local.dao.SavedSessionDao
import com.mandrecode.tempo.core.data.local.dao.TaskDao
import com.mandrecode.tempo.core.data.local.dao.TaskSectionDao
import com.mandrecode.tempo.core.domain.model.DayOfWeek
import com.mandrecode.tempo.core.domain.model.Periodicity
import com.mandrecode.tempo.core.domain.model.Priority
import com.mandrecode.tempo.core.domain.model.ThemeMode
import com.mandrecode.tempo.core.domain.repository.DailyFocusActivityRepository
import com.mandrecode.tempo.features.backup.data.BackupSettingsDataSource
import com.mandrecode.tempo.features.backup.data.mapper.toDto
import com.mandrecode.tempo.features.backup.domain.model.BackupSettings
import com.mandrecode.tempo.features.backup.domain.model.ImportMode
import com.mandrecode.tempo.features.backup.domain.model.ImportOutcome
import com.mandrecode.tempo.features.backup.domain.util.BackupPayloadValidator
import com.mandrecode.tempo.features.backup.domain.util.MergePlanner
import com.mandrecode.tempo.infrastructure.security.BackupEncryptionService
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Before
import org.junit.Test

class BackupRepositoryImplTest {
    private lateinit var taskDao: TaskDao
    private lateinit var categoryDao: CategoryDao
    private lateinit var habitDao: HabitDao
    private lateinit var habitChainDao: HabitChainDao
    private lateinit var memberDao: HabitChainMemberDao
    private lateinit var sectionDao: TaskSectionDao
    private lateinit var savedSessionDao: SavedSessionDao
    private lateinit var database: TempoDatabase
    private lateinit var context: Context
    private lateinit var settingsDataSource: BackupSettingsDataSource
    private val dailyFocusActivityRepository = mockk<DailyFocusActivityRepository>(relaxed = true)
    private lateinit var repository: BackupRepositoryImpl

    @Before
    fun setUp() {
        taskDao = mockk(relaxed = true)
        categoryDao = mockk(relaxed = true)
        habitDao = mockk(relaxed = true)
        habitChainDao = mockk(relaxed = true)
        memberDao = mockk(relaxed = true)
        sectionDao = mockk(relaxed = true)
        coEvery { sectionDao.getAllSectionsSync() } returns emptyList()
        savedSessionDao = mockk(relaxed = true)
        coEvery { savedSessionDao.getAllSync() } returns emptyList()
        context = mockk(relaxed = true)
        every { context.getString(any()) } returns "Inbox"
        settingsDataSource = mockk(relaxed = true)
        coEvery { settingsDataSource.snapshot() } returns backupSettings()
        database = mockk(relaxed = true)
        every { database.taskDao() } returns taskDao
        every { database.categoryDao() } returns categoryDao
        every { database.habitDao() } returns habitDao
        every { database.habitChainDao() } returns habitChainDao
        every { database.habitChainMemberDao() } returns memberDao
        every { database.taskSectionDao() } returns sectionDao
        every { database.savedSessionDao() } returns savedSessionDao
        mockkStatic("androidx.room.RoomDatabaseKt")
        @Suppress("UNCHECKED_CAST")
        coEvery { database.withTransaction(any<suspend () -> Any?>()) } coAnswers {
            (args[1] as (suspend () -> Any?)).invoke()
        }
        repository =
            BackupRepositoryImpl(
                database,
                BackupPayloadValidator(),
                MergePlanner(),
                settingsDataSource,
                dailyFocusActivityRepository,
                BackupEncryptionService(),
                context,
            )
    }

    private val testPassphrase = "correct horse battery staple".toCharArray()
    private val testJsonFormat = Json { ignoreUnknownKeys = true }

    /**
     * Every valid backup is an encrypted envelope now — there's no legacy plaintext format to
     * fall back to — so tests exercising the plaintext parsing/validation logic underneath the
     * encryption layer wrap their payload through this instead of handing raw JSON to
     * [BackupRepositoryImpl.importFromJson] directly.
     */
    private fun encryptedJsonOf(plaintext: String): String {
        val envelope = BackupEncryptionService().encrypt(plaintext, testPassphrase)
        return testJsonFormat.encodeToString(envelope.toDto())
    }

    @After
    fun tearDown() {
        unmockkStatic("androidx.room.RoomDatabaseKt")
    }

    @Test
    fun `newer schema version is rejected without touching the database`() =
        runTest {
            val outcome =
                repository.importFromJson(
                    encryptedJsonOf("""{"schemaVersion":99}"""),
                    ImportMode.MERGE,
                    testPassphrase,
                )

            assertThat(outcome)
                .isEqualTo(ImportOutcome.UnsupportedVersion(fileVersion = 99, maxSupported = 1))
            coVerify(exactly = 0) { taskDao.insertTask(any()) }
            coVerify(exactly = 0) { taskDao.deleteAllTasks() }
        }

    @Test
    fun `non-json content is reported as corrupt`() =
        runTest {
            assertThat(repository.importFromJson("definitely not json", ImportMode.MERGE))
                .isEqualTo(ImportOutcome.CorruptFile)
        }

    @Test
    fun `schema version below 1 is reported as corrupt`() =
        runTest {
            val outcome =
                repository.importFromJson(
                    encryptedJsonOf("""{"schemaVersion":0}"""),
                    ImportMode.REPLACE,
                    testPassphrase,
                )

            assertThat(outcome).isEqualTo(ImportOutcome.CorruptFile)
            coVerify(exactly = 0) { taskDao.deleteAllTasks() }
        }

    @Test
    fun `payload with missing schema version is reported as corrupt`() =
        runTest {
            val outcome =
                repository.importFromJson(encryptedJsonOf("""{"foo":1}"""), ImportMode.MERGE, testPassphrase)

            assertThat(outcome).isEqualTo(ImportOutcome.CorruptFile)
        }

    @Test
    fun `payload with unknown enum value is reported as corrupt`() =
        runTest {
            val json =
                """
                {"schemaVersion":1,"categories":[{"id":1,"name":"Work"}],
                 "tasks":[{"id":1,"title":"T","categoryId":1,"priority":"BANANAS"}]}
                """.trimIndent()

            val outcome = repository.importFromJson(encryptedJsonOf(json), ImportMode.MERGE, testPassphrase)

            assertThat(outcome).isEqualTo(ImportOutcome.CorruptFile)
        }

    @Test
    fun `payload failing referential validation leaves the database untouched`() =
        runTest {
            val json =
                """
                {"schemaVersion":1,"categories":[],
                 "tasks":[{"id":1,"title":"Orphan","categoryId":42}]}
                """.trimIndent()

            val outcome = repository.importFromJson(encryptedJsonOf(json), ImportMode.REPLACE, testPassphrase)

            assertThat(outcome).isInstanceOf(ImportOutcome.ValidationFailed::class.java)
            coVerify(exactly = 0) { taskDao.deleteAllTasks() }
            coVerify(exactly = 0) { taskDao.insertTask(any()) }
        }

    @Test
    fun `merging an export of the same data inserts nothing`() =
        runTest {
            stubLocalData()
            val json = repository.exportEncrypted(testPassphrase)

            val outcome = repository.importFromJson(json, ImportMode.MERGE, testPassphrase) as ImportOutcome.Success

            assertThat(outcome.summary.totalImported).isEqualTo(0)
            assertThat(outcome.summary.totalConflicts).isEqualTo(0)
            assertThat(outcome.summary.totalSkipped).isEqualTo(5)
            coVerify(exactly = 0) { taskDao.insertTask(any()) }
            coVerify(exactly = 0) { categoryDao.insertCategory(any()) }
            coVerify(exactly = 0) { habitDao.insertHabit(any()) }
            coVerify(exactly = 0) { habitChainDao.insertHabitChain(any()) }
            coVerify(exactly = 0) { sectionDao.insertSection(any()) }
        }

    @Test
    fun `export then replace-import restores identical rows`() =
        runTest {
            stubLocalData()
            val json = repository.exportEncrypted(testPassphrase)

            val insertedCategories = mutableListOf<CategoryEntity>()
            val insertedTasks = mutableListOf<TaskEntity>()
            val insertedHabits = mutableListOf<HabitEntity>()
            val insertedChains = mutableListOf<HabitChainEntity>()
            val insertedMembers = mutableListOf<List<HabitChainMemberEntity>>()
            coEvery { categoryDao.insertCategory(capture(insertedCategories)) } returns 0
            coEvery { taskDao.insertTask(capture(insertedTasks)) } returns 0
            coEvery { habitDao.insertHabit(capture(insertedHabits)) } returns 0
            coEvery { habitChainDao.insertHabitChain(capture(insertedChains)) } returns 0
            coEvery { memberDao.insertMembers(capture(insertedMembers)) } returns Unit

            val outcome = repository.importFromJson(json, ImportMode.REPLACE, testPassphrase)

            assertThat(outcome).isInstanceOf(ImportOutcome.Success::class.java)
            coVerify { memberDao.deleteAllMembers() }
            coVerify { taskDao.deleteAllTasks() }
            coVerify { categoryDao.deleteAllCategories() }
            assertThat(insertedCategories).containsExactlyElementsIn(localCategories())
            assertThat(insertedTasks).containsExactlyElementsIn(localTasks())
            assertThat(insertedHabits).containsExactlyElementsIn(localHabits())
            assertThat(insertedChains).containsExactlyElementsIn(localChains())
            assertThat(insertedMembers.single()).containsExactlyElementsIn(localMembers())
            coVerify { sectionDao.deleteAll() }
            coVerify { sectionDao.insertSection(localSections().single()) }
        }

    @Test
    fun `frozen v1 fixture still imports correctly once wrapped in an encrypted envelope`() =
        runTest {
            val plaintextJson = readFixture("/backup/v1-backup.json")
            val envelope = BackupEncryptionService().encrypt(plaintextJson, testPassphrase)
            val envelopeJson = testJsonFormat.encodeToString(envelope.toDto())
            val insertedTasks = mutableListOf<TaskEntity>()
            coEvery { taskDao.insertTask(capture(insertedTasks)) } returns 0

            assertThat(repository.isEncryptedBackup(envelopeJson)).isTrue()
            assertThat(repository.isEncryptedBackup(plaintextJson)).isFalse()

            val outcome =
                repository.importFromJson(envelopeJson, ImportMode.REPLACE, testPassphrase) as ImportOutcome.Success

            assertThat(outcome.summary.categories.imported).isEqualTo(2)
            assertThat(outcome.summary.tasks.imported).isEqualTo(2)
            assertThat(outcome.summary.habits.imported).isEqualTo(1)
            assertThat(outcome.summary.habitChains.imported).isEqualTo(1)
            val report = insertedTasks.first { it.title == "Report" }
            assertThat(report.priority).isEqualTo(Priority.HIGH)
            assertThat(report.periodicity).isEqualTo(Periodicity.WEEKLY)
            assertThat(report.reminderDate).isEqualTo(LocalDateTime(2026, 8, 1, 9, 0))
            assertThat(report.repeatDays).containsExactly(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY)
            val sub = insertedTasks.first { it.title == "Sub" }
            assertThat(sub.parentTaskId).isEqualTo(1L)
            // Written before sections existed (#61): everything lands unsectioned.
            assertThat(insertedTasks.map { it.sectionId }).containsExactly(null, null)
            coVerify(exactly = 0) { sectionDao.insertSection(any()) }
        }

    @Test
    fun `wrong passphrase on an encrypted import is reported distinctly`() =
        runTest {
            val json = repository.exportEncrypted(testPassphrase)

            val outcome = repository.importFromJson(json, ImportMode.MERGE, "wrong passphrase".toCharArray())

            assertThat(outcome).isEqualTo(ImportOutcome.WrongPassphrase)
        }

    @Test
    fun `encrypted envelope with malformed base64 fields is reported as corrupt, not thrown`() =
        runTest {
            // Well-formed JSON (right field names/types) but the base64 payloads are garbage —
            // must not crash the import, just report CorruptFile.
            val json =
                """
                {"encryptionVersion":1,"kdf":"PBKDF2WithHmacSHA256","iterations":200000,
                 "salt":"not-valid-base64!!!","iv":"also-not-base64!!!","ciphertext":"nope!!!"}
                """.trimIndent()

            val outcome = repository.importFromJson(json, ImportMode.MERGE, testPassphrase)

            assertThat(outcome).isEqualTo(ImportOutcome.CorruptFile)
        }

    @Test
    fun `envelope with an unsupported encryption version is not treated as an envelope`() =
        runTest {
            // A future, newer envelope version this build doesn't understand must fall back to
            // "not decryptable" rather than risk decrypting a payload laid out differently than
            // this code assumes.
            val json =
                """
                {"encryptionVersion":2,"kdf":"PBKDF2WithHmacSHA256","iterations":200000,
                 "salt":"c2FsdA==","iv":"aXY=","ciphertext":"Y2lwaGVy"}
                """.trimIndent()

            assertThat(repository.isEncryptedBackup(json)).isFalse()
            val outcome = repository.importFromJson(json, ImportMode.MERGE, testPassphrase)
            assertThat(outcome).isEqualTo(ImportOutcome.CorruptFile)
        }

    @Test
    fun `replace payload without a default but containing inbox id marks it default`() =
        runTest {
            val json =
                """
                {"schemaVersion":1,"categories":[{"id":-1,"name":"Inbox"}]}
                """.trimIndent()

            repository.importFromJson(encryptedJsonOf(json), ImportMode.REPLACE, testPassphrase)

            coVerify { categoryDao.setDefault(-1L) }
        }

    @Test
    fun `replace payload without any default category re-seeds inbox`() =
        runTest {
            val inserted = mutableListOf<CategoryEntity>()
            coEvery { categoryDao.insertCategory(capture(inserted)) } returns 0
            val json =
                """
                {"schemaVersion":1,"categories":[{"id":1,"name":"Work"}]}
                """.trimIndent()

            val outcome =
                repository.importFromJson(
                    encryptedJsonOf(json),
                    ImportMode.REPLACE,
                    testPassphrase,
                ) as ImportOutcome.Success

            val inbox = inserted.first { it.id == -1L }
            assertThat(inbox.isDefault).isTrue()
            assertThat(inbox.icon).isEqualTo("inbox")
            // The re-seeded Inbox row counts as an imported category.
            assertThat(outcome.summary.categories.imported).isEqualTo(2)
        }

    @Test
    fun `replace payload with a foreign default still re-creates the inbox row without stealing default`() =
        runTest {
            val inserted = mutableListOf<CategoryEntity>()
            coEvery { categoryDao.insertCategory(capture(inserted)) } returns 0
            val json =
                """
                {"schemaVersion":1,"categories":[{"id":5,"name":"Work","isDefault":true}]}
                """.trimIndent()

            repository.importFromJson(encryptedJsonOf(json), ImportMode.REPLACE, testPassphrase)

            val inbox = inserted.first { it.id == -1L }
            assertThat(inbox.isDefault).isFalse()
            coVerify(exactly = 0) { categoryDao.setDefault(any()) }
        }

    @Test
    fun `merge remaps subtask parents and next-instance links to fresh ids`() =
        runTest {
            val insertedTasks = mutableListOf<TaskEntity>()
            var nextId = 100L
            coEvery { categoryDao.insertCategory(any()) } returns 10L
            coEvery { taskDao.insertTask(capture(insertedTasks)) } answers { nextId++ }
            val json =
                """
                {"schemaVersion":1,
                 "categories":[{"id":1,"name":"Work"}],
                 "tasks":[
                   {"id":1,"title":"Parent","categoryId":1,"nextInstanceId":2},
                   {"id":2,"title":"Next","categoryId":1},
                   {"id":3,"title":"Sub","categoryId":1,"parentTaskId":1}
                 ]}
                """.trimIndent()

            val outcome =
                repository.importFromJson(
                    encryptedJsonOf(json),
                    ImportMode.MERGE,
                    testPassphrase,
                ) as ImportOutcome.Success

            assertThat(outcome.summary.tasks.imported).isEqualTo(3)
            val parent = insertedTasks.first { it.title == "Parent" }
            assertThat(parent.categoryId).isEqualTo(10L)
            assertThat(parent.nextInstanceId).isNull()
            val sub = insertedTasks.first { it.title == "Sub" }
            assertThat(sub.parentTaskId).isEqualTo(100L)
            coVerify { taskDao.updateTaskNextInstanceId(100L, any()) }
        }

    @Test
    fun `export carries the current settings and replace-import applies them`() =
        runTest {
            stubLocalData()
            val json = repository.exportEncrypted(testPassphrase)

            repository.importFromJson(json, ImportMode.REPLACE, testPassphrase)

            io.mockk.verify { settingsDataSource.apply(backupSettings()) }
        }

    @Test
    fun `merge never applies the file settings`() =
        runTest {
            stubLocalData()
            val json = repository.exportEncrypted(testPassphrase)

            repository.importFromJson(json, ImportMode.MERGE, testPassphrase)

            io.mockk.verify(exactly = 0) { settingsDataSource.apply(any()) }
        }

    @Test
    fun `replace still reports success when applying settings throws`() =
        runTest {
            stubLocalData()
            val json = repository.exportEncrypted(testPassphrase)
            every { settingsDataSource.apply(any()) } throws IllegalStateException("prefs unavailable")

            val outcome = repository.importFromJson(json, ImportMode.REPLACE, testPassphrase)

            assertThat(outcome).isInstanceOf(ImportOutcome.Success::class.java)
        }

    @Test
    fun `replace of a file without a settings section leaves settings untouched`() =
        runTest {
            val json = """{"schemaVersion":1,"categories":[{"id":-1,"name":"Inbox","isDefault":true}]}"""

            repository.importFromJson(encryptedJsonOf(json), ImportMode.REPLACE, testPassphrase)

            io.mockk.verify(exactly = 0) { settingsDataSource.apply(any()) }
        }

    @Test
    fun `unknown extra json fields are tolerated on import`() =
        runTest {
            val json =
                """
                {"schemaVersion":1,"someFutureField":"ignored",
                 "categories":[{"id":1,"name":"Work","futureFlag":true}]}
                """.trimIndent()

            val outcome =
                repository.importFromJson(
                    encryptedJsonOf(json),
                    ImportMode.MERGE,
                    testPassphrase,
                ) as ImportOutcome.Success

            assertThat(outcome.summary.categories.imported).isEqualTo(1)
        }

    @Test
    fun `merge remaps sections and unsections tasks whose section did not come across`() =
        runTest {
            val insertedTasks = mutableListOf<TaskEntity>()
            val insertedSections = mutableListOf<TaskSectionEntity>()
            var nextId = 100L
            coEvery { categoryDao.insertCategory(any()) } returns 10L
            coEvery { sectionDao.insertSection(capture(insertedSections)) } returns 50L
            coEvery { taskDao.insertTask(capture(insertedTasks)) } answers { nextId++ }
            val json =
                """
                {"schemaVersion":1,
                 "categories":[{"id":1,"name":"S&V"}],
                 "taskSections":[{"id":5,"categoryId":1,"name":"Cat","sortOrder":0}],
                 "tasks":[
                   {"id":1,"title":"Buy litter","categoryId":1,"sectionId":5},
                   {"id":2,"title":"Stray","categoryId":1,"sectionId":99}
                 ]}
                """.trimIndent()

            repository.importFromJson(encryptedJsonOf(json), ImportMode.MERGE, testPassphrase)

            assertThat(insertedSections.single())
                .isEqualTo(TaskSectionEntity(categoryId = 10L, name = "Cat", sortOrder = 0))
            assertThat(insertedTasks.first { it.title == "Buy litter" }.sectionId).isEqualTo(50L)
            assertThat(insertedTasks.first { it.title == "Stray" }.sectionId).isNull()
        }

    @Test
    fun `merge into an existing category reuses its same-named section`() =
        runTest {
            stubLocalData()
            val insertedTasks = mutableListOf<TaskEntity>()
            coEvery { taskDao.insertTask(capture(insertedTasks)) } returns 100L
            val json =
                """
                {"schemaVersion":1,
                 "categories":[{"id":7,"name":"Work","color":"red"}],
                 "taskSections":[{"id":3,"categoryId":7,"name":"Q3"}],
                 "tasks":[{"id":1,"title":"Budget","categoryId":7,"sectionId":3}]}
                """.trimIndent()

            repository.importFromJson(encryptedJsonOf(json), ImportMode.MERGE, testPassphrase)

            coVerify(exactly = 0) { sectionDao.insertSection(any()) }
            assertThat(insertedTasks.single().sectionId).isEqualTo(4L)
        }

    private fun stubLocalData() {
        coEvery { categoryDao.getAllCategoriesSync() } returns localCategories()
        coEvery { taskDao.getAllTasksSync() } returns localTasks()
        coEvery { habitDao.getAllHabitsSync() } returns localHabits()
        coEvery { habitChainDao.getAllHabitChainsSync() } returns localChains()
        coEvery { memberDao.getAllMembersSync() } returns localMembers()
        coEvery { sectionDao.getAllSectionsSync() } returns localSections()
    }

    private fun localCategories() =
        listOf(
            CategoryEntity(id = -1, name = "Inbox", icon = "inbox", isDefault = true, sortOrder = -1),
            CategoryEntity(id = 1, name = "Work", color = "red"),
        )

    private fun localSections() = listOf(TaskSectionEntity(id = 4, categoryId = 1, name = "Q3", sortOrder = 0))

    private fun localTasks() =
        listOf(
            TaskEntity(
                id = 1,
                title = "Report",
                description = "Quarterly numbers",
                categoryId = 1,
                priority = Priority.HIGH,
                reminderDate = LocalDateTime(2026, 8, 1, 9, 0),
                periodicity = Periodicity.WEEKLY,
                repeatDays = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY),
                sectionId = 4,
            ),
        )

    private fun localHabits() =
        listOf(
            HabitEntity(
                id = 1,
                title = "Run",
                description = "",
                createdDate = LocalDateTime(2026, 1, 1, 8, 0),
                completionHistory = "2026-01-02",
            ),
        )

    private fun localChains() =
        listOf(
            HabitChainEntity(
                id = 1,
                title = "Morning",
                createdDate = LocalDateTime(2026, 1, 1, 8, 0),
            ),
        )

    private fun localMembers() = listOf(HabitChainMemberEntity(chainId = 1, habitId = 1, sortOrder = 0))

    private fun backupSettings() =
        BackupSettings(
            themeMode = ThemeMode.DARK,
            useTempoColors = true,
            autoRemoveCompletedTasks = true,
            completedTaskRetentionDays = 30,
        )

    private fun readFixture(path: String): String =
        checkNotNull(javaClass.getResourceAsStream(path)) { "Missing fixture $path" }
            .bufferedReader()
            .use { it.readText() }

    private val linkedSession =
        SavedSessionEntity(id = 3, title = "Study", focusMinutes = 50, breakMinutes = 10, rounds = 4, taskId = 1)
    private val looseSession = SavedSessionEntity(id = 5, title = "Read", focusMinutes = 25, breakMinutes = 5)

    @Test
    fun `export then replace-import restores saved sessions with their ids and links`() =
        runTest {
            stubLocalData()
            coEvery { savedSessionDao.getAllSync() } returns listOf(linkedSession, looseSession)
            val json = repository.exportEncrypted(testPassphrase)
            val inserted = mutableListOf<SavedSessionEntity>()
            coEvery { savedSessionDao.insert(capture(inserted)) } returns 0

            val outcome = repository.importFromJson(json, ImportMode.REPLACE, testPassphrase)

            assertThat(outcome).isInstanceOf(ImportOutcome.Success::class.java)
            coVerify { savedSessionDao.deleteAll() }
            assertThat(inserted).containsExactly(linkedSession, looseSession).inOrder()
        }

    @Test
    fun `a restored session whose task the file does not carry comes back unlinked`() =
        runTest {
            stubLocalData()
            coEvery { savedSessionDao.getAllSync() } returns listOf(linkedSession.copy(taskId = 404))
            val json = repository.exportEncrypted(testPassphrase)
            val inserted = mutableListOf<SavedSessionEntity>()
            coEvery { savedSessionDao.insert(capture(inserted)) } returns 0

            repository.importFromJson(json, ImportMode.REPLACE, testPassphrase)

            assertThat(inserted.single().taskId).isNull()
        }

    @Test
    fun `merging an export of the same data adds no saved session twice`() =
        runTest {
            stubLocalData()
            coEvery { savedSessionDao.getAllSync() } returns listOf(linkedSession, looseSession)
            val json = repository.exportEncrypted(testPassphrase)

            repository.importFromJson(json, ImportMode.MERGE, testPassphrase)

            coVerify(exactly = 0) { savedSessionDao.insert(any()) }
        }

    @Test
    fun `merging adds the sessions this device lacks, once, linked to the matching local task`() =
        runTest {
            stubLocalData()
            coEvery { savedSessionDao.getAllSync() } returns
                listOf(linkedSession, linkedSession.copy(id = 9), looseSession)
            val json = repository.exportEncrypted(testPassphrase)
            coEvery { savedSessionDao.getAllSync() } returns listOf(looseSession)
            val inserted = mutableListOf<SavedSessionEntity>()
            coEvery { savedSessionDao.insert(capture(inserted)) } returns 0

            repository.importFromJson(json, ImportMode.MERGE, testPassphrase)

            // The incoming task matches the local one, so the link follows it there; the second
            // copy in the file is the same session and is not added again.
            assertThat(inserted).containsExactly(linkedSession.copy(id = 0, taskId = 1))
        }
}
