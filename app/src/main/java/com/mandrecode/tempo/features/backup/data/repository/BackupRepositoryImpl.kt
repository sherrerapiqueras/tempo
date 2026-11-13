package com.mandrecode.tempo.features.backup.data.repository

import android.content.Context
import androidx.room.withTransaction
import com.mandrecode.tempo.BuildConfig
import com.mandrecode.tempo.R
import com.mandrecode.tempo.core.data.entity.CategoryEntity
import com.mandrecode.tempo.core.data.entity.DEFAULT_INBOX_CATEGORY_ENTITY
import com.mandrecode.tempo.core.data.entity.HabitChainEntity
import com.mandrecode.tempo.core.data.entity.HabitChainMemberEntity
import com.mandrecode.tempo.core.data.local.TempoDatabase
import com.mandrecode.tempo.core.data.local.dao.SavedSessionDao
import com.mandrecode.tempo.core.domain.repository.DailyFocusActivityRepository
import com.mandrecode.tempo.features.backup.data.BackupSettingsDataSource
import com.mandrecode.tempo.features.backup.data.mapper.toDomain
import com.mandrecode.tempo.features.backup.data.mapper.toDto
import com.mandrecode.tempo.features.backup.data.mapper.toEnvelope
import com.mandrecode.tempo.features.backup.data.model.BackupEncryptedEnvelopeDto
import com.mandrecode.tempo.features.backup.data.model.BackupEnvelopeDto
import com.mandrecode.tempo.features.backup.data.model.BackupFileDto
import com.mandrecode.tempo.features.backup.data.model.ENCRYPTION_ENVELOPE_VERSION
import com.mandrecode.tempo.features.backup.domain.model.BackupData
import com.mandrecode.tempo.features.backup.domain.model.BackupSettings
import com.mandrecode.tempo.features.backup.domain.model.ChainMembership
import com.mandrecode.tempo.features.backup.domain.model.ImportCounts
import com.mandrecode.tempo.features.backup.domain.model.ImportMode
import com.mandrecode.tempo.features.backup.domain.model.ImportOutcome
import com.mandrecode.tempo.features.backup.domain.model.ImportSummary
import com.mandrecode.tempo.features.backup.domain.model.MergePlan
import com.mandrecode.tempo.features.backup.domain.repository.BackupRepository
import com.mandrecode.tempo.features.backup.domain.util.BackupPayloadValidator
import com.mandrecode.tempo.features.backup.domain.util.MergePlanner
import com.mandrecode.tempo.features.focus.data.mapper.toDomain
import com.mandrecode.tempo.features.focus.data.mapper.toEntity
import com.mandrecode.tempo.features.focus.domain.model.SavedSession
import com.mandrecode.tempo.features.routines.data.mapper.toDomain
import com.mandrecode.tempo.features.routines.data.mapper.toEntity
import com.mandrecode.tempo.features.routines.domain.model.HabitChain
import com.mandrecode.tempo.features.tasks.data.mapper.toDomain
import com.mandrecode.tempo.features.tasks.data.mapper.toEntity
import com.mandrecode.tempo.features.tasks.domain.model.Category
import com.mandrecode.tempo.infrastructure.security.BackupEncryptionService
import com.mandrecode.tempo.infrastructure.security.DecryptResult
import com.mandrecode.tempo.infrastructure.security.EncryptedEnvelope
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import kotlin.time.Clock

/** Highest backup schema version this app can read and the version it writes. */
const val BACKUP_SCHEMA_VERSION = 1

