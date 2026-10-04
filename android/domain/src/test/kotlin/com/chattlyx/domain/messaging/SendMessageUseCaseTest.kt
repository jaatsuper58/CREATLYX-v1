package com.chattlyx.domain.messaging

import androidx.paging.PagingData
import com.chattlyx.core.common.error.ChattlyError
import com.chattlyx.core.common.result.Result
import com.chattlyx.domain.messaging.usecases.SendMessageUseCase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest

private class FakeMessageRepository : MessageRepository {
    var sentBody: String? = null
    var sentPeer: String? = null

    override suspend fun sendMessage(peerAccountId: String, body: String): Result<String> {
        sentPeer = peerAccountId
        sentBody = body
        return Result.success("client-1")
    }

    override suspend fun acknowledge(serverIds: List<String>) = Unit
    override suspend fun markRead(conversationId: String) = Unit
    override suspend fun sync(): Result<Unit> = Result.success(Unit)
    override suspend fun sendTyping(conversationId: String, started: Boolean) = Unit
}

private class FakeConversationRepository : ConversationRepository {
    var openedWith: String? = null

    override fun observeConversations(): Flow<PagingData<Conversation>> = flowOf(PagingData.empty())
    override fun observeConversation(conversationId: String): Flow<Conversation?> = flowOf(null)
    override fun observeMessages(conversationId: String): Flow<PagingData<Message>> = flowOf(PagingData.empty())

    override suspend fun openConversationWith(peerAccountId: String): String {
        openedWith = peerAccountId
        return "dm:x:$peerAccountId"
    }

    override suspend fun setPinned(conversationId: String, pinned: Boolean) = Unit
}

class SendMessageUseCaseTest {

    private val messages = FakeMessageRepository()
    private val conversations = FakeConversationRepository()
    private val useCase = SendMessageUseCase(messages, conversations)

    @Test
    fun `valid message opens conversation and sends trimmed body`() = runTest {
        val result = useCase("peer-1", "  hello there  ")
        assertIs<Result.Success<String>>(result)
        assertEquals("peer-1", conversations.openedWith)
        assertEquals("peer-1", messages.sentPeer)
        assertEquals("hello there", messages.sentBody)
    }

    @Test
    fun `blank message fails validation without touching repositories`() = runTest {
        val result = useCase("peer-1", "   ")
        val failure = assertIs<Result.Failure>(result)
        assertIs<ChattlyError.Validation>(failure.error)
        assertNull(messages.sentBody)
        assertNull(conversations.openedWith)
    }

    @Test
    fun `message over 8000 chars fails validation`() = runTest {
        val result = useCase("peer-1", "x".repeat(8_001))
        assertIs<Result.Failure>(result)
        assertNull(messages.sentBody)
    }
}
