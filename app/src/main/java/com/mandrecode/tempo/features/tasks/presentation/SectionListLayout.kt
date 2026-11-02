package com.mandrecode.tempo.features.tasks.presentation

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.mandrecode.tempo.features.tasks.domain.model.Task
import com.mandrecode.tempo.features.tasks.presentation.model.SectionDropAction
import com.mandrecode.tempo.features.tasks.presentation.model.SectionedTaskList

// The layout rules behind the sectioned Tasks list (#61) — item keys, where a section starts,
// which section is on screen, what a drop means — kept apart from the composables that draw it
// so they are plain, unit-tested Kotlin.

/** Where a section being named will go: last in the category, or right after a section. */
internal sealed interface NewSectionDraft {
    data object AtEnd : NewSectionDraft

    data class After(
        val sectionId: Long,
    ) : NewSectionDraft
}

/** The Tasks list's transient section editing state (#61): what is being named or reordered. */
@Stable
internal class SectionEditState {
    var renamingSectionId by mutableStateOf<Long?>(null)
    var newSectionDraft by mutableStateOf<NewSectionDraft?>(null)
    var isReorderSheetVisible by mutableStateOf(false)
}

internal const val SECTION_HEADER_KEY_PREFIX = "section_header_"
internal const val SECTION_EMPTY_KEY_PREFIX = "section_empty_"

/** The lazy-list key of a section's header, also what the shortcut group scrolls to. */
internal fun sectionHeaderKey(sectionId: Long): String = "$SECTION_HEADER_KEY_PREFIX$sectionId"

/** Which section a list item belongs to, for the shortcut group's scroll spy. */
internal sealed interface SectionKey {
    data class Of(
        val sectionId: Long?,
    ) : SectionKey

    data object Unknown : SectionKey
}

/** The section a list item key belongs to, for the shortcut group's scroll spy. */
internal fun listKeySection(
    key: Any?,
    list: SectionedTaskList,
): SectionKey =
    when {
        key is Long && list.unsectioned.any { it.id == key } -> SectionKey.Of(null)
        key is Long ->
            list.sections.firstOrNull { ui -> ui.tasks.any { it.id == key } }?.let { SectionKey.Of(it.section.id) }
                ?: SectionKey.Unknown
        key is String && key.startsWith(SECTION_HEADER_KEY_PREFIX) ->
            SectionKey.Of(key.removePrefix(SECTION_HEADER_KEY_PREFIX).toLongOrNull())
        key is String && key.startsWith(SECTION_EMPTY_KEY_PREFIX) ->
            SectionKey.Of(key.removePrefix(SECTION_EMPTY_KEY_PREFIX).toLongOrNull())
        else -> SectionKey.Unknown
    }

/**
 * Where [sectionId]'s header sits in the list, mirroring [sectionedTaskItems]'s order:
 * unsectioned tasks, then per section its header, its tasks or placeholder unless collapsed, and a
 * new-section field placed after it.
 */
internal fun sectionHeaderListIndex(
    list: SectionedTaskList,
    collapsedSectionIds: Set<Long>,
    newSectionDraft: NewSectionDraft?,
    sectionId: Long,
): Int {
    var index = list.unsectioned.size
    for (sectionUi in list.sections) {
        val id = sectionUi.section.id
        if (id == sectionId) return index
        index += 1 + if (id in collapsedSectionIds) 0 else sectionUi.tasks.size.coerceAtLeast(1)
        if ((newSectionDraft as? NewSectionDraft.After)?.sectionId == id) index++
    }
    return 0
}

/** The event a drop turns into: a reorder inside the start section, a move anywhere else. */
internal fun sectionDropEvent(
    action: SectionDropAction,
    task: Task,
    sectionTasks: List<Task>,
): TasksContract.UiEvent =
    when (action) {
        is SectionDropAction.Reorder ->
            TasksContract.UiEvent.ReorderTasks(action.fromIndex, action.toIndex, sectionTasks)
        is SectionDropAction.Move -> TasksContract.UiEvent.MoveTaskToSection(task.id, action.sectionId, action.index)
    }
