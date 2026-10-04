package com.chattlyx.data.messaging

import com.chattlyx.core.common.dispatchers.ChattlyxDispatcher
import com.chattlyx.core.common.dispatchers.Dispatcher
import com.chattlyx.core.database.dao.ConversationDao
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * MSG-06 gap recovery: after (re)connect, pulls unacked envelopes for every
 * local conversation past its stored cursor and folds them into the store.
 */
@Singleton
class SyncEngine @Inject constructor(
    private val conversationDao: ConversationDao,
    private val messageRepository: MessageRepositoryImpl,
    private val conversationRepository: ConversationRepositoryImpl,
    @Dispatcher(ChattlyxDispatcher.IO) private val dispatcher: CoroutineDispatcher,
) {

    suspend fun syncAfterReconnect(): Int = withContext(dispatcher) {
        val cursors = conversationDao.allCursors()
        if (cursors.isEmpty()) return@withContext 0

        val result = messageRepository.syncConversations(
            cursors.associate { it.id to it.lastSeq },
        )
        val restoredMessages = when (result) {
            is com.chattlyx.core.common.result.Result.Success -> result.value
            is com.chattlyx.core.common.result.Result.Failure -> emptyList()
        }

        restoredMessages.forEach { message ->
            conversationRepository.onIncomingMessage(message, incrementUnread = true)
        }
        if (restoredMessages.isNotEmpty()) {
            Timber.d("Sync restored %d envelopes", restoredMessages.size)
        }
        restoredMessages.size
    }
}
