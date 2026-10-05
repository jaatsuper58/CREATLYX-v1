package com.chattlyx.feature.calls

import com.chattlyx.core.common.result.Result
import com.chattlyx.domain.calls.CallLogEntry
import com.chattlyx.domain.calls.CallMedia
import com.chattlyx.domain.calls.CallSession
import com.chattlyx.domain.calls.CallSessionState
import com.chattlyx.domain.calls.StartCallUseCase
import com.chattlyx.domain.calls.ObserveCallLogUseCase
import com.chattlyx.domain.messaging.ContactInfo
import com.chattlyx.domain.messaging.usecases.ObserveContactsUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import androidx.lifecycle.viewModelScope
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

/** CALL-01..05 view-model behaviour (ring state, controls, history). */
@OptIn(ExperimentalCoroutinesApi::class)
class CallsViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    private fun buildViewModel(
        session: CallSession = mockk(relaxed = true) {
            every { state } returns flowOf(CallSessionState.Idle)
            every { muted } returns flowOf(false)
            every { videoEnabled } returns flowOf(false)
        },
        startResult: Result<String> = Result.success("call-1"),
        contacts: List<ContactInfo> = emptyList(),
    ): CallsViewModel {
        return CallsViewModel(
            observeCallLog = mockk<ObserveCallLogUseCase> {
                every { this@mockk() } returns flowOf<List<CallLogEntry>>(emptyList())
            },
            callSession = session,
            startCallUseCase = mockk<StartCallUseCase> {
                coEvery { this@mockk(any(), any()) } returns startResult
            },
            observeContacts = mockk<ObserveContactsUseCase> {
                every { this@mockk() } returns flowOf(contacts)
            },
            callEngine = mockk(relaxed = true),
        )
    }

    @Test
    fun `placing a call flags callPlaced on success`() = runTest(dispatcher.scheduler) {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val viewModel = buildViewModel()
            runCurrent()
            assertFalse(viewModel.callPlaced.value)

            viewModel.call("peer-1", CallMedia.AUDIO)
            runCurrent()

            assertTrue(viewModel.callPlaced.value)
            viewModel.consumeCallPlaced()
            assertFalse(viewModel.callPlaced.value)
            viewModel.viewModelScope.cancel()
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `failed call keeps callPlaced false`() = runTest(dispatcher.scheduler) {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val viewModel = buildViewModel(
                startResult = Result.failure(
                    com.chattlyx.core.common.error.ChattlyError.Network(),
                ),
            )
            runCurrent()

            viewModel.call("peer-1", CallMedia.VIDEO)
            runCurrent()

            assertFalse(viewModel.callPlaced.value)
            viewModel.viewModelScope.cancel()
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `accept and hangUp delegate to the session`() = runTest(dispatcher.scheduler) {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val session = mockk<CallSession>(relaxed = true) {
                every { state } returns flowOf(CallSessionState.Idle)
                every { muted } returns flowOf(false)
                every { videoEnabled } returns flowOf(false)
            }
            val viewModel = buildViewModel(session = session)
            runCurrent()

            viewModel.accept()
            viewModel.hangUp()
            runCurrent()

            coVerify { session.accept() }
            coVerify { session.hangUp() }
            viewModel.viewModelScope.cancel()
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `displayNameFor falls back to a short id when no contact matches`() =
        runTest(dispatcher.scheduler) {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            try {
                val viewModel = buildViewModel()
                runCurrent()

                assertEquals("abcdef12", viewModel.displayNameFor("abcdef12-3456"))
                viewModel.viewModelScope.cancel()
            } finally {
                Dispatchers.resetMain()
            }
        }
}
