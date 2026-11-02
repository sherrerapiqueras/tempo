package com.mandrecode.tempo.features.tasks.presentation

import com.google.common.truth.Truth.assertThat
import com.mandrecode.tempo.features.tasks.domain.model.Task
import com.mandrecode.tempo.features.tasks.domain.model.TaskSection
import com.mandrecode.tempo.features.tasks.presentation.model.SectionUi
import com.mandrecode.tempo.features.tasks.presentation.model.SectionedTaskList
import kotlinx.collections.immutable.persistentListOf
import org.junit.Test

/** The Large-and-up section shortcuts (#61): which section is on screen, and where one starts. */
class SectionScrollSpyTest {
    private fun task(id: Long) = Task(id = id, title = "Task $id", description = "", categoryId = 3)

    private val cat = TaskSection(id = 10, categoryId = 3, name = "Cat")
    private val chores = TaskSection(id = 11, categoryId = 3, name = "Home chores")
    private val garden = TaskSection(id = 12, categoryId = 3, name = "Garden")
    private val list =
        SectionedTaskList(
            unsectioned = persistentListOf(task(1)),
            sections =
                persistentListOf(
                    SectionUi(cat, persistentListOf(task(2), task(3)), 0),
                    SectionUi(chores, persistentListOf(task(4)), 0),
                    SectionUi(garden, persistentListOf(), 0),
                ),
        )

    @Test
    fun `list keys map to their section`() {
        assertThat(listKeySection(1L, list)).isEqualTo(SectionKey.Of(null))
        assertThat(listKeySection(3L, list)).isEqualTo(SectionKey.Of(cat.id))
        assertThat(listKeySection(sectionHeaderKey(chores.id), list)).isEqualTo(SectionKey.Of(chores.id))
        assertThat(listKeySection("section_empty_${garden.id}", list)).isEqualTo(SectionKey.Of(garden.id))
        assertThat(listKeySection("completed_separator", list)).isEqualTo(SectionKey.Unknown)
    }

    @Test
    fun `header index follows the emitted order`() {
        // [1] [Cat] [2] [3] [Home chores] [4] [Garden] [empty]
        assertThat(sectionHeaderListIndex(list, emptySet(), null, cat.id)).isEqualTo(1)
        assertThat(sectionHeaderListIndex(list, emptySet(), null, chores.id)).isEqualTo(4)
        assertThat(sectionHeaderListIndex(list, emptySet(), null, garden.id)).isEqualTo(6)
    }

    @Test
    fun `collapsed sections and a new-section field shift the headers below them`() {
        // [1] [Cat] [new field] [Home chores] [Garden] ...
        val index = sectionHeaderListIndex(list, setOf(cat.id, chores.id), NewSectionDraft.After(cat.id), garden.id)

        assertThat(index).isEqualTo(4)
    }
}
