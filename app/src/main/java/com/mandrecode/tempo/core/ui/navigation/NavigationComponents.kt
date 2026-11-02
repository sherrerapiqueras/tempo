package com.mandrecode.tempo.core.ui.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mandrecode.tempo.core.ui.glass.glass
import com.mandrecode.tempo.core.ui.util.rememberPressableButtonAnimation

internal val FloatingRailStartPadding = 24.dp
internal val FloatingRailSurfaceWidth = FloatingToolbarItemSize + FloatingToolbarRailSurfacePadding * 2
internal val FloatingRailExpandedSurfaceWidth = 220.dp
private val FloatingRailContentGap = 16.dp
internal val FloatingRailContentStartPadding =
    FloatingRailStartPadding + FloatingRailSurfaceWidth + FloatingRailContentGap
internal val FloatingRailExpandedContentStartPadding =
    FloatingRailStartPadding + FloatingRailExpandedSurfaceWidth + FloatingRailContentGap
internal val ReadableContentMaxWidth = 840.dp

/** Enough to clear the floating bar spanning the bottom of a compact window. */
internal val SnackbarFloatingBarBottomPadding = 88.dp

/** The rail is up the side, so a snackbar only needs room to breathe. */
internal val SnackbarRailBottomPadding = 20.dp

/**
 * Lays out top-level screen content adaptively: reserves [railClearance] for the floating rail
 * and caps content at a readable width on wide windows, placed in the remaining space by
 * [horizontalBias] (-1 start, 0 centred, 1 end). Obtain the clearance for the current window
 * from [floatingRailContentClearance], and the bias beside a docked editor from
 * [animatedDockedContentBias].
 */
fun Modifier.adaptiveScreenContentLayout(
    railClearance: Dp,
    horizontalBias: Float = 0f,
): Modifier =
    padding(start = railClearance)
        .fillMaxWidth()
        .wrapContentWidth(BiasAlignment.Horizontal(horizontalBias))
        .widthIn(max = ReadableContentMaxWidth)

@Composable
fun TempoBottomRail(
    navigationContent: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    actionButton: (@Composable () -> Unit)? = null,
    soloActionContent: (@Composable () -> Unit)? = null,
    isRailLayout: Boolean = false,
) {
    val navContent = remember(navigationContent) { movableContentOf(navigationContent) }
    val actionContent = remember(actionButton) { actionButton?.let { movableContentOf(it) } }

    when {
        soloActionContent != null -> {
            SoloBottomRail(
                soloActionContent = soloActionContent,
                modifier = modifier,
            )
        }

        isRailLayout -> {
            LandscapeBottomRail(
                navigationContent = navContent,
                actionContent = actionContent,
                modifier = modifier,
            )
        }

        else -> {
            PortraitBottomRail(
                navigationContent = navContent,
                actionContent = actionContent,
                modifier = modifier,
            )
        }
    }
}

@Composable
private fun SoloBottomRail(
    soloActionContent: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(FloatingToolbarItemSpacing),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            soloActionContent()
        }
    }
}

@Composable
private fun LandscapeBottomRail(
    navigationContent: @Composable () -> Unit,
    actionContent: (@Composable () -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .fillMaxHeight()
                .navigationBarsPadding()
                .padding(
                    start = FloatingRailStartPadding,
                    top = 16.dp,
                    bottom = 16.dp,
                ),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(FloatingToolbarItemSpacing),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(FloatingToolbarItemSpacing),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                navigationContent()
                actionContent?.invoke()
            }
        }
    }
}

@Composable
private fun PortraitBottomRail(
    navigationContent: @Composable () -> Unit,
    actionContent: (@Composable () -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(FloatingToolbarItemSpacing),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            navigationContent()
            actionContent?.invoke()
        }
    }
}

@Composable
fun TempoBottomRailActionButton(
    iconRes: Int,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current
    val (buttonInteractionSource, buttonCornerRadius) = rememberPressableButtonAnimation()

    val shape = RoundedCornerShape(buttonCornerRadius.value)

    FloatingActionButton(
        modifier =
            modifier
                .size(FloatingToolbarActionButtonSize)
                .glass(shape, tint = MaterialTheme.colorScheme.primaryContainer),
        shape = shape,
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        elevation =
            FloatingActionButtonDefaults.elevation(
                defaultElevation = 0.dp,
                pressedElevation = 0.dp,
                focusedElevation = 0.dp,
                hoveredElevation = 0.dp,
            ),
        interactionSource = buttonInteractionSource,
        onClick = {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            onClick()
        },
    ) {
        Icon(
            painter = painterResource(id = iconRes),
            contentDescription = contentDescription,
            modifier = Modifier.size(22.dp),
        )
    }
}

@Composable
fun TempoSoloActionButton(
    iconRes: Int,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    expanded: Boolean = true,
) {
    val haptic = LocalHapticFeedback.current
    val (buttonInteractionSource, buttonCornerRadius) = rememberPressableButtonAnimation()
    val shape = RoundedCornerShape(buttonCornerRadius.value)

    ExtendedFloatingActionButton(
        text = { Text(label) },
        icon = {
            Icon(
                painter = painterResource(id = iconRes),
                contentDescription = null,
            )
        },
        expanded = expanded,
        onClick = {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            onClick()
        },
        modifier = modifier.glass(shape, tint = MaterialTheme.colorScheme.primaryContainer),
        shape = shape,
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        elevation =
            FloatingActionButtonDefaults.elevation(
                defaultElevation = 0.dp,
                pressedElevation = 0.dp,
                focusedElevation = 0.dp,
                hoveredElevation = 0.dp,
            ),
        interactionSource = buttonInteractionSource,
    )
}
