package com.chattlyx.domain.messaging.usecases

import androidx.paging.PagingData
import com.chattlyx.core.common.error.ChattlyError
import com.chattlyx.core.common.result.Result
import com.chattlyx.domain.messaging.Conversation
import com.chattlyx.domain.messaging.ConversationRepository
import com.chattlyx.domain.messaging.Message
import com.chattlyx.domain.messaging.MessageRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

/** MSG-01: compose + send a 1:1 text message. */
class SendMessageUseCase @Inject constructor(
    private val messageRepository: MessageRepository,
    private val conversationRepository: ConversationRepository,
) {

    suspend operator fun invoke(peerAccountId: String, body: String): Result<String> {
        val text = body.trim()
        if (text.isEmpty()) {
            return Result.failure(
                ChattlyError.Validation(field = "body", messageKey = "validation_message_empty"),
            )
        }
        if (text.length > MAX_MESSAGE_CHARS) {
            return Result.failure(
                ChattlyError.Validation(field = "body", messageKey = "validation_message_too_long"),
            )
        }
        conversationRepository.openConversationWith(peerAccountId)
        return messageRepository.sendMessage(peerAccountId, text)
    }

    companion object {
        /** Master spec limit (server /v1/config maxMessageChars). */
        const val MAX_MESSAGE_CHARS = 8_000
    }
}

/** MSG-08: chat list stream. */
class ObserveConversationsUseCase @Inject constructor(
    private val conversationRepository: ConversationRepository,
) {
    operator fun invoke(): Flow<PagingData<Conversation>> =
        conversationRepository.observeConversations()
}

/** MSG-03: message stream for one conversation. */
class ObserveMessagesUseCase @Inject constructor(
    private val conversationRepository: ConversationRepository,
) {
    operator fun invoke(conversationId: String): Flow<PagingData<Message>> =
        conversationRepository.observeMessages(conversationId)
}

/** Opens (or finds) a conversation with a peer; returns its id. */
class OpenConversationUseCase @Inject constructor(
    private val conversationRepository: ConversationRepository,
) {
    suspend operator fun invoke(peerAccountId: String): String =
        conversationRepository.openConversationWith(peerAccountId)
}

/** MSG-03: header/meta info for one conversation. */
class ObserveConversationUseCase @Inject constructor(
    private val conversationRepository: ConversationRepository,
) {
    operator fun invoke(conversationId: String): Flow<Conversation?> =
        conversationRepository.observeConversation(conversationId)
}

/** MSG-09: mark a conversation read locally and receipt to the sender. */
class MarkConversationReadUseCase @Inject constructor(
    private val messageRepository: MessageRepository,
) {
    suspend operator fun invoke(conversationId: String) =
        messageRepository.markRead(conversationId)
}

/** MSG-06: reconcile after reconnect or FCM wake. */
class SyncMessagesUseCase @Inject constructor(
    private val messageRepository: MessageRepository,
) {
    suspend operator fun invoke(): Result<Unit> = messageRepository.sync()
}