class BackupRepositoryImpl
    @Inject
    constructor(
        private val database: TempoDatabase,
        private val validator: BackupPayloadValidator,
        private val mergePlanner: MergePlanner,
        private val settingsDataSource: BackupSettingsDataSource,
        private val dailyFocusActivityRepository: DailyFocusActivityRepository,
        private val encryptionService: BackupEncryptionService,
        @param:ApplicationContext private val context: Context,
    ) : BackupRepository {
        private val jsonFormat =
            Json {
                ignoreUnknownKeys = true
                encodeDefaults = true
            }

        override suspend fun exportEncrypted(passphrase: CharArray): String {
            val data =
                database
                    .withTransaction { readAll() }
                    .copy(settings = settingsDataSource.snapshot())
            val exportedAt = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
            val plaintextJson =
                jsonFormat.encodeToString(
                    data.toDto(
                        schemaVersion = BACKUP_SCHEMA_VERSION,
                        appVersion = BuildConfig.VERSION_NAME,
                        exportedAt = exportedAt,
                    ),
                )
            val envelope = encryptionService.encrypt(plaintextJson, passphrase)
            return jsonFormat.encodeToString(envelope.toDto())
        }

        // Also requires the base64 fields to decode, not just the JSON shape/version — a
        // malformed envelope would otherwise prompt for a passphrase before failing as
        // CorruptFile anyway, an unnecessary step for a file that can never succeed.
        override fun isEncryptedBackup(content: String): Boolean =
            decodeEnvelopeOrNull(content, jsonFormat)?.let { toEncryptedEnvelopeOrNull(it) } != null

        override suspend fun importFromJson(
            content: String,
            mode: ImportMode,
            passphrase: CharArray?,
        ): ImportOutcome =
            when (val resolution = resolvePlaintext(content, passphrase, jsonFormat, encryptionService)) {
                is PlaintextResolution.Failed -> resolution.outcome
                is PlaintextResolution.Ready -> importDecodedPlaintext(resolution.json, mode)
            }

        private suspend fun importDecodedPlaintext(
            plaintextJson: String,
            mode: ImportMode,
        ): ImportOutcome {
            val fileVersion = decodeSchemaVersion(plaintextJson, jsonFormat)
            return when {
                // Versions below 1 never existed; such a file is not a Tempo backup.
                fileVersion == null || fileVersion < 1 -> ImportOutcome.CorruptFile
                fileVersion > BACKUP_SCHEMA_VERSION ->
                    ImportOutcome.UnsupportedVersion(
                        fileVersion = fileVersion,
                        maxSupported = BACKUP_SCHEMA_VERSION,
                    )

                else -> validateAndApply(plaintextJson, mode)
            }
        }

        private suspend fun validateAndApply(
            json: String,
            mode: ImportMode,
        ): ImportOutcome {
            val data = decodeData(json, jsonFormat) ?: return ImportOutcome.CorruptFile
            return when (val result = validator.validate(data)) {
                is BackupPayloadValidator.Result.Invalid ->
                    ImportOutcome.ValidationFailed(result.issues)

                is BackupPayloadValidator.Result.Valid ->
                    when (mode) {
                        ImportMode.REPLACE -> ImportOutcome.Success(replace(result.sanitized))
                        ImportMode.MERGE -> ImportOutcome.Success(merge(result.sanitized))
                    }
            }
        }

        private suspend fun readAll(): BackupData =
            BackupData(
                categories = database.categoryDao().getAllCategoriesSync().toDomain(),
                sections = database.taskSectionDao().getAllSectionsSync().map { it.toDomain() },
                tasks = database.taskDao().getAllTasksSync().toDomain(),
                habits = database.habitDao().getAllHabitsSync().toDomain(),
                habitChains = database.habitChainDao().getAllHabitChainsSync().map { it.toDomainChain() },
                chainMemberships =
                    database.habitChainMemberDao().getAllMembersSync().map {
                        ChainMembership(chainId = it.chainId, habitId = it.habitId, sortOrder = it.sortOrder)
                    },
                dailyFocusActivity = dailyFocusActivityRepository.getAll(),
                focusSessions = database.savedSessionDao().getAllSync().map { it.toDomain() },
            )

        /**
         * Wipes all tables and restores the payload exactly, preserving original ids.
         * Settings (SharedPreferences-backed, not part of the Room transaction) are
         * applied only after the database restore commits.
         */
        private suspend fun replace(data: BackupData): ImportSummary {
            val summary = replaceDatabase(data)
            dailyFocusActivityRepository.replaceAll(data.dailyFocusActivity)
            applySettingsBestEffort(settingsDataSource, data.settings)
            return summary
        }

        private suspend fun replaceDatabase(data: BackupData): ImportSummary =
            database.withTransaction {
                val memberDao = database.habitChainMemberDao()
                memberDao.deleteAllMembers()
                database.habitChainDao().deleteAllHabitChains()
                database.habitDao().deleteAllHabits()
                database.taskDao().deleteAllTasks()
                // The backup carries no attachments, and its tasks come back under their old ids:
                // rows left here would hang a deleted task's files on whichever imported task
                // reuses its id. The files themselves go at the next start (pruneOrphans).
                database.taskAttachmentDao().deleteAll()
                database.categoryDao().deleteAllCategories()
                database.taskSectionDao().deleteAll()
                database.savedSessionDao().deleteAll()

                data.categories.forEach { database.categoryDao().insertCategory(it.toEntity()) }
                val seededInboxCount = ensureDefaultCategory(data.categories)
                data.sections.forEach { database.taskSectionDao().insertSection(it.toEntity()) }
                data.tasks.forEach { database.taskDao().insertTask(it.toEntity()) }
                // A session whose task the file does not carry comes back standing alone, rather than
                // pointing at an id some later task could never have but nothing should rely on.
                val taskIds = data.tasks.mapTo(HashSet()) { it.id }
                data.focusSessions.forEach { session ->
                    database.savedSessionDao().insert(
                        session.copy(taskId = session.taskId?.takeIf { it in taskIds }).toEntity(),
                    )
                }
                data.habits.forEach { database.habitDao().insertHabit(it.toEntity()) }
                data.habitChains.forEach { database.habitChainDao().insertHabitChain(it.toEntity()) }
                memberDao.insertMembers(
                    data.chainMemberships.map {
                        HabitChainMemberEntity(chainId = it.chainId, habitId = it.habitId, sortOrder = it.sortOrder)
                    },
                )

                ImportSummary(
                    categories = ImportCounts(imported = data.categories.size + seededInboxCount),
                    tasks = ImportCounts(imported = data.tasks.size),
                    habits = ImportCounts(imported = data.habits.size),
                    habitChains = ImportCounts(imported = data.habitChains.size),
                )
            }

        /**
         * Restores two app invariants a payload may violate (pre-existing exports
         * or edited files): the seeded Inbox row (id -1) always exists — other code
         * references it directly, e.g. as the fallback task category — and some
         * category is marked default (the Inbox only when the payload brought none).
         * Returns the number of category rows this inserted beyond the payload (0 or 1)
         * so the import summary counts them.
         */
        private suspend fun ensureDefaultCategory(categories: List<Category>): Int {
            val hasDefault = categories.any { it.isDefault }
            val inboxId = DEFAULT_INBOX_CATEGORY_ENTITY.id
            return if (categories.none { it.id == inboxId }) {
                database.categoryDao().insertCategory(
                    CategoryEntity(
                        id = inboxId,
                        name = context.getString(R.string.category_inbox),
                        icon = "inbox",
                        isDefault = !hasDefault,
                        sortOrder = -1,
                    ),
                )
                1
            } else {
                if (!hasDefault) {
                    database.categoryDao().setDefault(inboxId)
                }
                0
            }
        }

        /**
         * Applies a [MergePlan]: inserts new records with fresh ids, remapping
         * references. The plan is computed (reading current data and running the
         * CPU-bound classification) before opening the write transaction, so the
         * transaction itself only performs mutations.
         */
        private suspend fun merge(data: BackupData): ImportSummary {
            val plan = mergePlanner.plan(incoming = data, local = readAll())
            return database.withTransaction {
                val categoryIds = plan.categoryIdMap.toMutableMap()
                plan.categoriesToInsert.forEach { category ->
                    categoryIds[category.id] =
                        database.categoryDao().insertCategory(category.copy(id = 0).toEntity())
                }
                val sectionIds = plan.sectionIdMap.toMutableMap()
                plan.sectionsToInsert.forEach { section ->
                    sectionIds[section.id] =
                        database.taskSectionDao().insertSection(
                            section.copy(id = 0, categoryId = categoryIds.getValue(section.categoryId)).toEntity(),
                        )
                }
                plan.sectionAliases.forEach { (alias, canonical) ->
                    sectionIds[canonical]?.let { sectionIds[alias] = it }
                }
                val taskIds = insertMergedTasks(plan, categoryIds, sectionIds)
                database.savedSessionDao().insertMerged(data.focusSessions, taskIds)
                val habitIds = plan.habitIdMap.toMutableMap()
                plan.habitsToInsert.forEach { habit ->
                    habitIds[habit.id] = database.habitDao().insertHabit(habit.copy(id = 0).toEntity())
                }
                val chainIds = plan.chainIdMap.toMutableMap()
                plan.chainsToInsert.forEach { chain ->
                    chainIds[chain.id] =
                        database.habitChainDao().insertHabitChain(chain.copy(id = 0).toEntity())
                }
                database.habitChainMemberDao().insertMembers(
                    plan.membershipsToInsert.map { membership ->
                        HabitChainMemberEntity(
                            chainId = chainIds.getValue(membership.chainId),
                            habitId = habitIds.getValue(membership.habitId),
                            sortOrder = membership.sortOrder,
                        )
                    },
                )
                plan.summary
            }
        }

        private suspend fun insertMergedTasks(
            plan: MergePlan,
            categoryIds: Map<Long, Long>,
            sectionIds: Map<Long, Long>,
        ): Map<Long, Long> {
            val taskIds = plan.taskIdMap.toMutableMap()
            plan.tasksToInsert.forEach { task ->
                taskIds[task.id] =
                    database.taskDao().insertTask(
                        task
                            .copy(
                                id = 0,
                                categoryId = categoryIds.getValue(task.categoryId),
                                parentTaskId = task.parentTaskId?.let(taskIds::getValue),
                                nextInstanceId = null,
                                // A task whose section did not come across lands unsectioned.
                                sectionId = task.sectionId?.let(sectionIds::get),
                            ).toEntity(),
                    )
            }
            // Second pass: next-instance links may point at tasks inserted later.
            plan.tasksToInsert
                .filter { it.nextInstanceId != null }
                .forEach { task ->
                    taskIds[task.nextInstanceId]?.let { target ->
                        database.taskDao().updateTaskNextInstanceId(taskIds.getValue(task.id), target)
                    }
                }
            return taskIds
        }
    }

