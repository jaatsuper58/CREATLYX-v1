package com.chattlyx.feature.chats

import androidx.lifecycle.SavedStateHandle
import androidx.paging.PagingData
import com.chattlyx.core.common.result.Result
import com.chattlyx.domain.messaging.Conversation
import com.chattlyx.domain.messaging.MessageRepository
import com.chattlyx.domain.messaging.RealtimeEvents
import com.chattlyx.domain.messaging.usecases.MarkConversationReadUseCase
import com.chattlyx.domain.messaging.usecases.ObserveConversationUseCase
import com.chattlyx.domain.messaging.usecases.ObserveMessagesUseCase
import com.chattlyx.domain.messaging.usecases.SendMessageUseCase
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
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

@OptIn(ExperimentalCoroutinesApi::class)
class ConversationViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val messageRepository = mockk<MessageRepository>(relaxed = true)
    private val sendMessageUseCase = mockk<SendMessageUseCase> {
        coEvery { this@mockk(any(), any()) } returns Result.success("client-1")
    }
    private val markReadUseCase = mockk<MarkConversationReadUseCase> {
        coEvery { this@mockk(any()) } just Runs
    }

    private fun buildViewModel(conversationValue: Conversation?): ConversationViewModel {
        val observeMessages = mockk<ObserveMessagesUseCase> {
            every { this@mockk(any()) } returns flowOf(PagingData.empty())
        }
        val observeConversation = mockk<ObserveConversationUseCase> {
            every { this@mockk(any()) } returns flowOf(conversationValue)
        }
        val realtime = mockk<RealtimeEvents> {
            every { typingEvents } returns emptyFlow()
        }
        return ConversationViewModel(
            savedStateHandle = SavedStateHandle(mapOf("conversationId" to "dm:a:b")),
            observeMessages = observeMessages,
            observeConversation = observeConversation,
            realtimeEvents = realtime,
            messageRepository = messageRepository,
            sendMessageUseCase = sendMessageUseCase,
            markConversationReadUseCase = markReadUseCase,
        )
    }

    @Test
    fun `opening a conversation marks it read`() = runTest(dispatcher.scheduler) {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            buildViewModel(conversationValue = null)
            runCurrent()
            coVerify { markReadUseCase("dm:a:b") }
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `typing is announced on input and stopped after the pause`() = runTest(dispatcher.scheduler) {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val viewModel = buildViewModel(conversationValue = null)
            runCurrent()

            viewModel.onTextChanged("hi")
            runCurrent()
            coVerify { messageRepository.sendTyping("dm:a:b", true) }

            advanceTimeBy(3_001)
            runCurrent()
            coVerify { messageRepository.sendTyping("dm:a:b", false) }
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `send clears composer and invokes the use case`() = runTest(dispatcher.scheduler) {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val conversation = Conversation(
                id = "dm:a:b",
                peerAccountId = "peer-b",
                peerName = "Bob",
                peerAvatarBlobId = null,
                lastMessageText = "",
                lastMessageAt = 0L,
                unreadCount = 0,
                pinned = false,
            )
            val viewModel = buildViewModel(conversationValue = conversation)

            // stateIn(WhileSubscribed) needs an active subscriber to deliver values.
            val subscriber = launch { viewModel.conversation.collect {} }
            runCurrent()

            viewModel.onTextChanged("hello bob")
            viewModel.send()
            runCurrent()

            assertEquals("", viewModel.composerText.value)
            coVerify { sendMessageUseCase("peer-b", "hello bob") }
            subscriber.cancel()
        } finally {
            Dispatchers.resetMain()
        }
    }
}
