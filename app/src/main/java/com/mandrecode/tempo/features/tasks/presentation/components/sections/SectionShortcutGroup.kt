package com.mandrecode.tempo.features.tasks.presentation.components.sections

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mandrecode.tempo.R
import com.mandrecode.tempo.core.ui.adaptive.adaptiveHorizontalMargin
import com.mandrecode.tempo.core.ui.components.ConnectedSelectorButton
import com.mandrecode.tempo.core.ui.components.selectorButtonColors
import kotlinx.collections.immutable.ImmutableList

private const val LIGHT_CATEGORY_LUMINANCE = 0.4f

/** One segment of the section shortcut group; [sectionId] null is "No section". */
@Immutable
internal data class SectionShortcut(
    val sectionId: Long?,
    val label: String,
    val count: Int,
)

/**
 * Large-and-up section shortcuts (#61): a connected single-choice group under the category row,
 * one segment per section, the checked one filled in the category's own colour and following the
 * section scrolled into view. Add and reorder sit apart, so neither reads as a section.
 */
@Composable
internal fun SectionShortcutGroup(
    categoryId: Long,
    shortcuts: ImmutableList<SectionShortcut>,
    checkedIndex: Int,
    categoryColor: Color?,
    onSelect: (SectionShortcut) -> Unit,
    onAddSection: () -> Unit,
    onReorderSections: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Each frame of the category switch shows its own category's sections, not the new ones twice.
    AnimatedContent(
        targetState = categoryId to shortcuts,
        contentKey = { (id, _) -> id },
        transitionSpec = {
            (slideInVertically { -it / 2 } + fadeIn()) togetherWith (slideOutVertically { -it / 2 } + fadeOut())
        },
        label = "sectionShortcuts",
        modifier = modifier,
    ) { (_, shownShortcuts) ->
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = adaptiveHorizontalMargin()).padding(bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val listState = rememberLazyListState()
            LaunchedEffect(checkedIndex) { if (checkedIndex >= 0) listState.animateScrollToItem(checkedIndex) }
            val checkedColor = categoryColor ?: MaterialTheme.colorScheme.primary
            val onChecked = if (checkedColor.luminance() > LIGHT_CATEGORY_LUMINANCE) Color.Black else Color.White
            LazyRow(
                state = listState,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                contentPadding = PaddingValues(0.dp),
                modifier = Modifier.weight(1f, fill = false),
            ) {
                itemsIndexed(shownShortcuts, key = { _, shortcut -> shortcut.sectionId ?: -1L }) { index, shortcut ->
                    ConnectedSelectorButton(
                        label = "${shortcut.label} · ${shortcut.count}",
                        checked = index == checkedIndex,
                        onClick = { onSelect(shortcut) },
                        index = index,
                        count = shownShortcuts.size,
                        colors =
                            selectorButtonColors(
                                checkedContainerColor = checkedColor,
                                checkedContentColor = onChecked,
                            ),
                        modifier = Modifier.testTag("sectionShortcut_${shortcut.sectionId ?: "none"}"),
                    )
                }
            }
            FilledTonalIconButton(onClick = onAddSection) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.section_add))
            }
            FilledTonalIconButton(onClick = onReorderSections) {
                Icon(
                    painter = painterResource(R.drawable.ic_drag_handle),
                    contentDescription = stringResource(R.string.section_reorder),
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}
