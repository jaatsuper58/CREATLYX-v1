package com.chattlyx.feature.chats

import androidx.paging.PagingData
import com.chattlyx.core.common.error.ChattlyError
import com.chattlyx.core.common.result.Result
import com.chattlyx.domain.groups.CreateGroupUseCase
import com.chattlyx.domain.groups.RefreshGroupsUseCase
import com.chattlyx.domain.messaging.RealtimeEvents
import com.chattlyx.domain.messaging.usecases.ObserveContactsUseCase
import com.chattlyx.domain.messaging.usecases.ObserveConversationsUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertEquals
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

/** GRP-01 group creation flow from the chat list. */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatsViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val refreshGroups = mockk<RefreshGroupsUseCase> {
        coEvery { this@mockk() } returns Result.success(Unit)
    }

    private fun buildViewModel(createResult: Result<String>): ChatsViewModel {
        val createGroup = mockk<CreateGroupUseCase> {
            coEvery { this@mockk(any(), any()) } returns createResult
        }
        return ChatsViewModel(
            observeConversations = mockk<ObserveConversationsUseCase> {
                every { this@mockk() } returns flowOf(PagingData.empty())
            },
            observeContacts = mockk<ObserveContactsUseCase> {
                every { this@mockk() } returns flowOf(emptyList())
            },
            realtimeEvents = mockk<RealtimeEvents> {
                every { typingEvents } returns emptyFlow()
            },
            createGroupUseCase = createGroup,
            refreshGroupsUseCase = refreshGroups,
            searchConversationsUseCase = mockk {
                coEvery { this@mockk(any()) } returns Result.success(emptyList())
            },
            searchMessagesUseCase = mockk {
                coEvery { this@mockk(any()) } returns Result.success(emptyList())
            },
        )
    }

    @Test
    fun `opening the list refreshes groups from the server`() = runTest(dispatcher.scheduler) {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val viewModel = buildViewModel(Result.success("g1"))
            runCurrent()
            coVerify { refreshGroups() }
            viewModel.viewModelScope.cancel()
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `successful creation publishes the group id`() = runTest(dispatcher.scheduler) {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val viewModel = buildViewModel(Result.success("group-123"))
            runCurrent()

            viewModel.createGroup("Team", listOf("a", "b"))
            runCurrent()

            assertEquals("group-123", viewModel.createdGroupId.value)
            viewModel.consumeCreatedGroup()
            assertEquals(null, viewModel.createdGroupId.value)
            viewModel.viewModelScope.cancel()
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `search query collects local hits after the debounce`() = runTest(dispatcher.scheduler) {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val hit = com.chattlyx.domain.messaging.MessageSearchHit(
                conversationId = "dm:x",
                snippet = "hello world",
                sentAt = 5L,
            )
            val viewModel = ChatsViewModel(
                observeConversations = mockk<ObserveConversationsUseCase> {
                    every { this@mockk() } returns flowOf(PagingData.empty())
                },
                observeContacts = mockk<ObserveContactsUseCase> {
                    every { this@mockk() } returns flowOf(emptyList())
                },
                realtimeEvents = mockk<RealtimeEvents> {
                    every { typingEvents } returns emptyFlow()
                },
                createGroupUseCase = mockk(relaxed = true),
                refreshGroupsUseCase = refreshGroups,
                searchConversationsUseCase = mockk {
                    coEvery { this@mockk(any()) } returns Result.success(emptyList())
                },
                searchMessagesUseCase = mockk {
                    coEvery { this@mockk(any()) } returns Result.success(listOf(hit))
                },
            )
            runCurrent()

            viewModel.onSearchQueryChanged("hello")
            assertEquals(0, viewModel.searchResults.value.messages.size)
            advanceTimeBy(300)
            runCurrent()

            assertEquals(1, viewModel.searchResults.value.messages.size)
            assertEquals("hello world", viewModel.searchResults.value.messages.first().snippet)

            viewModel.clearSearch()
            assertEquals(0, viewModel.searchResults.value.messages.size)
            viewModel.viewModelScope.cancel()
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `validation failures surface the message key`() = runTest(dispatcher.scheduler) {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val viewModel = buildViewModel(
                Result.failure(
                    ChattlyError.Validation(field = "name", messageKey = "validation_group_name_empty"),
                ),
            )
            runCurrent()

            viewModel.createGroup("", emptyList())
            runCurrent()

            assertEquals("validation_group_name_empty", viewModel.groupErrorKey.value)
            viewModel.consumeGroupError()
            assertEquals(null, viewModel.groupErrorKey.value)
            viewModel.viewModelScope.cancel()
        } finally {
            Dispatchers.resetMain()
        }
    }
}
