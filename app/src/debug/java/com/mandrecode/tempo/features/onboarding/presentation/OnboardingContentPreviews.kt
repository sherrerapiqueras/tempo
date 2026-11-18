@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package com.mandrecode.tempo.features.onboarding.presentation

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.ComposeUiFlags
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.mandrecode.tempo.core.ui.theme.TempoTheme

@Preview(name = "Phone", device = Devices.PHONE, showBackground = true)
@Preview(name = "Foldable", device = Devices.FOLDABLE, showBackground = true)
@Preview(name = "Tablet", device = Devices.TABLET, showBackground = true)
@Preview(name = "Desktop", device = Devices.DESKTOP, showBackground = true)
private annotation class PreviewOnboardingFormFactors

@Preview(name = "Tasks - Light", device = "id:pixel_9", showBackground = true)
@Preview(
    name = "Tasks - Dark",
    device = "id:pixel_9",
    uiMode = Configuration.UI_MODE_NIGHT_YES,
    showBackground = true,
)
@Composable
private fun OnboardingTasksPreview() {
    ComposeUiFlags.isMediaQueryIntegrationEnabled = true
    TempoTheme(useTempoColors = true) {
        OnboardingContent(
            uiState = OnboardingContract.UiState(),
            onEvent = {},
        )
    }
}

@Preview(name = "Setup - Light", device = "id:pixel_9", showBackground = true)
@Preview(
    name = "Setup - Dark",
    device = "id:pixel_9",
    uiMode = Configuration.UI_MODE_NIGHT_YES,
    showBackground = true,
)
@Composable
private fun OnboardingSetupPreview() {
    ComposeUiFlags.isMediaQueryIntegrationEnabled = true
    TempoTheme(useTempoColors = true) {
        OnboardingContent(
            uiState = OnboardingContract.UiState(currentPage = OnboardingContract.Page.APPEARANCE),
            onEvent = {},
        )
    }
}

@Preview(name = "Welcome - Light", device = "id:pixel_9", showBackground = true)
@Preview(
    name = "Welcome - Dark",
    device = "id:pixel_9",
    uiMode = Configuration.UI_MODE_NIGHT_YES,
    showBackground = true,
)
@Composable
private fun OnboardingWelcomePreview() {
    ComposeUiFlags.isMediaQueryIntegrationEnabled = true
    TempoTheme(useTempoColors = true) {
        OnboardingContent(
            uiState = OnboardingContract.UiState(currentPage = OnboardingContract.Page.WELCOME),
            onEvent = {},
        )
    }
}

@Preview(name = "Motion components", showBackground = true)
@Composable
private fun OnboardingMotionComponentsPreview() {
    TempoTheme(useTempoColors = true) {
        Surface {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OnboardingProgress(
                    currentPage = 1,
                    pageCount = OnboardingContract.PAGE_COUNT,
                )
                OnboardingButtonStyle.entries.forEach { style ->
                    AnimatedOnboardingButton(
                        label = style.name,
                        onClick = {},
                        style = style,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@PreviewOnboardingFormFactors
@Preview(
    name = "Short landscape",
    device = "spec:width=891dp,height=411dp,dpi=420",
    showBackground = true,
)
@Composable
private fun OnboardingAdaptiveSetupPreview() {
    ComposeUiFlags.isMediaQueryIntegrationEnabled = true
    TempoTheme(useTempoColors = true) {
        OnboardingContent(
            uiState = OnboardingContract.UiState(currentPage = OnboardingContract.Page.APPEARANCE),
            onEvent = {},
        )
    }
}
