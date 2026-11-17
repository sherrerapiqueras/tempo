package com.mandrecode.tempo.core.data.mapper

import com.google.common.truth.Truth.assertThat
import com.mandrecode.tempo.core.data.entity.CategoryEntity
import com.mandrecode.tempo.core.data.entity.HabitChainEntity
import com.mandrecode.tempo.core.data.entity.HabitChainWithMembers
import com.mandrecode.tempo.core.data.entity.HabitEntity
import com.mandrecode.tempo.core.data.entity.SavedSessionEntity
import com.mandrecode.tempo.core.data.entity.TaskAttachmentEntity
import com.mandrecode.tempo.core.data.entity.TaskEntity
import com.mandrecode.tempo.core.data.entity.TaskSectionEntity
import com.mandrecode.tempo.core.domain.model.UNSET_TIMESTAMP
import com.mandrecode.tempo.core.domain.model.UNSET_UID
import com.mandrecode.tempo.features.focus.data.mapper.toDomain
import com.mandrecode.tempo.features.focus.data.mapper.toEntity
import com.mandrecode.tempo.features.routines.data.mapper.toDomain
import com.mandrecode.tempo.features.routines.data.mapper.toEntity
import com.mandrecode.tempo.features.tasks.data.mapper.toDomain
import com.mandrecode.tempo.features.tasks.data.mapper.toEntity
import kotlinx.datetime.LocalDateTime
import org.junit.Test

/**
 * Every mapper carries a record's identity both ways (#30): an edit written back through a domain
 * model must keep the stored uid and timestamps, and `asNew()` must drop them.
 */
class IdentityMetadataMappingTest {
    private val created = LocalDateTime(2026, 1, 2, 3, 4)

    @Test
    fun `category round-trips identity and asNew clears it`() {
        val entity = CategoryEntity(id = 3, name = "Work", uid = UID, createdAt = CREATED, updatedAt = UPDATED)

        assertThat(entity.toDomain().toEntity()).isEqualTo(entity)
        entity.toDomain().asNew().let {
            assertThat(listOf(it.id, it.uid, it.createdAt, it.updatedAt)).isEqualTo(UNSET)
        }
    }

    @Test
    fun `task round-trips identity and next occurrence is a new record`() {
        val entity =
            TaskEntity(id = 3, title = "T", description = "", uid = UID, createdAt = CREATED, updatedAt = UPDATED)

        assertThat(entity.toDomain().toEntity()).isEqualTo(entity)
        entity.toDomain().asNextOccurrence(created).let {
            assertThat(listOf(it.id, it.uid, it.createdAt, it.updatedAt)).isEqualTo(UNSET)
        }
    }

    @Test
    fun `section round-trips identity and asNew clears it`() {
        val entity =
            TaskSectionEntity(id = 3, categoryId = 1, name = "S", uid = UID, createdAt = CREATED, updatedAt = UPDATED)

        assertThat(entity.toDomain().toEntity()).isEqualTo(entity)
        entity.toDomain().asNew().let {
            assertThat(listOf(it.id, it.uid, it.createdAt, it.updatedAt)).isEqualTo(UNSET)
        }
    }

    @Test
    fun `attachment round-trips identity and asNew clears it`() {
        val entity =
            TaskAttachmentEntity(
                id = 3,
                taskId = 1,
                displayName = "a.pdf",
                mimeType = null,
                sizeBytes = 1,
                storedName = "s",
                uid = UID,
                createdAt = CREATED,
                updatedAt = UPDATED,
            )

        assertThat(entity.toDomain().toEntity()).isEqualTo(entity)
        entity.toDomain().asNew().let {
            assertThat(listOf(it.id, it.uid, it.createdAt, it.updatedAt)).isEqualTo(UNSET)
        }
    }

    @Test
    fun `saved session round-trips identity and asNew clears it`() {
        val entity =
            SavedSessionEntity(
                id = 3,
                title = "Deep",
                focusMinutes = 50,
                breakMinutes = 10,
                uid = UID,
                createdAt = CREATED,
                updatedAt = UPDATED,
            )

        assertThat(entity.toDomain().toEntity()).isEqualTo(entity)
        entity.toDomain().asNew().let {
            assertThat(listOf(it.id, it.uid, it.createdAt, it.updatedAt)).isEqualTo(UNSET)
        }
    }

    @Test
    fun `habit round-trips identity and asNew clears it`() {
        val entity =
            HabitEntity(
                id = 3,
                title = "H",
                description = "",
                createdDate = created,
                uid = UID,
                createdAt = CREATED,
                updatedAt = UPDATED,
            )

        assertThat(entity.toDomain().toEntity()).isEqualTo(entity)
        entity.toDomain().asNew().let {
            assertThat(listOf(it.id, it.uid, it.createdAt, it.updatedAt)).isEqualTo(UNSET)
        }
    }

    @Test
    fun `habit chain round-trips identity and asNew clears it`() {
        val entity =
            HabitChainEntity(
                id = 3,
                title = "C",
                createdDate = created,
                uid = UID,
                createdAt = CREATED,
                updatedAt = UPDATED,
            )

        assertThat(HabitChainWithMembers(entity, emptyList()).toDomain().toEntity()).isEqualTo(entity)
        HabitChainWithMembers(entity, emptyList()).toDomain().asNew().let {
            assertThat(listOf(it.id, it.uid, it.createdAt, it.updatedAt)).isEqualTo(UNSET)
        }
    }

    private companion object {
        const val UID = "01234567-89ab-7def-8123-456789abcdef"
        const val CREATED = 1_000L
        const val UPDATED = 2_000L
        val UNSET = listOf(0L, UNSET_UID, UNSET_TIMESTAMP, UNSET_TIMESTAMP)
    }
}
