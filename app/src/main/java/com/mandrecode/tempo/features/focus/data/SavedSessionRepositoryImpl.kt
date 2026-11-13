package com.mandrecode.tempo.features.focus.data

import com.mandrecode.tempo.core.data.entity.SavedSessionEntity
import com.mandrecode.tempo.core.data.local.dao.SavedSessionDao
import com.mandrecode.tempo.features.focus.data.mapper.toDomain
import com.mandrecode.tempo.features.focus.data.mapper.toEntity
import com.mandrecode.tempo.features.focus.domain.model.SavedSession
import com.mandrecode.tempo.features.focus.domain.repository.SavedSessionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class SavedSessionRepositoryImpl
    @Inject
    constructor(
        private val dao: SavedSessionDao,
    ) : SavedSessionRepository {
        override fun getAll(): Flow<List<SavedSession>> = dao.getAll().map { it.map(SavedSessionEntity::toDomain) }

        override suspend fun getById(id: Long): SavedSession? = dao.getById(id)?.toDomain()

        override suspend fun insert(session: SavedSession): Long = dao.insert(session.toEntity())

        override suspend fun update(session: SavedSession) = dao.update(session.toEntity())

        override suspend fun delete(id: Long) = dao.deleteById(id)
    }
