package com.mandrecode.tempo.features.backup.domain.util

import com.mandrecode.tempo.features.backup.domain.model.BackupData
import com.mandrecode.tempo.features.backup.domain.model.BackupEntityKind
import com.mandrecode.tempo.features.backup.domain.model.ConflictReason
import com.mandrecode.tempo.features.backup.domain.model.ImportConflict
import com.mandrecode.tempo.features.backup.domain.model.ImportCounts
import com.mandrecode.tempo.features.backup.domain.model.ImportSummary
import com.mandrecode.tempo.features.backup.domain.model.MergePlan
import com.mandrecode.tempo.features.routines.domain.model.Habit
import com.mandrecode.tempo.features.routines.domain.model.HabitChain
import com.mandrecode.tempo.features.tasks.domain.model.Category
import com.mandrecode.tempo.features.tasks.domain.model.Task
import com.mandrecode.tempo.features.tasks.domain.model.TaskSection
import jakarta.inject.Inject

/**
 * Classifies every incoming record of a merge import as new (insert), exact
 * duplicate (skip, reuse local row) or conflict (skip, keep local row, report),
 * and produces the id-remapping plan the executor applies. Pure Kotlin — all
 * decisions are made against an in-memory snapshot of local data.
 *
 * Natural keys: category → name; task → title within its resolved category and
 * parent; habit → title; habit chain → title. Content comparison ignores ids
 * and sort orders. Records depending on a conflicted record cascade to
 * conflicts themselves ([ConflictReason.PARENT_CONFLICTED]).
 */
