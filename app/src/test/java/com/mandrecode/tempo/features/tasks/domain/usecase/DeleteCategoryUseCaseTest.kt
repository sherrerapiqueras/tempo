package com.mandrecode.tempo.features.tasks.domain.usecase

import com.google.common.truth.Truth.assertThat
import com.mandrecode.tempo.features.tasks.TEST_INBOX_CATEGORY
import com.mandrecode.tempo.features.tasks.domain.model.Category
import com.mandrecode.tempo.features.tasks.domain.model.CategoryDeletionSnapshot
import com.mandrecode.tempo.features.tasks.domain.repository.CategoryRepository
import com.mandrecode.tempo.features.tasks.domain.scheduler.TaskReminderScheduler
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

class DeleteCategoryUseCaseTest {
    private lateinit var useCase: DeleteCategoryUseCase
    private lateinit var categoryRepository: CategoryRepository
    private lateinit var taskReminderScheduler: TaskReminderScheduler

    @Before
    fun setup() {
        categoryRepository = mockk(relaxed = true)
        taskReminderScheduler = mockk(relaxed = true)
        coEvery { categoryRepository.getCategoryCount() } returns 3
        useCase = DeleteCategoryUseCase(categoryRepository, taskReminderScheduler)
    }

    @Test
    fun `deletes tasks by category then deletes category`() =
        runTest {
            val category = Category(id = 5L, name = "Work")
            val snapshot = CategoryDeletionSnapshot(category, emptyList())
            coEvery { categoryRepository.deleteCategoryWithSnapshot(category) } returns snapshot

            val result = useCase(category)

            assertThat(result).isEqualTo(DeleteCategoryUseCase.Result.Success(snapshot))
            coVerify { categoryRepository.deleteCategoryWithSnapshot(category) }
        }

    @Test
    fun `returns LastCategory when only one category exists`() =
        runTest {
            coEvery { categoryRepository.getCategoryCount() } returns 1

            val result = useCase(Category(id = 5L, name = "Work"))

            assertThat(result).isEqualTo(DeleteCategoryUseCase.Result.LastCategory)
            coVerify(exactly = 0) { categoryRepository.deleteCategoryWithSnapshot(any()) }
        }

    @Test
    fun `returns CannotDeleteDefault when deleting Inbox as default`() =
        runTest {
            val result = useCase(TEST_INBOX_CATEGORY)

            assertThat(result).isEqualTo(DeleteCategoryUseCase.Result.CannotDeleteDefault)
            coVerify(exactly = 0) { categoryRepository.deleteCategoryWithSnapshot(any()) }
        }

    @Test
    fun `returns CannotDeleteDefault when deleting default category`() =
        runTest {
            val result = useCase(Category(id = 5L, name = "Work", isDefault = true))

            assertThat(result).isEqualTo(DeleteCategoryUseCase.Result.CannotDeleteDefault)
            coVerify(exactly = 0) { categoryRepository.deleteCategoryWithSnapshot(any()) }
        }
}
