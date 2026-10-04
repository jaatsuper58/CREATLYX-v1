package com.chattlyx.feature.settings.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chattlyx.core.common.dispatchers.ChattlyxDispatcher
import com.chattlyx.core.common.dispatchers.Dispatcher
import com.chattlyx.core.common.error.ChattlyError
import com.chattlyx.core.common.result.Result
import com.chattlyx.domain.auth.usecases.GetProfileUseCase
import com.chattlyx.domain.auth.usecases.SignOutUseCase
import com.chattlyx.domain.auth.usecases.UpdateProfileUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** S36 account screen state. */
data class AccountState(
    val loading: Boolean = true,
    val displayName: String = "",
    val username: String = "",
    val about: String = "",
    val avatarBlobId: String? = null,
    val saving: Boolean = false,
    val savedFlash: Boolean = false,
    val errorRes: Int? = null,
    val signedOut: Boolean = false,
)

@HiltViewModel
class AccountViewModel @Inject constructor(
    private val getProfileUseCase: GetProfileUseCase,
    private val updateProfileUseCase: UpdateProfileUseCase,
    private val signOutUseCase: SignOutUseCase,
    @Dispatcher(ChattlyxDispatcher.DEFAULT) private val dispatcher: CoroutineDispatcher,
) : ViewModel() {

    private val _state = MutableStateFlow(AccountState())
    val state: StateFlow<AccountState> = _state.asStateFlow()

    init {
        load()
    }

    private fun load() {
        viewModelScope.launch(dispatcher) {
            when (val result = getProfileUseCase()) {
                is Result.Success -> _state.update {
                    it.copy(
                        loading = false,
                        displayName = result.value.displayName,
                        username = result.value.username.orEmpty(),
                        about = result.value.about,
                        avatarBlobId = result.value.avatarBlobId,
                    )
                }

                is Result.Failure -> _state.update {
                    it.copy(loading = false, errorRes = com.chattlyx.feature.settings.R.string.settings_account_error_generic)
                }
            }
        }
    }

    fun onNameChange(value: String) = _state.update { it.copy(displayName = value.take(40), errorRes = null) }
    fun onUsernameChange(value: String) = _state.update {
        it.copy(username = value.lowercase().filter { c -> c.isLetterOrDigit() || c == '_' }, errorRes = null)
    }
    fun onAboutChange(value: String) = _state.update { it.copy(about = value.take(140), errorRes = null) }

    fun save() {
        val current = _state.value
        if (current.saving) return
        viewModelScope.launch(dispatcher) {
            _state.update { it.copy(saving = true, errorRes = null) }
            when (
                val result = updateProfileUseCase(
                    displayName = current.displayName,
                    username = current.username.ifBlank { null },
                    about = current.about,
                    avatarBlobId = current.avatarBlobId,
                )
            ) {
                is Result.Success -> _state.update { it.copy(saving = false, savedFlash = true) }
                is Result.Failure -> _state.update {
                    val error = result.error
                    it.copy(saving = false, errorRes = error.toMessageRes())
                }
            }
        }
    }

    fun dismissSavedFlash() = _state.update { it.copy(savedFlash = false) }

    private fun ChattlyError.toMessageRes(): Int = when {
        this is ChattlyError.Server && code == "profile/username-taken" ->
            com.chattlyx.feature.settings.R.string.settings_account_username_taken

        else -> com.chattlyx.feature.settings.R.string.settings_account_error_generic
    }

    fun signOut() {
        viewModelScope.launch(dispatcher) {
            signOutUseCase()
            _state.update { it.copy(signedOut = true) }
        }
    }
}
