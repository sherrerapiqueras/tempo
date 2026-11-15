package com.mandrecode.tempo.features.focus.presentation.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mandrecode.tempo.R
import com.mandrecode.tempo.core.ui.util.rememberPressableButtonAnimation
import com.mandrecode.tempo.core.ui.util.titleResId
import com.mandrecode.tempo.features.focus.domain.model.FocusAgendaItem
import com.mandrecode.tempo.features.focus.domain.model.TaskFocusToday
import com.mandrecode.tempo.util.DateTimeFormatter
import kotlinx.datetime.LocalDate

private val UpNextCornerRadius = 22.dp
private val UpNextCardShape = RoundedCornerShape(UpNextCornerRadius)

/**
 * The one item worth starting next.
 *
 * On `tertiaryContainer` rather than `secondaryContainer`: the hero directly above already owns
 * `primaryContainer`, and secondary is a near neighbour of it in the light scheme, so the two cards
 * would read as one block. Tertiary is the blue already used for the selected category chip, so the
 * hue change is established app language rather than a new colour.
 *
 * The card is not rendered at all when nothing qualifies — see the caller. An empty second coloured
 * card is a lot of surface for no information.
 */
@Composable
internal fun UpNextCard(
    title: String,
    metadata: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    /** Drawn ahead of [metadata], which leads with the priority the icon belongs to. */
    metadataIconRes: Int? = null,
    trailingContent: (@Composable () -> Unit)? = null,
) {
    val haptic = LocalHapticFeedback.current

    Surface(
        modifier = modifier,
        shape = UpNextCardShape,
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
    ) {
        Row(
            modifier =
                Modifier
                    .clickable {
                        // Opening the item elsewhere is navigation, not a commit — the lighter tick.
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onClick()
                    }.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                if (!metadata.isNullOrBlank()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        // The flag reads as "priority" before the word does, the same way it does
                        // on the task's own card and in its editor.
                        metadataIconRes?.let { iconRes ->
                            Icon(
                                painter = painterResource(iconRes),
                                contentDescription = null,
                                modifier = Modifier.size(MetadataIconSize),
                                tint =
                                    MaterialTheme.colorScheme.onTertiaryContainer
                                        .copy(alpha = METADATA_ALPHA),
                            )
                        }
                        Text(
                            text = metadata,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color =
                                MaterialTheme.colorScheme.onTertiaryContainer
                                    .copy(alpha = METADATA_ALPHA),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = if (metadata.isNullOrBlank()) 0.dp else 2.dp),
                )
            }
            trailingContent?.invoke()
        }
    }
}

/**
 * The metadata line: due time when the item has one, in the user's 12h/24h preference. Kept
 * deliberately terse — the card is a prompt, not a detail view.
 *
 * Work carried over from a previous day says so instead of showing its time. The row mixes today's
 * work with what is left over from before it, and a bare "8:00 AM" on a card from last week read as
 * something due this morning — the one thing the time was there to tell you.
 */
@Composable
internal fun FocusAgendaItem.upNextMetadata(today: LocalDate?): String? {
    val context = LocalContext.current
    val entry = this as? FocusAgendaItem.TaskEntry
    val isOverdue =
        today != null &&
            entry
                ?.task
                ?.reminderDate
                ?.date
                ?.let { it < today } == true
    val parts =
        buildList {
            // Priority, then category, then time — the order the eye needs them in: how much this
            // matters, where it belongs, when it is due.
            // Leads the line: that the work is already under way outranks what kind of work it is.
            entry?.focusToday?.soFarTodayLabel()?.let { add(it.uppercase()) }
            priority?.let { add(stringResource(it.titleResId).uppercase()) }
            entry
                ?.categoryName
                ?.takeIf { it.isNotBlank() }
                ?.let { add(it.uppercase()) }
            // The same word the Tasks tab groups this work under, so the two tabs agree.
            if (isOverdue) {
                add(stringResource(R.string.active_overdue).uppercase())
            } else {
                dueTime?.let { add(DateTimeFormatter.formatTimeOfDay(it, context)) }
            }
        }
    return parts.takeIf { it.isNotEmpty() }?.joinToString(SEPARATOR)
}

/**
 * What this task has already had out of today, in one phrase.
 *
 * The runs it finished, or — when it finished none but was worked on anyway — the minutes it took.
 * A session stopped early leaves no run behind, and saying nothing at all about it would lose the
 * only trace of the work; the finished runs are the stronger fact when there are any, so they win.
 */
