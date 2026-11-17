package com.mandrecode.tempo.features.settings.presentation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.mandrecode.tempo.core.ui.components.CardGroupPosition
import com.mandrecode.tempo.core.ui.components.ConnectedCardGap
import com.mandrecode.tempo.core.ui.components.glassCardElevation
import com.mandrecode.tempo.core.ui.glass.glassCardColor
import com.mandrecode.tempo.core.ui.theme.settingsSectionTitle

/**
 * A titled settings section: its items stand as one connected group of glass cards (#423), rounded
 * at the group's two ends and joined tightly in between, the way the Tasks list groups its cards.
 *
 * Items are declared with [SettingsGroupScope.item]; one that is not [visible][SettingsGroupScope.item]
 * folds away with its seam, and the group around it re-rounds to its new ends.
 */
@Composable
internal fun SettingsSection(
    title: String,
    modifier: Modifier = Modifier,
    content: SettingsGroupScope.() -> Unit,
) {
    val items = SettingsGroupScope().apply(content).items
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.settingsSectionTitle,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Column(modifier = Modifier.fillMaxWidth()) {
            settingsGroupPositions(items.map { it.visible }).forEachIndexed { index, position ->
                key(index) {
                    SettingsGroupPiece(
                        visible = items[index].visible,
                        position = position,
                        // The seam belongs to the piece below it, so it folds away with that piece.
                        // It only exists when a visible piece stands above; the first one has none.
                        hasSeamAbove =
                            position != null &&
                                position != CardGroupPosition.SINGLE &&
                                position != CardGroupPosition.FIRST,
                        content = items[index].content,
                    )
                }
            }
        }
    }
}

/**
 * Each item's place in the group, null while it is hidden. Hidden items don't count: the visible
 * ones form the run, so the group re-rounds to its new ends.
 */
internal fun settingsGroupPositions(visible: List<Boolean>): List<CardGroupPosition?> {
    val visibleCount = visible.count { it }
    var visibleIndex = 0
    return visible.map { if (it) CardGroupPosition.of(visibleIndex++, visibleCount) else null }
}

/** Collects a [SettingsSection]'s items. */
internal class SettingsGroupScope {
    internal val items = mutableListOf<SettingsGroupItem>()

    /** One piece of the group; [visible] false folds it away (with an expand/shrink). */
    fun item(
        visible: Boolean = true,
        content: @Composable () -> Unit,
    ) {
        items += SettingsGroupItem(visible, content)
    }
}

internal class SettingsGroupItem(
    val visible: Boolean,
    val content: @Composable () -> Unit,
)

@Composable
private fun SettingsGroupPiece(
    visible: Boolean,
    position: CardGroupPosition?,
    hasSeamAbove: Boolean,
    content: @Composable () -> Unit,
) {
    // A piece on its way out keeps the shape it had, rather than snapping square as it shrinks.
    var shownPosition by remember { mutableStateOf(position ?: CardGroupPosition.SINGLE) }
    if (position != null) shownPosition = position
    val shape = shownPosition.animatedShape(SettingsCardRadius)
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
    ) {
        Card(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(top = if (hasSeamAbove) ConnectedCardGap else 0.dp),
            shape = shape,
            border = null,
            colors =
                CardDefaults.cardColors(
                    containerColor = glassCardColor(MaterialTheme.colorScheme.surfaceContainer),
                ),
            elevation = glassCardElevation(),
        ) {
            content()
        }
    }
}

private val SettingsCardRadius = 24.dp

@Composable
internal fun SettingsItem(
    icon: Int,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailingIcon: Int? = null,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = SettingsRowMinHeight)
                .clickable(onClick = onClick)
                .padding(SettingsItemPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SettingsIcon(icon = icon)
        Spacer(modifier = Modifier.width(16.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        trailingIcon?.let {
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                painter = painterResource(id = it),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
internal fun SettingsSwitchItem(
    icon: Int,
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = SettingsRowMinHeight)
                .padding(SettingsItemPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SettingsIcon(icon = icon)
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.semantics { contentDescription = title },
        )
    }
}

/**
 * A row that does two things, the way Android's own Settings does it: the switch turns the
 * feature on or off, and the rest of the row opens its own screen. The divider shows where one
 * target ends and the other begins.
 */
@Composable
internal fun SettingsSplitSwitchItem(
    icon: Int,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitleColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    Row(
        modifier = modifier.fillMaxWidth().height(IntrinsicSize.Min),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier =
                Modifier
                    .weight(1f)
                    .heightIn(min = SettingsRowMinHeight)
                    .clickable(onClick = onClick)
                    .padding(SettingsItemPadding),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SettingsIcon(icon = icon)
            Spacer(modifier = Modifier.width(16.dp))
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = subtitleColor,
                )
            }
        }
        VerticalDivider(
            modifier = Modifier.padding(vertical = 20.dp),
            color = MaterialTheme.colorScheme.outlineVariant,
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.padding(horizontal = 16.dp).semantics { contentDescription = title },
        )
    }
}

/**
 * Rows sized like Android's own Settings: at least [SettingsRowMinHeight] tall, whether they hold
 * one line beside a switch or a title over a summary, with room above and below rather than around.
 */
internal val SettingsItemPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
internal val SettingsRowMinHeight = 72.dp
internal val SettingsSectionContentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp)

@Composable
internal fun ColorDot(
    color: Color,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .size(14.dp)
                .clip(CircleShape)
                .background(color),
    )
}

@Composable
internal fun SettingsIcon(icon: Int) {
    Box(
        modifier = Modifier.size(40.dp),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(id = icon),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp),
        )
    }
}
