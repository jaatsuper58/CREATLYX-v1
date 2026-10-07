package com.chattlyx.app.ui

import androidx.lifecycle.viewModelScope
import com.chattlyx.core.datastore.SettingsRepository
import io.mockk.every
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

/** AUTH-* (Phase 8): app-lock gate arming/unarming semantics. */
@OptIn(ExperimentalCoroutinesApi::class)
class AppLockGateViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    private fun buildViewModel(enabled: Boolean): AppLockGateViewModel {
        val repository = mockk<SettingsRepository> {
            every { appLockEnabled } returns flowOf(enabled)
        }
        return AppLockGateViewModel(repository)
    }

    @Test
    fun `gate starts unlocked and arms on lock`() = runTest(dispatcher.scheduler) {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val viewModel = buildViewModel(enabled = true)
            runCurrent()
            assertFalse(viewModel.locked.value)

            viewModel.lock()
            assertTrue(viewModel.locked.value)

            viewModel.unlock()
            assertFalse(viewModel.locked.value)
            viewModel.viewModelScope.cancel()
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `lock preference flows through from settings`() = runTest(dispatcher.scheduler) {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val viewModel = buildViewModel(enabled = true)
            val subscriber = launch { viewModel.appLockEnabled.collect {} }
            runCurrent()

            assertEquals(true, viewModel.appLockEnabled.value)
            subscriber.cancel()
            viewModel.viewModelScope.cancel()
        } finally {
            Dispatchers.resetMain()
        }
    }
}
