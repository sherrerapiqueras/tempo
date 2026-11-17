package com.mandrecode.tempo.features.backup.domain.util

import com.mandrecode.tempo.core.domain.model.UNSET_UID
import com.mandrecode.tempo.features.backup.domain.model.BackupData
import com.mandrecode.tempo.features.backup.domain.model.ValidationIssue
import com.mandrecode.tempo.features.backup.domain.model.ValidationIssueKind
import jakarta.inject.Inject

/**
 * Validates the internal consistency of a decoded backup payload before any
 * database write. All checks run against the payload only — never against the
 * local database — so a failed validation guarantees local data was untouched.
 */
class BackupPayloadValidator
    @Inject
    constructor() {
        sealed interface Result {
            /**
             * Payload is consistent. [sanitized] has dangling nextInstanceId and section references
             * nulled, sections of unknown categories dropped, and saved sessions cleaned up.
             */
            data class Valid(
                val sanitized: BackupData,
            ) : Result

            data class Invalid(
                val issues: List<ValidationIssue>,
            ) : Result
        }

        fun validate(data: BackupData): Result {
            val issues =
                buildList {
                    addAll(duplicateIdIssues(data))
                    addAll(referenceIssues(data))
                }
            if (issues.isNotEmpty()) return Result.Invalid(issues)

            val taskIds = data.tasks.mapTo(mutableSetOf()) { it.id }
            val categoryIds = data.categories.mapTo(mutableSetOf()) { it.id }
            // Sections are structure, not content: one pointing nowhere is dropped, and a task
            // pointing at a section of another category (or none) is simply unsectioned (#61).
            val sanitizedSections =
                data.sections
                    .filter { it.categoryId in categoryIds }
                    .distinctBy { it.id }
            val sectionCategory = sanitizedSections.associate { it.id to it.categoryId }
            val sanitizedTasks =
                data.tasks.map { task ->
                    var sanitized = task
                    if (task.nextInstanceId != null && task.nextInstanceId !in taskIds) {
                        sanitized = sanitized.copy(nextInstanceId = null)
                    }
                    if (task.sectionId != null && sectionCategory[task.sectionId] != task.categoryId) {
                        sanitized = sanitized.copy(sectionId = null)
                    }
                    sanitized
                }
            // Recent sessions are sanitized the way sections are, not refused: one with no name is
            // dropped and a repeated id keeps its first, so nothing here can fail the insert that
            // follows (#517).
            val sanitizedSessions =
                data.focusSessions
                    .filter { it.title.isNotBlank() }
                    .distinctBy { it.id }
            // Identity (#30, schema v2) is sanitized too: a malformed uid, or one a record of the same
            // kind already used, is dropped, so that record imports as a new one instead of failing
            // the import or claiming another record's identity.
            return Result.Valid(
                data.copy(
                    categories = data.categories.withUsableUids({ it.uid }) { it.copy(uid = UNSET_UID) },
                    tasks = sanitizedTasks.withUsableUids({ it.uid }) { it.copy(uid = UNSET_UID) },
                    sections = sanitizedSections.withUsableUids({ it.uid }) { it.copy(uid = UNSET_UID) },
                    habits = data.habits.withUsableUids({ it.uid }) { it.copy(uid = UNSET_UID) },
                    habitChains = data.habitChains.withUsableUids({ it.uid }) { it.copy(uid = UNSET_UID) },
                    focusSessions = sanitizedSessions.withUsableUids({ it.uid }) { it.copy(uid = UNSET_UID) },
                ),
            )
        }

        private fun duplicateIdIssues(data: BackupData): List<ValidationIssue> =
            buildList {
                addAll(duplicates(data.categories.map { it.id }))
                addAll(duplicates(data.tasks.map { it.id }))
                addAll(duplicates(data.habits.map { it.id }))
                addAll(duplicates(data.habitChains.map { it.id }))
                addAll(duplicates(data.chainMemberships.map { "${it.chainId}:${it.habitId}" }))
            }

        // Details stay locale-neutral identifiers (titles or ids); the UI wraps
        // them in localized sentences per ValidationIssueKind.
        private fun duplicates(ids: List<Any>): List<ValidationIssue> =
            ids
                .groupingBy { it }
                .eachCount()
                .filterValues { it > 1 }
                .keys
                .map { ValidationIssue(ValidationIssueKind.DUPLICATE_ID, it.toString()) }

        private fun referenceIssues(data: BackupData): List<ValidationIssue> {
            val categoryIds = data.categories.mapTo(mutableSetOf()) { it.id }
            val taskIds = data.tasks.mapTo(mutableSetOf()) { it.id }
            val habitIds = data.habits.mapTo(mutableSetOf()) { it.id }
            val chainIds = data.habitChains.mapTo(mutableSetOf()) { it.id }
            return buildList {
                data.tasks.forEach { task ->
                    if (task.categoryId !in categoryIds) {
                        add(
                            ValidationIssue(
                                ValidationIssueKind.UNKNOWN_CATEGORY_REFERENCE,
                                task.title,
                            ),
                        )
                    }
                    if (task.parentTaskId != null && task.parentTaskId !in taskIds) {
                        add(
                            ValidationIssue(
                                ValidationIssueKind.UNKNOWN_PARENT_TASK_REFERENCE,
                                task.title,
                            ),
                        )
                    }
                }
                data.chainMemberships.forEach { membership ->
                    if (membership.chainId !in chainIds) {
                        add(
                            ValidationIssue(
                                ValidationIssueKind.UNKNOWN_CHAIN_REFERENCE,
                                membership.chainId.toString(),
                            ),
                        )
                    }
                    if (membership.habitId !in habitIds) {
                        add(
                            ValidationIssue(
                                ValidationIssueKind.UNKNOWN_HABIT_REFERENCE,
                                membership.habitId.toString(),
                            ),
                        )
                    }
                }
            }
        }
    }

private val CANONICAL_UUID = Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")

/** Keeps each well-formed uid the first time it appears in this list and unsets the rest. */
private fun <T> List<T>.withUsableUids(
    uid: (T) -> String,
    unset: (T) -> T,
): List<T> {
    val seen = mutableSetOf<String>()
    return map { record ->
        val value = uid(record)
        when {
            value == UNSET_UID -> record
            CANONICAL_UUID.matches(value) && seen.add(value) -> record
            else -> unset(record)
        }
    }
}
