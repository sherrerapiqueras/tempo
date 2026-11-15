package com.mandrecode.tempo.features.tasks.presentation

import androidx.lifecycle.viewModelScope
import com.mandrecode.tempo.R
import com.mandrecode.tempo.core.ui.util.toUserFacingMessage
import com.mandrecode.tempo.features.tasks.domain.model.Category
import com.mandrecode.tempo.features.tasks.domain.model.Task
import com.mandrecode.tempo.features.tasks.domain.model.TaskSection
import com.mandrecode.tempo.features.tasks.presentation.model.ActiveGroupKey
import com.mandrecode.tempo.features.tasks.presentation.model.CompletedGroupKey
import com.mandrecode.tempo.features.tasks.presentation.model.ReorderableRun
import com.mandrecode.tempo.features.tasks.presentation.model.SectionedTaskList
import com.mandrecode.tempo.features.tasks.presentation.model.SortOption
import com.mandrecode.tempo.features.tasks.presentation.model.TaskSortCriteria
import com.mandrecode.tempo.features.tasks.presentation.model.buildReorderableRuns
import com.mandrecode.tempo.features.tasks.presentation.model.buildSectionedList
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toPersistentList
import kotlinx.collections.immutable.toPersistentMap
import kotlinx.collections.immutable.toPersistentSet
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal fun TasksViewModel.selectCategory(categoryId: Long) {
    val sortOption = tasksScreenPreferencesRepository.getSortOption(categoryId)
    tasksScreenPreferencesRepository.setSelectedCategoryId(categoryId)
    mutableUiState.update { it.copy(selectedCategoryId = categoryId, sortOption = sortOption) }
}

/**
 * Selects the default category, falling back to the first one the same way [loadData] does when the
 * selection is missing. Does nothing until categories have loaded, or when it is already selected.
 */
internal fun TasksViewModel.showDefaultCategory() {
    val state = mutableUiState.value
    val defaultCategoryId =
        state.categories.firstOrNull { it.isDefault }?.id
            ?: state.categories.firstOrNull()?.id
            ?: return
    if (defaultCategoryId != state.selectedCategoryId) {
        selectCategory(defaultCategoryId)
    }
}

internal fun TasksViewModel.setSortOption(sortOption: SortOption) {
    val categoryId = mutableUiState.value.selectedCategoryId
    tasksScreenPreferencesRepository.setSortOption(categoryId, sortOption)
    mutableUiState.update { it.copy(sortOption = sortOption) }
}

private data class CalculatedData(
    val tasks: ImmutableList<Task>,
    val categories: ImmutableList<Category>,
    val counts: ImmutableMap<Long, Int>,
    val activeTaskGroups: ImmutableMap<ActiveGroupKey, ImmutableList<Task>>,
    val completedTaskGroups: ImmutableMap<CompletedGroupKey, ImmutableList<Task>>,
    val subtasksMap: ImmutableMap<Long, ImmutableList<Task>>,
    val reorderableRuns: ImmutableMap<Long, ReorderableRun>,
    val effectiveSelectedId: Long,
    val sectionedList: SectionedTaskList?,
    val sectionsByCategory: ImmutableMap<Long, ImmutableList<TaskSection>>,
)

private fun <K, V> Map<K, List<V>>.toImmutableListMap(): ImmutableMap<K, ImmutableList<V>> =
    entries
        .associate { (key, values) -> key to values.toPersistentList() }
        .toPersistentMap()

