package com.mandrecode.tempo.features.tasks.presentation

import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.ForcedSize
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.google.common.truth.Truth.assertThat
import com.mandrecode.tempo.core.ui.theme.TempoTheme
import com.mandrecode.tempo.features.tasks.domain.model.Category
import com.mandrecode.tempo.features.tasks.domain.model.Task
import com.mandrecode.tempo.features.tasks.domain.model.TaskSection
import com.mandrecode.tempo.features.tasks.presentation.components.sections.SectionShortcut
import com.mandrecode.tempo.features.tasks.presentation.components.sections.SectionShortcutGroup
import com.mandrecode.tempo.features.tasks.presentation.model.ActiveGroupKey
import com.mandrecode.tempo.features.tasks.presentation.model.SectionUi
import com.mandrecode.tempo.features.tasks.presentation.model.SectionedTaskList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import org.junit.Rule
import org.junit.Test

/** A sectioned category stays in one column at any pane width (#510), and the section shortcuts (#61). */
class TasksSectionedWideLayoutTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val sv = Category(id = 3, name = "S&V", isDefault = true)
    private val cat = TaskSection(id = 10, categoryId = 3, name = "Cat", sortOrder = 0)
    private val chores = TaskSection(id = 11, categoryId = 3, name = "Home chores", sortOrder = 1)

    private fun task(
        id: Long,
        title: String,
        sectionId: Long?,
    ) = Task(id = id, title = title, description = "", categoryId = sv.id, sectionId = sectionId)

    private fun setContent(width: Int) {
        val unsectioned = task(1, "Pay the internet bill", null)
        composeTestRule.setContent {
            DeviceConfigurationOverride(DeviceConfigurationOverride.ForcedSize(DpSize(width.dp, 800.dp))) {
                TempoTheme {
                    TasksContent(
                        uiState =
                            TasksContract.UiState(
                                isLoading = false,
                                categories = persistentListOf(sv),
                                selectedCategoryId = sv.id,
                                activeTasks = persistentMapOf(ActiveGroupKey.Flat to persistentListOf(unsectioned)),
                                sectionedList =
                                    SectionedTaskList(
                                        unsectioned = persistentListOf(unsectioned),
                                        sections =
                                            persistentListOf(
                                                SectionUi(cat, persistentListOf(task(2, "Buy litter", cat.id)), 0),
                                                SectionUi(chores, persistentListOf(task(3, "Descale", chores.id)), 0),
                                            ),
                                    ),
                            ),
                        onEvent = {},
                    )
                }
            }
        }
    }

    private fun assertSectionsShareOneColumn() {
        val cat = composeTestRule.onNodeWithText("Buy litter").getUnclippedBoundsInRoot()
        val chores = composeTestRule.onNodeWithText("Descale").getUnclippedBoundsInRoot()
        assertThat(chores.left).isEqualTo(cat.left)
        assertThat(chores.top).isGreaterThan(cat.bottom)
    }

    @Test
    fun widePane_keepsOneColumn() {
        setContent(width = 1000)

        assertSectionsShareOneColumn()
    }

    @Test
    fun narrowerPane_asWithADockedEditor_keepsOneColumn() {
        setContent(width = 840)

        assertSectionsShareOneColumn()
    }

    @Test
    fun shortcutGroup_checksTheCurrentSectionAndReportsTaps() {
        val tapped = mutableListOf<SectionShortcut>()
        val shortcuts =
            persistentListOf(
                SectionShortcut(null, "No section", 1),
                SectionShortcut(cat.id, "Cat", 1),
                SectionShortcut(chores.id, "Home chores", 1),
            )
        composeTestRule.setContent {
            TempoTheme {
                SectionShortcutGroup(
                    categoryId = sv.id,
                    shortcuts = shortcuts,
                    checkedIndex = 1,
                    categoryColor = null,
                    onSelect = { tapped += it },
                    onAddSection = {},
                    onReorderSections = {},
                )
            }
        }

        composeTestRule.onNodeWithTag("sectionShortcut_${cat.id}").assertIsOn()
        composeTestRule.onNodeWithTag("sectionShortcut_${chores.id}").performClick()

        assertThat(tapped.single().sectionId).isEqualTo(chores.id)
    }
}