/**
 * Adds the incoming saved sessions this device does not already have (#517).
 *
 * "Already has" is the same name with the same numbers: sessions carry no identity beyond that, and
 * importing the same file twice must not double the row on Focus. A link follows its task to
 * wherever the merge put it, and is dropped when the task did not come across.
 */
private suspend fun SavedSessionDao.insertMerged(
    incoming: List<SavedSession>,
    taskIds: Map<Long, Long>,
) {
    val known = getAllSync().map { it.toDomain() }.toMutableList()
    incoming.forEach { session ->
        if (known.any { it.hasSameSetupAs(session) }) return@forEach
        insert(session.copy(id = 0, taskId = session.taskId?.let(taskIds::get)).toEntity())
        known += session
    }
}

/**
 * [BackupRepository.importFromJson] promises local data is untouched whenever it
 * throws or returns anything but [ImportOutcome.Success]. By the time this runs
 * the database restore has already committed, so a failure applying settings
 * must not violate that contract by escaping as an exception — it is swallowed
 * and the restore is still reported as a success.
 */
private fun applySettingsBestEffort(
    settingsDataSource: BackupSettingsDataSource,
    settings: BackupSettings?,
) {
    if (settings == null) return
    try {
        settingsDataSource.apply(settings)
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        // Best-effort: the data restore already committed successfully.
    }
}

