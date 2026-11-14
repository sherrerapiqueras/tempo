package com.mandrecode.tempo.features.backup.domain.util

import com.google.common.truth.Truth.assertThat
import com.mandrecode.tempo.features.backup.domain.model.BackupData
import com.mandrecode.tempo.features.backup.domain.model.ChainMembership
import com.mandrecode.tempo.features.backup.domain.model.ValidationIssueKind
import com.mandrecode.tempo.features.focus.domain.model.SavedSession
import com.mandrecode.tempo.features.routines.domain.model.Habit
import com.mandrecode.tempo.features.routines.domain.model.HabitChain
import com.mandrecode.tempo.features.tasks.domain.model.Category
import com.mandrecode.tempo.features.tasks.domain.model.Task
import com.mandrecode.tempo.features.tasks.domain.model.TaskSection
import kotlinx.datetime.LocalDateTime
import org.junit.Test

class BackupPayloadValidatorTest {
    private val validator = BackupPayloadValidator()

    @Test
    fun `consistent payload is valid`() {
        val data =
            backupData(
                categories = listOf(category(id = 1)),
                tasks = listOf(task(id = 10, categoryId = 1), task(id = 11, categoryId = 1, parentTaskId = 10)),
                habits = listOf(habit(id = 20)),
                habitChains = listOf(chain(id = 30)),
                chainMemberships = listOf(ChainMembership(chainId = 30, habitId = 20, sortOrder = 0)),
            )

        val result = validator.validate(data)

        assertThat(result).isInstanceOf(BackupPayloadValidator.Result.Valid::class.java)
    }

    @Test
    fun `dangling nextInstanceId is nulled not fatal`() {
        val data =
            backupData(
                categories = listOf(category(id = 1)),
                tasks = listOf(task(id = 10, categoryId = 1, nextInstanceId = 999)),
            )

        val result = validator.validate(data) as BackupPayloadValidator.Result.Valid

        assertThat(
            result.sanitized.tasks
                .single()
                .nextInstanceId,
        ).isNull()
    }

    @Test
    fun `resolvable nextInstanceId is preserved`() {
        val data =
            backupData(
                categories = listOf(category(id = 1)),
                tasks =
                    listOf(
                        task(id = 10, categoryId = 1, nextInstanceId = 11),
                        task(id = 11, categoryId = 1),
                    ),
            )

        val result = validator.validate(data) as BackupPayloadValidator.Result.Valid

        assertThat(
            result.sanitized.tasks
                .first()
                .nextInstanceId,
        ).isEqualTo(11)
    }

    @Test
    fun `duplicate ids are reported`() {
        val data =
            backupData(
                categories = listOf(category(id = 1), category(id = 1, name = "Other")),
            )

        val result = validator.validate(data) as BackupPayloadValidator.Result.Invalid

        assertThat(result.issues.map { it.kind }).containsExactly(ValidationIssueKind.DUPLICATE_ID)
    }

    @Test
    fun `task referencing unknown category is invalid`() {
        val data =
            backupData(
                categories = listOf(category(id = 1)),
                tasks = listOf(task(id = 10, categoryId = 42, title = "Orphan")),
            )

        val result = validator.validate(data) as BackupPayloadValidator.Result.Invalid

        val issue = result.issues.single()
        assertThat(issue.kind).isEqualTo(ValidationIssueKind.UNKNOWN_CATEGORY_REFERENCE)
        assertThat(issue.detail).isEqualTo("Orphan")
    }

    @Test
    fun `subtask referencing unknown parent is invalid`() {
        val data =
            backupData(
                categories = listOf(category(id = 1)),
                tasks = listOf(task(id = 10, categoryId = 1, parentTaskId = 99)),
            )

        val result = validator.validate(data) as BackupPayloadValidator.Result.Invalid

        assertThat(result.issues.single().kind)
            .isEqualTo(ValidationIssueKind.UNKNOWN_PARENT_TASK_REFERENCE)
    }

    @Test
    fun `membership referencing unknown chain and habit reports both`() {
        val data =
            backupData(
                chainMemberships = listOf(ChainMembership(chainId = 1, habitId = 2, sortOrder = 0)),
            )

        val result = validator.validate(data) as BackupPayloadValidator.Result.Invalid

        assertThat(result.issues.map { it.kind })
            .containsExactly(
                ValidationIssueKind.UNKNOWN_CHAIN_REFERENCE,
                ValidationIssueKind.UNKNOWN_HABIT_REFERENCE,
            )
    }

    /** #61: a section is structure, so a broken reference is cleaned up rather than fatal. */
    @Test
    fun `sections of unknown categories are dropped and stray task sections are cleared`() {
        val data =
            backupData(
                categories = listOf(category(id = 1), category(id = 2)),
                tasks =
                    listOf(
                        task(id = 10, categoryId = 1).copy(sectionId = 5),
                        task(id = 11, categoryId = 2).copy(sectionId = 5),
                        task(id = 12, categoryId = 1).copy(sectionId = 6),
                    ),
            ).copy(
                sections =
                    listOf(
                        TaskSection(id = 5, categoryId = 1, name = "Cat"),
                        TaskSection(id = 6, categoryId = 99, name = "Orphan"),
                    ),
            )

        val result = validator.validate(data) as BackupPayloadValidator.Result.Valid

        assertThat(result.sanitized.sections.map { it.id }).containsExactly(5L)
        assertThat(result.sanitized.tasks.associate { it.id to it.sectionId })
            .containsExactly(10L, 5L, 11L, null, 12L, null)
    }

    private fun backupData(
        categories: List<Category> = emptyList(),
        tasks: List<Task> = emptyList(),
        habits: List<Habit> = emptyList(),
        habitChains: List<HabitChain> = emptyList(),
        chainMemberships: List<ChainMembership> = emptyList(),
    ) = BackupData(categories, tasks, habits, habitChains, chainMemberships)

    private fun category(
        id: Long,
        name: String = "Category $id",
    ) = Category(id = id, name = name)

    private fun task(
        id: Long,
        categoryId: Long,
        title: String = "Task $id",
        parentTaskId: Long? = null,
        nextInstanceId: Long? = null,
    ) = Task(
        id = id,
        title = title,
        description = "",
        categoryId = categoryId,
        parentTaskId = parentTaskId,
        nextInstanceId = nextInstanceId,
    )

    private fun habit(id: Long) =
        Habit(
            id = id,
            title = "Habit $id",
            description = "",
            createdDate = LocalDateTime(2026, 1, 1, 8, 0),
        )

    private fun chain(id: Long) =
        HabitChain(
            id = id,
            title = "Chain $id",
            createdDate = LocalDateTime(2026, 1, 1, 8, 0),
        )

    @Test
    fun `saved sessions are cleaned up rather than refused`() {
        fun session(
            id: Long,
            title: String = "Study",
            taskId: Long? = null,
        ) = SavedSession(id = id, title = title, focusMinutes = 25, breakMinutes = 5, taskId = taskId)
        val data =
            backupData(
                categories = listOf(category(id = 1)),
                tasks = listOf(task(id = 10, categoryId = 1)),
            ).copy(
                focusSessions =
                    listOf(
                        session(1, taskId = 10),
                        session(1, title = "Same id again"),
                        session(2, title = ""),
                        session(3, taskId = 404),
                    ),
            )

        val result = validator.validate(data) as BackupPayloadValidator.Result.Valid

        assertThat(result.sanitized.focusSessions)
            .containsExactly(session(1, taskId = 10), session(3))
            .inOrder()
    }
}
