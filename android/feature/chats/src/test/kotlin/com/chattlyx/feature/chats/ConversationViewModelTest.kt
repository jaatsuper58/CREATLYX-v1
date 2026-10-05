package com.chattlyx.feature.chats

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import android.content.Context
import com.chattlyx.core.common.error.ChattlyError
import com.chattlyx.core.common.result.Result
import com.chattlyx.domain.messaging.Conversation
import com.chattlyx.domain.messaging.Message
import com.chattlyx.domain.messaging.MessageRepository
import com.chattlyx.domain.messaging.RealtimeEvents
import com.chattlyx.domain.messaging.usecases.DownloadAttachmentUseCase
import com.chattlyx.domain.messaging.usecases.MarkConversationReadUseCase
import com.chattlyx.domain.messaging.usecases.ObserveConversationUseCase
import com.chattlyx.domain.messaging.usecases.ObserveMessagesUseCase
import com.chattlyx.domain.messaging.usecases.SendAttachmentUseCase
import com.chattlyx.domain.messaging.usecases.SendMessageUseCase
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.cancel
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
    private val sendAttachmentUseCase = mockk<SendAttachmentUseCase>()
    private val downloadAttachmentUseCase = mockk<DownloadAttachmentUseCase>()
    private val context = mockk<Context>(relaxed = true)
    private val callSession = mockk<com.chattlyx.domain.calls.CallSession>(relaxed = true)
    private val getPresenceUseCase = mockk<com.chattlyx.domain.social.GetPresenceUseCase>(relaxed = true)
    private val blockPeerUseCase = mockk<com.chattlyx.domain.social.BlockPeerUseCase>(relaxed = true)

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
            context = context,
            ioDispatcher = dispatcher,
            messageRepository = messageRepository,
            sendMessageUseCase = sendMessageUseCase,
            sendAttachmentUseCase = sendAttachmentUseCase,
            downloadAttachmentUseCase = downloadAttachmentUseCase,
            markConversationReadUseCase = markReadUseCase,
            callSession = callSession,
            getPresenceUseCase = getPresenceUseCase,
            blockPeerUseCase = blockPeerUseCase,
        )
    }

    @Test
    fun `opening a conversation marks it read`() = runTest(dispatcher.scheduler) {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val viewModel = buildViewModel(conversationValue = null)
            runCurrent()
            coVerify { markReadUseCase("dm:a:b") }
            viewModel.viewModelScope.cancel()
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
            viewModel.viewModelScope.cancel()
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `group conversations fan out through sendGroupMessage`() = runTest(dispatcher.scheduler) {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val group = Conversation(
                id = "grp:g1",
                peerAccountId = "g1",
                peerName = "Team",
                peerAvatarBlobId = null,
                lastMessageText = "",
                lastMessageAt = 0L,
                unreadCount = 0,
                pinned = false,
            )
            val viewModel = ConversationViewModel(
                savedStateHandle = SavedStateHandle(mapOf("conversationId" to "grp:g1")),
                observeMessages = mockk<ObserveMessagesUseCase> {
                    every { this@mockk(any()) } returns flowOf(PagingData.empty())
                },
                observeConversation = mockk<ObserveConversationUseCase> {
                    every { this@mockk(any()) } returns flowOf(group)
                },
                realtimeEvents = mockk<RealtimeEvents> { every { typingEvents } returns emptyFlow() },
                context = context,
                ioDispatcher = dispatcher,
                messageRepository = messageRepository,
                sendMessageUseCase = sendMessageUseCase,
                sendAttachmentUseCase = sendAttachmentUseCase,
                downloadAttachmentUseCase = downloadAttachmentUseCase,
                markConversationReadUseCase = markReadUseCase,
                callSession = callSession,
                getPresenceUseCase = getPresenceUseCase,
                blockPeerUseCase = blockPeerUseCase,
            )
            val subscriber = launch { viewModel.conversation.collect {} }
            runCurrent()

            viewModel.onTextChanged("hello team")
            viewModel.send()
            runCurrent()

            coVerify { messageRepository.sendGroupMessage("g1", "hello team") }
            subscriber.cancel()
            viewModel.viewModelScope.cancel()
        } finally {
            Dispatchers.resetMain()
        }
    }

    private fun messageWith(attachment: com.chattlyx.domain.messaging.AttachmentInfo?) = Message(
        id = "m1",
        clientId = "m1",
        serverId = "s1",
        conversationId = "dm:a:b",
        senderAccountId = "peer-b",
        body = "",
        status = com.chattlyx.domain.messaging.DeliveryStatus.DELIVERED,
        seq = 1,
        sentAt = 0,
        receivedAt = 0,
        isMine = false,
        attachment = attachment,
    )

    @Test
    fun `download failure surfaces a snack message key`() = runTest(dispatcher.scheduler) {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            coEvery { downloadAttachmentUseCase(any()) } returns
                Result.failure(ChattlyError.Network())
            val viewModel = buildViewModel(conversationValue = null)
            runCurrent()

            viewModel.download(messageWith(null))
            runCurrent()

            assertEquals("error_attachment_download", viewModel.snackMessageKey.value)
            viewModel.consumeSnack()
            assertEquals(null, viewModel.snackMessageKey.value)
            viewModel.viewModelScope.cancel()
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `localFile is null when the attachment is not ready`() = runTest(dispatcher.scheduler) {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val viewModel = buildViewModel(conversationValue = null)
            runCurrent()
            assertEquals(null, viewModel.localFile(messageWith(null)))
            viewModel.viewModelScope.cancel()
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
            viewModel.viewModelScope.cancel()
        } finally {
            Dispatchers.resetMain()
        }
    }
}
