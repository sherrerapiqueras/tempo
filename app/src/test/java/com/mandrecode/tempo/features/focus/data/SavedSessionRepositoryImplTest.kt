package com.mandrecode.tempo.features.focus.data

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.mandrecode.tempo.core.data.entity.SavedSessionEntity
import com.mandrecode.tempo.core.data.local.dao.SavedSessionDao
import com.mandrecode.tempo.features.focus.data.mapper.toDomain
import com.mandrecode.tempo.features.focus.data.mapper.toEntity
import com.mandrecode.tempo.features.focus.domain.model.SavedSession
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Test

class SavedSessionRepositoryImplTest {
    private val dao = mockk<SavedSessionDao>(relaxed = true)
    private val repository = SavedSessionRepositoryImpl(dao)

    private val entity =
        SavedSessionEntity(id = 3, title = "Study", focusMinutes = 50, breakMinutes = 10, rounds = 4, taskId = 7)
    private val session =
        SavedSession(id = 3, title = "Study", focusMinutes = 50, breakMinutes = 10, rounds = 4, taskId = 7)

    @Test
    fun `entity and domain map to each other field for field`() {
        assertThat(entity.toDomain()).isEqualTo(session)
        assertThat(session.toEntity()).isEqualTo(entity)
    }

    @Test
    fun `an unlinked session maps with no task`() {
        assertThat(entity.copy(taskId = null).toDomain().taskId).isNull()
    }

    @Test
    fun `all sessions are read as domain models`() =
        runTest {
            every { dao.getAll() } returns flowOf(listOf(entity))

            repository.getAll().test {
                assertThat(awaitItem()).containsExactly(session)
                awaitComplete()
            }
        }

    @Test
    fun `a session is found by id, or not at all`() =
        runTest {
            coEvery { dao.getById(3) } returns entity
            coEvery { dao.getById(4) } returns null

            assertThat(repository.getById(3)).isEqualTo(session)
            assertThat(repository.getById(4)).isNull()
        }

    @Test
    fun `inserting returns the new id, and updates and deletes reach the dao`() =
        runTest {
            coEvery { dao.insert(any()) } returns 11

            assertThat(repository.insert(session.copy(id = 0))).isEqualTo(11)
            repository.update(session)
            repository.delete(3)

            coVerify { dao.update(entity) }
            coVerify { dao.deleteById(3) }
        }
}