@Composable
internal fun TaskFocusToday.soFarTodayLabel(): String? =
    when {
        sessions > 0 -> pluralStringResource(R.plurals.focus_sessions_done, sessions, sessions)
        minutes > 0 -> stringResource(R.string.focus_today_minutes, minutes)
        else -> null
    }

private const val SEPARATOR = " · "
private val MetadataIconSize = 12.dp

/**
 * The only place a session starts. One tap, no duration picker — the length comes from Settings so
 * that starting stays a single gesture.
 */
@Composable
internal fun StartSessionButton(
    minutes: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current
    val (interactionSource, cornerRadius) =
        rememberPressableButtonAnimation(
            baseRadius = PillRadius,
            pressedRadius = PillPressedRadius,
        )

    val actionShape = RoundedCornerShape(cornerRadius.value)
    Surface(
        onClick = {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            onClick()
        },
        // The same height the worked-on pair stands at, so a card with one line of title is one
        // card whichever action it happens to be offering. Two heights for the same shape of card
        // made the row jog as it scrolled past.
        modifier = modifier.height(UpNextActionHeight),
        shape = actionShape,
        color = MaterialTheme.colorScheme.onTertiaryContainer,
        contentColor = MaterialTheme.colorScheme.tertiaryContainer,
        interactionSource = interactionSource,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = stringResource(R.string.focus_session_start, sessionLengthLabel(minutes)),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 14.dp),
            )
        }
    }
}

/**
 * Back to it, or done — the same pair the finished-break sheet offers.
 *
 * A task that has had a run at it and no tick is worked on, not untouched, and these are the only
 * two things left to say about it.
 */
@Composable
internal fun WorkedOnActions(
    onBackToWork: () -> Unit,
    onComplete: () -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        WorkedOnButton(
            icon = painterResource(R.drawable.ic_play_arrow),
            contentDescription = stringResource(R.string.focus_session_back_to_it),
            onClick = onBackToWork,
            filled = true,
        )
        WorkedOnButton(
            // The same tick the labelled buttons carry — and the disc around it is the button's,
            // so a glyph drawing its own ring would have put a circle inside a circle.
            icon = doneIcon(),
            contentDescription = stringResource(R.string.focus_session_mark_done),
            onClick = onComplete,
            filled = false,
        )
    }
}

/**
 * Both actions are discs of the same size, so the pair reads as a pair. [filled] is the one being
 * suggested — picking the work back up — and takes the card's ink; done is the quieter of the two
 * and takes a faint wash of it instead, present enough to be a button without competing with the
 * one beside it.
 */
@Composable
private fun WorkedOnButton(
    icon: Painter,
    contentDescription: String,
    onClick: () -> Unit,
    filled: Boolean,
) {
    val haptic = LocalHapticFeedback.current
    val (interactionSource, cornerRadius) =
        rememberPressableButtonAnimation(baseRadius = PillRadius, pressedRadius = PillPressedRadius)

    val actionShape = RoundedCornerShape(cornerRadius.value)
    Surface(
        onClick = {
            haptic.performHapticFeedback(
                if (filled) HapticFeedbackType.TextHandleMove else HapticFeedbackType.LongPress,
            )
            onClick()
        },
        modifier = Modifier.size(UpNextActionHeight),
        interactionSource = interactionSource,
        shape = actionShape,
        color =
            MaterialTheme.colorScheme.onTertiaryContainer.let {
                if (filled) it else it.copy(alpha = QUIET_CONTAINER_ALPHA)
            },
        contentColor =
            if (filled) {
                MaterialTheme.colorScheme.tertiaryContainer
            } else {
                MaterialTheme.colorScheme.onTertiaryContainer
            },
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                painter = icon,
                contentDescription = contentDescription,
                modifier = Modifier.size(WorkedOnIconSize),
            )
        }
    }
}

/** One height for every action a queued card can offer, so every such card is the same card. */
private val UpNextActionHeight = 40.dp
private val WorkedOnIconSize = 18.dp

/** Enough to draw the disc, not enough to read as a second filled button. */
private const val QUIET_CONTAINER_ALPHA = 0.18f
private const val METADATA_ALPHA = 0.75f
private val PillRadius = 20.dp
private val PillPressedRadius = 10.dp
