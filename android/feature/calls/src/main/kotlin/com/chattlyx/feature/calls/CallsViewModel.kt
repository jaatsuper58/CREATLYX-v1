package com.chattlyx.feature.calls

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chattlyx.core.common.result.Result
import com.chattlyx.domain.calls.CallLogEntry
import com.chattlyx.domain.calls.CallMedia
import com.chattlyx.domain.calls.CallSession
import com.chattlyx.domain.calls.CallSessionState
import com.chattlyx.domain.calls.ObserveCallLogUseCase
import com.chattlyx.domain.calls.StartCallUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** CALL-01/02/04/05: call history list + live session controls. */
@HiltViewModel
class CallsViewModel @Inject constructor(
    observeCallLog: ObserveCallLogUseCase,
    private val callSession: CallSession,
    private val startCallUseCase: StartCallUseCase,
    observeContacts: com.chattlyx.domain.messaging.usecases.ObserveContactsUseCase,
    private val callEngine: com.chattlyx.core.rtc.WebRtcCallEngine,
) : ViewModel() {

    /** CALL-01 video: renderer attach point for the in-call screen. */
    val engine: com.chattlyx.core.rtc.WebRtcCallEngine get() = callEngine

    val callLog: StateFlow<List<CallLogEntry>> = observeCallLog()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Registered contacts, used to name the caller on the ring overlay. */
    private val contacts: StateFlow<List<com.chattlyx.domain.messaging.ContactInfo>> =
        observeContacts()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Human-readable name for a peer account id (falls back to a short id). */
    fun displayNameFor(accountId: String): String =
        contacts.value.firstOrNull { it.accountId == accountId }?.displayName
            ?: accountId.take(8)

    val sessionState: StateFlow<CallSessionState> = callSession.state
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CallSessionState.Idle)

    val muted: StateFlow<Boolean> = callSession.muted
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val videoEnabled: StateFlow<Boolean> = callSession.videoEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /** One-shot: set when an outgoing call is placed so the UI can navigate. */
    private val _callPlaced = MutableStateFlow(false)
    val callPlaced: StateFlow<Boolean> = _callPlaced.asStateFlow()

    fun call(peerAccountId: String, media: CallMedia) {
        viewModelScope.launch {
            val result = startCallUseCase(peerAccountId, media)
            _callPlaced.value = result is Result.Success
        }
    }

    fun accept() {
        viewModelScope.launch { callSession.accept() }
    }

    fun decline() {
        viewModelScope.launch { callSession.decline() }
    }

    fun hangUp() {
        viewModelScope.launch { callSession.hangUp() }
    }

    fun toggleMute() {
        val next = !muted.value
        viewModelScope.launch { callSession.setMuted(next) }
    }

    fun toggleVideo() {
        val next = !videoEnabled.value
        viewModelScope.launch { callSession.setVideoEnabled(next) }
    }

    fun setSpeakerphone(enabled: Boolean) {
        viewModelScope.launch { callSession.setSpeakerphone(enabled) }
    }

    fun consumeCallPlaced() {
        _callPlaced.value = false
    }
}
