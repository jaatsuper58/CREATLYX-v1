package com.chattlyx.domain.messaging.usecases

import com.chattlyx.core.common.result.Result
import com.chattlyx.domain.messaging.Conversation
import com.chattlyx.domain.messaging.MessageSearchHit
import com.chattlyx.domain.messaging.SearchRepository
import javax.inject.Inject

/** SRCH-01: searches conversation names (local only). */
class SearchConversationsUseCase @Inject constructor(
    private val searchRepository: SearchRepository,
) {
    suspend operator fun invoke(query: String): Result<List<Conversation>> =
        Result.success(searchRepository.searchConversations(query))
}

/** SRCH-01: full-text message search (local FTS5 only). */
class SearchMessagesUseCase @Inject constructor(
    private val searchRepository: SearchRepository,
) {
    suspend operator fun invoke(query: String): Result<List<MessageSearchHit>> =
        Result.success(searchRepository.searchMessages(query))
}
