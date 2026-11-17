package com.mandrecode.tempo.core.data.local

import android.content.Context
import android.content.res.Configuration
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.mandrecode.tempo.core.domain.model.DEFAULT_INBOX_UID
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class DefaultCategoryCallbackTest {
    @Test
    fun renamedDefaultCategoryNameIsPreservedAcrossSpanishReopen() =
        runTest {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val englishContext = context.withLocale(Locale.ENGLISH)
            val spanishContext = context.withLocale(Locale.forLanguageTag("es"))
            val databaseName = "default_category_callback_test_${UUID.randomUUID()}"
            context.deleteDatabase(databaseName)

            try {
                val firstDatabase =
                    Room
                        .databaseBuilder(englishContext, TempoDatabase::class.java, databaseName)
                        .addCallback(TempoDatabase.inboxCallback(englishContext))
                        .addMigrations(*TempoDatabase.MIGRATIONS)
                        .build()
                try {
                    val existingDefaultCategory =
                        firstDatabase.categoryDao().getAllCategoriesSync().singleOrNull {
                            it.uid ==
                                DEFAULT_INBOX_UID
                        }
                    assertNotNull(
                        "Expected the seeded Inbox to exist before renaming in test setup.",
                        existingDefaultCategory,
                    )
                    firstDatabase.categoryDao().updateCategory(
                        existingDefaultCategory!!.copy(name = LEGACY_CATEGORY_NAME),
                    )
                } finally {
                    firstDatabase.close()
                }

                val reopenedDatabase =
                    Room
                        .databaseBuilder(spanishContext, TempoDatabase::class.java, databaseName)
                        .addCallback(TempoDatabase.inboxCallback(spanishContext))
                        .addMigrations(*TempoDatabase.MIGRATIONS)
                        .build()
                try {
                    val category = reopenedDatabase.categoryDao().getAllCategoriesSync().singleOrNull { it.uid == DEFAULT_INBOX_UID }
                    val localizedCategoryName = spanishContext.getString(com.mandrecode.tempo.R.string.category_inbox)

                    assertThat(localizedCategoryName).isNotEqualTo(LEGACY_CATEGORY_NAME)
                    assertThat(category).isNotNull()
                    category?.let {
                        assertThat(it.name).isEqualTo(LEGACY_CATEGORY_NAME)
                        assertThat(it.isDefault).isTrue()
                    }
                } finally {
                    reopenedDatabase.close()
                }
            } finally {
                context.deleteDatabase(databaseName)
            }
        }

    private fun Context.withLocale(locale: Locale): Context {
        val configuration = Configuration(resources.configuration)
        configuration.setLocale(locale)
        return createConfigurationContext(configuration)
    }

    private companion object {
        const val LEGACY_CATEGORY_NAME = "Inbox"
    }
}
