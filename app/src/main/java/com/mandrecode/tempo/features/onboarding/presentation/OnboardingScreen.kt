package com.mandrecode.tempo.features.onboarding.presentation

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mandrecode.tempo.core.ui.components.HoldSplashWhileFirstLoad
import com.mandrecode.tempo.features.backup.presentation.DriveRestoreDialogs
import com.mandrecode.tempo.features.backup.presentation.DriveRestoreEvent

@Composable
fun OnboardingScreen(
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: OnboardingViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val currentOnExit by rememberUpdatedState(onExit)
    // Nothing to load: a first launch onto onboarding lets the splash go straight away.
    HoldSplashWhileFirstLoad(isLoading = false)

    val driveConsentLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
            viewModel.onEvent(OnboardingContract.UiEvent.DriveRestore(DriveRestoreEvent.ConsentFinished(result.data)))
        }

    LaunchedEffect(viewModel) {
        viewModel.uiEffect.collect { effect ->
            when (effect) {
                is OnboardingContract.UiEffect.Exit -> currentOnExit()
                is OnboardingContract.UiEffect.LaunchDriveConsent ->
                    driveConsentLauncher.launch(IntentSenderRequest.Builder(effect.intent).build())
            }
        }
    }

    OnboardingContent(
        uiState = uiState,
        onEvent = viewModel::onEvent,
        modifier = modifier,
    )
    DriveRestoreDialogs(
        step = uiState.driveRestore,
        onEvent = { viewModel.onEvent(OnboardingContract.UiEvent.DriveRestore(it)) },
    )
}
