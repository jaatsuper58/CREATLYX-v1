package com.chattlyx.feature.settings

import app.cash.turbine.test
import com.chattlyx.core.common.error.ChattlyError
import com.chattlyx.core.common.result.Result
import com.chattlyx.domain.auth.usecases.DeleteAccountUseCase
import com.chattlyx.feature.settings.danger.DeleteAccountViewModel
import io.mockk.coEvery
import io.mockk.mockk
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DeleteAccountViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val deleteAccount = mockk<DeleteAccountUseCase>()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `delete is blocked until acknowledged`() = runTest {
        coEvery { deleteAccount() } returns Result.success(Unit)
        val vm = DeleteAccountViewModel(deleteAccount, dispatcher)

        vm.delete()

        vm.state.test {
            val state = awaitItem()
            assertFalse(state.deleted)
            assertFalse(state.deleting)
        }
        io.mockk.coVerify(exactly = 0) { deleteAccount() }
    }

    @Test
    fun `acknowledged delete succeeds`() = runTest {
        coEvery { deleteAccount() } returns Result.success(Unit)
        val vm = DeleteAccountViewModel(deleteAccount, dispatcher)

        vm.onAcknowledgedChange(true)
        vm.delete()

        vm.state.test {
            val state = awaitItem()
            assertTrue(state.deleted)
        }
    }

    @Test
    fun `failed delete surfaces error`() = runTest {
        coEvery { deleteAccount() } returns Result.failure(ChattlyError.Network())
        val vm = DeleteAccountViewModel(deleteAccount, dispatcher)

        vm.onAcknowledgedChange(true)
        vm.delete()

        vm.state.test {
            val state = awaitItem()
            assertTrue(state.errorVisible)
            assertFalse(state.deleted)
        }
    }
}
