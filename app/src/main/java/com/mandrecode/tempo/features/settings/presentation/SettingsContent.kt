package com.mandrecode.tempo.features.settings.presentation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.mandrecode.tempo.R
import com.mandrecode.tempo.core.domain.model.ThemeMode
import com.mandrecode.tempo.core.ui.components.ExpressiveChip
import com.mandrecode.tempo.core.ui.theme.LocalIsDarkTheme
import com.mandrecode.tempo.core.ui.theme.TempoDarkPrimary
import com.mandrecode.tempo.core.ui.theme.TempoLightPrimary
import com.mandrecode.tempo.util.dynamicColorScheme
import com.mandrecode.tempo.util.supportsDynamicColor

@Composable
fun SettingsContent(
    uiState: SettingsContract.UiState,
    onEvent: (SettingsContract.UiEvent) -> Unit,
    onOnboardingClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val navigationBarPadding = WindowInsets.navigationBars.asPaddingValues()

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(navigationBarPadding)
                .padding(horizontal = 16.dp)
                .padding(top = 24.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        ThemeSection(uiState = uiState, onEvent = onEvent)
        ColorSchemeSection(uiState = uiState, onEvent = onEvent)
        RemindersSection(uiState = uiState, onEvent = onEvent)
        VacationModeSection(uiState = uiState, onEvent = onEvent)
        CompletedTaskRetentionSection(uiState = uiState, onEvent = onEvent)
        FocusSection(uiState = uiState, onEvent = onEvent)
        BackupSection(uiState = uiState, onEvent = onEvent)
        DriveBackupSection(uiState = uiState, onEvent = onEvent)
        NotificationsSection()
        LanguageSection()
        AboutSection(
            appVersion = uiState.appVersion,
            onOnboardingClick = onOnboardingClick,
        )
    }
}

@Composable
internal fun ThemeSection(
    uiState: SettingsContract.UiState,
    onEvent: (SettingsContract.UiEvent) -> Unit,
) {
    SettingsSection(title = stringResource(R.string.theme)) {
        item {
            Column(
                modifier = Modifier.padding(SettingsSectionContentPadding),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    uiState.availableThemeModes.forEachIndexed { index, themeMode ->
                        ExpressiveChip(
                            label = themeMode.label(),
                            isSelected = uiState.selectedThemeMode == themeMode,
                            onClick = { onEvent(SettingsContract.UiEvent.ThemeModeSelected(themeMode)) },
                            isFirst = index == 0,
                            isLast = index == uiState.availableThemeModes.size - 1,
                            modifier = Modifier.weight(1f),
                            height = 48.dp,
                            horizontalPadding = 8.dp,
                            icon = {
                                Icon(
                                    painter = painterResource(themeMode.iconRes()),
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                            },
                        )
                    }
                }
                AnimatedVisibility(
                    visible = uiState.selectedThemeMode == ThemeMode.SYSTEM,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically(),
                ) {
                    Text(
                        text = stringResource(R.string.theme_description),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 4.dp),
                    )
                }
            }
        }
    }
}

