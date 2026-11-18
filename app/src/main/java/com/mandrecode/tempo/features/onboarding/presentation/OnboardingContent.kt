package com.mandrecode.tempo.features.onboarding.presentation

import android.widget.ImageView
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.mandrecode.tempo.R
import com.mandrecode.tempo.core.ui.glass.ambientBackground
import com.mandrecode.tempo.core.ui.theme.onboardingPageHeadlineRegular
import com.mandrecode.tempo.core.ui.theme.onboardingPageHeadlineShort
import com.mandrecode.tempo.core.ui.theme.onboardingWelcomeTitleRegular
import com.mandrecode.tempo.core.ui.theme.onboardingWelcomeTitleShort
import com.mandrecode.tempo.features.settings.presentation.ColorSchemeSection
import com.mandrecode.tempo.features.settings.presentation.ThemeSection

@Composable
fun OnboardingContent(
    uiState: OnboardingContract.UiState,
    onEvent: (OnboardingContract.UiEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val layout = rememberOnboardingLayout()
    Surface(
        modifier = modifier.fillMaxSize().ambientBackground(),
        color = Color.Transparent,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(
                        horizontal = layout.outerHorizontalPadding,
                        vertical = layout.outerVerticalPadding,
                    ),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            OnboardingSkip(
                isVisible = !uiState.isLastPage,
                layout = layout,
                onEvent = onEvent,
            )
            OnboardingProgress(
                currentPage = uiState.currentPage,
                pageCount = OnboardingContract.PAGE_COUNT,
                modifier = Modifier.widthIn(max = layout.footerMaxWidth).fillMaxWidth(),
            )
            OnboardingPage(
                uiState = uiState,
                onEvent = onEvent,
                layout = layout,
                modifier = Modifier.weight(1f).fillMaxWidth(),
            )
            OnboardingFooter(uiState = uiState, layout = layout, onEvent = onEvent)
        }
    }
}

@Composable
private fun OnboardingSkip(
    isVisible: Boolean,
    layout: OnboardingLayout,
    onEvent: (OnboardingContract.UiEvent) -> Unit,
) {
    Row(
        modifier =
            Modifier
                .widthIn(max = layout.pageMaxWidth)
                .fillMaxWidth()
                .heightIn(min = 48.dp),
        horizontalArrangement = Arrangement.End,
    ) {
        if (isVisible) {
            AnimatedOnboardingButton(
                label = stringResource(R.string.onboarding_skip),
                onClick = { onEvent(OnboardingContract.UiEvent.SkipClicked) },
                style = OnboardingButtonStyle.Text,
                modifier = Modifier.testTag(OnboardingTestTags.SKIP),
            )
        }
    }
}

@Composable
private fun OnboardingPage(
    uiState: OnboardingContract.UiState,
    onEvent: (OnboardingContract.UiEvent) -> Unit,
    layout: OnboardingLayout,
    modifier: Modifier = Modifier,
) {
    AnimatedContent(
        targetState = uiState.currentPage,
        transitionSpec = {
            val direction = if (targetState > initialState) 1 else -1
            (slideInHorizontally { width -> direction * width } + fadeIn()) togetherWith
                (slideOutHorizontally { width -> -direction * width } + fadeOut()) using
                SizeTransform(clip = false)
        },
        label = "onboardingPage",
        modifier = modifier.testTag(OnboardingTestTags.PAGE),
    ) { page ->
        Box(
            modifier = Modifier.fillMaxSize().clipToBounds(),
            contentAlignment = Alignment.Center,
        ) {
            when (page) {
                OnboardingContract.Page.WELCOME ->
                    OnboardingWelcomePage(
                        layout = layout,
                        onRestoreClick = { onEvent(OnboardingContract.UiEvent.RestoreFromDriveClicked) },
                    )
                OnboardingContract.Page.FOCUS -> FocusPage(layout = layout)
                OnboardingContract.Page.TASKS -> TasksAndCategoriesPage(layout = layout)
                OnboardingContract.Page.ROUTINES -> RoutinesAndRemindersPage(layout = layout)
                else -> AppearancePage(uiState = uiState, layout = layout, onEvent = onEvent)
            }
        }
    }
}

/**
 * The opener: the app's name and, for someone coming from another phone, a way to bring their data
 * back (#307) before anything else. The offer is the onboarding's own button in its tonal style:
 * clearly an action, but quieter than "Get started", at the foot of the page so the welcome leads.
 */
@Composable
internal fun OnboardingWelcomePage(
    layout: OnboardingLayout,
    onRestoreClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // A short window (a phone in landscape) can't fit the page, so it scrolls there, with fixed gaps
    // in place of the weights that centre it on a tall one: the restore offer must never be cut off.
    val gap: @Composable ColumnScope.() -> Unit = {
        Spacer(modifier = if (layout.isShort) Modifier.height(layout.sectionSpacing) else Modifier.weight(1f))
    }
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .then(if (layout.isShort) Modifier.verticalScroll(rememberScrollState()) else Modifier)
                .padding(vertical = layout.pageVerticalPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        gap()
        AndroidView(
            factory = { context ->
                ImageView(context).apply {
                    setImageResource(R.mipmap.ic_launcher)
                    scaleType = ImageView.ScaleType.FIT_CENTER
                }
            },
            update = { imageView -> imageView.contentDescription = null },
            modifier =
                Modifier
                    .size(if (layout.isShort) 96.dp else 144.dp)
                    .testTag(OnboardingTestTags.WELCOME_LOGO),
        )
        Text(
            text = stringResource(R.string.app_name),
            style =
                if (layout.isShort) {
                    MaterialTheme.typography.onboardingWelcomeTitleShort
                } else {
                    MaterialTheme.typography.onboardingWelcomeTitleRegular
                },
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = layout.sectionSpacing),
        )
        Text(
            text = stringResource(R.string.onboarding_welcome_tagline),
            style = if (layout.isShort) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp),
        )
        gap()
        WelcomeRestoreOffer(layout = layout, onRestoreClick = onRestoreClick)
    }
}