private fun HabitChainEntity.toDomainChain(): HabitChain =
    HabitChain(
        id = id,
        title = title,
        description = description,
        colorKey = colorKey,
        icon = icon,
        periodicReminder = periodicReminder,
        createdDate = createdDate,
        completionHistory = completionHistory,
        repeatDays = repeatDays,
    )

/** Outcome of resolving a picked file's content down to plaintext backup JSON. */
private sealed interface PlaintextResolution {
    data class Ready(
        val json: String,
    ) : PlaintextResolution

    data class Failed(
        val outcome: ImportOutcome,
    ) : PlaintextResolution
}

/**
 * Decrypts an encrypted backup envelope down to plaintext backup JSON. Every valid export is an
 * envelope — there is no unencrypted backup format to fall back to — so content that isn't one
 * is reported as corrupt rather than imported as-is.
 */
private fun resolvePlaintext(
    content: String,
    passphrase: CharArray?,
    jsonFormat: Json,
    encryptionService: BackupEncryptionService,
): PlaintextResolution {
    val envelopeDto = decodeEnvelopeOrNull(content, jsonFormat)
    val envelope = envelopeDto?.let { toEncryptedEnvelopeOrNull(it) }
    return when {
        // Not a decodable envelope at all, or one whose base64 payloads are malformed — a
        // tampered/truncated .tempo file or unrelated JSON, not a wrong-passphrase case.
        envelopeDto == null -> PlaintextResolution.Failed(ImportOutcome.CorruptFile)
        envelope == null -> PlaintextResolution.Failed(ImportOutcome.CorruptFile)
        passphrase == null -> PlaintextResolution.Failed(ImportOutcome.CorruptFile)
        else ->
            when (val decrypted = encryptionService.decrypt(envelope, passphrase)) {
                is DecryptResult.Success -> PlaintextResolution.Ready(decrypted.plaintext)
                DecryptResult.WrongPassphrase -> PlaintextResolution.Failed(ImportOutcome.WrongPassphrase)
                DecryptResult.Corrupt -> PlaintextResolution.Failed(ImportOutcome.CorruptFile)
            }
    }
}

