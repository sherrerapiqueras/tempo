package com.mandrecode.tempo.features.focus.domain.usecase

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.mandrecode.tempo.features.focus.domain.model.FocusSession
import com.mandrecode.tempo.features.focus.domain.model.SavedSession
import com.mandrecode.tempo.features.focus.domain.repository.SavedSessionRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.time.Clock
import kotlin.time.Instant

class SavedSessionUseCasesTest {
    private val repository = mockk<SavedSessionRepository>(relaxed = true)
    private val now = Instant.fromEpochMilliseconds(1_700_000_000_000)
    private val clock =
        object : Clock {
            override fun now(): Instant = now
        }

    private val study = SavedSession(id = 0, title = "Study", focusMinutes = 25, breakMinutes = 5)

    @Test
    fun `recent sessions are read as the repository orders them`() =
        runTest {
            val recents = listOf(study.copy(id = 2, title = "Work"), study.copy(id = 1))
            every { repository.getAll() } returns flowOf(recents)

            GetSavedSessionsUseCase(repository)().test {
                assertThat(awaitItem()).isEqualTo(recents)
                awaitComplete()
            }
        }

    @Test
    fun `remembering stamps the start time, normalizes, and returns the session with its id`() =
        runTest {
            coEvery { repository.remember(any()) } answers { firstArg<SavedSession>().copy(id = 7) }

            val remembered = RememberSessionUseCase(repository, clock)(study.copy(title = " Study ", focusMinutes = 0))

            val expected =
                study.copy(
                    id = 7,
                    focusMinutes = FocusSession.SESSION_LENGTH_RANGE.first,
                    lastUsedAt = now.toEpochMilliseconds(),
                )
            assertThat(remembered).isEqualTo(expected)
        }

    @Test
    fun `a session with no name is not remembered`() =
        runTest {
            assertThat(RememberSessionUseCase(repository, clock)(study.copy(title = "  "))).isNull()

            coVerify(exactly = 0) { repository.remember(any()) }
        }
}
