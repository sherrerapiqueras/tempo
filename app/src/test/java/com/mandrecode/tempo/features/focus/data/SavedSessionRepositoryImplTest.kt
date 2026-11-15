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
        SavedSessionEntity(
            id = 3,
            title = "Study",
            focusMinutes = 50,
            breakMinutes = 10,
            rounds = 4,
            lastUsedAt = 1_700,
        )
    private val session =
        SavedSession(id = 3, title = "Study", focusMinutes = 50, breakMinutes = 10, rounds = 4, lastUsedAt = 1_700)

    @Test
    fun `entity and domain map to each other field for field`() {
        assertThat(entity.toDomain()).isEqualTo(session)
        assertThat(session.toEntity()).isEqualTo(entity)
    }

    @Test
    fun `recent sessions are read as domain models`() =
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
    fun `remembering keeps the most recent few and returns the session under its id`() =
        runTest {
            coEvery { dao.remember(any(), any()) } returns 11

            val remembered = repository.remember(session.copy(id = 0))

            assertThat(remembered).isEqualTo(session.copy(id = 11))
            coVerify { dao.remember(entity.copy(id = 0), keep = SavedSession.RECENT_LIMIT) }
        }
}
