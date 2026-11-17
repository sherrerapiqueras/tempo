package com.mandrecode.tempo.features.backup.data.repository

import android.content.Context
import androidx.room.withTransaction
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
import com.mandrecode.tempo.features.backup.domain.util.BackupPayloadValidator
import com.mandrecode.tempo.features.backup.domain.util.MergePlanner
import com.mandrecode.tempo.infrastructure.security.BackupEncryptionService
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Before

/** Shared mocks, repository and fixtures for the backup repository tests. */
abstract class BackupRepositoryTestBase {
    protected lateinit var taskDao: TaskDao
    protected lateinit var categoryDao: CategoryDao
    protected lateinit var habitDao: HabitDao
    protected lateinit var habitChainDao: HabitChainDao
    protected lateinit var memberDao: HabitChainMemberDao
    protected lateinit var sectionDao: TaskSectionDao
    protected lateinit var savedSessionDao: SavedSessionDao
    protected lateinit var database: TempoDatabase
    protected lateinit var context: Context
    protected lateinit var settingsDataSource: BackupSettingsDataSource
    protected val dailyFocusActivityRepository = mockk<DailyFocusActivityRepository>(relaxed = true)
    protected lateinit var repository: BackupRepositoryImpl

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

    protected val testPassphrase = "correct horse battery staple".toCharArray()
    protected val testJsonFormat = Json { ignoreUnknownKeys = true }

    /**
     * Every valid backup is an encrypted envelope now — there's no legacy plaintext format to
     * fall back to — so tests exercising the plaintext parsing/validation logic underneath the
     * encryption layer wrap their payload through this instead of handing raw JSON to
     * [BackupRepositoryImpl.importFromJson] directly.
     */
    protected fun encryptedJsonOf(plaintext: String): String {
        val envelope = BackupEncryptionService().encrypt(plaintext, testPassphrase)
        return testJsonFormat.encodeToString(envelope.toDto())
    }

    @After
    fun tearDown() {
        unmockkStatic("androidx.room.RoomDatabaseKt")
    }

    protected fun stubLocalData() {
        coEvery { categoryDao.getAllCategoriesSync() } returns localCategories()
        coEvery { taskDao.getAllTasksSync() } returns localTasks()
        coEvery { habitDao.getAllHabitsSync() } returns localHabits()
        coEvery { habitChainDao.getAllHabitChainsSync() } returns localChains()
        coEvery { memberDao.getAllMembersSync() } returns localMembers()
        coEvery { sectionDao.getAllSectionsSync() } returns localSections()
    }

    protected fun localCategories() =
        listOf(
            CategoryEntity(id = -1, name = "Inbox", icon = "inbox", isDefault = true, sortOrder = -1),
            CategoryEntity(id = 1, name = "Work", color = "red"),
        )

    protected fun localSections() = listOf(TaskSectionEntity(id = 4, categoryId = 1, name = "Q3", sortOrder = 0))

    protected fun localTasks() =
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

    protected fun localHabits() =
        listOf(
            HabitEntity(
                id = 1,
                title = "Run",
                description = "",
                createdDate = LocalDateTime(2026, 1, 1, 8, 0),
                completionHistory = "2026-01-02",
            ),
        )

    protected fun localChains() =
        listOf(
            HabitChainEntity(
                id = 1,
                title = "Morning",
                createdDate = LocalDateTime(2026, 1, 1, 8, 0),
            ),
        )

    protected fun localMembers() = listOf(HabitChainMemberEntity(chainId = 1, habitId = 1, sortOrder = 0))

    protected fun backupSettings() =
        BackupSettings(
            themeMode = ThemeMode.DARK,
            useTempoColors = true,
            autoRemoveCompletedTasks = true,
            completedTaskRetentionDays = 30,
        )

    protected fun readFixture(path: String): String =
        checkNotNull(javaClass.getResourceAsStream(path)) { "Missing fixture $path" }
            .bufferedReader()
            .use { it.readText() }

    protected val study =
        SavedSessionEntity(
            id = 3,
            title = "Study",
            focusMinutes = 50,
            breakMinutes = 10,
            rounds = 4,
            lastUsedAt = 2_000,
        )
    protected val reading =
        SavedSessionEntity(
            id = 5,
            title = "Read",
            focusMinutes = 25,
            breakMinutes = 5,
            lastUsedAt = 1_000,
        )
}
