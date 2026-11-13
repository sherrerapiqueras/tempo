package com.mandrecode.tempo.features.focus.presentation.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** The dial on the session sheet: the hero card's own container colour, with its ink. */
@Composable
internal fun sessionSheetDialColors(): SessionDialColors =
    SessionDialColors(
        fill = MaterialTheme.colorScheme.primaryContainer,
        content = MaterialTheme.colorScheme.onPrimaryContainer,
        progress = MaterialTheme.colorScheme.primary,
        track = MaterialTheme.colorScheme.primary.copy(alpha = TRACK_ALPHA),
    )

/** The dial on the running-session card, which already stands on a container of its own. */
@Composable
internal fun sessionCardDialColors(): SessionDialColors =
    SessionDialColors(
        fill = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = CARD_FILL_ALPHA),
        content = MaterialTheme.colorScheme.onTertiaryContainer,
        progress = MaterialTheme.colorScheme.onTertiaryContainer,
        track = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = TRACK_ALPHA),
    )

/**
 * The session's countdown, as a shape rather than a ring.
 *
 * A filled, scalloped shape from the expressive set, with the time left traced around its edge. A
 * thin circle on a pale sheet was the one thing on the screen with no weight to it; a filled shape
 * in the hero card's own colour gives the timer the presence of the thing the sheet is for, and
 * belongs to the same family as the cards around it.
 *
 * The shape says which half of the work this is before any label is read: a cookie while focusing,
 * a flower while resting. It breathes while time is running and holds still the moment it is
 * paused — the heartbeat the ring's travelling wave used to be.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun SessionDial(
    progress: Float,
    isRunning: Boolean,
    isBreak: Boolean,
    colors: SessionDialColors,
    modifier: Modifier = Modifier,
    strokeWidth: Dp = DialStrokeWidth,
    content: @Composable BoxScope.() -> Unit,
) {
    val shape = (if (isBreak) MaterialShapes.Flower else MaterialShapes.Cookie12Sided).toShape()
    val breath by rememberInfiniteTransition(label = "session_dial").animateFloat(
        initialValue = RESTING_SCALE,
        targetValue = BREATH_SCALE,
        animationSpec = infiniteRepeatable(tween(BREATH_MILLIS), RepeatMode.Reverse),
        label = "session_dial_breath",
    )
    // Chased rather than read directly, so pausing eases back to rest instead of snapping there.
    val scale by animateFloatAsState(if (isRunning) breath else RESTING_SCALE, label = "session_dial_scale")
    val shown by animateFloatAsState(progress.coerceIn(0f, 1f), label = "session_dial_progress")

    Box(
        modifier =
            modifier.drawWithCache {
                val stroke = strokeWidth.toPx()
                // Inset by the stroke, so the trace sits on the shape's edge without being clipped.
                val inner = Size(size.width - stroke * 2, size.height - stroke * 2)
                val outline = shape.createOutline(inner, layoutDirection, this)
                val path = (outline as? Outline.Generic)?.path ?: Path()
                val measure = PathMeasure().apply { setPath(path, forceClosed = true) }
                val traced = Path()
                onDrawBehind {
                    scale(scale) {
                        translate(stroke, stroke) {
                            drawPath(path, colors.fill)
                            drawPath(path, colors.track, style = Stroke(stroke))
                            traced.rewind()
                            measure.getSegment(0f, measure.length * shown, traced, startWithMoveTo = true)
                            drawPath(traced, colors.progress, style = Stroke(stroke, cap = StrokeCap.Round))
                        }
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        CompositionLocalProvider(LocalContentColor provides colors.content) { content() }
    }
}

private val DialStrokeWidth = 6.dp
private const val TRACK_ALPHA = 0.22f
private const val CARD_FILL_ALPHA = 0.12f
private const val RESTING_SCALE = 1f
private const val BREATH_SCALE = 0.965f
private const val BREATH_MILLIS = 2400