class MergePlanner
    @Inject
    constructor() {
        fun plan(
            incoming: BackupData,
            local: BackupData,
        ): MergePlan {
            val conflicts = mutableListOf<ImportConflict>()
            val categories = planCategories(incoming, local, conflicts)
            val tasks = planTasks(incoming, local, categories, conflicts)
            val habits = planHabits(incoming, local, conflicts)
            val chains = planChains(incoming, local, conflicts)
            val sections = planSections(incoming, local, categories)
            val memberships =
                incoming.chainMemberships.filter { it.chainId in chains.insertedIds }
            return MergePlan(
                categoriesToInsert = categories.toInsert,
                categoryIdMap = categories.idMap,
                tasksToInsert = tasks.toInsert,
                taskIdMap = tasks.idMap,
                habitsToInsert = habits.toInsert,
                habitIdMap = habits.idMap,
                chainsToInsert = chains.toInsert,
                chainIdMap = chains.idMap,
                membershipsToInsert = memberships,
                sectionsToInsert = sections.toInsert,
                sectionIdMap = sections.idMap,
                sectionAliases = sections.aliases,
                summary =
                    ImportSummary(
                        categories = categories.counts,
                        tasks = tasks.counts,
                        habits = habits.counts,
                        habitChains = chains.counts,
                        conflicts = conflicts,
                    ),
            )
        }

        /** Mutable accumulation state for one entity kind. */
        private class EntityPlan<T> {
            val toInsert = mutableListOf<T>()
            val idMap = mutableMapOf<Long, Long>()
            val insertedIds = mutableSetOf<Long>()
            val conflictedIds = mutableSetOf<Long>()
            var imported = 0
            var skipped = 0
            var conflicted = 0

            val counts: ImportCounts
                get() = ImportCounts(imported = imported, skipped = skipped, conflicts = conflicted)
        }

        private fun planCategories(
            incoming: BackupData,
            local: BackupData,
            conflicts: MutableList<ImportConflict>,
        ): EntityPlan<Category> {
            val plan = EntityPlan<Category>()
            val localByName = local.categories.associateBy { it.name }
            var nextSortOrder = (local.categories.maxOfOrNull { it.sortOrder } ?: -1) + 1
            incoming.categories.sortedBy { it.sortOrder }.forEach { category ->
                val match = localByName[category.name]
                when {
                    match == null -> {
                        plan.toInsert += category.copy(isDefault = false, sortOrder = nextSortOrder++)
                        plan.insertedIds += category.id
                        plan.imported++
                    }

                    category.contentKey() == match.contentKey() -> {
                        plan.idMap[category.id] = match.id
                        plan.skipped++
                    }

                    else -> {
                        plan.idMap[category.id] = match.id
                        plan.conflictedIds += category.id
                        plan.conflicted++
                        conflicts +=
                            ImportConflict(
                                BackupEntityKind.CATEGORY,
                                category.name,
                                ConflictReason.CONTENT_MISMATCH,
                            )
                    }
                }
            }
            return plan
        }

        /**
         * Sections (#61) carry no content to conflict on: one matching a same-named section of
         * its resolved local category is reused, anything else is appended to that category.
         * Sections of conflicted categories still resolve to the local category, like tasks do.
         */
        private fun planSections(
            incoming: BackupData,
            local: BackupData,
            categories: EntityPlan<Category>,
        ): SectionPlan {
            val toInsert = mutableListOf<TaskSection>()
            val idMap = mutableMapOf<Long, Long>()
            val aliases = mutableMapOf<Long, Long>()
            // Same-named incoming sections of one category are one section, like local ones.
            val firstByName = mutableMapOf<Pair<Long, String>, Long>()
            val localByCategory = local.sections.groupBy { it.categoryId }
            val nextSortOrder =
                localByCategory.mapValues { (_, sections) -> sections.maxOf { it.sortOrder } + 1 }.toMutableMap()
            incoming.sections.sortedBy { it.sortOrder }.forEach { section ->
                val canonical = firstByName.getOrPut(section.categoryId to section.name) { section.id }
                if (canonical != section.id) {
                    aliases[section.id] = canonical
                    return@forEach
                }
                val localCategoryId = categories.idMap[section.categoryId]
                val match =
                    localCategoryId?.let { id -> localByCategory[id]?.firstOrNull { it.name == section.name } }
                when {
                    match != null -> idMap[section.id] = match.id
                    localCategoryId != null -> {
                        val sortOrder = nextSortOrder[localCategoryId] ?: 0
                        nextSortOrder[localCategoryId] = sortOrder + 1
                        toInsert += section.copy(sortOrder = sortOrder)
                    }
                    section.categoryId in categories.insertedIds -> toInsert += section
                }
            }
            return SectionPlan(toInsert, idMap, aliases)
        }

        private fun planTasks(
            incoming: BackupData,
            local: BackupData,
            categories: EntityPlan<Category>,
            conflicts: MutableList<ImportConflict>,
        ): EntityPlan<Task> {
            val plan = EntityPlan<Task>()
            // Grouped once so sibling lookup below is a map read instead of a full
            // scan of local.tasks per incoming task (avoids O(incoming × local)).
            val localByScope = local.tasks.groupBy { it.scope() }
            val nextSortOrders = mutableMapOf<TaskScope, Int>()
            forEachTaskParentsFirst(incoming.tasks) { task ->
                // The local scope an incoming task would land in (matched local
                // parent, or matched local category for top-level tasks). A null
                // scope means the scope itself is newly inserted, so no local
                // sibling can exist and the task is a plain insert.
                val parentId = task.parentTaskId
                when {
                    parentId != null && parentId in plan.conflictedIds ->
                        cascadeConflict(plan, conflicts, task)

                    parentId != null && parentId in plan.insertedIds ->
                        insertTask(plan, task, scope = null, nextSortOrders)

                    else -> {
                        val scope =
                            if (parentId != null) {
                                plan.idMap[parentId]?.let { TaskScope(parentId = it, categoryId = null) }
                            } else {
                                categories.idMap[task.categoryId]
                                    ?.let { TaskScope(parentId = null, categoryId = it) }
                            }
                        if (scope == null) {
                            insertTask(plan, task, scope = null, nextSortOrders)
                        } else {
                            classifyScopedTask(
                                plan,
                                conflicts,
                                task,
                                localByScope[scope].orEmpty(),
                                scope,
                                nextSortOrders,
                            )
                        }
                    }
                }
            }
            return plan
        }

        private fun Task.scope(): TaskScope =
            if (parentTaskId != null) {
                TaskScope(parentId = parentTaskId, categoryId = null)
            } else {
                TaskScope(parentId = null, categoryId = categoryId)
            }

        private fun classifyScopedTask(
            plan: EntityPlan<Task>,
            conflicts: MutableList<ImportConflict>,
            task: Task,
            siblings: List<Task>,
            scope: TaskScope,
            nextSortOrders: MutableMap<TaskScope, Int>,
        ) {
            val match = siblings.firstOrNull { it.title == task.title }
            when {
                match == null -> insertTask(plan, task, scope, nextSortOrders, siblings)
                task.contentKey() == match.contentKey() -> {
                    plan.idMap[task.id] = match.id
                    plan.skipped++
                }

                else -> {
                    plan.idMap[task.id] = match.id
                    plan.conflictedIds += task.id
                    plan.conflicted++
                    conflicts +=
                        ImportConflict(
                            BackupEntityKind.TASK,
                            task.title,
                            ConflictReason.CONTENT_MISMATCH,
                        )
                }
            }
        }

        private fun insertTask(
            plan: EntityPlan<Task>,
            task: Task,
            scope: TaskScope?,
            nextSortOrders: MutableMap<TaskScope, Int>,
            siblings: List<Task> = emptyList(),
        ) {
            val toInsert =
                if (scope != null) {
                    val next =
                        nextSortOrders.getOrPut(scope) {
                            (siblings.maxOfOrNull { it.sortOrder } ?: -1) + 1
                        }
                    nextSortOrders[scope] = next + 1
                    task.copy(sortOrder = next)
                } else {
                    task
                }
            plan.toInsert += toInsert
            plan.insertedIds += task.id
            plan.imported++
        }

        private fun cascadeConflict(
            plan: EntityPlan<Task>,
            conflicts: MutableList<ImportConflict>,
            task: Task,
        ) {
            plan.conflictedIds += task.id
            plan.conflicted++
            conflicts +=
                ImportConflict(
                    BackupEntityKind.TASK,
                    task.title,
                    ConflictReason.PARENT_CONFLICTED,
                )
        }

        /** Visits tasks so every parent is visited before its children, in stable sort order. */
        private fun forEachTaskParentsFirst(
            tasks: List<Task>,
            visit: (Task) -> Unit,
        ) {
            val childrenByParent = tasks.filter { it.parentTaskId != null }.groupBy { it.parentTaskId }
            val queue =
                ArrayDeque(
                    tasks
                        .filter { it.parentTaskId == null }
                        .sortedWith(compareBy({ it.categoryId }, { it.sortOrder })),
                )
            while (queue.isNotEmpty()) {
                val task = queue.removeFirst()
                visit(task)
                childrenByParent[task.id]
                    ?.sortedBy { it.sortOrder }
                    ?.let { queue.addAll(it) }
            }
        }

        private fun planHabits(
            incoming: BackupData,
            local: BackupData,
            conflicts: MutableList<ImportConflict>,
        ): EntityPlan<Habit> {
            val plan = EntityPlan<Habit>()
            val localByTitle = local.habits.associateBy { it.title }
            incoming.habits.forEach { habit ->
                val match = localByTitle[habit.title]
                when {
                    match == null -> {
                        plan.toInsert += habit
                        plan.insertedIds += habit.id
                        plan.imported++
                    }

                    habit.contentKey() == match.contentKey() -> {
                        plan.idMap[habit.id] = match.id
                        plan.skipped++
                    }

                    else -> {
                        plan.idMap[habit.id] = match.id
                        plan.conflictedIds += habit.id
                        plan.conflicted++
                        conflicts +=
                            ImportConflict(
                                BackupEntityKind.HABIT,
                                habit.title,
                                ConflictReason.CONTENT_MISMATCH,
                            )
                    }
                }
            }
            return plan
        }

        private fun planChains(
            incoming: BackupData,
            local: BackupData,
            conflicts: MutableList<ImportConflict>,
        ): EntityPlan<HabitChain> {
            val plan = EntityPlan<HabitChain>()
            val localByTitle = local.habitChains.associateBy { it.title }
            incoming.habitChains.forEach { chain ->
                val match = localByTitle[chain.title]
                when {
                    match == null -> {
                        plan.toInsert += chain
                        plan.insertedIds += chain.id
                        plan.imported++
                    }

                    chain.contentKey() == match.contentKey() &&
                        memberTitles(incoming, chain.id) == memberTitles(local, match.id) -> {
                        plan.idMap[chain.id] = match.id
                        plan.skipped++
                    }

                    else -> {
                        plan.idMap[chain.id] = match.id
                        plan.conflictedIds += chain.id
                        plan.conflicted++
                        conflicts +=
                            ImportConflict(
                                BackupEntityKind.HABIT_CHAIN,
                                chain.title,
                                ConflictReason.CONTENT_MISMATCH,
                            )
                    }
                }
            }
            return plan
        }

        private data class TaskScope(
            val parentId: Long?,
            val categoryId: Long?,
        )
    }

