package com.chattlyx.feature.contacts

import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chattlyx.core.common.dispatchers.ChattlyxDispatcher
import com.chattlyx.core.common.dispatchers.Dispatcher
import com.chattlyx.core.common.result.Result
import com.chattlyx.domain.messaging.ContactInfo
import com.chattlyx.domain.messaging.usecases.DiscoverContactsUseCase
import com.chattlyx.domain.messaging.usecases.ObserveContactsUseCase
import com.chattlyx.domain.messaging.usecases.OpenConversationUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Contacts screen state (CON-01..04). */
enum class ContactsPhase { IDLE, NEEDS_PERMISSION, LOADING, ERROR }

@HiltViewModel
class ContactsViewModel @Inject constructor(
    observeContacts: ObserveContactsUseCase,
    private val discoverContactsUseCase: DiscoverContactsUseCase,
    private val openConversationUseCase: OpenConversationUseCase,
    @Dispatcher(ChattlyxDispatcher.IO) private val dispatcher: CoroutineDispatcher,
) : ViewModel() {

    /** Registered peers cached locally (from previous discoveries). */
    val knownContacts: StateFlow<List<ContactInfo>> = observeContacts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _phase = MutableStateFlow(ContactsPhase.IDLE)
    val phase: StateFlow<ContactsPhase> = _phase

    /** Starts discovery with phone numbers read from the device contacts. */
    fun syncFromDevice(numbers: List<String>) {
        if (numbers.isEmpty()) {
            _phase.value = ContactsPhase.IDLE
            return
        }
        _phase.value = ContactsPhase.LOADING
        viewModelScope.launch(dispatcher) {
            when (discoverContactsUseCase(numbers)) {
                is Result.Success -> _phase.value = ContactsPhase.IDLE
                is Result.Failure -> _phase.value = ContactsPhase.ERROR
            }
        }
    }

    fun permissionDenied() {
        _phase.value = ContactsPhase.NEEDS_PERMISSION
    }

    /** Opens the conversation with a discovered peer and reports its id. */
    fun openChat(accountId: String, onConversationReady: (String) -> Unit) {
        viewModelScope.launch(dispatcher) {
            val conversationId = openConversationUseCase(accountId)
            onConversationReady(conversationId)
        }
    }

    fun resetError() {
        _phase.value = ContactsPhase.IDLE
    }

    companion object {
        const val CONTACTS_PERMISSION = Manifest.permission.READ_CONTACTS

        fun hasPermission(context: android.content.Context): Boolean =
            ContextCompat.checkSelfPermission(context, CONTACTS_PERMISSION) ==
                PackageManager.PERMISSION_GRANTED
    }
}
