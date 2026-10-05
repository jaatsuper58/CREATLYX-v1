package com.chattlyx.feature.settings.privacy

import androidx.lifecycle.viewModelScope
import com.chattlyx.core.datastore.SettingsRepository
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

/** AUTH-* (Phase 8): app-lock preference toggling. */
@OptIn(ExperimentalCoroutinesApi::class)
class AppLockViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    private fun buildViewModel(initial: Boolean): Pair<AppLockViewModel, SettingsRepository> {
        val repository = mockk<SettingsRepository> {
            every { appLockEnabled } returns flowOf(initial)
            coEvery { setAppLockEnabled(any()) } just Runs
        }
        return AppLockViewModel(repository) to repository
    }

    @Test
    fun `enable persists the preference after prompt confirmation`() =
        runTest(dispatcher.scheduler) {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            try {
                val (viewModel, repository) = buildViewModel(initial = false)
                runCurrent()

                viewModel.enable()
                runCurrent()

                coVerify { repository.setAppLockEnabled(true) }
                viewModel.viewModelScope.cancel()
            } finally {
                Dispatchers.resetMain()
            }
        }

    @Test
    fun `disable clears the preference immediately`() = runTest(dispatcher.scheduler) {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val (viewModel, repository) = buildViewModel(initial = true)
            val subscriber = launch { viewModel.appLockEnabled.collect {} }
            runCurrent()
            assertEquals(true, viewModel.appLockEnabled.first())

            viewModel.disable()
            runCurrent()

            coVerify { repository.setAppLockEnabled(false) }
            subscriber.cancel()
            viewModel.viewModelScope.cancel()
        } finally {
            Dispatchers.resetMain()
        }
    }
}