internal fun TasksViewModel.loadData() {
    viewModelScope.launch {
        combine(
            combine(taskRepository.getAllTasks(), sectionUseCases.repository.getAllSections(), ::Pair),
            categoryRepository.getAllCategories(),
            mutableUiState.map { it.selectedCategoryId }.distinctUntilChanged(),
            mutableUiState.map { it.sortOption }.distinctUntilChanged(),
            // Only a trigger: the day itself is read from the clock below, which may also have
            // moved backward (a time-zone or clock correction).
            currentDay,
        ) { (tasks, allSections), dbCategories, selectedCategoryId, sortOption, _ ->
            val allCategories = dbCategories.toPersistentList()

            // With no categories (still loading, or none exist) there is nothing to select: keep the
            // current selection instead of inventing an id, which would also be stored as the choice.
            val effectiveSelectedId =
                when {
                    allCategories.isEmpty() || allCategories.any { it.id == selectedCategoryId } ->
                        selectedCategoryId
                    else -> allCategories.firstOrNull { it.isDefault }?.id ?: allCategories.first().id
                }

            val uncompletedTasksCounts =
                tasks
                    .filter { !it.isCompleted && it.parentTaskId == null }
                    .groupingBy { it.categoryId }
                    .eachCount()
                    .toPersistentMap()

            val filteredTasks =
                tasks
                    .filter { it.categoryId == effectiveSelectedId }
                    .sortedWith(
                        compareBy<Task> { it.isCompleted }
                            .then(TaskSortCriteria.comparator(sortOption)),
                    )

            val today = systemToday()

            val parentTasks = filteredTasks.filter { it.parentTaskId == null }
            val (activeTasks, completedTasks) = parentTasks.partition { !it.isCompleted }

            val activeTaskGroups: ImmutableMap<ActiveGroupKey, ImmutableList<Task>> =
                when (sortOption) {
                    SortOption.BY_DATE ->
                        run {
                            val byDateGroups: Map<ActiveGroupKey, List<Task>> =
                                activeTasks
                                    .groupBy { task ->
                                        when {
                                            task.reminderDate == null ->
                                                ActiveGroupKey.ByDate(null)

                                            task.reminderDate.date < today ->
                                                ActiveGroupKey.Overdue

                                            else ->
                                                ActiveGroupKey.ByDate(task.reminderDate.date)
                                        }
                                    }
                            byDateGroups.toImmutableListMap()
                        }

                    SortOption.BY_PRIORITY ->
                        run {
                            val byPriorityGroups: Map<ActiveGroupKey, List<Task>> =
                                activeTasks
                                    .groupBy { ActiveGroupKey.ByPriority(it.priority) }
                            byPriorityGroups.toImmutableListMap()
                        }

                    SortOption.BY_TITLE ->
                        if (activeTasks.isEmpty()) {
                            persistentMapOf()
                        } else {
                            persistentMapOf(ActiveGroupKey.ByTitle to activeTasks.toPersistentList())
                        }

                    SortOption.MANUAL ->
                        if (activeTasks.isEmpty()) {
                            persistentMapOf()
                        } else {
                            persistentMapOf(ActiveGroupKey.Flat to activeTasks.toPersistentList())
                        }
                }

            val completedTaskGroups: ImmutableMap<CompletedGroupKey, ImmutableList<Task>> =
                when (sortOption) {
                    SortOption.BY_DATE ->
                        run {
                            val byDateGroups: Map<CompletedGroupKey, List<Task>> =
                                completedTasks
                                    .sortedByDescending { it.completedAt }
                                    .groupBy { CompletedGroupKey.ByDate(it.completedAt?.date) }
                            byDateGroups.toImmutableListMap()
                        }

                    SortOption.BY_PRIORITY ->
                        run {
                            val byPriorityGroups: Map<CompletedGroupKey, List<Task>> =
                                completedTasks
                                    .sortedWith(compareBy(nullsLast()) { it.priority?.sortOrder })
                                    .groupBy { CompletedGroupKey.ByPriority(it.priority) }
                            byPriorityGroups.toImmutableListMap()
                        }

                    SortOption.BY_TITLE ->
                        if (completedTasks.isEmpty()) {
                            persistentMapOf()
                        } else {
                            persistentMapOf(CompletedGroupKey.ByTitle to completedTasks.toPersistentList())
                        }

                    SortOption.MANUAL ->
                        if (completedTasks.isEmpty()) {
                            persistentMapOf()
                        } else {
                            persistentMapOf(CompletedGroupKey.Flat to completedTasks.toPersistentList())
                        }
                }

            val subtasksMap: ImmutableMap<Long, ImmutableList<Task>> =
                tasks
                    .asSequence()
                    .filter { it.categoryId == effectiveSelectedId }
                    .mapNotNull { task -> task.parentTaskId?.let { parentId -> parentId to task } }
                    .groupBy(
                        keySelector = { (parentId, _) -> parentId },
                        valueTransform = { (_, task) -> task },
                    ).mapValues { (_, subtasks) ->
                        subtasks.sortedWith(
                            compareBy<Task> { it.sortOrder }
                                .thenBy { it.id },
                        )
                    }.toImmutableListMap()

            val sectionsByCategory = allSections.groupBy { it.categoryId }.toImmutableListMap()
            // Sections replace the sort's group headers (#61), so reorderable runs are cut per
            // section instead of per sort group.
            val sectionedList =
                buildSectionedList(
                    activeTasks = activeTasks,
                    sections = sectionsByCategory[effectiveSelectedId].orEmpty(),
                    today = today,
                    sectionsEnabled = allCategories.any { it.id == effectiveSelectedId && it.sectionsEnabled },
                )
            val reorderGroups =
                sectionedList?.let { list -> listOf(list.unsectioned) + list.sections.map { it.tasks } }
                    ?: activeTaskGroups.values

            CalculatedData(
                tasks.toPersistentList(),
                allCategories,
                uncompletedTasksCounts,
                activeTaskGroups,
                completedTaskGroups,
                subtasksMap,
                buildReorderableRuns(reorderGroups, sortOption),
                effectiveSelectedId,
                sectionedList,
                sectionsByCategory,
            )
        }.flowOn(defaultDispatcher)
            .catch { exception ->
                if (exception is CancellationException) {
                    throw exception
                }
                mutableUiState.update { it.copy(isLoading = false) }
                showSnackbar(
                    R.string.msg_error_loading_data,
                    listOf(exception.toUserFacingMessage()),
                )
            }.collect { data ->
                val categoryChanged =
                    data.effectiveSelectedId != mutableUiState.value.selectedCategoryId
                val sortOption =
                    if (categoryChanged) {
                        tasksScreenPreferencesRepository.getSortOption(
                            data.effectiveSelectedId,
                        )
                    } else {
                        mutableUiState.value.sortOption
                    }

                // Collapsed ids of sections that went with a category or an import are dropped once, on
                // the first load: pruning later could race an undo that is re-collapsing a section.
                val isFirstLoad = mutableUiState.value.isLoading
                val existingSectionIds =
                    data.sectionsByCategory.values.flatMapTo(mutableSetOf()) { sections -> sections.map { it.id } }
                if (isFirstLoad) tasksScreenPreferencesRepository.pruneCollapsedSections(existingSectionIds)

                mutableUiState.update { currentState ->
                    currentState.copy(
                        collapsedSectionIds =
                            if (isFirstLoad) {
                                currentState.collapsedSectionIds.filter { it in existingSectionIds }.toPersistentSet()
                            } else {
                                currentState.collapsedSectionIds
                            },
                        tasks = data.tasks,
                        categories = data.categories,
                        uncompletedTasksCounts = data.counts,
                        activeTasks = data.activeTaskGroups,
                        completedTaskGroups = data.completedTaskGroups,
                        subtasksMap = data.subtasksMap,
                        reorderableRuns = data.reorderableRuns,
                        sectionedList = data.sectionedList,
                        sectionsByCategory = data.sectionsByCategory,
                        selectedCategoryId = data.effectiveSelectedId,
                        sortOption = sortOption,
                        isLoading = false,
                    )
                }
                if (categoryChanged) {
                    tasksScreenPreferencesRepository.setSelectedCategoryId(
                        data.effectiveSelectedId,
                    )
                }
            }
    }
}
