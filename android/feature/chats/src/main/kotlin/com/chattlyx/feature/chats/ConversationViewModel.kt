package com.chattlyx.feature.chats

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.chattlyx.domain.messaging.Conversation
import com.chattlyx.domain.messaging.Message
import com.chattlyx.domain.messaging.MessageRepository
import com.chattlyx.domain.messaging.RealtimeEvents
import com.chattlyx.domain.messaging.usecases.MarkConversationReadUseCase
import com.chattlyx.domain.messaging.usecases.ObserveConversationUseCase
import com.chattlyx.domain.messaging.usecases.ObserveMessagesUseCase
import com.chattlyx.domain.messaging.usecases.SendMessageUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** MSG-03 conversation state: paged messages, composer, typing in/out. */
@HiltViewModel
class ConversationViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    observeMessages: ObserveMessagesUseCase,
    observeConversation: ObserveConversationUseCase,
    realtimeEvents: RealtimeEvents,
    private val messageRepository: MessageRepository,
    private val sendMessageUseCase: SendMessageUseCase,
    private val markConversationReadUseCase: MarkConversationReadUseCase,
) : ViewModel() {

    private val conversationId: String = savedStateHandle.toRoute<ConversationRoute>().conversationId

    val messages: Flow<PagingData<Message>> =
        observeMessages(conversationId).cachedIn(viewModelScope)

    /** Conversation header info (peer name), null until loaded. */
    val conversation: StateFlow<Conversation?> = observeConversation(conversationId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Peer typing indicator for this conversation. */
    val peerTyping: StateFlow<Boolean> = realtimeEvents.typingEvents
        .filter { it.conversationId == conversationId }
        .map { it.started }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val composerText = MutableStateFlow("")

    private var typingStopJob: Job? = null

    init {
        // Entering a conversation marks it read and receipts the peer.
        viewModelScope.launch { markConversationReadUseCase(conversationId) }
    }

    fun onTextChanged(value: String) {
        composerText.value = value
        emitTyping(started = true)
    }

    fun send() {
        val body = composerText.value.trim()
        val peer = conversation.value?.peerAccountId
        if (body.isEmpty() || peer == null) return

        composerText.value = ""
        emitTyping(started = false)
        viewModelScope.launch {
            // Outgoing row appears immediately via the store (PENDING status);
            // failures flip it to FAILED for retry affordances.
            sendMessageUseCase(peer, body)
        }
    }

    fun markRead() {
        viewModelScope.launch { markConversationReadUseCase(conversationId) }
    }

    /**
     * Sends typing started=true and schedules the stop signal after a pause,
     * so the peer's indicator never sticks.
     */
    private fun emitTyping(started: Boolean) {
        typingStopJob?.cancel()
        viewModelScope.launch { messageRepository.sendTyping(conversationId, started) }
        if (started) {
            typingStopJob = viewModelScope.launch {
                delay(TYPING_STOP_AFTER_MS)
                messageRepository.sendTyping(conversationId, false)
            }
        }
    }

    private companion object {
        const val TYPING_STOP_AFTER_MS = 3_000L
    }
}
