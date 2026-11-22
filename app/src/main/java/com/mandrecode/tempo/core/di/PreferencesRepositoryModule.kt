package com.mandrecode.tempo.core.di

import com.mandrecode.tempo.core.data.preferences.ActiveLiveActivityPreferences
import com.mandrecode.tempo.core.data.preferences.ActiveLiveActivityPreferencesImpl
import com.mandrecode.tempo.core.data.preferences.AppPreferencesRepository
import com.mandrecode.tempo.core.data.preferences.AppPreferencesRepositoryImpl
import com.mandrecode.tempo.core.data.preferences.BuildFeatureFlags
import com.mandrecode.tempo.core.data.preferences.CompletedTaskRetentionPreferencesImpl
import com.mandrecode.tempo.core.data.preferences.FeatureFlagOverrides
import com.mandrecode.tempo.core.data.preferences.MissedReminderPreferencesImpl
import com.mandrecode.tempo.core.data.preferences.NavigationPreferencesRepository
import com.mandrecode.tempo.core.data.preferences.NavigationPreferencesRepositoryImpl
import com.mandrecode.tempo.core.data.preferences.OnboardingPreferencesRepository
import com.mandrecode.tempo.core.data.preferences.OnboardingPreferencesRepositoryImpl
import com.mandrecode.tempo.core.data.preferences.TasksScreenPreferencesRepository
import com.mandrecode.tempo.core.data.preferences.TasksScreenPreferencesRepositoryImpl
import com.mandrecode.tempo.core.data.preferences.ThemePreferencesRepository
import com.mandrecode.tempo.core.data.preferences.ThemePreferencesRepositoryImpl
import com.mandrecode.tempo.core.data.preferences.VacationModePreferencesImpl
import com.mandrecode.tempo.core.domain.model.FeatureFlags
import com.mandrecode.tempo.core.domain.repository.VacationModeRepository
import com.mandrecode.tempo.features.tasks.domain.repository.CompletedTaskRetentionPreferences
import com.mandrecode.tempo.features.tasks.domain.repository.MissedReminderPreferences
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class PreferencesRepositoryModule {
    @Binds
    abstract fun bindFeatureFlags(impl: BuildFeatureFlags): FeatureFlags

    @Binds
    abstract fun bindFeatureFlagOverrides(impl: BuildFeatureFlags): FeatureFlagOverrides

    @Binds
    abstract fun bindThemePreferencesRepository(impl: ThemePreferencesRepositoryImpl): ThemePreferencesRepository

    @Binds
    abstract fun bindNavigationPreferencesRepository(impl: NavigationPreferencesRepositoryImpl): NavigationPreferencesRepository

    @Binds
    abstract fun bindOnboardingPreferencesRepository(impl: OnboardingPreferencesRepositoryImpl): OnboardingPreferencesRepository

    @Binds
    abstract fun bindAppPreferencesRepository(impl: AppPreferencesRepositoryImpl): AppPreferencesRepository

    @Binds
    abstract fun bindTasksScreenPreferencesRepository(impl: TasksScreenPreferencesRepositoryImpl): TasksScreenPreferencesRepository

    @Binds
    abstract fun bindCompletedTaskRetentionPreferences(impl: CompletedTaskRetentionPreferencesImpl): CompletedTaskRetentionPreferences

    @Binds
    abstract fun bindActiveLiveActivityPreferences(impl: ActiveLiveActivityPreferencesImpl): ActiveLiveActivityPreferences

    @Binds
    abstract fun bindMissedReminderPreferences(impl: MissedReminderPreferencesImpl): MissedReminderPreferences

    @Binds
    abstract fun bindVacationModeRepository(impl: VacationModePreferencesImpl): VacationModeRepository
}
