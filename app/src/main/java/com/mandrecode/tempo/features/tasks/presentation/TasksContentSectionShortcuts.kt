package com.mandrecode.tempo.features.tasks.presentation

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.mandrecode.tempo.R
import com.mandrecode.tempo.core.ui.theme.LocalIsDarkTheme
import com.mandrecode.tempo.core.ui.theme.resolveColor
import com.mandrecode.tempo.features.tasks.domain.model.Category
import com.mandrecode.tempo.features.tasks.presentation.components.sections.SectionShortcut
import com.mandrecode.tempo.features.tasks.presentation.components.sections.SectionShortcutGroup
import com.mandrecode.tempo.features.tasks.presentation.model.SectionedTaskList
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.launch

/**
 * Wires the Large-and-up section shortcuts (#61) to the list: the checked segment follows the first
 * item on screen, and tapping one scrolls its section to the top.
 */
@Suppress("LongParameterList")
@Composable
internal fun TasksSectionShortcuts(
    list: SectionedTaskList,
    category: Category?,
    listState: LazyListState,
    collapsedSectionIds: Set<Long>,
    editState: SectionEditState,
    modifier: Modifier = Modifier,
) {
    val noSection = stringResource(R.string.section_none)
    val shortcuts =
        remember(list, noSection) {
            buildList {
                if (list.unsectioned.isNotEmpty()) add(SectionShortcut(null, noSection, list.unsectioned.size))
                list.sections.forEach { add(SectionShortcut(it.section.id, it.section.name, it.tasks.size)) }
            }.toPersistentList()
        }
    // The last section seen stays checked while the list shows something that belongs to none,
    // such as the Completed block.
    var lastSection by remember(list) { mutableStateOf<SectionKey>(SectionKey.Of(shortcuts.firstOrNull()?.sectionId)) }
    val checkedIndex by remember(shortcuts) {
        derivedStateOf {
            val firstKey =
                listKeySection(
                    listState.layoutInfo.visibleItemsInfo
                        .firstOrNull()
                        ?.key,
                    list,
                )
            if (firstKey is SectionKey.Of) lastSection = firstKey
            val current = (firstKey as? SectionKey.Of ?: lastSection as? SectionKey.Of)?.sectionId
            shortcuts.indexOfFirst { it.sectionId == current }
        }
    }
    val scope = rememberCoroutineScope()
    val colorScheme = MaterialTheme.colorScheme
    val isDarkTheme = LocalIsDarkTheme.current

    SectionShortcutGroup(
        categoryId = category?.id ?: 0L,
        shortcuts = shortcuts,
        checkedIndex = checkedIndex,
        categoryColor = resolveColor(category?.color, colorScheme, isDarkTheme),
        onSelect = { shortcut ->
            scope.launch {
                // No section id: the unsectioned tasks, at the top of the list.
                val index =
                    shortcut.sectionId?.let {
                        sectionHeaderListIndex(list, collapsedSectionIds, editState.newSectionDraft, it)
                    } ?: 0
                listState.animateScrollToItem(index)
            }
        },
        onAddSection = { editState.newSectionDraft = NewSectionDraft.AtEnd },
        onReorderSections = { editState.isReorderSheetVisible = true },
        modifier = modifier,
    )
}
