package com.mandrecode.tempo.features.settings.presentation

import android.app.PendingIntent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.TwoRowsTopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mandrecode.tempo.R
import com.mandrecode.tempo.core.ui.glass.ambientBackground
import com.mandrecode.tempo.core.ui.navigation.adaptiveScreenContentLayout
import com.mandrecode.tempo.core.ui.navigation.floatingRailContentClearance
import com.mandrecode.tempo.core.ui.navigation.isFloatingNavigationRailLayout
import com.mandrecode.tempo.core.ui.theme.settingsTopBarTitleCollapsed
import com.mandrecode.tempo.core.ui.theme.settingsTopBarTitleExpanded
import com.mandrecode.tempo.features.backup.presentation.DriveRestoreEvent

@Composable
fun SettingsScreen(
    onBackClick: () -> Unit,
    onOnboardingClick: () -> Unit,
    modifier: Modifier = Modifier,
    showBackButton: Boolean = true,
    showTitle: Boolean = true,
    viewModel: SettingsViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val exportLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.CreateDocument("application/json"),
        ) { uri ->
            if (uri != null) {
                viewModel.onEvent(SettingsContract.UiEvent.ExportDestinationPicked(uri))
            } else {
                viewModel.onEvent(SettingsContract.UiEvent.ExportCancelled)
            }
        }
    val importLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.OpenDocument(),
        ) { uri ->
            uri?.let { viewModel.onEvent(SettingsContract.UiEvent.ImportFilePicked(it)) }
        }

    val driveConsent = rememberDriveConsentLaunchers(viewModel::onEvent)

    LaunchedEffect(viewModel) {
        viewModel.uiEffect.collect { effect ->
            when (effect) {
                is SettingsContract.UiEffect.LaunchDriveConsent -> driveConsent.forBackup(effect.intent)

                is SettingsContract.UiEffect.LaunchDriveRestoreConsent -> driveConsent.forRestore(effect.intent)

                is SettingsContract.UiEffect.LaunchExportPicker ->
                    exportLauncher.launch(effect.suggestedFileName)

                is SettingsContract.UiEffect.LaunchImportPicker ->
                    importLauncher.launch(arrayOf("application/json"))

                is SettingsContract.UiEffect.ShowMessage -> {
                    @Suppress("LocalContextGetResourceValueCall")
                    val message =
                        if (effect.formatArgs.isNotEmpty()) {
                            context.getString(effect.message, *effect.formatArgs.toTypedArray())
                        } else {
                            context.getString(effect.message)
                        }
                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    SettingsScaffold(
        uiState = uiState,
        onEvent = viewModel::onEvent,
        onBackClick = onBackClick,
        onOnboardingClick = onOnboardingClick,
        modifier = modifier,
        showBackButton = showBackButton,
        showTitle = showTitle,
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun SettingsScaffold(
    uiState: SettingsContract.UiState,
    onEvent: (SettingsContract.UiEvent) -> Unit,
    onBackClick: () -> Unit,
    onOnboardingClick: () -> Unit,
    modifier: Modifier = Modifier,
    showBackButton: Boolean = true,
    showTitle: Boolean = true,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    // The ambient backdrop is painted on the Scaffold itself (Settings slides in over a tab and
    // must cover it), so the Scaffold and its top bar are transparent over it.
    val settingsContainerColor = Color.Transparent
    val isRailLayout = isFloatingNavigationRailLayout()

    Scaffold(
        containerColor = settingsContainerColor,
        contentWindowInsets = WindowInsets(0),
        modifier =
            modifier
                .adaptiveScreenContentLayout(railClearance = floatingRailContentClearance())
                .ambientBackground()
                .let {
                    if (isRailLayout) it else it.nestedScroll(scrollBehavior.nestedScrollConnection)
                },
        topBar = {
            SettingsTopBar(
                showTitle = showTitle,
                showBackButton = showBackButton,
                isRailLayout = isRailLayout,
                onBackClick = onBackClick,
                settingsContainerColor = settingsContainerColor,
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        SettingsContent(
            uiState = uiState,
            onEvent = onEvent,
            onOnboardingClick = onOnboardingClick,
            modifier = Modifier.padding(padding),
        )
    }
}

/**
 * Deliberately does not match the Tasks/Routines root-tab title treatment. Those tabs use the
 * bold [com.mandrecode.tempo.core.ui.theme.topBarTitle] typography token *and* separately apply
 * a primary title color via [com.mandrecode.tempo.core.ui.components.TempoTopBar]'s
 * `topAppBarColors`. Settings is a pushed detail screen using the M3 large-collapsing-title
 * pattern instead, so its title stays normal-weight and onSurface-colored per that pattern
 * rather than competing with the tab title's brand emphasis.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SettingsTopBar(
    showTitle: Boolean,
    showBackButton: Boolean,
    isRailLayout: Boolean,
    onBackClick: () -> Unit,
    settingsContainerColor: Color,
    scrollBehavior: TopAppBarScrollBehavior,
) {
    if (!showTitle) {
        Spacer(modifier = Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
        return
    }

    val navigationIcon: @Composable () -> Unit = {
        if (showBackButton) {
            IconButton(onClick = onBackClick) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.back),
                )
            }
        }
    }
    val topAppBarColors =
        TopAppBarDefaults.topAppBarColors(
            containerColor = settingsContainerColor,
            scrolledContainerColor = settingsContainerColor,
        )

    // Medium/expanded windows keep a fixed-size rail alongside this screen, so the large
    // display-style title has no scroll gesture to expand from and would just waste space.
    if (isRailLayout) {
        TopAppBar(
            title = {
                Text(
                    text = stringResource(R.string.settings),
                    style = MaterialTheme.typography.settingsTopBarTitleCollapsed,
                )
            },
            navigationIcon = navigationIcon,
            colors = topAppBarColors,
        )
    } else {
        TwoRowsTopAppBar(
            title = { expanded ->
                Text(
                    text = stringResource(R.string.settings),
                    style =
                        if (expanded) {
                            MaterialTheme.typography.settingsTopBarTitleExpanded
                        } else {
                            MaterialTheme.typography.settingsTopBarTitleCollapsed
                        },
                )
            },
            navigationIcon = navigationIcon,
            colors = topAppBarColors,
            collapsedHeight = TopAppBarDefaults.LargeAppBarCollapsedHeight,
            expandedHeight = TopAppBarDefaults.LargeAppBarExpandedHeight,
            scrollBehavior = scrollBehavior,
        )
    }
}

/** Shows Google's Drive consent (#306, #307) and reports its result to the flow that asked. */
private class DriveConsentLaunchers(
    val forBackup: (PendingIntent) -> Unit,
    val forRestore: (PendingIntent) -> Unit,
)

@Composable
private fun rememberDriveConsentLaunchers(onEvent: (SettingsContract.UiEvent) -> Unit): DriveConsentLaunchers {
    val backup =
        rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
            onEvent(SettingsContract.UiEvent.DriveBackup.ConsentFinished(result.data))
        }
    val restore =
        rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
            onEvent(SettingsContract.UiEvent.DriveBackup.Restore(DriveRestoreEvent.ConsentFinished(result.data)))
        }
    return DriveConsentLaunchers(
        forBackup = { backup.launch(IntentSenderRequest.Builder(it).build()) },
        forRestore = { restore.launch(IntentSenderRequest.Builder(it).build()) },
    )
}
