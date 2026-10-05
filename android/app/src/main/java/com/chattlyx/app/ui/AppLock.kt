package com.chattlyx.app.ui

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.chattlyx.app.R
import com.chattlyx.core.datastore.SettingsRepository
import com.chattlyx.core.designsystem.component.ChattlyxButton
import com.chattlyx.core.designsystem.component.ChattlyxButtonVariant
import com.chattlyx.core.designsystem.icon.ChattlyxIcons
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn

/**
 * AUTH-* (Phase 8): app-lock gate state. Locked state arms every time the
 * process goes to the background; unlocking requires a successful
 * BiometricPrompt. Preference reads are asynchronous, so the very first
 * frames after a cold start render unlocked — documented, matches the
 * DataStore-read timing of every preference-driven screen.
 */
@HiltViewModel
class AppLockGateViewModel @Inject constructor(
    settingsRepository: SettingsRepository,
) : ViewModel() {

    val appLockEnabled: StateFlow<Boolean> = settingsRepository.appLockEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    private val _locked = MutableStateFlow(false)
    val locked: StateFlow<Boolean> = _locked.asStateFlow()

    fun lock() {
        _locked.value = true
    }

    fun unlock() {
        _locked.value = false
    }
}

/**
 * Overlays [content] with a lock surface while the gate is armed. Content
 * stays composed underneath so navigation/UI state survives locking.
 */
@Composable
fun AppLockGate(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val viewModel: AppLockGateViewModel = hiltViewModel()
    val enabled by viewModel.appLockEnabled.collectAsStateWithLifecycle()
    val locked by viewModel.locked.collectAsStateWithLifecycle()
    val activity = LocalContext.current as? FragmentActivity

    // Re-arm whenever the whole process leaves the foreground.
    DisposableEffect(Unit) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) viewModel.lock()
        }
        ProcessLifecycleOwner.get().lifecycle.addObserver(observer)
        onDispose { ProcessLifecycleOwner.get().lifecycle.removeObserver(observer) }
    }

    val gateUp = enabled && locked
    LaunchedEffect(gateUp) {
        if (gateUp && activity != null) {
            authenticateToUnlock(activity) { viewModel.unlock() }
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        content()
        if (gateUp) {
            LockSurface(
                onUnlock = {
                    if (activity != null) {
                        authenticateToUnlock(activity) { viewModel.unlock() }
                    }
                },
            )
        }
    }
}

@Composable
private fun LockSurface(onUnlock: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = ChattlyxIcons.Lock,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.app_lock_title),
                style = MaterialTheme.typography.headlineSmall,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.app_lock_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(24.dp))
            ChattlyxButton(
                text = stringResource(R.string.app_lock_unlock),
                onClick = onUnlock,
                variant = ChattlyxButtonVariant.FILLED,
            )
        }
    }
}

private fun authenticateToUnlock(activity: FragmentActivity, onSuccess: () -> Unit) {
    val prompt = BiometricPrompt(
        activity,
        ContextCompat.getMainExecutor(activity),
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                onSuccess()
            }
        },
    )
    val info = BiometricPrompt.PromptInfo.Builder()
        .setTitle(activity.getString(R.string.app_lock_prompt_title))
        .setSubtitle(activity.getString(R.string.app_lock_prompt_subtitle))
        .setAllowedAuthenticators(
            BiometricManager.Authenticators.BIOMETRIC_STRONG or
                BiometricManager.Authenticators.DEVICE_CREDENTIAL,
        )
        .build()
    prompt.authenticate(info)
}
