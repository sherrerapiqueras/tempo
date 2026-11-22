package com.mandrecode.tempo.features.onboarding.presentation

import android.app.PendingIntent
import com.mandrecode.tempo.core.domain.model.ThemeMode
import com.mandrecode.tempo.features.backup.presentation.DriveRestoreEvent
import com.mandrecode.tempo.features.backup.presentation.DriveRestoreStep
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

object OnboardingContract {
    /**
     * The pages, in the order they are shown. Named rather than counted at each call site: the
     * order has changed more than once, and every magic index was somewhere else that had to be
     * found and corrected when it did.
     */
    object Page {
        /**
         * First, so that someone who already knows Tempo can restore their backup (#307) before
         * skipping the rest: Skip finishes onboarding, so an offer on a later page would be missed.
         */
        const val WELCOME = 0
        const val FOCUS = 1
        const val TASKS = 2
        const val ROUTINES = 3
        const val APPEARANCE = 4
    }

    const val PAGE_COUNT = 5

    data class UiState(
        val currentPage: Int = 0,
        val selectedThemeMode: ThemeMode = ThemeMode.SYSTEM,
        val availableThemeModes: ImmutableList<ThemeMode> =
            persistentListOf(ThemeMode.LIGHT, ThemeMode.DARK, ThemeMode.SYSTEM),
        val useTempoColors: Boolean = true,
        /** A restore from Google Drive under way (#307), null when none is. */
        val driveRestore: DriveRestoreStep? = null,
        /** Whether the welcome page offers a restore from Google Drive (FeatureFlag.DRIVE_BACKUP). */
        val isDriveRestoreAvailable: Boolean = true,
    ) {
        val isFirstPage: Boolean get() = currentPage == 0
        val isLastPage: Boolean get() = currentPage == PAGE_COUNT - 1
    }

    sealed interface UiEvent {
        data object NextClicked : UiEvent

        data object BackClicked : UiEvent

        data object SkipClicked : UiEvent

        data object FinishClicked : UiEvent

        data class UseTempoColorsToggled(
            val useTempoColors: Boolean,
        ) : UiEvent

        data class ThemeModeSelected(
            val mode: ThemeMode,
        ) : UiEvent

        /** Offer to bring back data from a Google Drive backup (#307). */
        data object RestoreFromDriveClicked : UiEvent

        data class DriveRestore(
            val event: DriveRestoreEvent,
        ) : UiEvent
    }

    sealed interface UiEffect {
        data object Exit : UiEffect

        /** Drive backup was just turned on: ask for notification permission where Android needs it. */
        data object RequestNotificationPermission : UiEffect

        /** Google's consent for Drive access; the result comes back as a DriveRestore(ConsentFinished). */
        data class LaunchDriveConsent(
            val intent: PendingIntent,
        ) : UiEffect
    }
}
