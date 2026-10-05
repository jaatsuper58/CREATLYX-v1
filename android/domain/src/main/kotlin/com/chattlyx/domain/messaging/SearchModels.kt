package com.chattlyx.domain.messaging

/** SRCH-01: local full-text + name search across conversations/messages. */

/** One message search hit (FTS5 snippet). */
data class MessageSearchHit(
    val conversationId: String,
    val snippet: String,
    val sentAt: Long,
)

/** SRCH-01 search surface (local, SQLCipher/FTS5-backed). */
interface SearchRepository {

    /** Conversations whose peer name matches the query. */
    suspend fun searchConversations(query: String): List<Conversation>

    /** Messages whose body matches the query (FTS5). */
    suspend fun searchMessages(query: String): List<MessageSearchHit>
}
