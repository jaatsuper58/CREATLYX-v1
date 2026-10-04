package com.chattlyx.domain.messaging

import com.chattlyx.core.common.result.Result
import com.chattlyx.domain.messaging.usecases.DiscoverContactsUseCase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest

private class FakeContactRepository : ContactRepository {
    var receivedNumbers: List<String> = emptyList()

    override fun observeContacts(): Flow<List<ContactInfo>> = flowOf(emptyList())

    override suspend fun discover(e164Numbers: List<String>): Result<List<ContactInfo>> {
        receivedNumbers = e164Numbers
        return Result.success(
            e164Numbers.mapIndexed { index, number ->
                ContactInfo("acct-$index", "User $index", null, null)
            },
        )
    }

    override suspend fun nameFor(accountId: String): String? = null
}

class DiscoverContactsUseCaseTest {

    private val repository = FakeContactRepository()
    private val useCase = DiscoverContactsUseCase(repository)

    @Test
    fun `valid e164 numbers pass through deduplicated`() = runTest {
        val result = useCase(listOf("+919876543210", "+919876543210", "+14155552671"))
        val success = assertIs<Result.Success<List<ContactInfo>>>(result)
        assertEquals(2, success.value.size)
        assertEquals(listOf("+919876543210", "+14155552671"), repository.receivedNumbers)
    }

    @Test
    fun `national numbers are normalised with fallback dial code`() = runTest {
        val result = useCase(listOf("9876543210"))
        assertIs<Result.Success<List<ContactInfo>>>(result)
        assertEquals(listOf("+919876543210"), repository.receivedNumbers)
    }

    @Test
    fun `garbage input yields empty discovery`() = runTest {
        val result = useCase(listOf("not-a-number"))
        val success = assertIs<Result.Success<List<ContactInfo>>>(result)
        assertTrue(repository.receivedNumbers.isEmpty())
        assertTrue(success.value.isEmpty())
    }
}
