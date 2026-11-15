package com.mandrecode.tempo.features.focus.domain.usecase

import com.mandrecode.tempo.features.focus.domain.model.SavedSession
import com.mandrecode.tempo.features.focus.domain.repository.SavedSessionRepository
import jakarta.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlin.time.Clock

/** The topic sessions most recently started, most recent first. */
class GetSavedSessionsUseCase
    @Inject
    constructor(
        private val savedSessionRepository: SavedSessionRepository,
    ) {
        operator fun invoke(): Flow<List<SavedSession>> = savedSessionRepository.getAll()
    }

/**
 * Notes that a topic session is being started, so it is offered next time.
 *
 * A session with no name is not remembered: the name is the only thing that tells two of them
 * apart. Everything else is brought inside what a timer can run rather than refused.
 */
class RememberSessionUseCase
    @Inject
    constructor(
        private val savedSessionRepository: SavedSessionRepository,
        private val clock: Clock,
    ) {
        /** @return the session as remembered, with its id, or `null` when it has no name. */
        suspend operator fun invoke(session: SavedSession): SavedSession? {
            val normalized = session.normalized()
            if (normalized.title.isEmpty()) return null
            return savedSessionRepository.remember(normalized.copy(lastUsedAt = clock.now().toEpochMilliseconds()))
        }
    }
