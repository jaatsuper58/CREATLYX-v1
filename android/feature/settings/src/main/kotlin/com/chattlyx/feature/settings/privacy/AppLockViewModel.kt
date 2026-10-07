package com.chattlyx.feature.settings.privacy

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chattlyx.core.datastore.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * AUTH-* (Phase 8): app-lock preference behind the Settings → Privacy row.
 * Enabling requires the caller to complete a BiometricPrompt confirmation
 * first (the UI performs it, then calls [enable]); disabling is immediate.
 */
@HiltViewModel
class AppLockViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    val appLockEnabled: StateFlow<Boolean> = settingsRepository.appLockEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /** Persist after a successful BiometricPrompt confirmation. */
    fun enable() {
        viewModelScope.launch { settingsRepository.setAppLockEnabled(true) }
    }

    fun disable() {
        viewModelScope.launch { settingsRepository.setAppLockEnabled(false) }
    }
}
