package com.mandrecode.tempo.core.data.local.dao

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.mandrecode.tempo.core.data.entity.TaskEntity
import com.mandrecode.tempo.core.data.local.InMemoryTempoDatabaseRule
import com.mandrecode.tempo.core.domain.model.Periodicity
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TaskDaoClearRemindersTest {
    @get:Rule
    val databaseRule = InMemoryTempoDatabaseRule()

    private val dao get() = databaseRule.database.taskDao()

    @Test
    fun clearAllReminders_clearsTheEndDateWithTheRestOfTheRecurrence() =
        runTest {
            val id =
                dao.insertTask(
                    TaskEntity(
                        title = "Water plants",
                        description = "",
                        reminderDate = LocalDateTime(2099, 1, 1, 10, 0),
                        periodicity = Periodicity.DAILY,
                        periodicityEndDate = LocalDate(2099, 3, 31),
                    ),
                )

            dao.clearAllReminders()

            val task = dao.getTaskById(id)
            assertThat(task?.periodicity).isNull()
            assertThat(task?.periodicityEndDate).isNull()
        }

    @Test
    fun endDate_isStoredAndReadBack() =
        runTest {
            val id =
                dao.insertTask(
                    TaskEntity(
                        title = "Water plants",
                        description = "",
                        periodicity = Periodicity.DAILY,
                        periodicityEndDate = LocalDate(2099, 3, 31),
                    ),
                )

            assertThat(dao.getTaskById(id)?.periodicityEndDate).isEqualTo(LocalDate(2099, 3, 31))
        }
}
