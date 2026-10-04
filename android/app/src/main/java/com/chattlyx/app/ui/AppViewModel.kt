package com.chattlyx.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chattlyx.core.common.dispatchers.ChattlyxDispatcher
import com.chattlyx.core.common.dispatchers.Dispatcher
import com.chattlyx.core.datastore.AppearanceSettings
import com.chattlyx.core.datastore.SettingsRepository
import com.chattlyx.core.push.PushTokenRegistrar
import com.chattlyx.core.push.PushWakeHandler
import com.chattlyx.data.messaging.RealtimeCoordinator
import com.chattlyx.domain.auth.usecases.HasActiveSessionUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Root gate (AUTH-01): shows onboarding until a session exists. */
enum class RootGate { LOADING, ONBOARDING, MAIN }

@HiltViewModel
class AppViewModel @Inject constructor(
    settingsRepository: SettingsRepository,
    private val hasActiveSessionUseCase: HasActiveSessionUseCase,
    private val realtimeCoordinator: RealtimeCoordinator,
    private val pushWakeHandler: PushWakeHandler,
    @Dispatcher(ChattlyxDispatcher.DEFAULT) private val dispatcher: CoroutineDispatcher,
) : ViewModel() {

    val appearance: Flow<AppearanceSettings> = settingsRepository.appearance

    private val _gate = MutableStateFlow(RootGate.LOADING)
    val gate: StateFlow<RootGate> = _gate.asStateFlow()

    init {
        viewModelScope.launch(dispatcher) {
            val registered = hasActiveSessionUseCase()
            _gate.value = if (registered) RootGate.MAIN else RootGate.ONBOARDING
            if (registered) startRealtime()
        }
    }

    /** Called when onboarding finishes successfully. */
    fun completeOnboarding() {
        _gate.value = RootGate.MAIN
        startRealtime()
    }

    /** Sign out / account deletion: back to onboarding (AUTH-10). */
    fun sessionEnded() {
        viewModelScope.launch(dispatcher) {
            realtimeCoordinator.stop()
            _gate.value = RootGate.ONBOARDING
        }
    }

    private fun startRealtime() {
        realtimeCoordinator.start()
        PushTokenRegistrar.registerCurrent { token ->
            viewModelScope.launch(dispatcher) {
                // NOT-01: token registration is idempotent server-side.
                pushWakeHandler.onTokenRefreshed(token)
            }
        }
    }
}