/** "Coming from another phone?" and its button, kept apart from "Get started" by a gap below. */
@Composable
private fun WelcomeRestoreOffer(
    layout: OnboardingLayout,
    onRestoreClick: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = stringResource(R.string.onboarding_restore_title),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        AnimatedOnboardingButton(
            label = stringResource(R.string.drive_restore_title),
            onClick = onRestoreClick,
            style = OnboardingButtonStyle.Tonal,
            modifier =
                Modifier
                    .padding(top = 12.dp)
                    .widthIn(max = layout.footerMaxWidth)
                    .fillMaxWidth()
                    .testTag(OnboardingTestTags.RESTORE),
        )
        // Room between the offer and "Get started", so the two never read as one pair of choices.
        Spacer(modifier = Modifier.height(if (layout.isShort) 16.dp else 48.dp))
    }
}

@Composable
private fun OnboardingFooter(
    uiState: OnboardingContract.UiState,
    layout: OnboardingLayout,
    onEvent: (OnboardingContract.UiEvent) -> Unit,
) {
    Row(
        modifier = Modifier.widthIn(max = layout.footerMaxWidth).fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (uiState.isFirstPage) {
            Spacer(modifier = Modifier.weight(1f))
        } else {
            AnimatedOnboardingButton(
                label = stringResource(R.string.back),
                onClick = { onEvent(OnboardingContract.UiEvent.BackClicked) },
                style = OnboardingButtonStyle.Glass,
                modifier = Modifier.weight(1f).testTag(OnboardingTestTags.BACK),
            )
        }
        val label =
            when {
                uiState.isLastPage -> R.string.onboarding_start
                uiState.isFirstPage -> R.string.onboarding_get_started
                else -> R.string.onboarding_next
            }
        AnimatedOnboardingButton(
            label = stringResource(label),
            onClick = {
                val event =
                    if (uiState.isLastPage) {
                        OnboardingContract.UiEvent.FinishClicked
                    } else {
                        OnboardingContract.UiEvent.NextClicked
                    }
                onEvent(event)
            },
            style = OnboardingButtonStyle.Primary,
            modifier =
                Modifier
                    .weight(if (uiState.isLastPage) FINAL_ACTION_WEIGHT else 1f)
                    .testTag(OnboardingTestTags.FORWARD),
        )
    }
}

@Composable
private fun AppearancePage(
    uiState: OnboardingContract.UiState,
    layout: OnboardingLayout,
    onEvent: (OnboardingContract.UiEvent) -> Unit,
) {
    val settingsUiState = uiState.toSettingsUiState()
    val onSettingsEvent = { event: com.mandrecode.tempo.features.settings.presentation.SettingsContract.UiEvent ->
        val onboardingEvent = event.toOnboardingEvent()
        if (onboardingEvent != null) onEvent(onboardingEvent)
    }

    AdaptiveOnboardingPage(
        layout = layout,
        intro = {
            if (!layout.isShort) {
                HeroIcon(iconRes = R.drawable.ic_palette)
            }
            Text(
                text = stringResource(R.string.onboarding_appearance_title),
                style =
                    if (layout.isShort) {
                        MaterialTheme.typography.onboardingPageHeadlineShort
                    } else {
                        MaterialTheme.typography.onboardingPageHeadlineRegular
                    },
                textAlign = TextAlign.Center,
            )
            Text(
                text = stringResource(R.string.onboarding_appearance_description),
                style =
                    if (layout.isShort) {
                        MaterialTheme.typography.bodyMedium
                    } else {
                        MaterialTheme.typography.bodyLarge
                    },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        },
        body = {
            ThemeSection(uiState = settingsUiState, onEvent = onSettingsEvent)
            ColorSchemeSection(uiState = settingsUiState, onEvent = onSettingsEvent)
        },
    )
}

internal object OnboardingTestTags {
    const val SKIP = "onboarding_skip"
    const val BACK = "onboarding_back"
    const val FORWARD = "onboarding_forward"
    const val PROGRESS = "onboarding_progress"
    const val PROGRESS_SEGMENT = "onboarding_progress_segment"
    const val PAGE = "onboarding_page"
    const val WELCOME_LOGO = "onboarding_welcome_logo"
    const val RESTORE = "onboarding_restore_from_drive"
    const val SINGLE_PANE = "onboarding_single_pane"
    const val TWO_PANE = "onboarding_two_pane"
}

internal val OnboardingMaxWidth = 600.dp

private const val FINAL_ACTION_WEIGHT = 2f
