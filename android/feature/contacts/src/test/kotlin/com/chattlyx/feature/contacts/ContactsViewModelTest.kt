package com.chattlyx.feature.contacts

import app.cash.turbine.test
import com.chattlyx.core.common.result.Result
import com.chattlyx.domain.messaging.ContactInfo
import com.chattlyx.domain.messaging.usecases.DiscoverContactsUseCase
import com.chattlyx.domain.messaging.usecases.ObserveContactsUseCase
import com.chattlyx.domain.messaging.usecases.OpenConversationUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

@OptIn(ExperimentalCoroutinesApi::class)
class ContactsViewModelTest {

    private val io = UnconfinedTestDispatcher()

    private val observeContacts = mockk<ObserveContactsUseCase> {
        every { this@mockk() } returns flowOf(emptyList())
    }
    private val discover = mockk<DiscoverContactsUseCase>()
    private val openConversation = mockk<OpenConversationUseCase> {
        coEvery { this@mockk(any()) } returns "dm:x:peer-1"
    }

    private fun viewModel() = ContactsViewModel(observeContacts, discover, openConversation, io)

    @Test
    fun `sync discovers contacts and returns to idle`() = runTest {
        Dispatchers.setMain(io)
        try {
            coEvery { discover(any()) } returns Result.success(
                listOf(ContactInfo("peer-1", "Asha", null, null)),
            )
            val viewModel = viewModel()

            viewModel.syncFromDevice(listOf("+919876543210"))
            runCurrent()

            viewModel.phase.test {
                assertEquals(ContactsPhase.IDLE, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
            coVerify { discover(listOf("+919876543210")) }
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `discovery failure surfaces the error phase`() = runTest {
        Dispatchers.setMain(io)
        try {
            coEvery { discover(any()) } returns Result.failure(
                com.chattlyx.core.common.error.ChattlyError.Network(),
            )
            val viewModel = viewModel()

            viewModel.syncFromDevice(listOf("+919876543210"))
            runCurrent()

            viewModel.phase.test {
                assertEquals(ContactsPhase.ERROR, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `openChat resolves the conversation id via callback`() = runTest {
        Dispatchers.setMain(io)
        try {
            val viewModel = viewModel()
            var resolved: String? = null

            viewModel.openChat("peer-1") { resolved = it }
            runCurrent()

            assertEquals("dm:x:peer-1", resolved)
        } finally {
            Dispatchers.resetMain()
        }
    }
}