@Composable
internal fun ColorSchemeSection(
    uiState: SettingsContract.UiState,
    onEvent: (SettingsContract.UiEvent) -> Unit,
) {
    val context = LocalContext.current
    val isDarkTheme = LocalIsDarkTheme.current
    val isPreview = LocalInspectionMode.current
    val dynamicDotColor =
        remember(context, isDarkTheme, isPreview) {
            if (!isPreview && supportsDynamicColor) {
                dynamicColorScheme(context, isDark = isDarkTheme).primary
            } else {
                if (isDarkTheme) darkColorScheme().primary else lightColorScheme().primary
            }
        }
    val tempoDotColor = remember(isDarkTheme) { if (isDarkTheme) TempoDarkPrimary else TempoLightPrimary }

    SettingsSection(title = stringResource(R.string.settings_color_scheme)) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(SettingsSectionContentPadding),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                ExpressiveChip(
                    label = stringResource(R.string.settings_color_scheme_dynamic),
                    isSelected = !uiState.useTempoColors,
                    onClick = { onEvent(SettingsContract.UiEvent.TempoColorsToggled(false)) },
                    isFirst = true,
                    isLast = false,
                    modifier = Modifier.weight(1f),
                    height = 48.dp,
                    horizontalPadding = 8.dp,
                    icon = {
                        ColorDot(
                            color = if (!uiState.useTempoColors) LocalContentColor.current else dynamicDotColor,
                        )
                    },
                )
                ExpressiveChip(
                    label = stringResource(R.string.settings_color_scheme_tempo),
                    isSelected = uiState.useTempoColors,
                    onClick = { onEvent(SettingsContract.UiEvent.TempoColorsToggled(true)) },
                    isFirst = false,
                    isLast = true,
                    modifier = Modifier.weight(1f),
                    height = 48.dp,
                    horizontalPadding = 8.dp,
                    icon = {
                        ColorDot(
                            color = if (uiState.useTempoColors) LocalContentColor.current else tempoDotColor,
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun NotificationsSection() {
    val context = LocalContext.current
    SettingsSection(title = stringResource(R.string.settings_notifications)) {
        item {
            SettingsItem(
                icon = R.drawable.ic_notifications,
                title = stringResource(R.string.settings_notifications_subtitle),
                subtitle = stringResource(R.string.settings_notifications_description),
                trailingIcon = R.drawable.ic_open_in_new,
                onClick = { openNotificationSettings(context) },
            )
        }
    }
}

@Composable
private fun LanguageSection() {
    val context = LocalContext.current
    val currentLocale = LocalLocale.current.platformLocale
    val languageName = currentLocale.getDisplayName(currentLocale).replaceFirstChar { it.titlecase(currentLocale) }

    SettingsSection(title = stringResource(R.string.settings_language)) {
        item {
            SettingsItem(
                icon = R.drawable.ic_language,
                title = stringResource(R.string.settings_language_subtitle),
                subtitle = languageName,
                trailingIcon = R.drawable.ic_open_in_new,
                onClick = { openLanguageSettings(context) },
            )
        }
    }
}

@Composable
private fun AboutSection(
    appVersion: String,
    onOnboardingClick: () -> Unit,
) {
    val context = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SettingsSection(title = stringResource(R.string.about)) {
            item {
                SettingsItem(
                    icon = R.drawable.ic_info,
                    title = stringResource(R.string.view_onboarding),
                    subtitle = stringResource(R.string.view_onboarding_description),
                    trailingIcon = R.drawable.ic_chevron_right,
                    onClick = onOnboardingClick,
                )
            }
            item {
                SettingsItem(
                    icon = R.drawable.ic_star,
                    title = stringResource(R.string.review_app),
                    subtitle = stringResource(R.string.review_app_description),
                    trailingIcon = R.drawable.ic_chevron_right,
                    onClick = { openReview(context) },
                )
            }
            item {
                SettingsItem(
                    icon = R.drawable.ic_feedback,
                    title = stringResource(R.string.send_feedback),
                    subtitle = stringResource(R.string.send_feedback_description),
                    trailingIcon = R.drawable.ic_chevron_right,
                    onClick = { openFeedback(context, appVersion) },
                )
            }
            item {
                SettingsItem(
                    icon = R.drawable.ic_code,
                    title = stringResource(R.string.source_code),
                    subtitle = stringResource(R.string.source_code_description),
                    trailingIcon = R.drawable.ic_chevron_right,
                    onClick = { openSourceCode(context) },
                )
            }
        }
        if (appVersion.isNotEmpty()) {
            Text(
                text = stringResource(R.string.version_format, appVersion),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun ThemeMode.label(): String =
    stringResource(
        when (this) {
            ThemeMode.LIGHT -> R.string.theme_light
            ThemeMode.DARK -> R.string.theme_dark
            ThemeMode.SYSTEM -> R.string.theme_system
        },
    )

private fun ThemeMode.iconRes(): Int =
    when (this) {
        ThemeMode.LIGHT -> R.drawable.ic_light_mode
        ThemeMode.DARK -> R.drawable.ic_dark_mode
        ThemeMode.SYSTEM -> R.drawable.ic_sync
    }
