package com.chattlyx.data.messaging

import com.chattlyx.core.common.dispatchers.ChattlyxDispatcher
import com.chattlyx.core.common.dispatchers.Dispatcher
import com.chattlyx.core.database.dao.ConversationDao
import com.chattlyx.core.database.dao.MessageDao
import com.chattlyx.domain.messaging.Conversation
import com.chattlyx.domain.messaging.MessageSearchHit
import com.chattlyx.domain.messaging.SearchRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/** SRCH-01: SQLCipher-local search (name LIKE + FTS5), never hits the wire. */
@Singleton
class SearchRepositoryImpl @Inject constructor(
    private val conversationDao: ConversationDao,
    private val messageDao: MessageDao,
    @Dispatcher(ChattlyxDispatcher.IO) private val ioDispatcher: CoroutineDispatcher,
) : SearchRepository {

    override suspend fun searchConversations(query: String): List<Conversation> =
        withContext(ioDispatcher) {
            val trimmed = query.trim()
            if (trimmed.isEmpty()) return@withContext emptyList()
            conversationDao.searchByName(trimmed, MAX_RESULTS).map { row ->
                Conversation(
                    id = row.id,
                    peerAccountId = row.peerAccountId,
                    peerName = row.peerName,
                    peerAvatarBlobId = row.peerAvatarBlobId,
                    lastMessageText = row.lastMessageText,
                    lastMessageAt = row.lastMessageAt,
                    unreadCount = row.unreadCount,
                    pinned = row.pinned,
                )
            }
        }

    override suspend fun searchMessages(query: String): List<MessageSearchHit> =
        withContext(ioDispatcher) {
            val trimmed = query.trim()
            if (trimmed.isEmpty()) return@withContext emptyList()
            messageDao.searchHits(escapeFtsQuery(trimmed), MAX_RESULTS).map { row ->
                MessageSearchHit(
                    conversationId = row.conversationId,
                    snippet = row.body.take(SNIPPET_CHARS),
                    sentAt = row.sentAt,
                )
            }
        }

    /**
     * FTS5 MATCH syntax: quote each token so punctuation is treated as text.
     */
    private fun escapeFtsQuery(query: String): String =
        query.split(Regex("\\s+"))
            .filter { it.isNotEmpty() }
            .joinToString(" ") { token -> "\"${token.replace("\"", "\"\"")}\"" }

    private companion object {
        const val MAX_RESULTS = 50
        const val SNIPPET_CHARS = 120
    }
}
