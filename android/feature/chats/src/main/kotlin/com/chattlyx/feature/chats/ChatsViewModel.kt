package com.chattlyx.feature.chats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.chattlyx.domain.messaging.Conversation
import com.chattlyx.domain.messaging.RealtimeEvents
import com.chattlyx.domain.messaging.usecases.ObserveConversationsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.scan
import kotlinx.coroutines.flow.stateIn

/** Chat list state (MSG-08): paged conversations + typing hints. */
@HiltViewModel
class ChatsViewModel @Inject constructor(
    observeConversations: ObserveConversationsUseCase,
    realtimeEvents: RealtimeEvents,
) : ViewModel() {

    val conversations: Flow<PagingData<Conversation>> =
        observeConversations().cachedIn(viewModelScope)

    /** conversationId -> true while the peer is typing. */
    val typingByConversation: StateFlow<Map<String, Boolean>> =
        realtimeEvents.typingEvents
            .scan(emptyMap<String, Boolean>()) { acc, event ->
                acc + (event.conversationId to event.started)
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = emptyMap(),
            )
}
