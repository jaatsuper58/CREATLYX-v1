package com.chattlyx.feature.chats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.chattlyx.core.common.result.Result
import com.chattlyx.core.common.result.fold
import com.chattlyx.core.common.result.getOrNull
import com.chattlyx.domain.groups.CreateGroupUseCase
import com.chattlyx.domain.groups.RefreshGroupsUseCase
import com.chattlyx.domain.messaging.ContactInfo
import com.chattlyx.domain.messaging.Conversation
import com.chattlyx.domain.messaging.RealtimeEvents
import com.chattlyx.domain.messaging.usecases.ObserveContactsUseCase
import com.chattlyx.domain.messaging.usecases.ObserveConversationsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.scan
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Chat list state (MSG-08 + GRP-01): paged conversations, groups, typing. */
@HiltViewModel
class ChatsViewModel @Inject constructor(
    observeConversations: ObserveConversationsUseCase,
    observeContacts: ObserveContactsUseCase,
    realtimeEvents: RealtimeEvents,
    private val createGroupUseCase: CreateGroupUseCase,
    private val refreshGroupsUseCase: RefreshGroupsUseCase,
    private val searchConversationsUseCase: com.chattlyx.domain.messaging.usecases.SearchConversationsUseCase,
    private val searchMessagesUseCase: com.chattlyx.domain.messaging.usecases.SearchMessagesUseCase,
) : ViewModel() {

    val conversations: Flow<PagingData<Conversation>> =
        observeConversations().cachedIn(viewModelScope)

    /** Registered peers available for GRP-01 member selection. */
    val contacts: StateFlow<List<ContactInfo>> = observeContacts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** One-shot: newly created group id, consumed to open the conversation. */
    private val _createdGroupId = MutableStateFlow<String?>(null)
    val createdGroupId: StateFlow<String?> = _createdGroupId.asStateFlow()

    /** One-shot validation/server error message key from group creation. */
    private val _groupErrorKey = MutableStateFlow<String?>(null)
    val groupErrorKey: StateFlow<String?> = _groupErrorKey.asStateFlow()

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

    // SRCH-01: query + combined local results.
    val searchQuery = MutableStateFlow("")

    data class SearchResults(
        val conversations: List<com.chattlyx.domain.messaging.Conversation> = emptyList(),
        val messages: List<com.chattlyx.domain.messaging.MessageSearchHit> = emptyList(),
    )

    private val _searchResults = MutableStateFlow(SearchResults())
    val searchResults: StateFlow<SearchResults> = _searchResults.asStateFlow()

    private var searchJob: kotlinx.coroutines.Job? = null

    init {
        // Server is the group source of truth; hydrate the cache on entry.
        viewModelScope.launch { refreshGroupsUseCase() }
    }

    /** SRCH-01: debounced local search across names + FTS5 bodies. */
    fun onSearchQueryChanged(query: String) {
        searchQuery.value = query
        searchJob?.cancel()
        if (query.isBlank()) {
            _searchResults.value = SearchResults()
            return
        }
        searchJob = viewModelScope.launch {
            kotlinx.coroutines.delay(SEARCH_DEBOUNCE_MS)
            val conversations = searchConversationsUseCase(query)
                .getOrNull().orEmpty()
            val messages = searchMessagesUseCase(query)
                .getOrNull().orEmpty()
            _searchResults.value = SearchResults(conversations, messages)
        }
    }

    fun clearSearch() {
        searchQuery.value = ""
        _searchResults.value = SearchResults()
    }

    private companion object {
        const val SEARCH_DEBOUNCE_MS = 250L
    }

    /** GRP-01: creates the group and publishes its id for navigation. */
    fun createGroup(name: String, memberAccountIds: List<String>) {
        viewModelScope.launch {
            createGroupUseCase(name, memberAccountIds).fold(
                onSuccess = { groupId ->
                    _createdGroupId.value = groupId
                },
                onFailure = { error ->
                    _groupErrorKey.value = when (error) {
                        is com.chattlyx.core.common.error.ChattlyError.Validation ->
                            error.messageKey
                        else -> "error_group_create"
                    }
                },
            )
        }
    }

    fun consumeCreatedGroup() {
        _createdGroupId.value = null
    }

    fun consumeGroupError() {
        _groupErrorKey.value = null
    }
}
