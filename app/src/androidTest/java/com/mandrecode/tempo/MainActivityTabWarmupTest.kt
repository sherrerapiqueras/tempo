package com.mandrecode.tempo

import android.annotation.SuppressLint
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import com.mandrecode.tempo.core.ui.navigation.tabViewModelKey
import com.mandrecode.tempo.features.focus.presentation.FocusViewModel
import com.mandrecode.tempo.features.routines.presentation.RoutinesViewModel
import com.mandrecode.tempo.features.tasks.presentation.TasksViewModel
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Every tab's data is loaded before the tab is first opened, so it appears finished on its first
 * frame instead of as its bare background with the content arriving on top (#438).
 */
@RunWith(AndroidJUnit4::class)
class MainActivityTabWarmupTest {
    @Test
    fun tabsNotYetOpened_haveTheirDataLoadedAlready() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            // Focus is the tab on screen, so its view model exists either way; wait for its data.
            // Generous deadlines: a cold first launch also creates and keys the database.
            assertThat(eventually { !tab<FocusViewModel>(scenario.activity()).uiState.value.isLoading }).isTrue()

            // Routines and Tasks have never been opened. Their view models must already exist,
            // created by the activity in the background. That is checked in the store without
            // looking them up, since a lookup would create them and prove nothing.
            val warmKeys = listOf(RoutinesViewModel::class.java, TasksViewModel::class.java).map(::tabViewModelKey)
            assertThat(eventually { scenario.activity().storedViewModelKeys().containsAll(warmKeys) }).isTrue()

            // Created in the background, so their data finishes loading without the tab opening.
            assertThat(
                eventually {
                    val activity = scenario.activity()
                    !tab<RoutinesViewModel>(activity).uiState.value.isLoading &&
                        !tab<TasksViewModel>(activity).uiState.value.isLoading
                },
            ).isTrue()
        }
    }

    /**
     * The Focus session overlay and the task editor it opens sit outside the tabs' navigation
     * entries and resolve their own view models against the activity; the tabs' must be separate
     * instances, or the overlay would drive the Focus tab's editor and share the Tasks tab's form.
     */
    @Test
    fun tabViewModels_areSeparateFromTheOverlaysOwn() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val provider = ViewModelProvider(activity)
                assertThat(tab<FocusViewModel>(activity))
                    .isNotSameInstanceAs(provider[FocusViewModel::class.java])
                assertThat(tab<TasksViewModel>(activity))
                    .isNotSameInstanceAs(provider[TasksViewModel::class.java])
            }
        }
    }

    private inline fun <reified VM : ViewModel> tab(activity: MainActivity): VM =
        ViewModelProvider(activity)[tabViewModelKey(VM::class.java), VM::class.java]

    private fun ActivityScenario<MainActivity>.activity(): MainActivity {
        var activity: MainActivity? = null
        onActivity { activity = it }
        return activity!!
    }

    // keys() is library-group API, read here only to tell "already created" from "created by this
    // lookup", which no public API can.
    @SuppressLint("RestrictedApi")
    private fun MainActivity.storedViewModelKeys(): Set<String> = viewModelStore.keys().filterIsInstance<String>().toSet()

    /** Polls [condition] on the main thread until it holds or [WAIT_MS] passes. */
    private fun eventually(condition: () -> Boolean): Boolean {
        val deadline = System.currentTimeMillis() + WAIT_MS
        while (System.currentTimeMillis() < deadline) {
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            var holds = false
            InstrumentationRegistry.getInstrumentation().runOnMainSync { holds = condition() }
            if (holds) return true
            Thread.sleep(POLL_MS)
        }
        return false
    }

    private companion object {
        const val WAIT_MS = 30_000L
        const val POLL_MS = 100L
    }
}