/** Ordered habit titles of a chain's members — the id-independent membership fingerprint. */
private fun memberTitles(
    data: BackupData,
    chainId: Long,
): List<String> {
    val habitTitles = data.habits.associate { it.id to it.title }
    return data.chainMemberships
        .filter { it.chainId == chainId }
        .sortedBy { it.sortOrder }
        .mapNotNull { habitTitles[it.habitId] }
}

// Whether sections are on is a view preference, like the sort order, not content to conflict on.
private fun Category.contentKey() = asNew().copy(sortOrder = 0, isDefault = false, sectionsEnabled = false)

// nextInstanceId values are database ids and meaningless across devices, but
// *having* a next-occurrence link is content: a linked archived task must not
// classify as an exact duplicate of an unlinked local task (or vice versa).
private fun Task.contentKey() =
    asNew().copy(
        sortOrder = 0,
        nextInstanceId = if (nextInstanceId == null) null else 0,
        categoryId = 0,
        parentTaskId = null,
        // Section ids are database ids too, and sections are structure rather than content.
        sectionId = null,
    )

private fun Habit.contentKey() = asNew()

private fun HabitChain.contentKey() = asNew().copy(habitIds = emptyList())

private data class SectionPlan(
    val toInsert: List<TaskSection>,
    val idMap: Map<Long, Long>,
    val aliases: Map<Long, Long>,
)
