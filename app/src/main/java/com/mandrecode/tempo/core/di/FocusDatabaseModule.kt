package com.mandrecode.tempo.core.di

import com.mandrecode.tempo.core.data.local.TempoDatabase
import com.mandrecode.tempo.core.data.local.dao.SavedSessionDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** The Focus feature's own DAOs, beside [DatabaseModule] rather than adding to it. */
@Module
@InstallIn(SingletonComponent::class)
object FocusDatabaseModule {
    @Provides
    fun provideSavedSessionDao(database: TempoDatabase): SavedSessionDao = database.savedSessionDao()
}
