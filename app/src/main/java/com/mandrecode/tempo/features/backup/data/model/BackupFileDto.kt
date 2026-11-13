package com.mandrecode.tempo.features.backup.data.model

import kotlinx.serialization.Serializable

/**
 * On-disk backup format, schema version 1 — documented in `docs/BACKUP_FORMAT.md`.
 *
 * These DTOs are the stable persisted contract: they are deliberately decoupled
 * from Room entities and domain models so refactors cannot silently change the
 * file format. Evolution rules: fields may only be added with defaults under the
 * same [BackupFileDto.schemaVersion]; renames, removals or semantic changes must
 * bump it. Dates are ISO-8601 local date-times, enums are serialized by name,
 * repeat days are ISO day numbers (Monday = 1).
 */
@Serializable
data class BackupFileDto(
    val schemaVersion: Int,
    val appVersion: String = "",
    val exportedAt: String = "",
    val categories: List<CategoryBackupDto> = emptyList(),
    val tasks: List<TaskBackupDto> = emptyList(),
    val habits: List<HabitBackupDto> = emptyList(),
    val habitChains: List<HabitChainBackupDto> = emptyList(),
    val habitChainMembers: List<ChainMemberBackupDto> = emptyList(),
    val settings: SettingsBackupDto? = null,
    /** Per-day activity history; absent in files written before Focus mode existed. */
    val dailyFocusActivity: List<DailyFocusActivityBackupDto> = emptyList(),
    /** Sections inside categories (#61); absent in files written before sections existed. */
    val taskSections: List<TaskSectionBackupDto> = emptyList(),
    /** Saved focus sessions (#517); absent in files written before they existed. */
    val focusSessions: List<FocusSessionBackupDto> = emptyList(),
)

/** One saved focus session. [taskId] is the task it is linked to, when it has one. */
@Serializable
data class FocusSessionBackupDto(
    val id: Long,
    val title: String,
    val focusMinutes: Int = 25,
    val breakMinutes: Int = 5,
    val rounds: Int = 1,
    val taskId: Long? = null,
)

/** One recorded day of activity. The quiet/some/all state is derived, so it is not stored. */
@Serializable
data class DailyFocusActivityBackupDto(
    val date: String,
    val scheduledCount: Int = 0,
    val completedCount: Int = 0,
    val focusMinutes: Int = 0,
)

/** App configuration section; absent in files written before it existed. */
@Serializable
data class SettingsBackupDto(
    val themeMode: String = "SYSTEM",
    val useTempoColors: Boolean = false,
    // Files written before the tab options were removed still carry routinesTabEnabled,
    // tasksTabEnabled, enabledTabs and defaultTab; they are ignored on import.
    val autoRemoveCompletedTasks: Boolean = false,
    val completedTaskRetentionDays: Int = 30,
    val vacationPeriods: List<VacationPeriodBackupDto> = emptyList(),
)

/**
 * One vacation-mode pause. [end] is absent for the open-ended period the user is currently in;
 * both dates are ISO-8601 local dates.
 */
@Serializable
data class VacationPeriodBackupDto(
    val start: String,
    val end: String? = null,
)

/** Minimal projection used to read [BackupFileDto.schemaVersion] before full decoding. */
@Serializable
data class BackupEnvelopeDto(
    val schemaVersion: Int,
)

/** Highest encryption envelope version this app can decrypt and the version it writes. */
const val ENCRYPTION_ENVELOPE_VERSION = 1

/**
 * On-disk shape of an encrypted export (the `.tempo` format) — an outer envelope wrapping an
 * AES-256-GCM-encrypted [BackupFileDto] JSON string, keyed by a passphrase-derived key (see
 * `BackupEncryptionService`). Orthogonal to [BackupFileDto.schemaVersion]: this only changes
 * when the *encryption* format changes, documented in `docs/BACKUP_FORMAT.md`.
 *
 * [encryptionVersion] has no default: a missing value must fail to decode rather than silently
 * defaulting to a version — that would let arbitrary JSON sharing the other field names be
 * misidentified as a genuine encrypted envelope.
 */
@Serializable
data class BackupEncryptedEnvelopeDto(
    val encryptionVersion: Int,
    val kdf: String,
    val iterations: Int,
    val salt: String,
    val iv: String,
    val ciphertext: String,
)

@Serializable
data class CategoryBackupDto(
    val id: Long,
    val name: String,
    val color: String? = null,
    val icon: String? = null,
    val isDefault: Boolean = false,
    val sortOrder: Int = 0,
    /** Whether the category uses sections (#61); absent (off) in files written before them. */
    val sectionsEnabled: Boolean = false,
)

@Serializable
data class TaskBackupDto(
    val id: Long,
    val title: String,
    val description: String = "",
    val isCompleted: Boolean = false,
    val categoryId: Long,
    val priority: String? = null,
    val reminderDate: String? = null,
    val periodicity: String? = null,
    val periodicityInterval: Int = 1,
    val repeatDays: List<Int>? = null,
    val monthDayOption: String? = null,
    /** The series' last date (#509); absent (no end) in files written before end dates existed. */
    val periodicityEndDate: String? = null,
    val parentTaskId: Long? = null,
    val sortOrder: Int = 0,
    val completedAt: String? = null,
    val nextInstanceId: Long? = null,
    /** The task's section (#61); absent (unsectioned) in files written before sections existed. */
    val sectionId: Long? = null,
)

@Serializable
data class TaskSectionBackupDto(
    val id: Long,
    val categoryId: Long,
    val name: String,
    val sortOrder: Int = 0,
)

@Serializable
data class HabitBackupDto(
    val id: Long,
    val title: String,
    val description: String = "",
    val icon: String? = null,
    val colorKey: String? = null,
    val reminderDate: String? = null,
    val isCompleted: Boolean = false,
    val habitType: String = "BUILD",
    val createdDate: String,
    val completionHistory: String = "",
    val repeatDays: List<Int>? = null,
)

@Serializable
data class HabitChainBackupDto(
    val id: Long,
    val title: String,
    val description: String = "",
    val colorKey: String? = null,
    val icon: String? = null,
    val periodicReminder: String? = null,
    val createdDate: String,
    val completionHistory: String = "",
    val repeatDays: List<Int>? = null,
)

@Serializable
data class ChainMemberBackupDto(
    val chainId: Long,
    val habitId: Long,
    val sortOrder: Int,
)