/**
 * Only decodes envelopes at [ENCRYPTION_ENVELOPE_VERSION]; a future, newer envelope version
 * this build doesn't understand must be treated as "not a decryptable envelope" rather than
 * risk decrypting a payload laid out differently than this code assumes.
 */
private fun decodeEnvelopeOrNull(
    content: String,
    jsonFormat: Json,
): BackupEncryptedEnvelopeDto? {
    val dto =
        try {
            jsonFormat.decodeFromString<BackupEncryptedEnvelopeDto>(content)
        } catch (_: SerializationException) {
            return null
        }
    return dto.takeIf { it.encryptionVersion == ENCRYPTION_ENVELOPE_VERSION }
}

/** [BackupEncryptedEnvelopeDto.toEnvelope] base64-decodes its fields and can throw on bad input. */
private fun toEncryptedEnvelopeOrNull(dto: BackupEncryptedEnvelopeDto): EncryptedEnvelope? =
    try {
        dto.toEnvelope()
    } catch (_: IllegalArgumentException) {
        null
    }

private fun decodeSchemaVersion(
    json: String,
    jsonFormat: Json,
): Int? =
    try {
        jsonFormat.decodeFromString<BackupEnvelopeDto>(json).schemaVersion
    } catch (_: SerializationException) {
        null
    }

private fun decodeData(
    json: String,
    jsonFormat: Json,
): BackupData? =
    try {
        jsonFormat.decodeFromString<BackupFileDto>(json).toDomain()
    } catch (_: SerializationException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    }
